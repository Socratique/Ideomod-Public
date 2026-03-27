package com.ideomancer.ideomod.block;

import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A WallBlock variant that participates in sympathetic movement.
 * The sympathetic channel is set at construction and determines which
 * other sympathetic blocks this wall will link with at range.
 *
 * isStickyBlock() returns true so vanilla's resolver calls addBranchingBlocks,
 * which triggers the sympathetic search mixin. canStickTo() always returns false
 * so walls never grab neighbors via direct contact — all connections resolve
 * through sympathetic movement only.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class SympatheticWallBlock extends WallBlock implements ISympathetic {

    private final String channel;

    public SympatheticWallBlock(String channel, BlockBehaviour.Properties properties) {
        super(properties);
        this.channel = channel;
    }

    @Override
    public String getSympatheticChannel() {
        return channel;
    }

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return false;
    }
}
