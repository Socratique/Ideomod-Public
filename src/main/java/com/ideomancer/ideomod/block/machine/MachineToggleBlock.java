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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.ticks.TickPriority;
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
 * Signal detection delegated to MachineRedstoneHelper (shared single source
 * of truth for all Machine Family redstone specializations).
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
     * Position-based anti-feedback for signal reads through solid blocks.
     * When non-null, stores the position of the instance currently scanning.
     * getSignal/getDirectSignal return 0 ONLY at the scanning position,
     * allowing other instances of this class to output normally through solid
     * blocks. Passed to MachineRedstoneHelper for vanilla signal reads.
     *
     * FIX 2026-03-29: Changed from ThreadLocal<Boolean> to ThreadLocal<BlockPos>
     * to allow same-type machine blocks to power each other through solid blocks.
     */
    static final ThreadLocal<BlockPos> shouldSignal = ThreadLocal.withInitial(() -> null);

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

    @Override
    public int getRedstoneOutput(BlockState state) {
        return state.getValue(POWERED) ? 15 : 0;
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
        return true; // Always a signal source — position-based suppression in getSignal/getDirectSignal
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (pos.equals(shouldSignal.get())) return 0; // Suppress self-reads only
        if (!state.getValue(POWERED)) return 0;

        // Suppress output toward our input face.
        Direction inputDir = state.getValue(INPUT_FACE).getDirection();
        if (inputDir != null && direction == inputDir.getOpposite()) {
            return 0;
        }

        return 15;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (pos.equals(shouldSignal.get())) return 0; // Suppress self-reads only
        return getSignal(state, level, pos, direction);
    }

    // ==================== neighborChanged ====================

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block sourceBlock, BlockPos sourcePos, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (pos.equals(updatingPos.get())) return;
        if (absorbing.get()) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

        // Suppress edge detection during piston settlement window
        if (machineBE.isPistonSettling()) return;

        boolean currentlyPowered = state.getValue(POWERED);

        if (currentlyPowered) {
            // === POWERED: only respond to input face updates ===
            Direction inputFace = machineBE.getInputFace();
            if (inputFace != null) {
                Direction sourceFace = MachineRedstoneHelper.getFaceFromSourcePos(pos, sourcePos);
                if (sourceFace != null && sourceFace != inputFace) {
                    return; // Output face update — ignore
                }
            }

            // Check if input signal dropped
            if (inputFace != null) {
                int inputSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, inputFace, shouldSignal);
                if (inputSignal <= 0) {
                    // Signal dropped. Arm the toggle (inputWasLow = true).
                    if (!machineBE.isInputWasLow()) {
                        machineBE.setInputWasLow(true);
                        LOGGER.debug("[TOGGLE] input dropped at {} — armed", pos);
                    }
                } else {
                    // Signal still present.
                    if (machineBE.isInputWasLow()) {
                        // Armed and signal returned → schedule toggle-off.
                        if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                            level.scheduleTick(pos, this, 2, TickPriority.HIGH);
                        }
                    }
                }
            }

        } else {
            // === UNPOWERED: full scan to find new input ===
            int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
            int bestSignal = scan[0];
            Direction bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;

            if (bestSignal > 0 && bestFace != null) {
                Direction currentInputFace = machineBE.getInputFace();

                if (currentInputFace != null && currentInputFace == bestFace
                        && !machineBE.isInputWasLow()) {
                    // Pre-existing signal — ignore until it drops and rises
                    LOGGER.debug("[TOGGLE] pre-existing signal on {} at {} — ignoring", bestFace, pos);
                    return;
                }

                // Genuine rising edge — schedule turn on.
                machineBE.setInputFace(bestFace);
                if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                    level.scheduleTick(pos, this, 2, TickPriority.HIGH);
                }
            } else if (bestSignal <= 0) {
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

        // Piston settlement: clear the flag and re-snapshot signal state
        // so the edge detector is correctly armed after all blocks settle.
        if (machineBE.isPistonSettling()) {
            machineBE.setPistonSettling(false);
            boolean isPowered = state.getValue(POWERED);
            Direction inputFace = machineBE.getInputFace();
            if (isPowered && inputFace != null) {
                int inputSignal = MachineRedstoneHelper.getIncomingSignalFromFace(
                        level, pos, inputFace, shouldSignal);
                machineBE.setInputWasLow(inputSignal <= 0);
            } else if (!isPowered) {
                int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
                if (scan[0] > 0 && scan[1] >= 0) {
                    machineBE.setInputFace(Direction.values()[scan[1]]);
                    machineBE.setInputWasLow(false);
                } else {
                    machineBE.setInputWasLow(true);
                }
            }
            LOGGER.debug("[TOGGLE] piston settled at {}, POWERED={}, inputWasLow={}",
                    pos, isPowered, machineBE.isInputWasLow());
            return;
        }

        boolean wasPowered = state.getValue(POWERED);

        if (!wasPowered) {
            // UNPOWERED → check if we should turn ON.
            Direction currentInput = machineBE.getInputFace();

            // FIX 2026-03-31e: After piston movement, a toggle with cleared
            // inputFace has null. Scan all faces to recover the input direction.
            // In normal gameplay this is unreachable because neighborChanged
            // always sets inputFace before scheduling tick().
            if (currentInput == null) {
                int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
                if (scan[0] > 0 && scan[1] >= 0) {
                    currentInput = Direction.values()[scan[1]];
                    machineBE.setInputFace(currentInput);
                    LOGGER.debug("[TOGGLE] tick scan found signal on {} at {} — recovering", currentInput, pos);
                }
            }

            if (currentInput != null) {
                int inputSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, currentInput, shouldSignal);

                if (inputSignal > 0) {
                    machineBE.setInputWasLow(false);
                    InputFace inputFaceEnum = InputFace.fromDirection(currentInput);

                    LOGGER.debug("[TOGGLE] ON at {} input={} signal={}", pos, currentInput, inputSignal);

                    updatingPos.set(pos);
                    try {
                        level.setBlock(pos, state
                                .setValue(POWERED, true)
                                .setValue(INPUT_FACE, inputFaceEnum), 3);

                        // FIX 2026-03-29: Notify neighbors of adjacent blocks so power
                        // propagates through solid blocks (like vanilla repeaters do).
                        for (Direction dir : Direction.values()) {
                            level.updateNeighborsAt(pos.relative(dir), this);
                        }
                    } finally {
                        updatingPos.set(null);
                    }
                }
            }

        } else {
            // POWERED → toggle OFF
            Direction currentInput = machineBE.getInputFace();
            int remainingSignal = 0;
            if (currentInput != null) {
                remainingSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, currentInput, shouldSignal);
            }

            if (remainingSignal > 0) {
                machineBE.setInputWasLow(false);
                LOGGER.debug("[TOGGLE] OFF at {} — absorbing remaining signal on {}", pos, currentInput);
            } else {
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

                // FIX 2026-03-29: Notify neighbors of adjacent blocks so power
                // propagates through solid blocks (like vanilla repeaters do).
                for (Direction dir : Direction.values()) {
                    level.updateNeighborsAt(pos.relative(dir), this);
                }
            } finally {
                updatingPos.set(null);
            }
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
            if (movedByPiston) {
                // Piston placement: suppress edge detection for 1 tick while
                // the rest of the push group settles. The BE's inputWasLow and
                // inputFace are already restored from movement data.
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MachineFrameBlockEntity machineBE) {
                    machineBE.setPistonSettling(true);
                }
                level.scheduleTick(pos, this, 1, TickPriority.HIGH);
            } else {
                // Normal placement: absorb any pre-existing signal silently.
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MachineFrameBlockEntity machineBE) {
                    absorbing.set(true);
                    try {
                        int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
                        int bestSignal = scan[0];
                        Direction bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;

                        if (bestSignal > 0 && bestFace != null) {
                            machineBE.setInputFace(bestFace);
                            machineBE.setInputWasLow(false);
                            LOGGER.debug("[TOGGLE] placed at {} — absorbed pre-existing signal on {} (strength {})",
                                    pos, bestFace, bestSignal);
                        }
                    } finally {
                        absorbing.set(false);
                    }
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
            // FIX 2026-03-29: Added !movedByPiston guard. During piston movement,
            // blocks are temporarily removed — firing neighbor updates here would
            // cause unnecessary redstone recalculations mid-move.
            if (!level.isClientSide() && !movedByPiston && state.getValue(POWERED)) {
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
