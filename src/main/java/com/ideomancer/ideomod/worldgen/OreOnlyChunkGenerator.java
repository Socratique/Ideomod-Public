package com.ideomancer.ideomod.worldgen;

import com.ideomancer.ideomod.registry.ModBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.common.Tags;

/**
 * A debug chunk generator that generates terrain normally but then strips
 * all non-ore blocks, leaving only ores floating in air. Useful for
 * visualizing ore distribution and generation rates.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class OreOnlyChunkGenerator extends NoiseBasedChunkGenerator {

    public static final Codec<OreOnlyChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(gen -> gen.biomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(gen -> gen.generatorSettings)
            ).apply(instance, OreOnlyChunkGenerator::new)
    );

    // Store our own reference since the parent field is private
    private final Holder<NoiseGeneratorSettings> generatorSettings;

    public OreOnlyChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
        this.generatorSettings = settings;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
        // No-op: skip surface rules (grass, dirt, sand, etc.)
        // This keeps the terrain as raw stone/deepslate, making ore stripping cleaner.
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk,
                                     StructureManager structureManager) {
        // Let vanilla decoration run (this places ores, among other features)
        super.applyBiomeDecoration(level, chunk, structureManager);

        // Now strip all non-ore blocks from this chunk
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(minX + x, y, minZ + z);
                    BlockState state = chunk.getBlockState(pos);

                    if (!state.isAir() && !isOreBlock(state)) {
                        chunk.setBlockState(pos, air, false);
                    }
                }
            }
        }
    }

    /**
     * Checks whether the given block state is an ore that should be preserved.
     * Uses the forge:ores tag for all vanilla and mod-added ores, plus
     * explicit checks for our custom blocks.
     */
    private static boolean isOreBlock(BlockState state) {
        // Forge ore tag covers all vanilla ores and any mod that tags properly
        if (state.is(Tags.Blocks.ORES)) {
            return true;
        }
        // Our custom blocks that count as ores for visualization
        if (state.is(ModBlocks.ODDSTONE.get())) {
            return true;
        }
        if (state.is(ModBlocks.HOLLOW_STONE.get())) {
            return true;
        }
        return false;
    }
}
