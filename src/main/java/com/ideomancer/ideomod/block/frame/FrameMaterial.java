package com.ideomancer.ideomod.block.frame;

/**
 * Material type for Frame Family blocks.
 *
 * Determines activation chain propagation (iron chains through iron only,
 * copper through copper only) and canStickTo compatibility (cross-material
 * frames are mutually exclusive).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public enum FrameMaterial {
    IRON,
    COPPER
}
