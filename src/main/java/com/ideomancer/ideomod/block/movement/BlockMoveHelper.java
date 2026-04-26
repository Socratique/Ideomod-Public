package com.ideomancer.ideomod.block.movement;

import com.google.common.collect.Maps;
import com.ideomancer.ideomod.Ideomod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ideomancer.ideomod.block.piston.RangedPistonFlags;
import com.ideomancer.ideomod.block.HollowstoneBlock;
import com.ideomancer.ideomod.block.HollowstoneBlockEntity;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.client.render.MovingFrameData;
import net.minecraft.resources.ResourceLocation;

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
 * BLOCK ENTITY MOVEMENT:
 * ======================
 * Block entities that implement {@link IPistonMovable} get their data
 * serialized before the move and restored after the 2-tick animation.
 * This is an OPT-IN system -- only block entities that explicitly implement
 * the interface are affected. Vanilla block entities are untouched.
 *
 * The save/restore flow:
 *   1. During snapshot phase, check each block in toPush for IPistonMovable
 *   2. If found, call saveMovementData() and store the CompoundTag in
 *      MovingBlockEntityData keyed by DESTINATION position
 *   3. After the 2-tick animation completes, PistonMovingBlockEntityMixin
 *      retrieves the saved data and loads it into the fresh block entity.
 *
 * CRITICAL: The save phase (step 2.5) must ONLY run on the server side.
 * This method is called from triggerEvent() so it runs on both client and
 * server. Client-side block entities don't have synced inventory data, so
 * client saves would overwrite server saves with empty data in the shared
 * static MovingBlockEntityData map.
 *
 * CALLERS:
 * ========
 *   - RangedPistonUtil (Repulser/Attractor) -- scans a line, then calls here
 *   - MachineTrackBlock -- knows the Trolley is adjacent, calls here directly
 *   - Future: any system that needs piston-style block movement
 *
 * MUST be called from triggerEvent() (or equivalent block event handler)
 * so that it runs on BOTH client and server. This is required for
 * PistonMovingBlockEntity rendering -- see BLOCK_EVENT_LESSON.md.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
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

        // ---- STEP 2.5: Save IPistonMovable block entity data (Ideomod addition) ----
        // CRITICAL: Only save on server side. Client block entities don't have
        // synced inventories, so client saves overwrite server saves with empty
        // data. MovingBlockEntityData is a shared static map -- client writes
        // clobber server writes with the same key. This was the cause of the
        // "item deleted on move" bug for BlockMoveHelper-driven systems.
        String dimensionKey = level.dimension().location().toString();
        Map<BlockPos, CompoundTag> savedBlockEntityData = new HashMap<>();

        if (!level.isClientSide()) {
            for (int i = 0; i < toPush.size(); i++) {
                BlockPos sourcePos = toPush.get(i);
                BlockEntity be = level.getBlockEntity(sourcePos);

                if (be instanceof IPistonMovable movable) {
                    BlockPos destPos = sourcePos.relative(pushDir);
                    CompoundTag data = movable.saveMovementData();
                    if (data != null) {
                        savedBlockEntityData.put(destPos, data);
                        Ideomod.LOGGER.info("[BlockMoveHelper] SAVED IPistonMovable data from {} -> dest {} (tags: {})",
                                sourcePos, destPos, data.getAllKeys());
                    }
                }
            }
        }

        // ---- STEP 2.6: Capture framed block render data on client (Ideomod addition) ----
        // BlockMoveHelper is called from triggerEvent (runs on both sides).
        // On the client, vanilla's PistonBaseBlock.moveBlocks() mixin won't fire
        // because BlockMoveHelper doesn't call moveBlocks(). We must populate
        // MovingFrameData here so the transit renderer can draw inner blocks.
        // The toPush list already includes sympathetic blocks (resolver mixin
        // adds them during addBranchingBlocks), so no separate sympathetic
        // scan is needed.
        if (level.isClientSide()) {
            MovingFrameData.clearAll();
            for (BlockPos sourcePos : toPush) {
                BlockState srcState = level.getBlockState(sourcePos);
                if (srcState.getBlock() instanceof FramedBlock) {
                    BlockEntity srcBe = level.getBlockEntity(sourcePos);
                    if (srcBe instanceof FramedBlockEntity framedBE && framedBE.hasInnerBlock()) {
                        ResourceLocation innerBlockId = framedBE.getInnerBlockId();
                        if (innerBlockId != null) {
                            Direction facing = srcState.getValue(FramedBlock.FACING);
                            int innerSticky = srcState.getValue(FramedBlock.INNER_STICKY);
                            BlockPos destPos = sourcePos.relative(pushDir);
                            MovingFrameData.store(destPos, innerBlockId, facing, innerSticky);
                        }
                    }
                } else if (srcState.getBlock() instanceof HollowstoneBlock) {
                    BlockEntity srcBe = level.getBlockEntity(sourcePos);
                    if (srcBe instanceof HollowstoneBlockEntity hollowBE) {
                        ResourceLocation innerBlockId = hollowBE.getInnerBlockId();
                        if (innerBlockId != null) {
                            BlockPos destPos = sourcePos.relative(pushDir);
                            // Dummy facing/innerSticky — renderer uses defaultBlockState for hollowstone
                            MovingFrameData.store(destPos, innerBlockId, Direction.NORTH, 0);
                        }
                    }
                }
            }
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

        // ---- STEP 4.5: Register saved block entity data for restoration (Ideomod addition) ----
        // Now that blocks are in transit, store the saved data so that when
        // PistonMovingBlockEntityMixin fires at tick completion, the data can
        // be retrieved and restored into the fresh block entity.
        for (Map.Entry<BlockPos, CompoundTag> entry : savedBlockEntityData.entrySet()) {
            MovingBlockEntityData.store(dimensionKey, entry.getKey(), entry.getValue());
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

        Ideomod.LOGGER.debug("BlockMoveHelper at {} -- moved {} blocks {} ({} with block entity data)",
                initiatorPos, toPush.size(), pushDir, savedBlockEntityData.size());

        return true;
    }

    // ========================================================================
    // NEIGHBOR UPDATE UTILITY
    // ========================================================================

    /**
     * Notifies all 6 neighbors of a position about a block change.
     * Replaces the repeated for(Direction) { level.updateNeighborsAt(...) } pattern.
     *
     * @param level The world
     * @param pos   The block position
     * @param block The block that changed
     */
    public static void updateAllNeighbors(Level level, BlockPos pos, Block block) {
        for (Direction dir : Direction.values()) {
            level.updateNeighborsAt(pos.relative(dir), block);
        }
    }
}
