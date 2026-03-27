package com.ideomancer.ideomod.block.movement;

import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Enum representing the types of sticky blocks in the mod.
 * Single source of truth for sticky block classification and compatibility.
 *
 * Replaces scattered instanceof chains and magic integer constants.
 * Adding a new sticky type means: add an enum value, define its exclusions
 * in isExcludedFrom(), and done — every canStickTo automatically picks it up.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public enum StickyType {
    NONE(0),
    SLIME(1),
    HONEY(2),
    ODD_SLIME(3),
    ODDSTONE(4);

    private final int innerStickyValue;

    StickyType(int innerStickyValue) {
        this.innerStickyValue = innerStickyValue;
    }

    /** The integer value stored in FramedBlock.INNER_STICKY blockstate property */
    public int getInnerStickyValue() {
        return innerStickyValue;
    }

    /**
     * Classify a block into a StickyType.
     * Replaces FramedBlockEntity.computeStickyType() instanceof chain.
     */
    public static StickyType fromBlock(Block block) {
        if (block instanceof SlimeBlock) return SLIME;
        if (block instanceof HoneyBlock) return HONEY;
        if (block instanceof OddSlimeBlock) return ODD_SLIME;
        if (block instanceof OddstoneBlock) return ODDSTONE;
        // Oddstone variants (CobbledOddstoneBlock, OddstoneBricksBlock) are
        // standalone blocks (not OddstoneBlock subclasses) with oddstone-type
        // stickiness. They share the ODDSTONE sticky type for all stickiness
        // rules; only their sympathetic channel differs.
        if (block instanceof com.ideomancer.ideomod.block.CobbledOddstoneBlock) return ODDSTONE;
        if (block instanceof com.ideomancer.ideomod.block.OddstoneBricksBlock) return ODDSTONE;
        return NONE;
    }

    /**
     * Get the StickyType from a FramedBlock's INNER_STICKY blockstate value.
     */
    public static StickyType fromInnerStickyValue(int value) {
        for (StickyType type : values()) {
            if (type.innerStickyValue == value) return type;
        }
        return NONE;
    }

    /**
     * Get the StickyType of a block state.
     * For FramedBlock, reads the INNER_STICKY property.
     * For other blocks, classifies by instanceof.
     */
    public static StickyType of(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof FramedBlock) {
            return fromInnerStickyValue(state.getValue(FramedBlock.INNER_STICKY));
        }
        return fromBlock(block);
    }

    /**
     * Check if this sticky type is mutually exclusive with another.
     * Mutual exclusion means blocks of these types will NEVER stick to each other.
     *
     * The exclusion rules:
     * - SLIME excludes HONEY and ODD_SLIME
     * - HONEY excludes SLIME and ODD_SLIME
     * - ODD_SLIME excludes SLIME and HONEY
     * - ODDSTONE has no mutual exclusions (it grabs slime types)
     * - NONE has no exclusions
     */
    public boolean isExcludedFrom(StickyType other) {
        if (this == NONE || other == NONE) return false;
        if (this == ODDSTONE || other == ODDSTONE) return false;

        // The three slime-family types are pairwise exclusive
        if (this == SLIME && (other == HONEY || other == ODD_SLIME)) return true;
        if (this == HONEY && (other == SLIME || other == ODD_SLIME)) return true;
        if (this == ODD_SLIME && (other == SLIME || other == HONEY)) return true;

        return false;
    }

    /**
     * Check if this type can grab (stick to) a target type.
     * Used for oddstone's selective grabbing behavior.
     *
     * - ODDSTONE can grab: all slime types (SLIME, HONEY, ODD_SLIME) but NOT other ODDSTONE.
     *   Oddstone-to-oddstone connections resolve exclusively via sympathetic movement,
     *   not direct-contact stickiness. This prevents cross-variant sticking (e.g.
     *   cobbled oddstone sticking to oddstone bricks when adjacent).
     * - SLIME/HONEY/ODD_SLIME can grab: everything except their excluded types
     * - NONE: cannot grab anything
     */
    public boolean canGrab(StickyType target) {
        if (this == NONE) return false;

        if (this == ODDSTONE) {
            // Oddstone grabs slime-family types only. Oddstone-to-oddstone
            // connections are handled by sympathetic movement (ISympathetic channels).
            return target == SLIME || target == HONEY || target == ODD_SLIME;
        }

        // Slime family types: grab everything except excluded types
        return !isExcludedFrom(target);
    }

    /**
     * Check if a block is a vanilla-style sticky block (slime or honey).
     * Replaces scattered instanceof SlimeBlock || instanceof HoneyBlock checks.
     */
    public static boolean isVanillaSticky(Block block) {
        return block instanceof SlimeBlock || block instanceof HoneyBlock;
    }
}
