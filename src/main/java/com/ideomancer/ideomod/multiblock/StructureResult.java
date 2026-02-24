package com.ideomancer.ideomod.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable scan report produced by StructureScanner after examining blocks
 * around a core position. Everything downstream reads from this object —
 * the Manager uses it to decide whether to register, the EffectHandler uses
 * it to know which blocks to modify, and the query API exposes it to
 * consuming systems.
 *
 * A result with valid=false means the scan found the structure incomplete
 * or malformed. The Manager will not register invalid results.
 *
 * All BlockPos values are immutable copies — safe to store long-term.
 */
public class StructureResult {

    private final boolean valid;
    private final String definitionName;
    private final BlockPos corePos;

    // Orientation — nullable for future scan types that don't involve directional blocks
    @Nullable
    private final Direction facing;
    @Nullable
    private final ResourceLocation furnaceType;

    // Cluster bounds (just the effector blocks, e.g., the furnace slab)
    @Nullable
    private final BlockPos clusterMin;
    @Nullable
    private final BlockPos clusterMax;

    // Full structure bounds (all 3 layers including frame)
    @Nullable
    private final BlockPos boundsMin;
    @Nullable
    private final BlockPos boundsMax;

    // Component positions by role
    private final List<BlockPos> effectorPositions;   // Furnaces — blocks that receive effects
    private final List<BlockPos> connectorPositions;  // Frame blocks — structural, no effects
    private final List<BlockPos> airPositions;         // Front-face openings — must be air

    // Contributor stats — empty for Phase 1, populated by future contributor blocks
    private final Map<String, Double> contributorStats;

    private StructureResult(Builder builder) {
        this.valid = builder.valid;
        this.definitionName = builder.definitionName;
        this.corePos = builder.corePos.immutable();
        this.facing = builder.facing;
        this.furnaceType = builder.furnaceType;
        this.clusterMin = builder.clusterMin != null ? builder.clusterMin.immutable() : null;
        this.clusterMax = builder.clusterMax != null ? builder.clusterMax.immutable() : null;
        this.boundsMin = builder.boundsMin != null ? builder.boundsMin.immutable() : null;
        this.boundsMax = builder.boundsMax != null ? builder.boundsMax.immutable() : null;

        // Deep-copy all position lists with immutable BlockPos
        this.effectorPositions = copyImmutable(builder.effectorPositions);
        this.connectorPositions = copyImmutable(builder.connectorPositions);
        this.airPositions = copyImmutable(builder.airPositions);

        this.contributorStats = Collections.unmodifiableMap(new HashMap<>(builder.contributorStats));
    }

    /**
     * Creates a failed (invalid) result. Used when the scan detects an incomplete
     * or malformed structure. No position data is populated.
     */
    public static StructureResult invalid(String definitionName, BlockPos corePos) {
        return new Builder(definitionName, corePos)
                .setValid(false)
                .build();
    }

    private static List<BlockPos> copyImmutable(List<BlockPos> source) {
        List<BlockPos> copy = new ArrayList<>(source.size());
        for (BlockPos pos : source) {
            copy.add(pos.immutable());
        }
        return Collections.unmodifiableList(copy);
    }

    // ========================================================================
    // Getters
    // ========================================================================

    public boolean isValid() {
        return valid;
    }

    public String getDefinitionName() {
        return definitionName;
    }

    public BlockPos getCorePos() {
        return corePos;
    }

    @Nullable
    public Direction getFacing() {
        return facing;
    }

    @Nullable
    public ResourceLocation getFurnaceType() {
        return furnaceType;
    }

    @Nullable
    public BlockPos getClusterMin() {
        return clusterMin;
    }

    @Nullable
    public BlockPos getClusterMax() {
        return clusterMax;
    }

    @Nullable
    public BlockPos getBoundsMin() {
        return boundsMin;
    }

    @Nullable
    public BlockPos getBoundsMax() {
        return boundsMax;
    }

    /** Furnaces — the blocks that receive effects (NBT flags, custom names). */
    public List<BlockPos> getEffectorPositions() {
        return effectorPositions;
    }

    /** Frame blocks — structural, validated against the frame tag. */
    public List<BlockPos> getConnectorPositions() {
        return connectorPositions;
    }

    /** Front-face openings — positions that must be air. */
    public List<BlockPos> getAirPositions() {
        return airPositions;
    }

    /**
     * Contributor stats map. Keys are stat names (e.g., "speedBonus"),
     * values are totals computed from contributor blocks in the structure.
     * Empty for Phase 1 furnace array — future contributor blocks populate this.
     */
    public Map<String, Double> getContributorStats() {
        return contributorStats;
    }

    /**
     * Convenience: get a single contributor stat, defaulting to 0.0 if absent.
     */
    public double getStat(String key) {
        return contributorStats.getOrDefault(key, 0.0);
    }

    // ========================================================================
    // Builder
    // ========================================================================

    public static class Builder {
        private boolean valid = true;
        private final String definitionName;
        private final BlockPos corePos;

        private Direction facing;
        private ResourceLocation furnaceType;
        private BlockPos clusterMin;
        private BlockPos clusterMax;
        private BlockPos boundsMin;
        private BlockPos boundsMax;

        private final List<BlockPos> effectorPositions = new ArrayList<>();
        private final List<BlockPos> connectorPositions = new ArrayList<>();
        private final List<BlockPos> airPositions = new ArrayList<>();
        private final Map<String, Double> contributorStats = new HashMap<>();

        public Builder(String definitionName, BlockPos corePos) {
            this.definitionName = definitionName;
            this.corePos = corePos;
        }

        public Builder setValid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder setFacing(Direction facing) {
            this.facing = facing;
            return this;
        }

        public Builder setFurnaceType(ResourceLocation furnaceType) {
            this.furnaceType = furnaceType;
            return this;
        }

        public Builder setClusterBounds(BlockPos min, BlockPos max) {
            this.clusterMin = min;
            this.clusterMax = max;
            return this;
        }

        public Builder setStructureBounds(BlockPos min, BlockPos max) {
            this.boundsMin = min;
            this.boundsMax = max;
            return this;
        }

        public Builder addEffector(BlockPos pos) {
            this.effectorPositions.add(pos);
            return this;
        }

        public Builder addConnector(BlockPos pos) {
            this.connectorPositions.add(pos);
            return this;
        }

        public Builder addAir(BlockPos pos) {
            this.airPositions.add(pos);
            return this;
        }

        public Builder addStat(String key, double value) {
            this.contributorStats.merge(key, value, Double::sum);
            return this;
        }

        public StructureResult build() {
            return new StructureResult(this);
        }
    }
}
