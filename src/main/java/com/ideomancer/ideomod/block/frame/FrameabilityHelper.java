package com.ideomancer.ideomod.block.frame;

import com.ideomancer.ideomod.registry.ModTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Static utility for determining whether a block can be wrapped in a frame.
 *
 * Uses a TAG-BASED WHITELIST: only blocks in the ideomod:frameable_blocks
 * tag are frameable. This replaces the old heuristic approach (full cube
 * check, no block entity, etc.) with explicit opt-in.
 *
 * Benefits:
 *   - New blocks (ours, vanilla updates, other mods) are NOT automatically
 *     frameable — they must be explicitly added to the tag.
 *   - Datapack-extensible: other mods or pack makers can add their blocks
 *     to the tag via JSON without touching Java code.
 *   - Predictable: you can see exactly what's frameable by reading one file.
 *
 * Safety checks still apply on top of the tag:
 *   - NOT already a FramedBlock (no double-framing)
 *   - NOT a Frame Block (can't frame an empty frame)
 *   - NOT air or liquid
 *
 * Checked at craft time by FramedBlockRecipe and at creative tab population
 * time by the BuildCreativeModeTabContentsEvent handler.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FrameabilityHelper {

    /**
     * Check if a block can be wrapped in a frame.
     *
     * Primary gate: block must be in the ideomod:frameable_blocks tag.
     * Secondary safety checks prevent nonsensical operations even if
     * a block is somehow tagged incorrectly.
     *
     * @param block The block to check
     * @return true if the block is frameable
     */
    public static boolean isFrameable(Block block) {
        BlockState defaultState = block.defaultBlockState();

        // Safety: no air or liquids (even if somehow tagged)
        if (defaultState.isAir()) return false;
        if (!defaultState.getFluidState().isEmpty()) return false;

        // Safety: no double-framing
        if (block instanceof FramedBlock) return false;

        // Safety: can't frame an empty frame
        if (block instanceof IFrameBlock) return false;

        // PRIMARY GATE: must be in the frameable_blocks whitelist tag
        if (!defaultState.is(ModTags.FRAMEABLE_BLOCKS)) return false;

        return true;
    }
}
