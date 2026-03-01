package com.ideomancer.ideomod.block.frame;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Interface for all Frame Family blocks (Iron Frame, Copper Frame, and their
 * Framed Block variants when implemented).
 *
 * Provides a common type check for the stickiness system and activation logic.
 * Any block implementing this interface participates in the Frame activation
 * system: it can be activated by adjacent Machine Blocks, and activation
 * propagates through chains of same-material IFrameBlock instances.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public interface IFrameBlock {

    /**
     * Returns the frame material of this block (IRON or COPPER).
     * Used for material-matching during activation propagation and
     * canStickTo checks (iron only sticks to iron, copper to copper).
     */
    FrameMaterial getFrameMaterial();

    /**
     * Check if a BlockState represents an ACTIVE IFrameBlock.
     * Works for both FrameBlock and FramedBlock which have separate ACTIVE properties.
     *
     * @param state The block state to check
     * @return true if the block is an IFrameBlock and its ACTIVE property is true
     */
    static boolean isActive(BlockState state) {
        if (!(state.getBlock() instanceof IFrameBlock)) return false;
        if (state.getBlock() instanceof FramedBlock) {
            return state.getValue(FramedBlock.ACTIVE);
        }
        if (state.getBlock() instanceof FrameBlock) {
            return state.getValue(FrameBlock.ACTIVE);
        }
        return false;
    }
}
