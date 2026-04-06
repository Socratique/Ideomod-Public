package com.ideomancer.ideomod.client.render;

import com.ideomancer.ideomod.block.machine.MachineGrabberBlock;
import com.ideomancer.ideomod.block.machine.MachinePistonBlock;
import com.ideomancer.ideomod.block.machine.MachinePistonHeadBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Block Entity Special Renderer for Machine Family blocks.
 *
 * Registered against the MACHINE_FRAME BlockEntityType (shared by all
 * Machine Family blocks). Handles multiple rendering modes:
 *
 * 1. PISTON ARM ANIMATION: For MachinePistonBlock and MachineStickyPistonBlock.
 *    Renders the arm model at an interpolated offset during 2-tick extend/retract.
 *
 * 2. INNER BLOCK RENDERING: For MachineGrabberBlock.
 *    Renders a smaller inner block (slime/honey) visible through the
 *    transparent center of the outer texture. Same approach as
 *    HollowstoneRenderer: 0.998 scale with best-of-6-neighbors lighting.
 *
 * All other machine blocks get no extra rendering from this BESR.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@OnlyIn(Dist.CLIENT)
public class MachinePistonArmRenderer implements BlockEntityRenderer<MachineFrameBlockEntity> {

    private static final float INNER_SCALE = 0.998f;
    private static final float INNER_OFFSET = (1f - INNER_SCALE) / 2f;

    private final BlockRenderDispatcher blockRenderer;

    public MachinePistonArmRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(MachineFrameBlockEntity be, float partialTick, PoseStack poseStack,
                        MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        BlockState baseState = be.getBlockState();
        Block block = baseState.getBlock();

        // --- Inner block rendering for Grabber ---
        if (block instanceof MachineGrabberBlock grabber) {
            Block innerBlock = grabber.getGrabberType() == MachineGrabberBlock.GrabberType.SLIME
                    ? Blocks.SLIME_BLOCK : Blocks.HONEY_BLOCK;
            renderInnerBlock(innerBlock.defaultBlockState(), be, poseStack, bufferSource,
                    packedLight, packedOverlay);
            return;
        }

        // --- Piston arm animation (existing behavior) ---
        if (!(block instanceof MachinePistonBlock pistonBlock)) return;

        boolean sticky = pistonBlock.isSticky();

        for (Direction dir : Direction.values()) {
            int animDir = be.getArmAnimDirection(dir);
            if (animDir == 0) continue;

            float progress = be.getArmRenderProgress(dir, partialTick);

            // Calculate how far along the extension axis the arm should be.
            float extensionOffset;
            if (animDir > 0) {
                extensionOffset = progress;
            } else {
                extensionOffset = 1.0f - progress;
            }

            // Don't render if fully retracted
            if (extensionOffset <= 0.001f) continue;

            BlockState armState = ModBlocks.MACHINE_PISTON_HEAD.get().defaultBlockState()
                    .setValue(MachinePistonHeadBlock.FACING, dir)
                    .setValue(MachinePistonHeadBlock.STICKY, sticky);

            poseStack.pushPose();

            poseStack.translate(
                    dir.getStepX() * extensionOffset,
                    dir.getStepY() * extensionOffset,
                    dir.getStepZ() * extensionOffset
            );

            blockRenderer.renderSingleBlock(armState, poseStack, bufferSource, packedLight, packedOverlay);

            poseStack.popPose();
        }
    }

    /**
     * Renders an inner block at 0.998 scale inside the machine block.
     * Visible through the transparent center of the outer texture.
     * Same approach as HollowstoneRenderer.
     */
    private void renderInnerBlock(BlockState innerState, MachineFrameBlockEntity be,
                                   PoseStack poseStack, MultiBufferSource bufferSource,
                                   int packedLight, int packedOverlay) {
        int light = sampleBestLight(be.getLevel(), be.getBlockPos(), packedLight);

        poseStack.pushPose();
        poseStack.translate(INNER_OFFSET, INNER_OFFSET, INNER_OFFSET);
        poseStack.scale(INNER_SCALE, INNER_SCALE, INNER_SCALE);

        blockRenderer.renderSingleBlock(innerState, poseStack, bufferSource, light, packedOverlay);

        poseStack.popPose();
    }

    /**
     * Sample the best (brightest) light from all 6 face neighbors.
     * Same approach as HollowstoneRenderer and FramedBlockRenderer.
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

    /**
     * Increase render distance slightly — arms extend into adjacent block space.
     */
    @Override
    public int getViewDistance() {
        return 68;
    }
}
