package com.ideomancer.ideomod.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Machine Frame -- the universal connector in the Machine Family.
 *
 * All shared behavior (GUI, drops, clone, block entity, lifecycle hooks,
 * stickiness) is inherited from AbstractMachineBlock. Machine Frame adds
 * only the static utility methods that other systems reference, and its
 * POWERED look: unpowered it sends out activation (Sean's "active" texture),
 * powered by redstone it suppresses instead ("inactive", the see-through hole).
 * POWERED is display only; FrameActivation reads the redstone level itself.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameBlock extends AbstractMachineBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public MachineFrameBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        updatePowered(state, level, pos);   // after a piston move or a swap back from a specialization
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        updatePowered(state, level, pos);
    }

    private static void updatePowered(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.FRAME;
    }

    // ========================================================================
    // MACHINE FAMILY HELPERS
    // ========================================================================

    public static boolean isMachineFamily(Block block) {
        return IMachineFamily.isMachineFamily(block);
    }

    /**
     * Returns true for blocks that carry isolated machine-internal redstone
     * (Track's POWER system). The Conductor is NOT included here because it
     * carries vanilla redstone signal, not Track-internal signal.
     */
    public static boolean isRedstoneConduit(Block block) {
        return block instanceof MachineTrackBlock;
    }
}
