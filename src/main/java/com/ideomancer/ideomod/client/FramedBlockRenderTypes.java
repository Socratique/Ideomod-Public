package com.ideomancer.ideomod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom RenderType definitions for framed block inner rendering.
 *
 * TRANSLUCENT INNER BLOCK STRATEGY:
 * Two-pass rendering using standard RenderType.solid() for the depth/base
 * pass and TRANSLUCENT_NO_DEPTH_WRITE for the color pass:
 *
 *   Pass 1 (RenderType.solid()): Renders the inner block opaquely into the
 *           solid buffer, which writes both color and depth. The depth fills
 *           the hole left by the frame overlay's discarded transparent pixels.
 *           The opaque color is a temporary visual that gets overwritten.
 *           Minecraft guarantees solid renders before translucent.
 *
 *   Pass 2 (TRANSLUCENT_NO_DEPTH_WRITE): Renders the inner block with proper
 *           alpha blending (multi-element models, correct transparency) but
 *           no depth writes. The color overwrites the opaque base from Pass 1
 *           with the correct translucent appearance. Depth from Pass 1 remains.
 *
 * Using standard RenderType.solid() for Pass 1 avoids flush-ordering issues
 * with custom render types — Minecraft's pipeline always flushes solid before
 * translucent, guaranteeing depth values exist before the color pass runs.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public final class FramedBlockRenderTypes extends RenderType {

    private FramedBlockRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                                    int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                                    Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /**
     * Translucent render type with depth writes disabled.
     * Identical to RenderType.translucent() except writeMaskState is
     * COLOR_WRITE instead of COLOR_DEPTH_WRITE.
     *
     * Used as the color pass for translucent inner blocks — provides
     * correct alpha blending over the depth base established by the
     * solid pre-pass.
     */
    public static final RenderType TRANSLUCENT_NO_DEPTH_WRITE = create(
            "ideomod_translucent_no_depth",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            true,    // affectsCrumbling
            true,    // sortOnUpload (required for translucent)
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true)
    );
}
