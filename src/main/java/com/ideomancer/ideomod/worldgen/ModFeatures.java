package com.ideomancer.ideomod.worldgen;

import com.ideomancer.ideomod.Ideomod;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for custom worldgen features and chunk generators.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, Ideomod.MOD_ID);

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Ideomod.MOD_ID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> HOLLOWSTONE_LAVA_FORMATION =
            FEATURES.register("hollowstone_lava_formation",
                    () -> new HollowstoneLavaFormation(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<Codec<? extends ChunkGenerator>> ORE_ONLY =
            CHUNK_GENERATORS.register("ore_only", () -> OreOnlyChunkGenerator.CODEC);
}
