package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.movement.IPistonMovable;
import com.ideomancer.ideomod.block.movement.MovingBlockEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin into PistonBaseBlock for two Ideomod features:
 *
 * FEATURE 1: ALLOW MOVABLE BLOCK ENTITIES (isPushable)
 * =====================================================
 * Vanilla hardcodes "block entities can't move" in isPushable().
 * We bypass this for block entities implementing IPistonMovable.
 *
 * FEATURE 2: SAVE BLOCK ENTITY DATA BEFORE VANILLA PISTON MOVES (moveBlocks)
 * ============================================================================
 * We save IPistonMovable block entity data at HEAD of moveBlocks(),
 * before vanilla destroys the block entities during the move.
 *
 * For EXTENSION (extending=true):
 *   We construct our own PistonStructureResolver to find which blocks
 *   will move, then save their data keyed by destination position.
 *
 * For RETRACTION (extending=false):
 *   Our resolver returns false (likely due to block entity checks deep
 *   in the resolver's internal logic), but vanilla still moves the blocks.
 *   Instead of fighting the resolver, we directly check the pull position
 *   (pistonPos + direction * 2) and scan the sticky group attached to it.
 *
 * Restore is handled by PistonMovingBlockEntityMixin.
 *
 * CRITICAL: moveBlocks runs on BOTH client and server (via triggerEvent).
 * We must only save on the server side. Client-side block entities don't
 * have synced inventory data, so saving on the client would overwrite the
 * server's good data with empty data in the shared static map.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {

    // ========================================================================
    // FEATURE 1: ALLOW MOVABLE BLOCK ENTITIES
    // ========================================================================

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void ideomod$allowMovableBlockEntities(
            BlockState blockState, Level level, BlockPos pos,
            Direction pushDirection, boolean allowDestroy, Direction pistonFacing,
            CallbackInfoReturnable<Boolean> cir) {

        if (!blockState.hasBlockEntity()) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IPistonMovable) {
            cir.setReturnValue(true);
        }
    }

    // ========================================================================
    // FEATURE 2: SAVE BLOCK ENTITY DATA BEFORE VANILLA PISTON MOVES
    // ========================================================================

    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private void ideomod$saveMovableBlockEntityData(
            Level level, BlockPos pistonPos, Direction direction,
            boolean extending, CallbackInfoReturnable<Boolean> cir) {

        if (level.isClientSide()) return;

        String dimensionKey = level.dimension().location().toString();

        if (extending) {
            saveForExtension(level, pistonPos, direction, dimensionKey);
        } else {
            saveForRetraction(level, pistonPos, direction, dimensionKey);
        }
    }

    /**
     * Extension: construct a resolver, iterate toPush, save IPistonMovable data.
     * The resolver works reliably for extension because our isPushable mixin
     * handles the block entity check.
     */
    private void saveForExtension(Level level, BlockPos pistonPos,
                                   Direction direction, String dimensionKey) {
        PistonStructureResolver resolver = new PistonStructureResolver(
                level, pistonPos, direction, true
        );

        if (!resolver.resolve()) return;

        List<BlockPos> toPush = resolver.getToPush();
        if (toPush.isEmpty()) return;

        for (BlockPos sourcePos : toPush) {
            saveBlockEntityAt(level, sourcePos, sourcePos.relative(direction), dimensionKey);
        }
    }

    /**
     * Retraction: save block entity data for blocks being pulled by a sticky piston.
     *
     * We cannot rely on PistonStructureResolver for retraction -- it returns false
     * for our IPistonMovable blocks (likely due to internal isPushable calls with
     * different parameters than what our HEAD mixin intercepts). But vanilla's own
     * moveBlocks DOES move the blocks successfully.
     *
     * Instead, we directly check the pull position and scan the sticky group.
     *
     * The pulled block is at pistonPos + direction * 2 (one past the piston head).
     * Movement direction for retraction is direction.getOpposite() (toward piston).
     * So destination = sourcePos.relative(direction.getOpposite()).
     */
    private void saveForRetraction(Level level, BlockPos pistonPos,
                                    Direction direction, String dimensionKey) {
        Direction moveDir = direction.getOpposite(); // blocks move toward the piston

        // The block being pulled is 2 spaces out from the piston base
        // (1 for the head + 1 for the pulled block)
        BlockPos pullPos = pistonPos.relative(direction, 2);
        BlockState pullState = level.getBlockState(pullPos);

        if (pullState.isAir()) return;

        // Save the pulled block itself
        BlockPos destPos = pullPos.relative(moveDir);
        saveBlockEntityAt(level, pullPos, destPos, dimensionKey);

        // If the pulled block is sticky, scan its group for more blocks
        if (pullState.isStickyBlock()) {
            scanStickyGroup(level, pullPos, moveDir, dimensionKey);
        }

        Ideomod.LOGGER.info("[PistonBaseMixin] Retraction save complete for pull pos {}", pullPos);
    }

    /**
     * Scan for additional IPistonMovable blocks attached to the pulled block
     * via stickiness. This handles cases where a sticky Machine Frame drags
     * other Machine Frames during retraction.
     *
     * Uses breadth-first scan checking stickiness relationships.
     * Any IPistonMovable block entity found in the group gets its data saved.
     */
    private void scanStickyGroup(Level level, BlockPos startPos,
                                  Direction moveDir, String dimensionKey) {
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.LinkedList<BlockPos> queue = new java.util.LinkedList<>();
        visited.add(startPos); // start block already saved by caller

        // Seed with neighbors of the start block
        for (Direction dir : Direction.values()) {
            queue.add(startPos.relative(dir));
        }

        int limit = 12; // vanilla piston push limit
        int count = 0;

        while (!queue.isEmpty() && count < limit) {
            BlockPos pos = queue.poll();
            if (visited.contains(pos)) continue;
            visited.add(pos);

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            // Check if this block is stuck to any block already in the group
            boolean stuckToGroup = false;
            for (Direction dir : Direction.values()) {
                BlockPos adjacentInGroup = pos.relative(dir);
                if (visited.contains(adjacentInGroup) && !adjacentInGroup.equals(pos)) {
                    BlockState adjacentState = level.getBlockState(adjacentInGroup);
                    if (adjacentState.isStickyBlock()
                            && adjacentState.canStickTo(state)
                            && state.canStickTo(adjacentState)) {
                        stuckToGroup = true;
                        break;
                    }
                }
            }

            if (!stuckToGroup) continue;
            count++;

            // Save this block's data if it's IPistonMovable
            BlockPos destPos = pos.relative(moveDir);
            saveBlockEntityAt(level, pos, destPos, dimensionKey);

            // If this block is also sticky, scan its neighbors too
            if (state.isStickyBlock()) {
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = pos.relative(dir);
                    if (!visited.contains(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }
    }

    /**
     * Save a single block entity's IPistonMovable data if applicable.
     */
    private void saveBlockEntityAt(Level level, BlockPos sourcePos,
                                    BlockPos destPos, String dimensionKey) {
        BlockEntity be = level.getBlockEntity(sourcePos);
        if (be instanceof IPistonMovable movable) {
            CompoundTag data = movable.saveMovementData();
            if (data != null) {
                MovingBlockEntityData.store(dimensionKey, destPos, data);
                Ideomod.LOGGER.info("[PistonBaseMixin] SAVED IPistonMovable data from {} -> dest {} (tags: {})",
                        sourcePos, destPos, data.getAllKeys());
            }
        }
    }
}
