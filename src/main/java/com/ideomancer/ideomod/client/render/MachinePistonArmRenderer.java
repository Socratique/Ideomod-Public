package com.ideomancer.ideomod.client.render;

import com.ideomancer.ideomod.block.machine.MachinePistonBlock;
import com.ideomancer.ideomod.block.machine.MachinePistonHeadBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Block Entity Special Renderer for Machine Piston arm animation.
 *
 * Registered against the MACHINE_FRAME BlockEntityType (shared by all
 * Machine Family blocks). Only renders for MachinePistonBlock and
 * MachineStickyPistonBlock instances — all other machines get no
 * extra rendering from this BESR.
 *
 * ANIMATION:
 * ==========
 * For each face with an active animation (armAnimDirection != 0),
 * renders the arm model at an interpolated offset:
 *
 * Extension (animDir=+1): arm grows outward from the piston face.
 *   offset = progress * 1.0 in the extension direction.
 *   At progress 0 the arm is at the face, at 1.0 it's fully extended.
 *
 * Retraction (animDir=-1): arm shrinks back into the piston.
 *   offset = (1.0 - progress) * 1.0 in the extension direction.
 *   At progress 0 the arm is fully extended, at 1.0 it's at the face.
 *
 * The arm model (MachinePistonHeadBlock's JSON model) is an 8x8x16 bar.
 * We render it via renderSingleBlock() with the correct FACING and STICKY
 * blockstate properties.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@OnlyIn(Dist.CLIENT)
public class MachinePistonArmRenderer implements BlockEntityRenderer<MachineFrameBlockEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public MachinePistonArmRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(MachineFrameBlockEntity be, float partialTick, PoseStack poseStack,
                        MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // Only render for piston blocks
        BlockState baseState = be.getBlockState();
        if (!(baseState.getBlock() instanceof MachinePistonBlock pistonBlock)) return;

        boolean sticky = pistonBlock.isSticky();

        for (Direction dir : Direction.values()) {
            int animDir = be.getArmAnimDirection(dir);
            if (animDir == 0) continue;

            float progress = be.getArmRenderProgress(dir, partialTick);

            // Calculate how far along the extension axis the arm should be.
            // Extension: arm grows from 0 (at face) to 1.0 (fully out)
            // Retraction: arm shrinks from 1.0 (fully out) to 0 (at face)
            float extensionOffset;
            if (animDir > 0) {
                // Extending: progress 0->1 maps to offset 0->1
                extensionOffset = progress;
            } else {
                // Retracting: progress 0->1 maps to offset 1->0
                extensionOffset = 1.0f - progress;
            }

            // Don't render if fully retracted (offset 0 = inside the base block)
            if (extensionOffset <= 0.001f) continue;

            // Build the arm blockstate
            BlockState armState = ModBlocks.MACHINE_PISTON_HEAD.get().defaultBlockState()
                    .setValue(MachinePistonHeadBlock.FACING, dir)
                    .setValue(MachinePistonHeadBlock.STICKY, sticky);

            poseStack.pushPose();

            // Translate to the arm position: extensionOffset along the direction.
            // At offset 0, the arm is at the piston face (origin).
            // At offset 1, the arm is one full block out (adjacent block space).
            poseStack.translate(
                    dir.getStepX() * extensionOffset,
                    dir.getStepY() * extensionOffset,
                    dir.getStepZ() * extensionOffset
            );

            // Render the arm model
            blockRenderer.renderSingleBlock(armState, poseStack, bufferSource, packedLight, packedOverlay);

            poseStack.popPose();
        }
    }

    /**
     * Increase render distance slightly — arms extend into adjacent block space.
     */
    @Override
    public int getViewDistance() {
        return 68;
    }
}
