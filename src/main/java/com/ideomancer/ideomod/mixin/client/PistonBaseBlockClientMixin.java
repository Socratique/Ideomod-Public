package com.ideomancer.ideomod.mixin.client;

import com.ideomancer.ideomod.block.HollowstoneBlock;
import com.ideomancer.ideomod.block.HollowstoneBlockEntity;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.movement.MovementScanHelper;
import com.ideomancer.ideomod.client.render.MovingFrameData;
import com.ideomancer.ideomod.config.IdeomodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CLIENT-SIDE mixin into PistonBaseBlock.moveBlocks().
 *
 * Captures FramedBlock inner block IDs, facing, and inner sticky type
 * BEFORE vanilla destroys the block entities during piston movement.
 * Stores them in MovingFrameData so the transit renderer can render
 * the inner block with correct orientation during the 2-tick animation.
 *
 * EXTENSION and RETRACTION: Both use PistonStructureResolver.
 * For retraction, the piston head must be removed before creating the
 * resolver (mirrors vanilla's moveBlocks behavior and the server-side
 * mixin fix). See PistonBaseBlockMixin for full explanation.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockClientMixin {

    @Unique
    private static final Logger IDEOMOD_LOGGER = LoggerFactory.getLogger("Ideomod");


    /**
     * Captures the inner blocks of framed blocks and Hollowstone this move carries, for the
     * transit renderer, from vanilla's own resolver at the moment moveBlocks asks it for the
     * blocks to move (see ResolvedMoves). Until 2026-11-16 this resolved the move a second time
     * at HEAD of moveBlocks, plus a separate Oddstone scan on pulls.
     */
    @Inject(method = "moveBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;getToPush()Ljava/util/List;",
            ordinal = 0))
    private void ideomod$captureFramedBlockDataForRenderer(
            Level level, BlockPos pistonPos, Direction direction,
            boolean extending, CallbackInfoReturnable<Boolean> cir) {

        if (!level.isClientSide()) return;
        PistonStructureResolver resolver = com.ideomancer.ideomod.block.movement.ResolvedMoves.take();
        if (resolver == null) return;

        MovingFrameData.prune();

        Direction moveDir = extending ? direction : direction.getOpposite();
        int captured = 0;
        for (BlockPos sourcePos : resolver.getToPush()) {
            BlockPos destPos = sourcePos.relative(moveDir);
            if (ideomod$captureIfFramed(level, sourcePos, destPos)
                    || ideomod$captureIfHollowstone(level, sourcePos, destPos)) {
                captured++;
            }
        }

        if (captured > 0) {
            IDEOMOD_LOGGER.debug("[ClientCapture] Captured {} inner-block(s) for {} at {}",
                    captured, extending ? "extension" : "retraction", pistonPos);
        }
    }

    /**
     * If the block at sourcePos is a FramedBlock with an inner block,
     * store its inner block ID, facing, and inner sticky type in
     * MovingFrameData keyed by destPos.
     */
    @Unique
    private static boolean ideomod$captureIfFramed(Level level, BlockPos sourcePos, BlockPos destPos) {
        BlockState state = level.getBlockState(sourcePos);
        if (!(state.getBlock() instanceof FramedBlock)) return false;

        BlockEntity be = level.getBlockEntity(sourcePos);
        if (!(be instanceof FramedBlockEntity framedBE)) return false;
        if (!framedBE.hasInnerBlock()) return false;

        ResourceLocation innerBlockId = framedBE.getInnerBlockId();
        if (innerBlockId == null) return false;

        Direction facing = state.getValue(FramedBlock.FACING);
        int innerSticky = state.getValue(FramedBlock.INNER_STICKY);

        MovingFrameData.store(destPos, innerBlockId, facing, innerSticky);
        return true;
    }

    /**
     * If the block at sourcePos is a HollowstoneBlock with an inner block,
     * store its inner block ID in MovingFrameData keyed by destPos.
     * Uses Direction.NORTH and innerSticky=0 as placeholders (unused by
     * the renderer for hollowstone — it renders default state only).
     */
    @Unique
    private static boolean ideomod$captureIfHollowstone(Level level, BlockPos sourcePos, BlockPos destPos) {
        BlockState state = level.getBlockState(sourcePos);
        if (!(state.getBlock() instanceof HollowstoneBlock)) return false;

        BlockEntity be = level.getBlockEntity(sourcePos);
        if (!(be instanceof HollowstoneBlockEntity hollowBE)) return false;

        ResourceLocation innerBlockId = hollowBE.getInnerBlockId();
        if (innerBlockId == null) return false;

        // Store with dummy facing/innerSticky — renderer uses defaultBlockState for hollowstone
        MovingFrameData.store(destPos, innerBlockId, Direction.NORTH, 0);
        return true;
    }
}
