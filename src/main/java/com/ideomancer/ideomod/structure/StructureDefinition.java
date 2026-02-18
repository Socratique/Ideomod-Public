package com.ideomancer.ideomod.structure;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Describes a structure type — the blueprint that tells the scanner what to
 * look for and how to evaluate it. One instance per structure type, created
 * at mod init and registered with the StructureManager.
 *
 * For Phase 1, definitions are built in Java (see FurnaceArrayDefinition).
 * Phase 2 will load them from JSON data files instead.
 *
 * The definition does not know about any specific structure in the world.
 * It only describes the rules.
 */
public class StructureDefinition {

    /**
     * Scan algorithm types. The StructureScanner dispatches based on this.
     *
     * PATTERN — rigid shape where the scanner knows exactly what should be where.
     *           Used by furnace array and similar fixed-frame structures.
     *
     * PROXIMITY — freeform detection within a radius from the core, ignoring walls.
     *             Used by enchanting table and similar open-area structures.
     *
     * PROXIMITY_WALLED — freeform detection that flood-fills through air and stops
     *                     at walls. Used by brewery and similar room-based structures.
     */
    public enum ScanMode {
        PATTERN,
        PROXIMITY,
        PROXIMITY_WALLED
    }

    // Identity
    private final String name;

    // Scan configuration
    private final ScanMode scanMode;

    // Core blocks — placing one of these triggers a scan attempt.
    // For the furnace array: furnace, blast_furnace, smoker.
    private final Set<ResourceLocation> coreBlocks;

    // Frame material tag — blocks in this tag are valid frame material.
    // Nullable because proximity-scan structures may not have a frame concept.
    @Nullable
    private final TagKey<Block> frameTag;

    // Cluster size limits (for PATTERN scan mode)
    private final int maxClusterWidth;
    private final int maxClusterHeight;

    // Effect handler — nullable. Most structures don't need one.
    // Only required when the structure must actively push state into block entities.
    @Nullable
    private final StructureEffectHandler effectHandler;

    // Scan radius for PROXIMITY and PROXIMITY_WALLED modes.
    // Ignored by PATTERN mode. Default 0 for Phase 1.
    private final int scanRadius;

    // ========================================================================
    // Future-proofing fields — present but empty for Phase 1.
    // These exist so future structures (brewery, enchanting table, etc.)
    // can use them without restructuring the class.
    // ========================================================================

    // Spatial validators: per-block placement checks (e.g., "has_clear_face")
    // Phase 1: empty. Future: list of validator name strings.

    // Global validators: post-scan checks on the full result (e.g., "cluster_is_rectangular")
    // Phase 1: empty for the definition — these checks are baked into the pattern scan logic.

    // Contributor mappings: which blocks contribute what stats
    // Phase 1: empty. Future: map of ResourceLocation → stat contributions.

    private StructureDefinition(Builder builder) {
        this.name = builder.name;
        this.scanMode = builder.scanMode;
        this.coreBlocks = Collections.unmodifiableSet(new HashSet<>(builder.coreBlocks));
        this.frameTag = builder.frameTag;
        this.maxClusterWidth = builder.maxClusterWidth;
        this.maxClusterHeight = builder.maxClusterHeight;
        this.effectHandler = builder.effectHandler;
        this.scanRadius = builder.scanRadius;
    }

    // ========================================================================
    // Getters
    // ========================================================================

    /** Unique string ID for this structure type (e.g., "machine_furnace_array"). */
    public String getName() {
        return name;
    }

    /** Which scan algorithm to use. */
    public ScanMode getScanMode() {
        return scanMode;
    }

    /**
     * Set of block ResourceLocations that can trigger a scan when placed.
     * The Manager checks placed blocks against this set.
     */
    public Set<ResourceLocation> getCoreBlocks() {
        return coreBlocks;
    }

    /**
     * Block tag for valid frame materials. Null for structures without a frame concept.
     */
    @Nullable
    public TagKey<Block> getFrameTag() {
        return frameTag;
    }

    /** Max width of the core cluster (perpendicular horizontal axis). */
    public int getMaxClusterWidth() {
        return maxClusterWidth;
    }

    /** Max height of the core cluster (vertical axis). */
    public int getMaxClusterHeight() {
        return maxClusterHeight;
    }

    /**
     * Effect handler for structures that need to actively modify the world.
     * Null for passive structures that are just queried by other systems.
     */
    @Nullable
    public StructureEffectHandler getEffectHandler() {
        return effectHandler;
    }

    /** Scan radius for proximity scan modes. Ignored by PATTERN mode. */
    public int getScanRadius() {
        return scanRadius;
    }

    /**
     * Quick check: is this block a valid core for this structure type?
     * Used by the Manager to filter block-place events.
     */
    public boolean isCoreBlock(ResourceLocation blockId) {
        return coreBlocks.contains(blockId);
    }

    // ========================================================================
    // Builder
    // ========================================================================

    public static class Builder {
        private final String name;
        private ScanMode scanMode = ScanMode.PATTERN;
        private final Set<ResourceLocation> coreBlocks = new HashSet<>();
        private TagKey<Block> frameTag;
        private int maxClusterWidth = 5;
        private int maxClusterHeight = 5;
        private StructureEffectHandler effectHandler;
        private int scanRadius = 0;

        public Builder(String name) {
            this.name = name;
        }

        public Builder setScanMode(ScanMode scanMode) {
            this.scanMode = scanMode;
            return this;
        }

        public Builder addCoreBlock(ResourceLocation blockId) {
            this.coreBlocks.add(blockId);
            return this;
        }

        public Builder setFrameTag(TagKey<Block> frameTag) {
            this.frameTag = frameTag;
            return this;
        }

        public Builder setMaxClusterSize(int width, int height) {
            this.maxClusterWidth = width;
            this.maxClusterHeight = height;
            return this;
        }

        public Builder setEffectHandler(StructureEffectHandler handler) {
            this.effectHandler = handler;
            return this;
        }

        public Builder setScanRadius(int radius) {
            this.scanRadius = radius;
            return this;
        }

        public StructureDefinition build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("StructureDefinition must have a name");
            }
            if (coreBlocks.isEmpty()) {
                throw new IllegalStateException("StructureDefinition '" + name + "' must have at least one core block");
            }
            return new StructureDefinition(this);
        }
    }
}
