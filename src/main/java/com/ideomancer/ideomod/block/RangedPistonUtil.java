package com.ideomancer.ideomod.block;

import com.google.common.collect.Maps;
import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Shared utility for the Repulser/Attractor family of ranged piston blocks.
 *
 * The movement logic closely mirrors vanilla PistonBaseBlock.moveBlocks()
 * to inherit all of Mojang's edge-case handling: entity pushing with
 * precise hitbox math, proper update ordering, light updates, neighbor
 * notifications, waterlogging, and interaction with other moving pistons.
 *
 * HOW IT WORKS:
 * =============
 *   1. SCAN: Walk a line in FACING direction, find target block.
 *   2. RESOLVE: Use PistonStructureResolver to get the full move group.
 *      The resolver is given the DEVICE position as pistonPos (safe,
 *      far from the action). A ThreadLocal in RangedPistonFlags tells
 *      our mixin to redirect the resolver's startPos to the actual
 *      target block.
 *   3. MOVE: Following vanilla's moveBlocks() pattern exactly:
 *      a) Snapshot block states
 *      b) Destroy breakable blocks (drop resources, set to air)
 *      c) Place MovingPistonBlock + PistonMovingBlockEntity at each
 *         DESTINATION -- sources still exist at this point
 *      d) Set vacated source positions to air (flag 82)
 *      e) Run neighbor/indirect-neighbor updates
 *   4. SOUND: Play piston extend sound from device position.
 *
 * KEY DIFFERENCE FROM VANILLA:
 * ============================
 * The only differences from vanilla moveBlocks() are:
 *   - No piston head block entity (we have no piston head)
 *   - pistonPos is the device, not a real piston
 *   - startPos is redirected via mixin (see PistonStructureResolverMixin)
 *   - Runs during neighborChanged, not during blockEvent phase
 *     (vanilla pistons use the blockEvent system for timing)
 */
public class RangedPistonUtil {

    /** How far the device scans for targets. */
    public static final int SCAN_RANGE = 12;

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Repulser: find nearest target, push away from device.
     */
    public static boolean tryRepulse(Level level, BlockPos devicePos,
                                      Direction facing, Predicate<BlockState> filter) {
        BlockPos targetPos = scanForNearest(level, devicePos, facing, filter);
        if (targetPos == null) return false;
        Direction pushDir = facing;
        return resolveAndMove(level, devicePos, targetPos, pushDir);
    }

    /**
     * Attractor: find farthest target, pull toward device.
     * Implemented as a "reverse push" -- pushDir points toward the device.
     */
    public static boolean tryAttract(Level level, BlockPos devicePos,
                                      Direction facing, Predicate<BlockState> filter) {
        BlockPos targetPos = scanForFarthest(level, devicePos, facing, filter);
        if (targetPos == null) return false;
        Direction pushDir = facing.getOpposite();
        return resolveAndMove(level, devicePos, targetPos, pushDir);
    }

    // ========================================================================
    // SCANNING
    // ========================================================================

    private static BlockPos scanForNearest(Level level, BlockPos devicePos,
                                            Direction facing, Predicate<BlockState> filter) {
        for (int dist = 1; dist <= SCAN_RANGE; dist++) {
            BlockPos checkPos = devicePos.relative(facing, dist);
            if (filter.test(level.getBlockState(checkPos))) return checkPos;
        }
        return null;
    }

    private static BlockPos scanForFarthest(Level level, BlockPos devicePos,
                                             Direction facing, Predicate<BlockState> filter) {
        BlockPos farthest = null;
        for (int dist = 1; dist <= SCAN_RANGE; dist++) {
            BlockPos checkPos = devicePos.relative(facing, dist);
            if (filter.test(level.getBlockState(checkPos))) farthest = checkPos;
        }
        return farthest;
    }

    // ========================================================================
    // RESOLVE AND MOVE
    // ========================================================================

    /**
     * Mirrors vanilla PistonBaseBlock.moveBlocks() as closely as possible.
     * See that method for the canonical version of this logic.
     */
    private static boolean resolveAndMove(Level level, BlockPos devicePos,
                                           BlockPos targetPos, Direction pushDir) {

        // ---- STEP 1: Resolve the move group ----
        List<BlockPos> toPush;
        List<BlockPos> toDestroy;

        RangedPistonFlags.setTarget(targetPos);
        try {
            PistonStructureResolver resolver = new PistonStructureResolver(
                    level, devicePos, pushDir, true
            );
            if (!resolver.resolve()) {
                Ideomod.LOGGER.debug("Ranged piston at {} -- resolve failed", devicePos);
                return false;
            }
            toPush = resolver.getToPush();
            toDestroy = resolver.getToDestroy();
        } finally {
            RangedPistonFlags.clear();
        }

        if (toPush.isEmpty()) return false;
        if (!toPush.contains(targetPos)) return false;
        if (toPush.contains(devicePos)) return false;

        // ---- STEP 2: Snapshot block states (mirrors vanilla lines 277-282) ----
        // map tracks source positions that need to be cleared to air later
        Map<BlockPos, BlockState> map = Maps.newHashMap();
        List<BlockState> movedStates = new ArrayList<>();

        for (BlockPos pos : toPush) {
            BlockState state = level.getBlockState(pos);
            movedStates.add(state);
            map.put(pos, state);
        }

        // Track all original states for neighbor updates (vanilla uses ablockstate[])
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
        // Iterate in reverse (farthest first), place at DESTINATION.
        // Source blocks still exist in the world at this point -- this matches vanilla.
        Direction direction = pushDir; // extending = true, so direction = pushDir

        for (int l = toPush.size() - 1; l >= 0; --l) {
            BlockPos sourcePos = toPush.get(l);
            BlockState sourceState = level.getBlockState(sourcePos);
            // Shift to destination (mirrors: blockpos3 = blockpos3.relative(direction))
            BlockPos destPos = sourcePos.relative(direction);
            map.remove(destPos); // Don't clear this position to air later

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
        // Only positions that weren't overwritten by a MovingPistonBlock destination
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos vacatedPos : map.keySet()) {
            level.setBlock(vacatedPos, air, 82);
        }

        // ---- STEP 6: Neighbor updates (mirrors vanilla lines 329-348) ----
        // Indirect neighbor updates for vacated positions
        for (Map.Entry<BlockPos, BlockState> entry : map.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            state.updateIndirectNeighbourShapes(level, pos, 2);
            air.updateNeighbourShapes(level, pos, 2);
            air.updateIndirectNeighbourShapes(level, pos, 2);
        }

        // Neighbor updates for destroyed positions
        stateIdx = 0;
        for (int i1 = toDestroy.size() - 1; i1 >= 0; --i1) {
            BlockState origState = allOriginalStates[stateIdx++];
            BlockPos destroyPos = toDestroy.get(i1);
            origState.updateIndirectNeighbourShapes(level, destroyPos, 2);
            level.updateNeighborsAt(destroyPos, origState.getBlock());
        }

        // Neighbor updates for moved positions (from source positions)
        for (int j1 = toPush.size() - 1; j1 >= 0; --j1) {
            level.updateNeighborsAt(toPush.get(j1), allOriginalStates[stateIdx++].getBlock());
        }

        // ---- STEP 7: Sound effect ----
        level.playSound(null, devicePos, SoundEvents.PISTON_EXTEND,
                SoundSource.BLOCKS, 0.5F, 0.8F);

        Ideomod.LOGGER.debug("Ranged piston at {} -- moving {} blocks {}",
                devicePos, toPush.size(), pushDir);

        return true;
    }

    // ========================================================================
    // FILTER PREDICATES
    // ========================================================================

    /** Only Oddstone blocks. Used by Oddstone Repulser/Attractor. */
    public static final Predicate<BlockState> ODDSTONE_ONLY =
            state -> state.is(ModBlocks.ODDSTONE.get());

    /** Any piston-movable block. Used by Arcane variants (future). */
    public static final Predicate<BlockState> ANY_MOVABLE = state -> {
        if (state.isAir()) return false;
        if (state.getDestroySpeed(null, BlockPos.ZERO) < 0) return false;
        PushReaction reaction = state.getPistonPushReaction();
        return reaction != PushReaction.BLOCK && reaction != PushReaction.IGNORE;
    };
}
