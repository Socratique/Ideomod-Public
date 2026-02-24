package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.multiblock.effects.FurnaceArrayEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on AbstractFurnaceBlockEntity that provides the +50% smelting speed
 * boost for furnaces in a registered Machine Furnace Array.
 *
 * Hooks into the TAIL of serverTick (after vanilla's normal cooking progress
 * increment). If the furnace has the IdeomodEnhanced flag in its persistent
 * data, adds +1 extra cooking progress every other tick.
 *
 * Result: vanilla gives +1 every tick. On even ticks we add another +1.
 * Average per tick: (1 + 2) / 2 = 1.5 → 50% speed boost.
 *
 * The flag is stamped by FurnaceArrayEffect when the structure forms,
 * and cleared when it deregisters. The mixin just reads it — no coupling
 * to the structure system itself.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    // Shadow vanilla's private cooking fields so we can read and modify them.
    // These are instance fields on AbstractFurnaceBlockEntity.
    @Shadow
    private int cookingProgress;

    @Shadow
    private int cookingTotalTime;

    /**
     * Injected at the end of serverTick. Since serverTick is a static method,
     * the block entity is received as a parameter rather than via 'this'.
     * We cast through Object to access the shadowed instance fields.
     */
    @Inject(
            method = "serverTick",
            at = @At("TAIL")
    )
    private static void ideomod$enhancedCookingBoost(
            Level level, BlockPos pos, BlockState state,
            AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {

        // Check for the enhanced flag in Forge's persistent data
        if (!blockEntity.getPersistentData().getBoolean(FurnaceArrayEffect.NBT_ENHANCED)) {
            return;
        }

        // Cast to access shadowed fields. At runtime, the mixin class IS the
        // target class, so this cast is safe. The Object intermediate avoids
        // compile-time type checking.
        AbstractFurnaceBlockEntityMixin self =
                (AbstractFurnaceBlockEntityMixin) (Object) blockEntity;

        // Only boost while actively cooking (progress is advancing toward completion)
        if (self.cookingProgress <= 0 || self.cookingProgress >= self.cookingTotalTime) {
            return;
        }

        // Add +1 extra progress on even ticks.
        // Vanilla already gave +1 this tick. On even ticks we make it +2.
        // Odd ticks stay at vanilla's +1. Average: 1.5 per tick = 50% boost.
        if (level.getGameTime() % 2 == 0) {
            self.cookingProgress++;
        }

        // Safety clamp: vanilla completes smelts via an EQUALITY check
        // (cookingProgress == cookingTotalTime), not >=. If our increment
        // pushes progress to or past the target, vanilla's next tick would
        // increment past it and the equality check would never pass.
        // Clamping to totalTime - 1 ensures vanilla's next increment lands
        // exactly on the target value.
        if (self.cookingProgress >= self.cookingTotalTime) {
            self.cookingProgress = self.cookingTotalTime - 1;
        }
    }
}
