package com.ideomancer.ideomod.block;

/**
 * Interface for blocks that participate in sympathetic movement.
 *
 * Sympathetic movement allows blocks to be pulled/pushed together at range
 * when moved by pistons, without requiring physical adjacency. Blocks on
 * the same sympathetic channel will link with each other; blocks on different
 * channels are isolated.
 *
 * Each block type gets its own isolated channel. Full blocks, walls, stairs,
 * and slabs each have separate channels even within the same material family.
 *
 * Channels (12 total):
 *   - "oddstone"                : OddstoneBlock (full block)
 *   - "cobbled_oddstone"        : CobbledOddstoneBlock (full block)
 *   - "oddstone_bricks"         : OddstoneBricksBlock (full block)
 *   - "oddstone_wall"           : Oddstone wall
 *   - "oddstone_stairs"         : Oddstone stairs
 *   - "oddstone_slab"           : Oddstone slab
 *   - "cobbled_oddstone_wall"   : Cobbled oddstone wall
 *   - "cobbled_oddstone_stairs" : Cobbled oddstone stairs
 *   - "cobbled_oddstone_slab"   : Cobbled oddstone slab
 *   - "oddstone_brick_wall"     : Oddstone brick wall
 *   - "oddstone_brick_stairs"   : Oddstone brick stairs
 *   - "oddstone_brick_slab"     : Oddstone brick slab
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public interface ISympathetic {

    /**
     * Returns the sympathetic channel identifier for this block.
     * Only blocks sharing the same channel will link sympathetically.
     *
     * @return a non-null string identifying the sympathetic channel
     */
    String getSympatheticChannel();
}
