package com.ideomancer.ideomod.client.render;

import com.ideomancer.ideomod.block.HollowstoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntityRenderer for Hollowstone blocks.
 *
 * Simplified version of FramedBlockRenderer — cosmetic only.
 * Only renders when FILLED=true. Renders the inner block's default state
 * at 0.998 scale inside the Hollowstone shell.
 *
 * No FACING transfer (Hollowstone has no FACING property).
 * No INNER_STICKY / INNER_LIGHT properties.
 * No piston transit rendering.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class HollowstoneRenderer implements BlockEntityRenderer<HollowstoneBlockEntity> {

    private static final float INNER_SCALE = 0.998f;
    private static final float INNER_OFFSET = (1f - INNER_SCALE) / 2f;

    public HollowstoneRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HollowstoneBlockEntity be, float partialTick, PoseStack poseStack,
                        MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!be.hasInnerBlock()) return;

        Block innerBlock = be.getInnerBlock();
        if (innerBlock == null) return;

        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        BlockState innerState = innerBlock.defaultBlockState();

        int light = sampleBestLight(level, pos, packedLight);

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        poseStack.pushPose();
        poseStack.translate(INNER_OFFSET, INNER_OFFSET, INNER_OFFSET);
        poseStack.scale(INNER_SCALE, INNER_SCALE, INNER_SCALE);

        dispatcher.renderSingleBlock(innerState, poseStack, bufferSource, light, packedOverlay);

        poseStack.popPose();
    }

    /**
     * Sample the best (brightest) light from all 6 face neighbors.
     * Same approach as FramedBlockRenderer.
     */
    private static int sampleBestLight(Level level, BlockPos pos, int fallbackLight) {
        if (level == null) return fallbackLight;

        int maxBlock = 0;
        int maxSky = 0;

        for (Direction face : Direction.values()) {
            BlockPos neighbor = pos.relative(face);
            maxBlock = Math.max(maxBlock, level.getBrightness(LightLayer.BLOCK, neighbor));
            maxSky = Math.max(maxSky, level.getBrightness(LightLayer.SKY, neighbor));
        }

        return LightTexture.pack(maxBlock, maxSky);
    }
}
