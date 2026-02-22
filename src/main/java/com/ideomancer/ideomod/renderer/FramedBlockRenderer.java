package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.block.FramedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * BlockEntityRenderer for Framed Blocks.
 *
 * TWO-LAYER RENDERING:
 *   Layer 1 (this BESR): Inner block texture, drawn as a cuboid very slightly
 *           inset (epsilon = 0.001) so the frame overlay always wins depth test.
 *   Layer 2 (JSON model via RenderShape.MODEL): Frame overlay texture with
 *           transparent center pixels. Renders at full block size on top.
 *
 * LIGHTING:
 * BESR-drawn faces don't receive chunk ambient occlusion. We sample the
 * world light at the neighboring block position for each face and use that
 * as the packed light, giving proper directional brightness matching
 * surrounding blocks.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlockRenderer implements BlockEntityRenderer<FramedBlockEntity> {

    private static final float E = 0.001f;
    private static final float MIN = E;
    private static final float MAX = 1f - E;

    public FramedBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FramedBlockEntity be, float partialTick, PoseStack poseStack,
                        MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!be.hasInnerBlock()) return;

        BlockState innerState = be.getInnerDefaultState();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(innerState);
        RandomSource random = RandomSource.create(42L);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutoutMipped());

        poseStack.pushPose();

        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();

        for (Direction face : Direction.values()) {
            TextureAtlasSprite sprite = getFaceSprite(model, innerState, face, random);
            if (sprite == null) continue;

            // Sample light from the neighbor on this face for proper brightness
            int faceLight = packedLight;
            if (level != null) {
                BlockPos neighbor = pos.relative(face);
                int blockLight = level.getBrightness(LightLayer.BLOCK, neighbor);
                int skyLight = level.getBrightness(LightLayer.SKY, neighbor);
                faceLight = LightTexture.pack(blockLight, skyLight);
            }

            drawFace(poseStack, consumer, face, sprite, faceLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private TextureAtlasSprite getFaceSprite(BakedModel model, BlockState state,
                                              Direction face, RandomSource random) {
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

    /**
     * Draw one face of the epsilon-inset cuboid.
     *
     * Side faces (NORTH/SOUTH/EAST/WEST): CW when viewed from outside.
     * Top/Bottom faces (UP/DOWN): CCW when viewed from outside.
     *
     * This mixed convention matches how Minecraft's rendering pipeline
     * actually processes quads after coordinate transforms.
     */
    private void drawFace(PoseStack poseStack, VertexConsumer consumer, Direction face,
                           TextureAtlasSprite sprite, int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();

        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        switch (face) {
            case DOWN -> {  // Y=MIN, normal (0,-1,0)
                quad(pose, consumer,
                    0,   MIN, 0,   u0, v1,
                    1,   MIN, 0,   u1, v1,
                    1,   MIN, 1,   u1, v0,
                    0,   MIN, 1,   u0, v0,
                    0, -1, 0, packedLight, packedOverlay);
            }
            case UP -> {    // Y=MAX, normal (0,1,0)
                quad(pose, consumer,
                    0,   MAX, 1,   u0, v0,
                    1,   MAX, 1,   u1, v0,
                    1,   MAX, 0,   u1, v1,
                    0,   MAX, 0,   u0, v1,
                    0, 1, 0, packedLight, packedOverlay);
            }
            case NORTH -> { // Z=MIN, normal (0,0,-1)
                quad(pose, consumer,
                    0,   MAX, MIN, u0, v0,
                    1,   MAX, MIN, u1, v0,
                    1,   MIN, MIN, u1, v1,
                    0,   MIN, MIN, u0, v1,
                    0, 0, -1, packedLight, packedOverlay);
            }
            case SOUTH -> { // Z=MAX, normal (0,0,1)
                quad(pose, consumer,
                    1,   MAX, MAX, u0, v0,
                    0,   MAX, MAX, u1, v0,
                    0,   MIN, MAX, u1, v1,
                    1,   MIN, MAX, u0, v1,
                    0, 0, 1, packedLight, packedOverlay);
            }
            case WEST -> {  // X=MIN, normal (-1,0,0)
                quad(pose, consumer,
                    MIN, MAX, 1,   u0, v0,
                    MIN, MAX, 0,   u1, v0,
                    MIN, MIN, 0,   u1, v1,
                    MIN, MIN, 1,   u0, v1,
                    -1, 0, 0, packedLight, packedOverlay);
            }
            case EAST -> {  // X=MAX, normal (1,0,0)
                quad(pose, consumer,
                    MAX, MAX, 0,   u0, v0,
                    MAX, MAX, 1,   u1, v0,
                    MAX, MIN, 1,   u1, v1,
                    MAX, MIN, 0,   u0, v1,
                    1, 0, 0, packedLight, packedOverlay);
            }
        }
    }

    private void quad(PoseStack.Pose pose, VertexConsumer consumer,
                       float x0, float y0, float z0, float u0, float v0,
                       float x1, float y1, float z1, float u1, float v1,
                       float x2, float y2, float z2, float u2, float v2,
                       float x3, float y3, float z3, float u3, float v3,
                       float nx, float ny, float nz,
                       int packedLight, int packedOverlay) {
        vertex(pose, consumer, x0, y0, z0, u0, v0, nx, ny, nz, packedLight, packedOverlay);
        vertex(pose, consumer, x1, y1, z1, u1, v1, nx, ny, nz, packedLight, packedOverlay);
        vertex(pose, consumer, x2, y2, z2, u2, v2, nx, ny, nz, packedLight, packedOverlay);
        vertex(pose, consumer, x3, y3, z3, u3, v3, nx, ny, nz, packedLight, packedOverlay);
    }

    private void vertex(PoseStack.Pose pose, VertexConsumer consumer,
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
