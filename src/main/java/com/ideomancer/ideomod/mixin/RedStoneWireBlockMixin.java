package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.block.machine.MachineConductorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets Machine Conductors take part in vanilla dust's anti-feedback rule.
 *
 * Vanilla dust works out its power in calculateTargetStrength(). While it
 * does, it sets its own shouldSignal=false, so every dust in the world stops
 * reporting strong power and dust never reads other dust through a solid
 * block. That flag lives on RedStoneWireBlock, so it can't reach the
 * Conductor (a different block class). This mixin raises the Conductor's own
 * flag for the same window: Conductor.getDirectSignal() returns 0 while any
 * dust is scanning.
 *
 * Fixes Known Issue #3 (dust powered by a Conductor through a solid block).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mixin(RedStoneWireBlock.class)
public abstract class RedStoneWireBlockMixin {

    @Inject(method = "calculateTargetStrength", at = @At("HEAD"))
    private void ideomod$beginDustScan(Level level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        MachineConductorBlock.setDustScanning(true);
    }

    @Inject(method = "calculateTargetStrength", at = @At("RETURN"))
    private void ideomod$endDustScan(Level level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        MachineConductorBlock.setDustScanning(false);
    }
}
