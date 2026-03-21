package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Machine Toggle — omnidirectional flip-flop / T-latch.
 *
 * Receives a redstone signal on any face; that face becomes the input face.
 * First rising edge = ON (outputs 15 on all 5 non-input faces).
 * Input signal must drop to 0 (arming the toggle), then rise again to toggle OFF.
 * Specialization item: Lever.
 *
 * RISING-EDGE-ONLY DESIGN:
 *   The Toggle only responds to transitions from no-signal to signal (rising edges).
 *   Pre-existing signals present at placement time are absorbed silently — the
 *   block snapshots the current signal landscape in onPlace and waits for the
 *   signal to drop and rise again before toggling.
 *
 * Edge-detection state:
 *   inputWasLow (in MachineFrameBlockEntity):
 *     - Set to FALSE when we first see a signal (either on placement or on toggle-on)
 *     - Set to TRUE when input face signal drops to 0 while we're tracking it
 *     - When TRUE and input face signal rises again → toggle state changes
 *
 * Anti-feedback architecture (simplified from Repeater — no delay complication):
 *   1. getSignal/getDirectSignal: return 0 toward input face, 15 on others.
 *   2. neighborChanged: if POWERED, only respond to input face updates.
 *   3. shouldSignal ThreadLocal: suppresses self-reads through solid blocks.
 *   4. updatingPos ThreadLocal: blocks re-entrant neighborChanged during setBlock.
 *
 * Visual: INPUT_FACE blockstate property drives model rotation.
 *   When POWERED, input face shows machine_toggle (off texture), other 5 show
 *   machine_toggle_on. Inner indicator tinted: bright red when powered, dark
 *   maroon when not.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineToggleBlock extends AbstractMachineBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineToggleBlock.class);

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final EnumProperty<InputFace> INPUT_FACE = EnumProperty.create("input_face", InputFace.class);

    /**
     * Suppresses both isSignalSource and getDirectSignal during our own scan.
     * Prevents us from reading our own output through adjacent solid blocks.
     */
    private static final ThreadLocal<Boolean> shouldSignal = ThreadLocal.withInitial(() -> true);

    /**
     * Stores the position of the toggle currently inside its tick() setBlock call.
     * neighborChanged at THIS position is blocked (prevents stale dust reads).
     * neighborChanged at OTHER positions proceeds normally.
     */
    private static final ThreadLocal<BlockPos> updatingPos = ThreadLocal.withInitial(() -> null);

    /**
     * Guard for onPlace signal snapshot. When true, the unpowered path in
     * neighborChanged skips scheduling a turn-on tick because onPlace is
     * handling the initial signal absorption.
     */
    private static final ThreadLocal<Boolean> absorbing = ThreadLocal.withInitial(() -> false);

    public MachineToggleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, false)
                .setValue(INPUT_FACE, InputFace.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, INPUT_FACE);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.TOGGLE;
    }

    // ==================== InputFace Enum (Blockstate Property) ====================

    /**
     * Blockstate-safe enum for the input face direction.
     * NONE = no input face (unpowered, no signal received yet).
     * Direction values map to the 6 faces.
     */
    public enum InputFace implements StringRepresentable {
        NONE("none", null),
        NORTH("north", Direction.NORTH),
        SOUTH("south", Direction.SOUTH),
        EAST("east", Direction.EAST),
        WEST("west", Direction.WEST),
        UP("up", Direction.UP),
        DOWN("down", Direction.DOWN);

        private final String name;
        private final Direction direction;

        InputFace(String name, Direction direction) {
            this.name = name;
            this.direction = direction;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public Direction getDirection() {
            return this.direction;
        }

        public static InputFace fromDirection(Direction dir) {
            if (dir == null) return NONE;
            return switch (dir) {
                case NORTH -> NORTH;
                case SOUTH -> SOUTH;
                case EAST -> EAST;
                case WEST -> WEST;
                case UP -> UP;
                case DOWN -> DOWN;
            };
        }
    }

    // ==================== Redstone Signal Output ====================

    @Override
    public boolean isSignalSource(BlockState state) {
        return shouldSignal.get();
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!shouldSignal.get()) return 0;
        if (!state.getValue(POWERED)) return 0;

        // Suppress output toward our input face.
        // direction param = FROM the querying block TO us.
        // Suppress when direction == inputFace.getOpposite().
        Direction inputDir = state.getValue(INPUT_FACE).getDirection();
        if (inputDir != null && direction == inputDir.getOpposite()) {
            return 0;
        }

        return 15;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!shouldSignal.get()) return 0;
        return getSignal(state, level, pos, direction);
    }

    // ==================== Signal Detection ====================

    /**
     * Read incoming signal from a specific face.
     * Same structure as Repeater/Conductor.getIncomingSignalFromFace.
     */
    private int getIncomingSignalFromFace(Level level, BlockPos pos, Direction face) {
        BlockPos neighborPos = pos.relative(face);
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();

        if (neighborBlock instanceof MachineConductorBlock) {
            int signal = neighborState.getSignal(level, neighborPos, face);
            return Math.max(0, signal - 1);

        } else if (neighborBlock instanceof MachineRepeaterBlock) {
            if (neighborState.getValue(MachineRepeaterBlock.POWERED)) {
                BlockEntity nbe = level.getBlockEntity(neighborPos);
                if (nbe instanceof MachineFrameBlockEntity nMachine) {
                    Direction nInput = nMachine.getInputFace();
                    if (nInput != null && nInput == face.getOpposite()) {
                        return 0; // We're on their input face
                    }
                    return 15;
                }
            }
            return 0;

        } else if (neighborBlock instanceof MachineToggleBlock) {
            if (neighborState.getValue(POWERED)) {
                Direction nInputDir = neighborState.getValue(INPUT_FACE).getDirection();
                if (nInputDir != null && nInputDir == face.getOpposite()) {
                    return 0; // We're on their input face
                }
                return 15;
            }
            return 0;

        } else if (neighborBlock instanceof RedStoneWireBlock) {
            int power = neighborState.getValue(RedStoneWireBlock.POWER);
            return Math.max(0, power - 1);

        } else {
            shouldSignal.set(false);
            try {
                return level.getSignal(neighborPos, face);
            } finally {
                shouldSignal.set(true);
            }
        }
    }

    /**
     * Scan all 6 faces for best incoming signal.
     * Returns [bestSignal, bestFaceOrdinal] (-1 if no signal).
     */
    private int[] scanAllFaces(Level level, BlockPos pos) {
        int bestSignal = 0;
        int bestFaceOrd = -1;
        for (Direction dir : Direction.values()) {
            int signal = getIncomingSignalFromFace(level, pos, dir);
            if (signal > bestSignal) {
                bestSignal = signal;
                bestFaceOrd = dir.ordinal();
            }
        }
        return new int[]{bestSignal, bestFaceOrd};
    }

    // ==================== Helper ====================

    /**
     * Determine which face a source position is on relative to us.
     * Returns null if sourcePos is not an immediate neighbor.
     */
    private static Direction getFaceFromSourcePos(BlockPos myPos, BlockPos sourcePos) {
        int dx = sourcePos.getX() - myPos.getX();
        int dy = sourcePos.getY() - myPos.getY();
        int dz = sourcePos.getZ() - myPos.getZ();

        if (dx == 1 && dy == 0 && dz == 0) return Direction.EAST;
        if (dx == -1 && dy == 0 && dz == 0) return Direction.WEST;
        if (dx == 0 && dy == 1 && dz == 0) return Direction.UP;
        if (dx == 0 && dy == -1 && dz == 0) return Direction.DOWN;
        if (dx == 0 && dy == 0 && dz == 1) return Direction.SOUTH;
        if (dx == 0 && dy == 0 && dz == -1) return Direction.NORTH;
        return null;
    }

    // ==================== neighborChanged ====================

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block sourceBlock, BlockPos sourcePos, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (pos.equals(updatingPos.get())) return; // Re-entrant call from our own tick — ignore
        if (absorbing.get()) return; // onPlace is absorbing initial signal — ignore

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

        boolean currentlyPowered = state.getValue(POWERED);

        if (currentlyPowered) {
            // === POWERED: only respond to input face updates ===
            Direction inputFace = machineBE.getInputFace();
            if (inputFace != null) {
                Direction sourceFace = getFaceFromSourcePos(pos, sourcePos);
                if (sourceFace != null && sourceFace != inputFace) {
                    // Output face update — ignore completely.
                    return;
                }
            }

            // Input face update while powered.
            // Check the input signal state for edge detection.
            if (inputFace != null) {
                int inputSignal = getIncomingSignalFromFace(level, pos, inputFace);

                if (inputSignal <= 0 && !machineBE.isInputWasLow()) {
                    // Signal dropped — arm the toggle for next rising edge.
                    machineBE.setInputWasLow(true);
                    LOGGER.debug("[TOGGLE] armed at {} (input dropped on {})", pos, inputFace);

                } else if (inputSignal > 0 && machineBE.isInputWasLow()) {
                    // Rising edge detected while armed — schedule toggle OFF.
                    if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                        level.scheduleTick(pos, this, 2); // 1 redstone tick
                    }
                    LOGGER.debug("[TOGGLE] rising edge at {} — scheduling OFF", pos);
                }
                // If signal is still high and not armed, or signal is low and already armed: no-op.
            }

        } else {
            // === UNPOWERED: check for rising edge ===
            int[] scan = scanAllFaces(level, pos);
            int bestSignal = scan[0];
            Direction bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;

            if (bestSignal > 0 && bestFace != null) {
                // Signal detected while unpowered.
                // Check if this is a genuine rising edge or a pre-existing signal
                // that was absorbed on placement.
                Direction currentInputFace = machineBE.getInputFace();

                if (currentInputFace != null && currentInputFace == bestFace
                        && !machineBE.isInputWasLow()) {
                    // This face was already tracked as having signal and we haven't
                    // seen it go low yet. This is a pre-existing signal (absorbed on
                    // placement or left over after toggle-off on a different face).
                    // Do NOT toggle on — wait for it to drop first.
                    LOGGER.debug("[TOGGLE] pre-existing signal on {} at {} — ignoring", bestFace, pos);
                    return;
                }

                // Genuine rising edge — schedule turn on.
                machineBE.setInputFace(bestFace);
                if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                    level.scheduleTick(pos, this, 2); // 1 redstone tick
                }
            } else if (bestSignal <= 0) {
                // No signal on any face. If we were tracking a pre-existing signal
                // from placement, mark it as having gone low.
                if (machineBE.getInputFace() != null && !machineBE.isInputWasLow()) {
                    machineBE.setInputWasLow(true);
                    LOGGER.debug("[TOGGLE] pre-existing signal dropped at {} — now armed", pos);
                }
                machineBE.setInputFace(null);
            }
        }

        FrameActivation.markDirty(level, pos);
    }

    // ==================== tick ====================

    /**
     * Scheduled tick — the ONLY place where POWERED state changes.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

        boolean wasPowered = state.getValue(POWERED);

        if (!wasPowered) {
            // UNPOWERED → check if we should turn ON.
            Direction currentInput = machineBE.getInputFace();

            if (currentInput != null) {
                int inputSignal = getIncomingSignalFromFace(level, pos, currentInput);

                if (inputSignal > 0) {
                    // Verify this is a genuine rising edge, not pre-existing signal.
                    // If inputWasLow is false and we got here from a neighborChanged
                    // that already checked, the neighborChanged wouldn't have scheduled
                    // this tick for a pre-existing signal. So if we're here, it's legit.

                    // Turn ON.
                    machineBE.setInputWasLow(false); // Not armed yet — signal is currently high.
                    InputFace inputFaceEnum = InputFace.fromDirection(currentInput);

                    LOGGER.debug("[TOGGLE] ON at {} input={} signal={}", pos, currentInput, inputSignal);

                    updatingPos.set(pos);
                    try {
                        level.setBlock(pos, state
                                .setValue(POWERED, true)
                                .setValue(INPUT_FACE, inputFaceEnum), 3);
                    } finally {
                        updatingPos.set(null);
                    }
                }
                // If signal is gone by the time tick fires, do nothing — stay off.
            }

        } else {
            // POWERED → toggle OFF (armed rising edge was detected).
            // After turning off, we need to handle the case where the signal
            // that just toggled us off is still present. We absorb it by
            // keeping inputFace set and inputWasLow = false, so the block
            // won't immediately re-toggle when it sees that same signal.
            Direction currentInput = machineBE.getInputFace();
            int remainingSignal = 0;
            if (currentInput != null) {
                remainingSignal = getIncomingSignalFromFace(level, pos, currentInput);
            }

            if (remainingSignal > 0) {
                // Signal is still present on the input face after toggle-off.
                // Absorb it: keep inputFace, set inputWasLow = false.
                // The block stays off and waits for the signal to drop and rise.
                machineBE.setInputWasLow(false);
                LOGGER.debug("[TOGGLE] OFF at {} — absorbing remaining signal on {}", pos, currentInput);
            } else {
                // Signal is gone. Clean slate.
                machineBE.setInputFace(null);
                machineBE.setInputWasLow(false);
                LOGGER.debug("[TOGGLE] OFF at {} — clean", pos);
            }

            updatingPos.set(pos);
            try {
                InputFace faceEnum = remainingSignal > 0
                        ? InputFace.fromDirection(currentInput)
                        : InputFace.NONE;
                level.setBlock(pos, state
                        .setValue(POWERED, false)
                        .setValue(INPUT_FACE, faceEnum), 3);
            } finally {
                updatingPos.set(null);
            }
        }
    }

    // ==================== Lifecycle ====================

    /**
     * On placement, snapshot any pre-existing signal and absorb it.
     * The block stays OFF but records which face has signal, preventing
     * that static signal from being treated as a rising edge.
     *
     * The absorbing ThreadLocal prevents neighborChanged (which fires
     * during onPlace due to block placement notifications) from
     * scheduling a turn-on tick for the pre-existing signal.
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity machineBE) {
                // Snapshot current signal landscape.
                // Use absorbing guard to prevent neighborChanged from
                // scheduling a tick during our scan.
                absorbing.set(true);
                try {
                    int[] scan = scanAllFaces(level, pos);
                    int bestSignal = scan[0];
                    Direction bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;

                    if (bestSignal > 0 && bestFace != null) {
                        // Pre-existing signal found. Record it but stay OFF.
                        // inputWasLow = false means "haven't seen this drop yet."
                        machineBE.setInputFace(bestFace);
                        machineBE.setInputWasLow(false);
                        LOGGER.debug("[TOGGLE] placed at {} — absorbed pre-existing signal on {} (strength {})",
                                pos, bestFace, bestSignal);
                    }
                    // If no signal, inputFace stays null — normal fresh placement.
                } finally {
                    absorbing.set(false);
                }
            }
            FrameActivation.markDirty(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, movedByPiston);
            // If we were powered, neighbors need to know we're gone.
            if (!level.isClientSide() && state.getValue(POWERED)) {
                for (Direction dir : Direction.values()) {
                    level.updateNeighborsAt(pos.relative(dir), this);
                }
            }
        } else {
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                             @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity placer,
                             ItemStack stack) {
        // No-op — toggle doesn't need placement specialization swap logic.
    }
}
