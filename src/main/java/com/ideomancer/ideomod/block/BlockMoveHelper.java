package com.ideomancer.ideomod.block;

import com.google.common.collect.Maps;
import com.ideomancer.ideomod.Ideomod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * General-purpose utility for moving blocks using vanilla piston mechanics.
 *
 * Any block that needs to push/pull other blocks at a distance can call
 * {@link #moveBlockAt} with three parameters:
 *   1. initiatorPos -- who is causing the move (protected from being overwritten)
 *   2. targetPos    -- the block to move (can be non-adjacent to initiator)
 *   3. pushDir      -- direction of movement
 *
 * Everything else (structure resolution, animation, push limits, stickiness
 * rules, sound) comes along for free from vanilla's piston system.
 *
 * CALLERS:
 * ========
 *   - RangedPistonUtil (Repulser/Attractor) -- scans a line, then calls here
 *   - MachineTrackBlock -- knows the Trolley is adjacent, calls here directly
 *   - Future: any system that needs piston-style block movement
 *
 * HOW IT WORKS (mirrors vanilla PistonBaseBlock.moveBlocks()):
 * ===========================================================
 *   1. Set ThreadLocal target so mixin redirects resolver's startPos
 *   2. Create PistonStructureResolver with initiatorPos as pistonPos
 *      (safe, far from action -- mixin redirects to real target)
 *   3. Resolve the full move group (respects push limits, stickiness, etc.)
 *   4. Safety check: no block may land on initiatorPos
 *   5. Snapshot states, destroy breakables, place MovingPistonBlock at
 *      destinations (sources still exist), clear vacated sources, run
 *      neighbor updates, play sound
 *
 * MUST be called from triggerEvent() (or equivalent block event handler)
 * so that it runs on BOTH client and server. This is required for
 * PistonMovingBlockEntity rendering -- see BLOCK_EVENT_LESSON.md.
 */
public class BlockMoveHelper {

    /**
     * Move the block (and its piston group) at targetPos in pushDir direction.
     *
     * @param level         The world (Level, not ServerLevel -- must run on both sides)
     * @param initiatorPos  Position of the block causing the move (protected from overwrite)
     * @param targetPos     Position of the block to move
     * @param pushDir       Direction to move the target
     * @return true if the move succeeded, false if blocked
     */
    public static boolean moveBlockAt(Level level, BlockPos initiatorPos,
                                       BlockPos targetPos, Direction pushDir) {

        // ---- STEP 1: Resolve the move group ----
        List<BlockPos> toPush;
        List<BlockPos> toDestroy;

        RangedPistonFlags.setTarget(targetPos);
        try {
            PistonStructureResolver resolver = new PistonStructureResolver(
                    level, initiatorPos, pushDir, true
            );
            if (!resolver.resolve()) {
                Ideomod.LOGGER.debug("BlockMoveHelper at {} -- resolve failed", initiatorPos);
                return false;
            }
            toPush = resolver.getToPush();
            toDestroy = resolver.getToDestroy();
        } finally {
            RangedPistonFlags.clear();
        }

        // Basic sanity checks
        if (toPush.isEmpty()) return false;
        if (!toPush.contains(targetPos)) return false;
        if (toPush.contains(initiatorPos)) return false;

        // Safety: reject if ANY block's destination is the initiator itself.
        // Prevents sympathetic/sticky blocks from being pushed into and
        // overwriting the device. Mirrors how vanilla pistons can't push
        // blocks into the piston base.
        for (BlockPos pushPos : toPush) {
            if (pushPos.relative(pushDir).equals(initiatorPos)) {
                Ideomod.LOGGER.debug("BlockMoveHelper at {} -- blocked, block at {} would move into initiator",
                        initiatorPos, pushPos);
                return false;
            }
        }

        // ---- STEP 2: Snapshot block states (mirrors vanilla lines 277-282) ----
        Map<BlockPos, BlockState> map = Maps.newHashMap();
        List<BlockState> movedStates = new ArrayList<>();

        for (BlockPos pos : toPush) {
            BlockState state = level.getBlockState(pos);
            movedStates.add(state);
            map.put(pos, state);
        }

        BlockState[] allOriginalStates = new BlockState[toPush.size() + toDestroy.size()];
        int stateIdx = 0;

        // ---- STEP 3: Destroy breakable blocks (mirrors vanilla lines 289-301) ----
        for (int k = toDestroy.size() - 1; k >= 0; --k) {
            BlockPos destroyPos = toDestroy.get(k);
            BlockState destroyState = level.getBlockState(destroyPos);
            BlockEntity be = destroyState.hasBlockEntity() ? level.getBlockEntity(destroyPos) : null;
            Block.dropResources(destroyState, level, destroyPos, be);
            level.setBlock(destroyPos, Blocks.AIR.defaultBlockState(), 18);
            allOriginalStates[stateIdx++] = destroyState;
        }

        // ---- STEP 4: Place MovingPistonBlock at destinations (mirrors vanilla lines 303-312) ----
        for (int l = toPush.size() - 1; l >= 0; --l) {
            BlockPos sourcePos = toPush.get(l);
            BlockState sourceState = level.getBlockState(sourcePos);
            BlockPos destPos = sourcePos.relative(pushDir);
            map.remove(destPos);

            BlockState movingState = Blocks.MOVING_PISTON.defaultBlockState()
                    .setValue(PistonBaseBlock.FACING, pushDir);
            level.setBlock(destPos, movingState, 68);
            level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(
                    destPos, movingState, movedStates.get(l),
                    pushDir,
                    true,   // extending
                    false   // NOT a piston head
            ));
            allOriginalStates[stateIdx++] = sourceState;
        }

        // ---- STEP 5: Clear vacated source positions to air (mirrors vanilla lines 324-327) ----
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos vacatedPos : map.keySet()) {
            level.setBlock(vacatedPos, air, 82);
        }

        // ---- STEP 6: Neighbor updates (mirrors vanilla lines 329-348) ----
        for (Map.Entry<BlockPos, BlockState> entry : map.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            state.updateIndirectNeighbourShapes(level, pos, 2);
            air.updateNeighbourShapes(level, pos, 2);
            air.updateIndirectNeighbourShapes(level, pos, 2);
        }

        stateIdx = 0;
        for (int i1 = toDestroy.size() - 1; i1 >= 0; --i1) {
            BlockState origState = allOriginalStates[stateIdx++];
            BlockPos destroyPos = toDestroy.get(i1);
            origState.updateIndirectNeighbourShapes(level, destroyPos, 2);
            level.updateNeighborsAt(destroyPos, origState.getBlock());
        }

        for (int j1 = toPush.size() - 1; j1 >= 0; --j1) {
            level.updateNeighborsAt(toPush.get(j1), allOriginalStates[stateIdx++].getBlock());
        }

        // ---- STEP 7: Sound effect ----
        level.playSound(null, initiatorPos, SoundEvents.PISTON_EXTEND,
                SoundSource.BLOCKS, 0.5F, 0.8F);

        Ideomod.LOGGER.debug("BlockMoveHelper at {} -- moved {} blocks {}",
                initiatorPos, toPush.size(), pushDir);

        return true;
    }
}
