package com.ideomancer.ideomod.mixin.client;

import com.ideomancer.ideomod.block.FramedBlock;
import com.ideomancer.ideomod.client.MovingFrameData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Client-side mixin into PistonHeadRenderer.
 *
 * Renders the inner block texture for FramedBlocks during piston transit.
 *
 * Injects at HEAD of render(). Applies the same animation offset as vanilla
 * using getXOff/getYOff/getZOff, then draws the inner block cuboid.
 * Vanilla proceeds to render the frame overlay with the same offset.
 *
 * LIGHTING:
 *   The PMBE sits at the destination position, but the block is visually
 *   sliding from the source. We sample light at the block's current visual
 *   position by computing the source position from the PMBE direction and
 *   interpolating based on progress.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mixin(PistonHeadRenderer.class)
public abstract class PistonMovingBlockEntityRendererMixin {

    @Unique
    private static final float E = 0.001f;

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

        // Debug: log once to confirm mixin is firing
        if (!ideomod$loggedOnce) {
            IDEOMOD_LOGGER.info("[RendererMixin] Mixin active - first render() call");
            ideomod$loggedOnce = true;
        }

        if (!(movedState.getBlock() instanceof FramedBlock)) return;

        BlockPos destPos = blockEntity.getBlockPos();
        ResourceLocation innerBlockId = MovingFrameData.get(destPos);
        if (innerBlockId == null) return;

        Block innerBlock = BuiltInRegistries.BLOCK.get(innerBlockId);
        if (innerBlock == Blocks.AIR) return;

        BlockState innerState = innerBlock.defaultBlockState();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(innerState);
        RandomSource random = RandomSource.create(42L);

        // Use the moving block render type — matches what Forge uses for the overlay
        RenderType renderType = ItemBlockRenderTypes.getMovingBlockRenderType(innerState);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        poseStack.pushPose();

        // Apply the same animation offset as vanilla's render() method.
        poseStack.translate(
                blockEntity.getXOff(partialTick),
                blockEntity.getYOff(partialTick),
                blockEntity.getZOff(partialTick)
        );

        // === LIGHTING ===
        // Sample light at the block's current VISUAL position, not the PMBE's
        // block position (which is the destination, possibly underground).
        //
        // Vanilla's PistonHeadRenderer samples light at:
        //   blockpos = getBlockPos().relative(getMovementDirection().getOpposite())
        // which is the SOURCE position. We do the same — the source position
        // is where the block appears to be for most of the animation.
        BlockPos sourcePos = destPos.relative(blockEntity.getMovementDirection().getOpposite());

        // Use the source position's light for the first half of the animation,
        // and the destination's light for the second half.
        float progress = blockEntity.getProgress(partialTick);
        BlockPos lightPos = progress < 0.5f ? sourcePos : destPos;

        int lightAtPos = LightTexture.pack(
                level.getBrightness(LightLayer.BLOCK, lightPos),
                level.getBrightness(LightLayer.SKY, lightPos)
        );

        for (Direction face : Direction.values()) {
            TextureAtlasSprite sprite = ideomod$getFaceSprite(model, innerState, face, random);
            if (sprite == null) continue;

            ideomod$drawFace(poseStack, consumer, face, sprite, lightAtPos, packedOverlay);
        }

        poseStack.popPose();
    }

    // ========================================================================
    // RENDERING HELPERS
    // ========================================================================

    @Unique
    private static TextureAtlasSprite ideomod$getFaceSprite(
            BakedModel model, BlockState state, Direction face, RandomSource random) {
        List<BakedQuad> quads = model.getQuads(state, face, random);
        if (!quads.isEmpty()) {
            return quads.get(0).getSprite();
        }
        quads = model.getQuads(state, null, random);
        if (!quads.isEmpty()) {
            return quads.get(0).getSprite();
        }
        return model.getParticleIcon();
    }

    @Unique
    private static void ideomod$drawFace(PoseStack poseStack, VertexConsumer consumer,
                                          Direction face, TextureAtlasSprite sprite,
                                          int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();

        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        float min = E;
        float max = 1f - E;

        switch (face) {
            case DOWN -> ideomod$quad(pose, consumer,
                    0,   min, 0,   u0, v1,
                    1,   min, 0,   u1, v1,
                    1,   min, 1,   u1, v0,
                    0,   min, 1,   u0, v0,
                    0, -1, 0, packedLight, packedOverlay);
            case UP -> ideomod$quad(pose, consumer,
                    0,   max, 1,   u0, v0,
                    1,   max, 1,   u1, v0,
                    1,   max, 0,   u1, v1,
                    0,   max, 0,   u0, v1,
                    0, 1, 0, packedLight, packedOverlay);
            case NORTH -> ideomod$quad(pose, consumer,
                    0,   max, min, u0, v0,
                    1,   max, min, u1, v0,
                    1,   min, min, u1, v1,
                    0,   min, min, u0, v1,
                    0, 0, -1, packedLight, packedOverlay);
            case SOUTH -> ideomod$quad(pose, consumer,
                    1,   max, max, u0, v0,
                    0,   max, max, u1, v0,
                    0,   min, max, u1, v1,
                    1,   min, max, u0, v1,
                    0, 0, 1, packedLight, packedOverlay);
            case WEST -> ideomod$quad(pose, consumer,
                    min, max, 1,   u0, v0,
                    min, max, 0,   u1, v0,
                    min, min, 0,   u1, v1,
                    min, min, 1,   u0, v1,
                    -1, 0, 0, packedLight, packedOverlay);
            case EAST -> ideomod$quad(pose, consumer,
                    max, max, 0,   u0, v0,
                    max, max, 1,   u1, v0,
                    max, min, 1,   u1, v1,
                    max, min, 0,   u0, v1,
                    1, 0, 0, packedLight, packedOverlay);
        }
    }

    @Unique
    private static void ideomod$quad(PoseStack.Pose pose, VertexConsumer consumer,
                                      float x0, float y0, float z0, float u0, float v0,
                                      float x1, float y1, float z1, float u1, float v1,
                                      float x2, float y2, float z2, float u2, float v2,
                                      float x3, float y3, float z3, float u3, float v3,
                                      float nx, float ny, float nz,
                                      int packedLight, int packedOverlay) {
        ideomod$vertex(pose, consumer, x0, y0, z0, u0, v0, nx, ny, nz, packedLight, packedOverlay);
        ideomod$vertex(pose, consumer, x1, y1, z1, u1, v1, nx, ny, nz, packedLight, packedOverlay);
        ideomod$vertex(pose, consumer, x2, y2, z2, u2, v2, nx, ny, nz, packedLight, packedOverlay);
        ideomod$vertex(pose, consumer, x3, y3, z3, u3, v3, nx, ny, nz, packedLight, packedOverlay);
    }

    @Unique
    private static void ideomod$vertex(PoseStack.Pose pose, VertexConsumer consumer,
                                        float x, float y, float z,
                                        float u, float v,
                                        float nx, float ny, float nz,
                                        int packedLight, int packedOverlay) {
        consumer.vertex(pose.pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }
}
