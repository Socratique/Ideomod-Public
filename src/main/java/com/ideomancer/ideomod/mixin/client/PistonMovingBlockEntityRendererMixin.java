package com.ideomancer.ideomod.mixin.client;

import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.client.render.MovingFrameData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side mixin into PistonHeadRenderer.
 *
 * Renders the inner block for FramedBlocks during piston transit using
 * BlockRenderDispatcher.renderSingleBlock(). Handles multi-element models,
 * translucency, tinting, and directional textures via FACING from
 * MovingFrameData.
 *
 * LIGHTING:
 *   Brightest-of-neighbors at source and dest, blended by animation progress.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mixin(PistonHeadRenderer.class)
public abstract class PistonMovingBlockEntityRendererMixin {

    @Unique
    private static final float INNER_SCALE = 0.998f;
    @Unique
    private static final float INNER_OFFSET = (1f - INNER_SCALE) / 2f;

    @Unique
    private static final Logger IDEOMOD_LOGGER = LoggerFactory.getLogger("Ideomod");

    @Unique
    private static boolean ideomod$loggedOnce = false;

    @Inject(method = "render(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"))
    private void ideomod$renderInnerBlockDuringTransit(
            PistonMovingBlockEntity blockEntity, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int packedOverlay, CallbackInfo ci) {

        Level level = blockEntity.getLevel();
        if (level == null) return;

        BlockState movedState = blockEntity.getMovedState();

        if (!ideomod$loggedOnce) {
            IDEOMOD_LOGGER.info("[RendererMixin] Mixin active - first render() call");
            ideomod$loggedOnce = true;
        }

        if (!(movedState.getBlock() instanceof FramedBlock)) return;

        BlockPos destPos = blockEntity.getBlockPos();
        MovingFrameData.FrameTransitInfo info = MovingFrameData.get(destPos);
        if (info == null) return;

        Block innerBlock = BuiltInRegistries.BLOCK.get(info.innerBlockId());
        if (innerBlock == Blocks.AIR) return;

        BlockState innerState = ideomod$buildInnerState(innerBlock, info.facing());

        BlockPos sourcePos = destPos.relative(blockEntity.getMovementDirection().getOpposite());
        float progress = blockEntity.getProgress(partialTick);
        int blendedLight = ideomod$blendLight(level, sourcePos, destPos, progress, packedLight);

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        poseStack.pushPose();

        poseStack.translate(
                blockEntity.getXOff(partialTick),
                blockEntity.getYOff(partialTick),
                blockEntity.getZOff(partialTick)
        );

        poseStack.translate(INNER_OFFSET, INNER_OFFSET, INNER_OFFSET);
        poseStack.scale(INNER_SCALE, INNER_SCALE, INNER_SCALE);

        dispatcher.renderSingleBlock(innerState, poseStack, bufferSource, blendedLight, packedOverlay);

        poseStack.popPose();
    }

    // ========================================================================
    // INNER STATE CONSTRUCTION
    // ========================================================================

    @Unique
    private static BlockState ideomod$buildInnerState(Block innerBlock, Direction facing) {
        BlockState innerState = innerBlock.defaultBlockState();

        if (innerState.hasProperty(DirectionalBlock.FACING)) {
            return innerState.setValue(DirectionalBlock.FACING, facing);
        }

        if (innerState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            Direction horizontal = facing;
            if (facing == Direction.UP || facing == Direction.DOWN) {
                horizontal = Direction.NORTH;
            }
            return innerState.setValue(HorizontalDirectionalBlock.FACING, horizontal);
        }

        if (innerState.hasProperty(RotatedPillarBlock.AXIS)) {
            return innerState.setValue(RotatedPillarBlock.AXIS, facing.getAxis());
        }

        return innerState;
    }

    // ========================================================================
    // LIGHTING
    // ========================================================================

    @Unique
    private static int ideomod$blendLight(Level level, BlockPos sourcePos, BlockPos destPos,
                                           float progress, int fallbackLight) {
        if (level == null) return fallbackLight;

        int srcMaxBlock = 0, srcMaxSky = 0;
        int dstMaxBlock = 0, dstMaxSky = 0;

        for (Direction face : Direction.values()) {
            BlockPos srcNeighbor = sourcePos.relative(face);
            srcMaxBlock = Math.max(srcMaxBlock, level.getBrightness(LightLayer.BLOCK, srcNeighbor));
            srcMaxSky = Math.max(srcMaxSky, level.getBrightness(LightLayer.SKY, srcNeighbor));

            BlockPos dstNeighbor = destPos.relative(face);
            dstMaxBlock = Math.max(dstMaxBlock, level.getBrightness(LightLayer.BLOCK, dstNeighbor));
            dstMaxSky = Math.max(dstMaxSky, level.getBrightness(LightLayer.SKY, dstNeighbor));
        }

        int blendedBlock = Math.round(Mth.lerp(progress, srcMaxBlock, dstMaxBlock));
        int blendedSky = Math.round(Mth.lerp(progress, srcMaxSky, dstMaxSky));
        return LightTexture.pack(blendedBlock, blendedSky);
    }
}
