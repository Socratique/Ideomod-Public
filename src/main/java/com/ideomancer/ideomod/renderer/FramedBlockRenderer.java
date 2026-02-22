package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.block.FramedBlock;
import com.ideomancer.ideomod.block.FramedBlockEntity;
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
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntityRenderer for Framed Blocks.
 *
 * TWO-LAYER RENDERING:
 *   Layer 1 (this BESR): Inner block rendered via renderSingleBlock(),
 *           slightly scaled down (0.998) to sit behind the frame surface.
 *           Handles multi-element models, tinting, translucency, and all
 *           model geometry natively.
 *   Layer 2 (JSON model via RenderShape.MODEL): Frame overlay texture with
 *           transparent center pixels. Renders at full block size on top.
 *
 * TRANSLUCENCY:
 * Framed blocks use noOcclusion() in their block properties, which prevents
 * neighboring blocks from culling their faces. This means terrain behind a
 * framed glass/slime/odd slime is always rendered. The inner block's
 * translucent pixels correctly blend with the visible neighbor faces behind.
 *
 * FACING TRANSFER:
 * Covers DirectionalBlock.FACING, HorizontalDirectionalBlock.FACING,
 * and RotatedPillarBlock.AXIS.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlockRenderer implements BlockEntityRenderer<FramedBlockEntity> {

    private static final float INNER_SCALE = 0.998f;
    private static final float INNER_OFFSET = (1f - INNER_SCALE) / 2f;

    public FramedBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FramedBlockEntity be, float partialTick, PoseStack poseStack,
                        MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!be.hasInnerBlock()) return;

        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        Block innerBlock = be.getInnerBlock();

        BlockState framedState = be.getBlockState();
        BlockState innerState = buildInnerState(innerBlock, framedState);

        int light = sampleBestLight(level, pos, packedLight);

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        poseStack.pushPose();
        poseStack.translate(INNER_OFFSET, INNER_OFFSET, INNER_OFFSET);
        poseStack.scale(INNER_SCALE, INNER_SCALE, INNER_SCALE);

        dispatcher.renderSingleBlock(innerState, poseStack, bufferSource, light, packedOverlay);

        poseStack.popPose();
    }

    // ========================================================================
    // INNER STATE CONSTRUCTION
    // ========================================================================

    /**
     * Build inner state with orientation from the framed block's FACING.
     *
     * Transfers:
     *   - DirectionalBlock.FACING (6-way: odd slime, pistons, observers)
     *   - HorizontalDirectionalBlock.FACING (4-way: furnaces, carved pumpkins)
     *     — vertical FACING clamped to NORTH for horizontal-only blocks
     *   - RotatedPillarBlock.AXIS (logs, pillars, hay bales)
     *     — FACING mapped to axis: UP/DOWN→Y, NORTH/SOUTH→Z, EAST/WEST→X
     */
    static BlockState buildInnerState(Block innerBlock, BlockState framedState) {
        BlockState innerState = innerBlock.defaultBlockState();
        Direction framedFacing = framedState.getValue(FramedBlock.FACING);

        if (innerState.hasProperty(DirectionalBlock.FACING)) {
            return innerState.setValue(DirectionalBlock.FACING, framedFacing);
        }

        if (innerState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            Direction horizontal = framedFacing;
            if (framedFacing == Direction.UP || framedFacing == Direction.DOWN) {
                horizontal = Direction.NORTH;
            }
            return innerState.setValue(HorizontalDirectionalBlock.FACING, horizontal);
        }

        if (innerState.hasProperty(RotatedPillarBlock.AXIS)) {
            return innerState.setValue(RotatedPillarBlock.AXIS, framedFacing.getAxis());
        }

        return innerState;
    }

    // ========================================================================
    // LIGHTING
    // ========================================================================

    /**
     * Sample the best (brightest) light from all 6 face neighbors.
     * renderSingleBlock takes a single packed light value, so we use the
     * maximum block and sky light from all directions.
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
