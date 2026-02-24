package com.ideomancer.ideomod.client.render;

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
 * Uses the inner block's defaultBlockState() when querying quads, so
 * multi-element models (slime's nested cubes) and directional models
 * (odd slime's front/back/side textures) render correctly in inventory.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlockItemModel implements BakedModel {

    private final BakedModel originalModel;
    private final ItemOverrides overrides;

    public FramedBlockItemModel(BakedModel originalModel) {
        this.originalModel = originalModel;
        this.overrides = new FramedBlockItemOverrides(originalModel);
    }

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
    // CUSTOM ITEM OVERRIDES
    // ========================================================================

    private static class FramedBlockItemOverrides extends ItemOverrides {

        private final BakedModel frameModel;
        private final Map<ResourceLocation, BakedModel> cache = new ConcurrentHashMap<>();

        FramedBlockItemOverrides(BakedModel frameModel) {
            this.frameModel = frameModel;
        }

        @Override
        public BakedModel resolve(BakedModel original, ItemStack stack,
                                   @Nullable ClientLevel level,
                                   @Nullable LivingEntity entity, int seed) {
            CompoundTag beTag = stack.getTagElement("BlockEntityTag");
            if (beTag == null || !beTag.contains("InnerBlock")) {
                return original;
            }

            String innerBlockIdStr = beTag.getString("InnerBlock");
            ResourceLocation innerBlockId = new ResourceLocation(innerBlockIdStr);

            BakedModel cached = cache.get(innerBlockId);
            if (cached != null) return cached;

            Block innerBlock = BuiltInRegistries.BLOCK.get(innerBlockId);
            if (innerBlock == Blocks.AIR) {
                return original;
            }

            BlockState innerState = innerBlock.defaultBlockState();
            BakedModel innerModel = Minecraft.getInstance().getBlockRenderer()
                    .getBlockModel(innerState);

            BakedModel composite = new CompositeModel(innerModel, frameModel, innerState);
            cache.put(innerBlockId, composite);
            return composite;
        }
    }

    // ========================================================================
    // COMPOSITE MODEL
    // ========================================================================

    /**
     * Merges inner block quads with frame overlay quads.
     *
     * Uses the inner block's defaultBlockState() when querying quads,
     * ensuring multi-element models and state-dependent textures resolve
     * correctly. Frame overlay quads use null state (they're state-independent).
     */
    private static class CompositeModel implements BakedModel {

        private final BakedModel innerModel;
        private final BakedModel frameModel;
        private final BlockState innerState;

        CompositeModel(BakedModel innerModel, BakedModel frameModel, BlockState innerState) {
            this.innerModel = innerModel;
            this.frameModel = frameModel;
            this.innerState = innerState;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction,
                                         RandomSource random) {
            // Use the stored inner state for correct quad resolution
            List<BakedQuad> innerQuads = innerModel.getQuads(innerState, direction, random);
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
            return ItemOverrides.EMPTY;
        }
    }
}
