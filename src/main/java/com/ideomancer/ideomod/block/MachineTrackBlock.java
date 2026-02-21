package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Machine Track -- a non-directional Machine Family block that pushes
 * adjacent Machine Trolleys when it receives a redstone signal.
 *
 * PUSH DIRECTION LOGIC:
 * =====================
 * The Track figures out which face received the redstone signal and pushes
 * in the OPPOSITE direction. Signal direction is determined by checking
 * actual signal sources -- redstone components (via tag) and conduit blocks
 * (currently only other Tracks, since Machine Frame no longer conducts).
 * Vanilla's level.hasSignal() is NOT used because it includes weak power
 * leaking through solid blocks, which would cause Trolleys and other inert
 * blocks to appear as signal sources.
 *
 *   Signal from NORTH face → push SOUTH
 *   Signal from TOP        → push DOWN
 *   etc.
 *
 * ALL adjacent Trolleys are pushed in that direction, not just the one
 * on the push face.
 *
 * REDSTONE CONDUCTION:
 * ====================
 * Conducts redstone signal with decay of 1 per block. Track-to-Track chains
 * work. Track-to-Machine Frame chains are broken (Machine Frame no longer
 * conducts). The connection will restore when the Machine Conductor
 * specialization is implemented (insert redstone dust into Machine Frame).
 *
 * STICKINESS:
 * ===========
 * Part of the Machine Family -- sticks to Machine Frames, other Tracks,
 * and Trolleys. During a Track-initiated push, the Track itself is
 * protected from being included in the move group because its position
 * is passed as initiatorPos to BlockMoveHelper.
 *
 * Uses block event system for proper client-server animation sync.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class MachineTrackBlock extends Block {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    private static final int EVENT_PUSH = 0;

    private static boolean isUpdating = false;

    public MachineTrackBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
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
        return MachineFrameBlock.canMachineFamilyStickTo(other);
    }

    // ========================================================================
    // REDSTONE CONDUCTION (independent -- Track conducts on its own)
    // ========================================================================

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    // ========================================================================
    // REDSTONE + PUSH ACTIVATION
    // ========================================================================

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;
        if (isUpdating) return;

        int oldPower = state.getValue(POWER);
        int newPower = calculatePower(level, pos);

        if (newPower != oldPower) {
            isUpdating = true;
            try {
                level.setBlock(pos, state.setValue(POWER, newPower), 2);
                level.updateNeighborsAt(pos, this);
            } finally {
                isUpdating = false;
            }

            if (oldPower == 0 && newPower > 0) {
                scheduleAllPushes(level, pos, newPower);
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this)) {
            updatePowerState(level, pos, state);
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !newState.is(this)) {
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void updatePowerState(Level level, BlockPos pos, BlockState state) {
        int newPower = calculatePower(level, pos);
        if (newPower != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, newPower), 2);
            level.updateNeighborsAt(pos, this);
        }
    }

    private int calculatePower(Level level, BlockPos pos) {
        int bestPower = 0;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            Block neighborBlock = neighborState.getBlock();

            if (MachineFrameBlock.isRedstoneConduit(neighborBlock)) {
                int neighborPower = neighborState.getValue(POWER) - 1;
                if (neighborPower > bestPower) {
                    bestPower = neighborPower;
                }
            } else if (isRedstoneComponent(neighborBlock)) {
                int signal = level.getSignal(neighborPos, dir);
                if (signal > bestPower) {
                    bestPower = signal;
                }
            }
        }

        return Math.min(15, Math.max(0, bestPower));
    }

    private static boolean isRedstoneComponent(Block block) {
        return block.defaultBlockState().is(ModTags.REDSTONE_COMPONENTS);
    }

    // ========================================================================
    // PUSH SCHEDULING
    // ========================================================================

    private void scheduleAllPushes(Level level, BlockPos pos, int myPower) {
        for (Direction signalDir : Direction.values()) {
            BlockPos neighborPos = pos.relative(signalDir);
            BlockState neighborState = level.getBlockState(neighborPos);
            Block neighborBlock = neighborState.getBlock();

            boolean isActualSource = false;

            if (MachineFrameBlock.isRedstoneConduit(neighborBlock)) {
                int neighborPower = neighborState.getValue(POWER);
                isActualSource = neighborPower > myPower;
            } else if (isRedstoneComponent(neighborBlock)) {
                int signal = level.getSignal(neighborPos, signalDir);
                isActualSource = signal > 0;
            }

            if (!isActualSource) continue;

            Direction pushDir = signalDir.getOpposite();

            for (Direction trolleyFace : Direction.values()) {
                BlockPos trolleyPos = pos.relative(trolleyFace);
                if (level.getBlockState(trolleyPos).getBlock() instanceof MachineTrolleyBlock) {
                    int param = packDirections(pushDir, trolleyFace);
                    level.blockEvent(pos, this, EVENT_PUSH, param);
                }
            }
        }
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (id == EVENT_PUSH) {
            Direction pushDir = unpackPushDir(param);
            Direction trolleyFace = unpackTrolleyFace(param);
            BlockPos trolleyPos = pos.relative(trolleyFace);

            if (!(level.getBlockState(trolleyPos).getBlock() instanceof MachineTrolleyBlock)) {
                return true;
            }

            BlockMoveHelper.moveBlockAt(level, pos, trolleyPos, pushDir);
            return true;
        }
        return false;
    }

    // ========================================================================
    // DIRECTION PACKING
    // ========================================================================

    private static int packDirections(Direction pushDir, Direction trolleyFace) {
        return pushDir.get3DDataValue() | (trolleyFace.get3DDataValue() << 3);
    }

    private static Direction unpackPushDir(int packed) {
        return Direction.from3DDataValue(packed & 0x7);
    }

    private static Direction unpackTrolleyFace(int packed) {
        return Direction.from3DDataValue((packed >> 3) & 0x7);
    }
}
