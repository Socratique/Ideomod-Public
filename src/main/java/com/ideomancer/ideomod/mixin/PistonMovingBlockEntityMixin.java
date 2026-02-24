package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.movement.IPistonMovable;
import com.ideomancer.ideomod.block.movement.MovingBlockEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into PistonMovingBlockEntity to restore IPistonMovable block entity
 * data after piston animation completes.
 *
 * This is the RESTORE phase of the piston movement data pipeline:
 *   SAVE:    PistonBaseBlockMixin (vanilla pistons) or BlockMoveHelper (our systems)
 *   TRANSIT: MovingBlockEntityData holds the data during animation
 *   RESTORE: This mixin (runs for ALL piston completions)
 *
 * TWO RESTORE PATHS:
 *   1. tick() TAIL — normal 2-tick piston animation. Block transitions from
 *      MOVING_PISTON to real block on the final tick.
 *   2. finalTick() TAIL — 1-tick (0-tick) piston pulses. Vanilla calls
 *      finalTick() instead of the normal tick() cycle, which immediately
 *      places the real block. Without hooking this, data is lost on 1-tick
 *      pulses because tick() never sees the "real block placed" condition.
 *
 * CRITICAL: Do NOT add competing retrieval logic elsewhere (e.g. Block.onPlace).
 * MovingBlockEntityData.retrieve() CONSUMES the data. If onPlace() calls retrieve()
 * before this mixin runs, the data is gone. Worse, onPlace() fires BEFORE the
 * block entity is created, so even if it retrieves data, there's no block entity
 * to load it into. This was the cause of the original "item deleted on move" bug.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMovingBlockEntityMixin {

    /**
     * Normal restore path: 2-tick piston animation completion.
     * Fires at TAIL of tick() — checks if the real block has been placed
     * (no longer MOVING_PISTON) and restores data if found.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private static void ideomod$restoreMovableBlockEntityData(
            Level level, BlockPos pos, BlockState state,
            PistonMovingBlockEntity blockEntity, CallbackInfo ci) {

        if (level.isClientSide()) return;

        BlockState currentState = level.getBlockState(pos);
        if (currentState.is(Blocks.MOVING_PISTON)) return;

        ideomod$tryRestore(level, pos);
    }

    /**
     * Fast restore path: 1-tick piston pulse completion.
     * finalTick() immediately places the real block without going through
     * the normal tick() animation cycle. We hook TAIL to restore data
     * after the block has been placed.
     *
     * NOTE: finalTick() is a non-static instance method (unlike tick() which
     * is static). The inject callback receives 'this' implicitly via the mixin.
     */
    @Inject(method = "finalTick", at = @At("TAIL"))
    private void ideomod$restoreMovableBlockEntityDataFinalTick(CallbackInfo ci) {
        PistonMovingBlockEntity self = (PistonMovingBlockEntity)(Object)this;
        Level level = self.getLevel();
        BlockPos pos = self.getBlockPos();

        if (level == null || level.isClientSide()) return;

        ideomod$tryRestore(level, pos);
    }

    /**
     * Shared restore logic for both tick() and finalTick() paths.
     */
    private static void ideomod$tryRestore(Level level, BlockPos pos) {
        String dimensionKey = level.dimension().location().toString();
        CompoundTag savedData = MovingBlockEntityData.retrieve(dimensionKey, pos);

        if (savedData != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IPistonMovable movable) {
                movable.loadMovementData(savedData);
                Ideomod.LOGGER.info("[PistonBEMixin] RESTORED IPistonMovable data at {} (tags: {})",
                        pos, savedData.getAllKeys());
            } else {
                Ideomod.LOGGER.warn("[PistonBEMixin] Retrieved data at {} but BE is not IPistonMovable: {}",
                        pos, be != null ? be.getClass().getSimpleName() : "NULL");
            }
        }
    }
}
