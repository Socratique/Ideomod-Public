package com.ideomancer.ideomod.block.frame;

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
}
