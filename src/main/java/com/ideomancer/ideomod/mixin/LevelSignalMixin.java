package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.item.RedstoneRodSignalManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects Redstone Rod phantom signals into vanilla's signal propagation.
 *
 * Targets BOTH getSignal() and getDirectSignal() on BlockBehaviour.BlockStateBase
 * — the concrete class methods that all signal queries flow through.
 *
 * getSignal (weak power):
 *   SignalGetter.getSignal(pos, dir) calls state.getSignal(level, pos, dir).
 *   This is the primary path for hasNeighborSignal() checks.
 *
 * getDirectSignal (strong power):
 *   SignalGetter.getDirectSignalTo(pos) calls state.getDirectSignal(level, pos, dir).
 *   This is the secondary path used when the source block is a redstone
 *   conductor (solid/opaque block). SignalGetter.getSignal() checks
 *   shouldCheckWeakPower() and, if true, also calls getDirectSignalTo().
 *   By injecting here too, we ensure the rod works even when a solid block
 *   sits at the phantom source position.
 *
 * When the RedstoneRodSignalManager has an active pulse at the queried
 * (pos, direction), we return 15 immediately — the target block sees full
 * power from the rod's clicked face for exactly 1 redstone tick.
 *
 * The instanceof Level guard ensures we only inject for actual game levels.
 *
 * Performance: the manager's empty-map fast-path means zero overhead when
 * no rod pulses are active.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class LevelSignalMixin {

    @Inject(method = "getSignal", at = @At("HEAD"), cancellable = true)
    private void ideomod$injectRedstoneRodSignal(BlockGetter blockGetter, BlockPos pos,
                                                  Direction direction,
                                                  CallbackInfoReturnable<Integer> cir) {
        if (blockGetter instanceof Level level) {
            int injected = RedstoneRodSignalManager.getInjectedSignal(level, pos, direction);
            if (injected > 0) {
                cir.setReturnValue(injected);
            }
        }
    }

    @Inject(method = "getDirectSignal", at = @At("HEAD"), cancellable = true)
    private void ideomod$injectRedstoneRodDirectSignal(BlockGetter blockGetter, BlockPos pos,
                                                        Direction direction,
                                                        CallbackInfoReturnable<Integer> cir) {
        if (blockGetter instanceof Level level) {
            int injected = RedstoneRodSignalManager.getInjectedSignal(level, pos, direction);
            if (injected > 0) {
                cir.setReturnValue(injected);
            }
        }
    }
}
