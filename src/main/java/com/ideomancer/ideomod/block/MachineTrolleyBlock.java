package com.ideomancer.ideomod.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Machine Trolley -- a passive Machine Family block designed to be pushed
 * by Machine Tracks.
 *
 * The Trolley has no active behavior of its own. It's a cargo carrier:
 *   - Sticks to other Machine Family blocks (Machine Frame, Track, Trolley)
 *   - Gets pushed by Tracks when they receive redstone signals
 *   - Carries anything stuck to it (via Machine Family or slime stickiness)
 *   - Can be pushed by regular pistons too
 *
 * FUTURE:
 * =======
 * Will become a Machine Frame specialization (insert minecart into Machine
 * Frame slot) rather than a separate block class. For now it's standalone.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class MachineTrolleyBlock extends Block {

    public MachineTrolleyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return MachineFrameBlock.canMachineFamilyStickTo(other);
    }
}
