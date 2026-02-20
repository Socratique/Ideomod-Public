package com.ideomancer.ideomod.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Machine Trolley -- a passive Machine Family block designed to be pushed
 * by Machine Tracks.
 *
 * The Trolley has no active behavior of its own. It's a cargo carrier:
 *   - Sticks to other Machine Family blocks (Machine Block, Track, Trolley)
 *   - Gets pushed by Tracks when they receive redstone signals
 *   - Carries anything stuck to it (via Machine Family or slime stickiness)
 *   - Can be pushed by regular pistons too
 *
 * STICKINESS:
 * ===========
 * Same rules as all Machine Family blocks:
 *   - Sticks to Machine Block, Machine Track, Machine Trolley
 *   - Permissive to slime/honey/odd slime (they can grab it, it can't grab them)
 *   - No stickiness to regular blocks
 *
 * During a Track-initiated push, the Track won't be dragged along because
 * the resolver treats the Track as pistonPos (immovable origin).
 *
 * FUTURE:
 * =======
 * When the Machine Block chassis system is implemented, Track and Trolley
 * will become Machine Block variants (item-slot transforms) rather than
 * separate block classes. For now they're standalone for testing.
 */
public class MachineTrolleyBlock extends Block {

    public MachineTrolleyBlock(Properties properties) {
        super(properties);
    }

    // ========================================================================
    // MACHINE FAMILY STICKINESS
    // ========================================================================

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return MachineBlock.canMachineFamilyStickTo(other);
    }
}
