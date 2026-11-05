package com.ideomancer.ideomod.client.render;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.piston.AbstractRangedPistonBlock;
import com.ideomancer.ideomod.block.piston.OddForceBeams;
import com.ideomancer.ideomod.block.piston.RangedPistonUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the odd force beam: when a Repulser or Attractor fires, a 10x10 pillar of
 * the odd force texture runs from its face to the centre of the furthest Oddstone
 * in its line. The far end is found again every frame, including Oddstone that is
 * still sliding, so the beam stretches and shrinks with the blocks it moves. The
 * texture's arrows (drawn pointing down in the image) point the way the force acts
 * and drift slowly that way. It shows only while the push or pull is moving the blocks.
 *
 * Built like vanilla's beacon beam (same render type), turned so
 * its length runs along the device's facing.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class OddForceBeamRenderer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Ideomod.MOD_ID, "textures/misc/odd_force.png");
    /** Half the beam's width: 10 pixels across. */
    private static final float HALF = 5f / 16f;
    /** The pattern sits in the middle 10 columns of the 16-pixel texture. */
    private static final float U0 = 3f / 16f, U1 = 13f / 16f;
    /** How fast the arrows drift, in blocks per second. */
    private static final float DRIFT = 1.5f;

    private OddForceBeamRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        long gameTime = level.getGameTime();
        OddForceBeams.prune(gameTime);
        if (OddForceBeams.active().isEmpty()) return;

        float partialTick = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.beaconBeam(TEXTURE, true);
        VertexConsumer consumer = buffers.getBuffer(type);

        for (OddForceBeams.Beam beam : OddForceBeams.active()) {
            if (!(level.getBlockState(beam.pos()).getBlock() instanceof AbstractRangedPistonBlock)) continue;
            float age = gameTime - beam.startTick() + partialTick;
            if (age < 0 || age > OddForceBeams.LIFETIME) continue;
            float alpha = 1f;

            float end = furthestOddstone(level, beam, partialTick);
            if (end <= 0.5f) continue;

            pose.pushPose();
            pose.translate(beam.pos().getX() + 0.5 - cam.x, beam.pos().getY() + 0.5 - cam.y,
                    beam.pos().getZ() + 0.5 - cam.z);
            pose.mulPose(beam.facing().getRotation()); // +Y now runs along the facing
            float sign = beam.repulse() ? 1f : -1f;
            float scroll = DRIFT * age / 20f;
            drawTube(pose.last(), consumer, alpha, 0.5f, end,
                    sign * 0.5f - scroll, sign * end - scroll);
            pose.popPose();
        }
        buffers.endBatch(type);
    }

    /**
     * Distance from the device's centre to the centre of the furthest Oddstone in its
     * line, counting a sliding block at its drawn position. -1 if there is none.
     */
    private static float furthestOddstone(Level level, OddForceBeams.Beam beam, float partialTick) {
        Direction facing = beam.facing();
        int range = RangedPistonUtil.getScanRange() + 1; // a pushed block can end one past the range
        float best = -1f;
        for (int d = 1; d <= range; d++) {
            BlockPos p = beam.pos().relative(facing, d);
            BlockState state = level.getBlockState(p);
            if (RangedPistonUtil.ODDSTONE_ONLY.test(state)) {
                best = Math.max(best, d);
            } else if (state.is(Blocks.MOVING_PISTON)
                    && level.getBlockEntity(p) instanceof PistonMovingBlockEntity moving
                    && RangedPistonUtil.ODDSTONE_ONLY.test(moving.getMovedState())) {
                float off = moving.getXOff(partialTick) * facing.getStepX()
                        + moving.getYOff(partialTick) * facing.getStepY()
                        + moving.getZOff(partialTick) * facing.getStepZ();
                best = Math.max(best, d + off);
            }
        }
        return best;
    }

    /** Four outward faces of a square tube along +Y, laid out like the beacon beam's glow. */
    private static void drawTube(PoseStack.Pose pose, VertexConsumer c, float alpha,
                                 float y0, float y1, float v0, float v1) {
        float h = HALF;
        quad(pose, c, alpha, y0, y1, -h, -h, h, -h, v0, v1);
        quad(pose, c, alpha, y0, y1, h, h, -h, h, v0, v1);
        quad(pose, c, alpha, y0, y1, h, -h, h, h, v0, v1);
        quad(pose, c, alpha, y0, y1, -h, h, -h, -h, v0, v1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer c, float alpha, float y0, float y1,
                             float x1, float z1, float x2, float z2, float v0, float v1) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        // corners in this order face outward (the beacon's order, reversed, faced inward)
        vertex(m, n, c, alpha, x2, y1, z2, U0, v1);
        vertex(m, n, c, alpha, x2, y0, z2, U0, v0);
        vertex(m, n, c, alpha, x1, y0, z1, U1, v0);
        vertex(m, n, c, alpha, x1, y1, z1, U1, v1);
    }

    private static void vertex(Matrix4f m, Matrix3f n, VertexConsumer c, float alpha,
                               float x, float y, float z, float u, float v) {
        c.vertex(m, x, y, z).color(1f, 1f, 1f, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(n, 0f, 1f, 0f).endVertex();
    }
}
