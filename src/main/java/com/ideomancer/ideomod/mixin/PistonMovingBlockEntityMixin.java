package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.IPistonMovable;
import com.ideomancer.ideomod.block.MovingBlockEntityData;
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
 * data after the 2-tick piston animation completes.
 *
 * This is the RESTORE phase of the piston movement data pipeline:
 *   SAVE:    PistonBaseBlockMixin (vanilla pistons) or BlockMoveHelper (our systems)
 *   TRANSIT: MovingBlockEntityData holds the data during the 2-tick animation
 *   RESTORE: This mixin (runs for ALL piston completions)
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

    @Inject(method = "tick", at = @At("TAIL"))
    private static void ideomod$restoreMovableBlockEntityData(
            Level level, BlockPos pos, BlockState state,
            PistonMovingBlockEntity blockEntity, CallbackInfo ci) {

        if (level.isClientSide()) return;

        // Only attempt restore when the real block has been placed.
        // During animation ticks, the block is still MOVING_PISTON.
        // retrieve() CONSUMES data -- calling it too early would lose it.
        BlockState currentState = level.getBlockState(pos);
        if (currentState.is(Blocks.MOVING_PISTON)) return;

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
