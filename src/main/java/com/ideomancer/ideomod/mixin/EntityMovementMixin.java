package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts Entity speed/jump factor resolution to support Framed Blocks.
 *
 * PROBLEM:
 * Block.getSpeedFactor() and Block.getJumpFactor() take no position parameter.
 * FramedBlock is a single Block class wrapping hundreds of inner blocks, so it
 * can't return different speed/jump factors per-position through those methods.
 *
 * SOLUTION:
 * Entity.getBlockSpeedFactor() and Entity.getBlockJumpFactor() DO have position
 * context (they look up the block at the entity's feet). We intercept here and
 * check: if the block is a FramedBlock, look up the block entity, get the inner
 * block, and return ITS speed/jump factor instead.
 *
 * This handles:
 * - Framed Soul Sand (speed factor 0.4)
 * - Framed Honey Block (speed factor 0.4, jump factor 0.5)
 * - Any future whitelist additions with custom speed/jump factors
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mixin(Entity.class)
public abstract class EntityMovementMixin {

    @Shadow
    public abstract Level level();

    /**
     * Intercept speed factor calculation.
     *
     * Vanilla flow: Entity.getBlockSpeedFactor() checks blockstate at entity
     * position, then at position below if the first is air. Calls
     * blockState.getBlock().getSpeedFactor().
     *
     * We inject at RETURN to check the result. If the entity is standing on
     * (or inside) a FramedBlock, we replace the return value with the inner
     * block's speed factor.
     */
    @Inject(method = "getBlockSpeedFactor", at = @At("RETURN"), cancellable = true)
    private void ideomod$modifySpeedFactor(CallbackInfoReturnable<Float> cir) {
        // Only bother checking if vanilla returned 1.0 (default).
        // If it already returned something else, the block at the entity's
        // feet already has a custom speed factor — not a framed block.
        // (FramedBlock.getSpeedFactor() returns 1.0 / default.)
        if (cir.getReturnValue() != 1.0f) return;

        Entity self = (Entity) (Object) this;
        Level level = self.level();

        // Check the block at entity's position first
        BlockPos entityPos = self.blockPosition();
        float factor = ideomod$getFramedSpeedFactor(level, entityPos);
        if (factor != 1.0f) {
            cir.setReturnValue(factor);
            return;
        }

        // Also check block below (vanilla does this for blocks like soul sand
        // where the entity stands ON the block, not IN it)
        BlockPos belowPos = new BlockPos(
                self.getBlockX(),
                (int) Math.floor(self.getY() - 0.5000001),
                self.getBlockZ()
        );
        factor = ideomod$getFramedSpeedFactor(level, belowPos);
        if (factor != 1.0f) {
            cir.setReturnValue(factor);
        }
    }

    /**
     * Intercept jump factor calculation.
     *
     * Same pattern as speed factor. Vanilla checks block at entity position
     * and below. We check if it's a framed block and delegate to the inner.
     */
    @Inject(method = "getBlockJumpFactor", at = @At("RETURN"), cancellable = true)
    private void ideomod$modifyJumpFactor(CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValue() != 1.0f) return;

        Entity self = (Entity) (Object) this;
        Level level = self.level();

        BlockPos entityPos = self.blockPosition();
        float factor = ideomod$getFramedJumpFactor(level, entityPos);
        if (factor != 1.0f) {
            cir.setReturnValue(factor);
            return;
        }

        BlockPos belowPos = new BlockPos(
                self.getBlockX(),
                (int) Math.floor(self.getY() - 0.5000001),
                self.getBlockZ()
        );
        factor = ideomod$getFramedJumpFactor(level, belowPos);
        if (factor != 1.0f) {
            cir.setReturnValue(factor);
        }
    }

    /**
     * Check if the block at the given position is a FramedBlock, and if so,
     * return the inner block's speed factor. Returns 1.0f if not a framed
     * block or if the block entity is missing.
     */
    private float ideomod$getFramedSpeedFactor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FramedBlock)) return 1.0f;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FramedBlockEntity fbe && fbe.hasInnerBlock()) {
            return fbe.getInnerBlock().getSpeedFactor();
        }
        return 1.0f;
    }

    /**
     * Check if the block at the given position is a FramedBlock, and if so,
     * return the inner block's jump factor. Returns 1.0f if not a framed
     * block or if the block entity is missing.
     */
    private float ideomod$getFramedJumpFactor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FramedBlock)) return 1.0f;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FramedBlockEntity fbe && fbe.hasInnerBlock()) {
            return fbe.getInnerBlock().getJumpFactor();
        }
        return 1.0f;
    }
}
