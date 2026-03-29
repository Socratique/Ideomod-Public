package com.ideomancer.ideomod.worldgen;

import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom worldgen feature: Hollowstone Lava Formation.
 *
 * Generates Oddstone + Hollowstone clusters at the edges of underground lava pools.
 *
 * Algorithm:
 * 1. Scan from origin in a small radius for a lava source block
 * 2. Find solid stone-type "shore" blocks adjacent to the lava
 * 3. Place 2-3 Oddstone blocks at shore positions
 * 4. Spread Hollowstone from each Oddstone into adjacent lava sources (organic shape)
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class HollowstoneLavaFormation extends Feature<NoneFeatureConfiguration> {

    /** How far from the origin to search for lava */
    private static final int LAVA_SCAN_RADIUS = 8;

    /** Min/max Oddstone blocks to place per formation */
    private static final int MIN_ODDSTONE = 2;
    private static final int MAX_ODDSTONE = 3;

    /** Max Hollowstone blocks to place per Oddstone */
    private static final int HOLLOWSTONE_PER_ODDSTONE = 2;

    /** Chance for Hollowstone to spread one more block from placed Hollowstone */
    private static final float SECONDARY_SPREAD_CHANCE = 0.5f;

    public HollowstoneLavaFormation(com.mojang.serialization.Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        // Step 1: Find a lava source block near the origin
        BlockPos lavaPos = findLavaSource(level, origin, random);
        if (lavaPos == null) return false;

        // Step 2: Find shore blocks (solid stone adjacent to lava)
        List<BlockPos> shorePositions = findShoreBlocks(level, lavaPos);
        if (shorePositions.size() < MIN_ODDSTONE) {
            // Try nearby lava sources
            for (Direction dir : Direction.values()) {
                BlockPos altLava = lavaPos.relative(dir);
                if (isLavaSource(level, altLava)) {
                    shorePositions = findShoreBlocks(level, altLava);
                    if (shorePositions.size() >= MIN_ODDSTONE) {
                        lavaPos = altLava;
                        break;
                    }
                }
            }
            if (shorePositions.size() < MIN_ODDSTONE) return false;
        }

        // Step 3: Place Oddstone at random shore positions
        int oddstoneCount = MIN_ODDSTONE + random.nextInt(MAX_ODDSTONE - MIN_ODDSTONE + 1);
        oddstoneCount = Math.min(oddstoneCount, shorePositions.size());

        List<BlockPos> placedOddstone = new ArrayList<>();
        List<BlockPos> remaining = new ArrayList<>(shorePositions);

        for (int i = 0; i < oddstoneCount && !remaining.isEmpty(); i++) {
            int idx = random.nextInt(remaining.size());
            BlockPos shorePos = remaining.remove(idx);
            level.setBlock(shorePos, ModBlocks.ODDSTONE.get().defaultBlockState(), 2);
            placedOddstone.add(shorePos);
        }

        if (placedOddstone.isEmpty()) return false;

        // Step 4: Spread Hollowstone from each Oddstone into adjacent lava
        BlockState hollowstone = ModBlocks.HOLLOW_STONE.get().defaultBlockState();
        List<BlockPos> placedHollowstone = new ArrayList<>();

        for (BlockPos oddPos : placedOddstone) {
            List<BlockPos> lavaNeighbors = getLavaSourceNeighbors(level, oddPos);
            shuffleList(lavaNeighbors, random);

            int placed = 0;
            for (BlockPos lavaN : lavaNeighbors) {
                if (placed >= HOLLOWSTONE_PER_ODDSTONE) break;
                level.setBlock(lavaN, hollowstone, 2);
                placedHollowstone.add(lavaN);
                placed++;
            }
        }

        // Secondary spread: from each placed Hollowstone, chance to spread one more
        List<BlockPos> secondarySpread = new ArrayList<>();
        for (BlockPos hPos : placedHollowstone) {
            if (random.nextFloat() < SECONDARY_SPREAD_CHANCE) {
                List<BlockPos> lavaNeighbors = getLavaSourceNeighbors(level, hPos);
                if (!lavaNeighbors.isEmpty()) {
                    BlockPos spreadPos = lavaNeighbors.get(random.nextInt(lavaNeighbors.size()));
                    level.setBlock(spreadPos, hollowstone, 2);
                    secondarySpread.add(spreadPos);
                }
            }
        }

        return true;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private BlockPos findLavaSource(WorldGenLevel level, BlockPos origin, RandomSource random) {
        // Scan in a randomized spiral outward from origin
        for (int r = 0; r <= LAVA_SCAN_RADIUS; r++) {
            for (int attempt = 0; attempt < Math.max(1, r * 6); attempt++) {
                int dx = random.nextInt(r * 2 + 1) - r;
                int dy = random.nextInt(r * 2 + 1) - r;
                int dz = random.nextInt(r * 2 + 1) - r;
                BlockPos check = origin.offset(dx, dy, dz);
                if (isLavaSource(level, check)) {
                    return check;
                }
            }
        }
        return null;
    }

    private boolean isLavaSource(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.LAVA)) return false;
        FluidState fluid = level.getFluidState(pos);
        return fluid.isSource();
    }

    private List<BlockPos> findShoreBlocks(WorldGenLevel level, BlockPos lavaPos) {
        List<BlockPos> shore = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = lavaPos.relative(dir);
            BlockState state = level.getBlockState(neighbor);
            if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
                shore.add(neighbor);
            }
        }
        return shore;
    }

    private List<BlockPos> getLavaSourceNeighbors(WorldGenLevel level, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (isLavaSource(level, neighbor)) {
                result.add(neighbor);
            }
        }
        return result;
    }

    private void shuffleList(List<BlockPos> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            BlockPos temp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, temp);
        }
    }
}
