package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import com.ideomancer.ideomod.block.movement.BlockMoveHelper;

/**
 * Machine Track -- pushes adjacent Machine Trolleys on redstone signal.
 *
 * Overrides lifecycle hooks from AbstractMachineBlock to add isolated
 * redstone power tracking and Trolley push scheduling.
 *
 * Signal input: Tracks accept signal from two sources:
 *   1. Other Tracks/Conductors -- via direct blockstate POWER read (decay by 1)
 *   2. Any vanilla redstone source -- via level.getSignal() query
 *
 * Signal output: NONE. isSignalSource=false, getSignal=0, getDirectSignal=0.
 * Track POWER is purely internal bookkeeping, invisible to vanilla redstone.
 * This isolation eliminates feedback loops, so no input whitelist is needed.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class MachineTrackBlock extends AbstractMachineBlock {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final int EVENT_PUSH = 0;
    private static final ThreadLocal<Boolean> isUpdating = ThreadLocal.withInitial(() -> false);

    public MachineTrackBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    public MachineType getMachineType() { return MachineType.TRACK; }

    @Override
    public boolean isSignalSource(BlockState state) { return false; }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 0; }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 0; }

    // ========================================================================
    // LIFECYCLE (overrides base for Track-specific redstone + push logic)
    // ========================================================================

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;
        if (isUpdating.get()) return;
        int oldPower = state.getValue(POWER);
        int newPower = calculatePower(level, pos);
        if (newPower != oldPower) {
            isUpdating.set(true);
            try {
                level.setBlock(pos, state.setValue(POWER, newPower), 2);
                level.updateNeighborsAt(pos, this);
            } finally {
                isUpdating.set(false);
            }
            if (oldPower == 0 && newPower > 0) scheduleAllPushes(level, pos, newPower);
        }
        FrameActivation.markDirty(level, pos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this)) {
            updatePowerState(level, pos, state);
            BlockMoveHelper.updateAllNeighbors(level, pos, this);
            FrameActivation.markDirty(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            BlockMoveHelper.updateAllNeighbors(level, pos, this);
            FrameActivation.markDirty(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ========================================================================
    // REDSTONE INTERNALS
    // ========================================================================

    private void updatePowerState(Level level, BlockPos pos, BlockState state) {
        int newPower = calculatePower(level, pos);
        if (newPower != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, newPower), 2);
            level.updateNeighborsAt(pos, this);
        }
    }

    /**
     * Calculates the strongest signal reaching this Track from any neighbor.
     *
     * Two input paths:
     *   1. Conduit blocks (other Tracks, Conductors) -- read POWER blockstate directly, decay by 1
     *   2. Everything else -- query vanilla level.getSignal() for redstone input
     *
     * Since Tracks emit no vanilla signal (isSignalSource=false), there is no
     * feedback risk from accepting signal from any vanilla source.
     */
    private int calculatePower(Level level, BlockPos pos) {
        int bestPower = 0;
        for (Direction dir : Direction.values()) {
            BlockPos nPos = pos.relative(dir);
            BlockState nState = level.getBlockState(nPos);
            Block nBlock = nState.getBlock();
            if (MachineFrameBlock.isRedstoneConduit(nBlock)) {
                // Conduit path: direct blockstate read with decay
                int p = nState.getValue(POWER) - 1;
                if (p > bestPower) bestPower = p;
            } else {
                // Vanilla path: any redstone source feeds in directly
                int s = level.getSignal(nPos, dir);
                if (s > bestPower) bestPower = s;
            }
        }
        return Math.min(15, Math.max(0, bestPower));
    }

    // ========================================================================
    // PUSH SCHEDULING
    // ========================================================================

    private void scheduleAllPushes(Level level, BlockPos pos, int myPower) {
        for (Direction signalDir : Direction.values()) {
            BlockPos nPos = pos.relative(signalDir);
            BlockState nState = level.getBlockState(nPos);
            Block nBlock = nState.getBlock();
            boolean isSource = false;
            if (MachineFrameBlock.isRedstoneConduit(nBlock)) {
                isSource = nState.getValue(POWER) > myPower;
            } else {
                // Any vanilla source with signal counts as a directional source
                isSource = level.getSignal(nPos, signalDir) > 0;
            }
            if (!isSource) continue;
            Direction pushDir = signalDir.getOpposite();
            for (Direction tFace : Direction.values()) {
                BlockPos tPos = pos.relative(tFace);
                if (level.getBlockState(tPos).getBlock() instanceof MachineTrolleyBlock) {
                    level.blockEvent(pos, this, EVENT_PUSH, packDirs(pushDir, tFace));
                }
            }
        }
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (id == EVENT_PUSH) {
            Direction pushDir = Direction.from3DDataValue(param & 0x7);
            Direction tFace = Direction.from3DDataValue((param >> 3) & 0x7);
            BlockPos tPos = pos.relative(tFace);
            if (!(level.getBlockState(tPos).getBlock() instanceof MachineTrolleyBlock)) return true;
            BlockMoveHelper.moveBlockAt(level, pos, tPos, pushDir);
            return true;
        }
        return false;
    }

    private static int packDirs(Direction a, Direction b) {
        return a.get3DDataValue() | (b.get3DDataValue() << 3);
    }
}
