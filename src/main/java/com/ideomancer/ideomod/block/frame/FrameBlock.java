package com.ideomancer.ideomod.block.frame;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;

/**
 * Base class for empty Frame Blocks (Iron Frame, Copper Frame).
 *
 * Passive activation: all lifecycle events call FrameActivation.recalculate()
 * which does a full network wipe-and-rebuild from Machine Block sources.
 * Frames never decide their own activation state.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FrameBlock extends Block implements IFrameBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final FrameMaterial frameMaterial;

    public FrameBlock(FrameMaterial material, Properties properties) {
        super(properties);
        this.frameMaterial = material;
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    public FrameMaterial getFrameMaterial() {
        return frameMaterial;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    // ========================================================================
    // STICKINESS
    // ========================================================================

    @Override
    public boolean isStickyBlock(BlockState state) {
        return state.getValue(ACTIVE);
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        Block otherBlock = other.getBlock();

        if (otherBlock instanceof IFrameBlock otherFrame) {
            return otherFrame.getFrameMaterial() == this.frameMaterial;
        }
        if (MachineFrameBlock.isMachineFamily(otherBlock)) return true;
        if (otherBlock instanceof SlimeBlock) return true;
        if (otherBlock instanceof HoneyBlock) return true;
        if (otherBlock instanceof OddSlimeBlock) return true;

        return false;
    }

    // ========================================================================
    // LIFECYCLE — all trigger full network recalculation
    // ========================================================================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            FrameActivation.recalculate(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !movedByPiston) {
            FrameActivation.recalculate(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide()) {
            FrameActivation.recalculate(level, pos);
        }
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    @Override
    public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level,
                                           BlockPos pos) {
        return true;
    }
}
