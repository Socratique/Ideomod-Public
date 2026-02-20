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
 * (Machine Block / Track with higher power). Vanilla's level.hasSignal()
 * is NOT used because it includes weak power leaking through solid blocks,
 * which would cause Trolleys and other inert blocks to appear as signal
 * sources.
 *
 *   Signal from NORTH face → push SOUTH
 *   Signal from TOP        → push DOWN
 *   etc.
 *
 * ALL adjacent Trolleys are pushed in that direction, not just the one
 * on the push face. Example:
 *   Track at (0,0), Trolley at (0,1), signal from WEST.
 *   pushDir = EAST. Trolley at (0,1) moves to (1,1).
 *
 * REDSTONE CONDUCTION:
 * ====================
 * Same as Machine Block -- conducts redstone signal with decay of 1 per
 * block. Interoperable with Machine Block conduction chains. This means
 * a line of Tracks can carry signal, and signal direction is preserved
 * (the face receiving the strongest signal determines push direction).
 *
 * Uses POWER (0-15) instead of a boolean POWERED. Rising edge for push
 * activation is detected as: old power was 0, new power > 0.
 *
 * EDGE CASES:
 * ===========
 * - Trolley directly opposite the signal (on the push face): pushed away.
 * - Trolley on the signal face: would need to move through the Track.
 *   BlockMoveHelper's safety check rejects this (destination = Track pos).
 * - Signal arrives through a Trolley: Trolley is not a redstone component
 *   or conduit, so it's never treated as a signal source. Other adjacent
 *   actual sources determine push direction.
 * - Multiple signals on different faces: each fires independently.
 *   Multiple Trolleys pushed in the same direction: each is independent.
 *
 * STICKINESS:
 * ===========
 * Part of the Machine Family -- sticks to Machine Blocks, other Tracks,
 * and Trolleys. During a Track-initiated push, the Track itself is
 * protected from being included in the move group because its position
 * is passed as initiatorPos to BlockMoveHelper, and the
 * PistonStructureResolver treats that as pistonPos (immovable origin).
 *
 * Uses block event system for proper client-server animation sync.
 */
public class MachineTrackBlock extends Block {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    /**
     * Block event ID: push a trolley.
     * param encodes two directions packed into one int:
     *   bits 0-2: pushDir (direction to push)
     *   bits 3-5: trolleyFace (which face the Trolley is on, relative to Track)
     */
    private static final int EVENT_PUSH = 0;

    /** Guard flag to prevent infinite recursion during redstone updates. */
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
        return MachineBlock.canMachineFamilyStickTo(other);
    }

    // ========================================================================
    // REDSTONE CONDUCTION (mirrors MachineBlock)
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

            // Rising edge: was unpowered, now powered → schedule pushes
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

            // Accept decayed signal from other conductors (Machine Block + Track)
            if (MachineBlock.isRedstoneConduit(neighborBlock)) {
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

    /**
     * Determine which faces have actual signal sources, then push all
     * adjacent Trolleys in the opposite direction.
     *
     * IMPORTANT: Does NOT use level.hasSignal() because that includes
     * vanilla's "weak power" mechanic, where solid blocks adjacent to a
     * signal source appear to have signal themselves. Since the Track now
     * emits strong signal via POWER, any solid block next to it (including
     * Trolleys) would appear to be a signal source, causing misfires.
     *
     * Instead, checks neighbors directly:
     *   - Conduit blocks (Machine Block / Track) with higher POWER = signal source
     *   - Tagged redstone components with nonzero signal = signal source
     *   - Everything else (Trolleys, stone, air, etc.) = ignored
     *
     * @param myPower the Track's current power level (just set this tick)
     */
    private void scheduleAllPushes(Level level, BlockPos pos, int myPower) {
        for (Direction signalDir : Direction.values()) {
            BlockPos neighborPos = pos.relative(signalDir);
            BlockState neighborState = level.getBlockState(neighborPos);
            Block neighborBlock = neighborState.getBlock();

            boolean isActualSource = false;

            if (MachineBlock.isRedstoneConduit(neighborBlock)) {
                // Another conduit with higher power = signal flows from there to here
                int neighborPower = neighborState.getValue(POWER);
                isActualSource = neighborPower > myPower;
            } else if (isRedstoneComponent(neighborBlock)) {
                // Tagged redstone component (lever, dust, repeater, etc.)
                int signal = level.getSignal(neighborPos, signalDir);
                isActualSource = signal > 0;
            }
            // Everything else (Trolleys, stone, air, etc.) is never a signal source

            if (!isActualSource) continue;

            Direction pushDir = signalDir.getOpposite();

            // Check ALL 6 adjacent faces for Trolleys to push in this direction
            for (Direction trolleyFace : Direction.values()) {
                BlockPos trolleyPos = pos.relative(trolleyFace);
                if (level.getBlockState(trolleyPos).getBlock() instanceof MachineTrolleyBlock) {
                    int param = packDirections(pushDir, trolleyFace);
                    level.blockEvent(pos, this, EVENT_PUSH, param);
                }
            }
        }
    }

    /**
     * Block event handler -- runs on BOTH client and server.
     * Unpacks pushDir and trolleyFace from param, re-verifies the Trolley
     * is still there, then pushes it.
     */
    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (id == EVENT_PUSH) {
            Direction pushDir = unpackPushDir(param);
            Direction trolleyFace = unpackTrolleyFace(param);
            BlockPos trolleyPos = pos.relative(trolleyFace);

            // Re-verify: Trolley must still be there
            if (!(level.getBlockState(trolleyPos).getBlock() instanceof MachineTrolleyBlock)) {
                return true; // consumed event, but nothing to push
            }

            BlockMoveHelper.moveBlockAt(level, pos, trolleyPos, pushDir);
            return true;
        }
        return false;
    }

    // ========================================================================
    // DIRECTION PACKING
    // ========================================================================

    /**
     * Pack two Direction values into one int.
     * Bits 0-2: pushDir (0-5), bits 3-5: trolleyFace (0-5).
     */
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
