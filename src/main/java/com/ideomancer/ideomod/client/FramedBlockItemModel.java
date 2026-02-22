package com.ideomancer.ideomod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom BakedModel for framed block items that shows the inner block
 * texture composited with the frame overlay.
 *
 * Without this, all framed block items look identical in inventory/creative
 * (just the frame texture). With this, "Iron Framed Stone" shows stone
 * texture with iron frame overlay, "Iron Framed Dirt" shows dirt, etc.
 *
 * ARCHITECTURE:
 *   This is a BakedModel WRAPPER. It delegates most methods to the original
 *   JSON-baked model (the frame overlay). The key override is getOverrides(),
 *   which returns a custom ItemOverrides that reads the ItemStack's NBT to
 *   determine the inner block, then returns a CompositeModel that merges
 *   the inner block's quads with the frame overlay's quads.
 *
 *   CompositeModel instances are cached by inner block ID so we don't
 *   rebuild geometry every frame. The cache is keyed by ResourceLocation
 *   which is interned by the registry, so lookups are fast.
 *
 * REGISTRATION:
 *   Wrapped around the original item models in Ideomod's ModelEvent handler.
 *   See Ideomod.java onModifyBakingResult().
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlockItemModel implements BakedModel {

    /** The original JSON-baked frame overlay model (what we're wrapping) */
    private final BakedModel originalModel;

    /** Custom overrides that return composite models based on NBT */
    private final ItemOverrides overrides;

    public FramedBlockItemModel(BakedModel originalModel) {
        this.originalModel = originalModel;
        this.overrides = new FramedBlockItemOverrides(originalModel);
    }

    // ========================================================================
    // BakedModel delegation — everything goes to the original model
    // except getOverrides() which returns our custom implementation
    // ========================================================================

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
        return originalModel.getQuads(state, direction, random);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return originalModel.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return originalModel.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return originalModel.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        // Must return false so the model system renders our quads
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return originalModel.getParticleIcon();
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemTransforms getTransforms() {
        return originalModel.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    // ========================================================================
    // CUSTOM ITEM OVERRIDES — reads NBT, returns composite model
    // ========================================================================

    /**
     * ItemOverrides that intercepts item rendering to check for InnerBlock NBT.
     * When found, returns a CompositeModel that layers the inner block's quads
     * underneath the frame overlay's quads.
     */
    private static class FramedBlockItemOverrides extends ItemOverrides {

        private final BakedModel frameModel;

        /** Cache of composite models keyed by inner block ResourceLocation */
        private final Map<ResourceLocation, BakedModel> cache = new ConcurrentHashMap<>();

        FramedBlockItemOverrides(BakedModel frameModel) {
            this.frameModel = frameModel;
        }

        @Override
        public BakedModel resolve(BakedModel original, ItemStack stack,
                                   @Nullable ClientLevel level,
                                   @Nullable LivingEntity entity, int seed) {
            // Read inner block ID from ItemStack NBT
            CompoundTag beTag = stack.getTagElement("BlockEntityTag");
            if (beTag == null || !beTag.contains("InnerBlock")) {
                return original; // No inner block — render plain frame
            }

            String innerBlockIdStr = beTag.getString("InnerBlock");
            ResourceLocation innerBlockId = new ResourceLocation(innerBlockIdStr);

            // Check cache first
            BakedModel cached = cache.get(innerBlockId);
            if (cached != null) return cached;

            // Build composite model: inner block quads + frame overlay quads
            Block innerBlock = BuiltInRegistries.BLOCK.get(innerBlockId);
            if (innerBlock == Blocks.AIR) {
                return original; // Unknown block — render plain frame
            }

            BlockState innerState = innerBlock.defaultBlockState();
            BakedModel innerModel = Minecraft.getInstance().getBlockRenderer()
                    .getBlockModel(innerState);

            BakedModel composite = new CompositeModel(innerModel, frameModel);
            cache.put(innerBlockId, composite);
            return composite;
        }
    }

    // ========================================================================
    // COMPOSITE MODEL — merges inner block quads with frame overlay quads
    // ========================================================================

    /**
     * A BakedModel that returns quads from both the inner block model and
     * the frame overlay model, layered together.
     *
     * Inner block quads come first (drawn underneath), then frame overlay
     * quads on top. Since the frame texture has transparent interior pixels,
     * the inner block texture shows through.
     *
     * Note: unlike the BESR which uses epsilon inset to avoid z-fighting,
     * item rendering doesn't have this problem because both layers are
     * rendered as part of the same item model with the same depth buffer
     * context. The slight quad overlap is handled by draw order.
     */
    private static class CompositeModel implements BakedModel {

        private final BakedModel innerModel;
        private final BakedModel frameModel;

        CompositeModel(BakedModel innerModel, BakedModel frameModel) {
            this.innerModel = innerModel;
            this.frameModel = frameModel;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction,
                                         RandomSource random) {
            // Combine quads: inner block first, then frame overlay on top
            List<BakedQuad> innerQuads = innerModel.getQuads(null, direction, random);
            List<BakedQuad> frameQuads = frameModel.getQuads(null, direction, random);

            if (innerQuads.isEmpty()) return frameQuads;
            if (frameQuads.isEmpty()) return innerQuads;

            List<BakedQuad> combined = new ArrayList<>(innerQuads.size() + frameQuads.size());
            combined.addAll(innerQuads);
            combined.addAll(frameQuads);
            return combined;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return frameModel.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return frameModel.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return frameModel.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return frameModel.getParticleIcon();
        }

        @Override
        @SuppressWarnings("deprecation")
        public ItemTransforms getTransforms() {
            return frameModel.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            // Return empty overrides — this composite IS the resolved model,
            // we don't want to recurse through overrides again
            return ItemOverrides.EMPTY;
        }
    }
}
