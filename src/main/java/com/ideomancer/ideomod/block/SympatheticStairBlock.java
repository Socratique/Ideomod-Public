package com.ideomancer.ideomod.block;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * A StairBlock variant that participates in sympathetic movement.
 * The sympathetic channel is set at construction and determines which
 * other sympathetic blocks these stairs will link with at range.
 *
 * isStickyBlock() returns true so vanilla's resolver calls addBranchingBlocks,
 * which triggers the sympathetic search mixin. canStickTo() always returns false
 * so stairs never grab neighbors via direct contact — all connections resolve
 * through sympathetic movement only.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class SympatheticStairBlock extends StairBlock implements ISympathetic {

    private final String channel;

    public SympatheticStairBlock(String channel, Supplier<BlockState> baseState,
                                  BlockBehaviour.Properties properties) {
        super(baseState, properties);
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
