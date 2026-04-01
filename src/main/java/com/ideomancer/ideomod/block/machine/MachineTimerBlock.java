package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.ideomancer.ideomod.config.IdeomodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.ticks.TickPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Machine Timer — edge-triggered timed output.
 *
 * Receives a rising edge on any face (that face becomes the input face).
 * Emits full-strength signal (15) on all 5 non-input faces for a player-
 * configured duration (in ticks), then turns off. Requires the input signal
 * to drop to 0 and the output to have ended before re-triggering.
 *
 * Specialization item: Clock (Items.CLOCK).
 *
 * EDGE DETECTION:
 *   Same rising-edge-only pattern as MachineToggleBlock. Uses inputWasLow
 *   in MachineFrameBlockEntity to track whether signal has dropped since
 *   last activation. After the timer expires and output ends, the block
 *   requires a fresh signal (drop + rise) to fire again.
 *
 * TIMER COUNTDOWN:
 *   timerDuration (in MachineFrameBlockEntity): the configured tick count.
 *   timerTicksRemaining (in MachineFrameBlockEntity): counts down each tick.
 *   When POWERED, the block schedules itself every 1 game tick. Each tick
 *   decrements timerTicksRemaining. When it reaches 0, POWERED -> false.
 *
 * ANTI-FEEDBACK ARCHITECTURE (5 layers, same as Repeater/Transformer):
 *   1. getSignal/getDirectSignal: return 0 toward input face, 15 on others.
 *   2. neighborChanged: if POWERED, only respond to input face updates.
 *   3. shouldSignal ThreadLocal: suppresses self-reads through solid blocks.
 *   4. updatingPos ThreadLocal: blocks re-entrant neighborChanged during setBlock.
 *   5. absorbing ThreadLocal: guards onPlace signal snapshot.
 *
 * GUI: 16 duration buttons in 2 rows (8+8) showing configurable tick presets.
 *      Content height: 89px (to fit label + 2 button rows).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineTimerBlock extends AbstractMachineBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineTimerBlock.class);

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    /**
     * Suppresses both isSignalSource and getDirectSignal during our own scan.
     * Prevents us from reading our own output through adjacent solid blocks.
     * Passed to MachineRedstoneHelper for vanilla signal reads.
     */
    /**
     * Position-based anti-feedback. See MachineRepeaterBlock for full Javadoc.
     * FIX 2026-03-29: Changed from Boolean to BlockPos for same-type power through solid blocks.
     */
    static final ThreadLocal<BlockPos> shouldSignal = ThreadLocal.withInitial(() -> null);

    /**
     * Stores the position of the timer currently inside its tick() setBlock call.
     * neighborChanged at THIS position is blocked (prevents stale dust reads).
     */
    private static final ThreadLocal<BlockPos> updatingPos = ThreadLocal.withInitial(() -> null);

    /**
     * Guard for onPlace signal snapshot. When true, the unpowered path in
     * neighborChanged skips scheduling a turn-on tick because onPlace is
     * handling the initial signal absorption.
     */
    private static final ThreadLocal<Boolean> absorbing = ThreadLocal.withInitial(() -> false);

    public MachineTimerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.TIMER;
    }

    @Override
    public int getRedstoneOutput(BlockState state) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getGuiContentHeight() {
        return 89; // Label + 2 rows of duration buttons
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
        // Read inputFace from BE (no blockstate property for input face on Timer).
        if (level instanceof Level realLevel) {
            BlockEntity be = realLevel.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity machineBE) {
                Direction inputFace = machineBE.getInputFace();
                if (inputFace != null && direction == inputFace.getOpposite()) {
                    return 0;
                }
            }
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

            // While powered (timer running), we don't need to check for signal
            // changes — the timer runs to completion regardless. The input signal
            // only matters for arming the next trigger.
            if (inputFace != null) {
                int inputSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, inputFace, shouldSignal);
                if (inputSignal <= 0) {
                    // Signal dropped while timer is running. Arm for next trigger.
                    if (!machineBE.isInputWasLow()) {
                        machineBE.setInputWasLow(true);
                        LOGGER.debug("[TIMER] input dropped at {} while running — armed", pos);
                    }
                }
            }

        } else {
            // === UNPOWERED: full scan to find new input ===
            // Timer must have finished AND signal must have dropped+risen
            if (machineBE.getTimerTicksRemaining() > 0) {
                // Timer countdown still active but we're unpowered? Shouldn't happen.
                // Safety: ignore.
                return;
            }

            int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
            int bestSignal = scan[0];
            Direction bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;

            if (bestSignal > 0 && bestFace != null) {
                Direction currentInputFace = machineBE.getInputFace();

                if (currentInputFace != null && currentInputFace == bestFace
                        && !machineBE.isInputWasLow()) {
                    // Pre-existing signal — ignore until it drops and rises
                    LOGGER.debug("[TIMER] pre-existing signal on {} at {} — ignoring", bestFace, pos);
                    return;
                }

                // Genuine rising edge — schedule turn on.
                machineBE.setInputFace(bestFace);
                if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                    level.scheduleTick(pos, this, 2, TickPriority.HIGH);
                }
            } else if (bestSignal <= 0) {
                // Signal dropped — arm for next trigger
                if (machineBE.getInputFace() != null && !machineBE.isInputWasLow()) {
                    machineBE.setInputWasLow(true);
                    LOGGER.debug("[TIMER] signal dropped at {} — now armed", pos);
                }
                machineBE.setInputFace(null);
            }
        }

        FrameActivation.markDirty(level, pos);
    }

    // ==================== tick ====================

    /**
     * Scheduled tick — handles both initial turn-on and timer countdown.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

        boolean wasPowered = state.getValue(POWERED);

        if (!wasPowered) {
            // UNPOWERED → check if we should turn ON and start timer.
            Direction currentInput = machineBE.getInputFace();

            // FIX 2026-03-31e: After piston movement, a timer that expired
            // "clean" has inputFace=null. Scan all faces to recover the input
            // direction. In normal gameplay this is unreachable because
            // neighborChanged always sets inputFace before scheduling tick().
            if (currentInput == null) {
                int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
                if (scan[0] > 0 && scan[1] >= 0) {
                    currentInput = Direction.values()[scan[1]];
                    machineBE.setInputFace(currentInput);
                    LOGGER.debug("[TIMER] tick scan found signal on {} at {} — recovering", currentInput, pos);
                }
            }

            if (currentInput != null) {
                int inputSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, currentInput, shouldSignal);

                if (inputSignal > 0) {
                    // Start the timer
                    int duration = machineBE.getTimerDuration();
                    machineBE.setTimerTicksRemaining(duration);
                    machineBE.setInputWasLow(false);

                    LOGGER.debug("[TIMER] ON at {} input={} duration={}", pos, currentInput, duration);

                    updatingPos.set(pos);
                    try {
                        level.setBlock(pos, state.setValue(POWERED, true), 3);

                        // FIX 2026-03-29: Notify neighbors of adjacent blocks so power
                        // propagates through solid blocks (like vanilla repeaters do).
                        for (Direction dir : Direction.values()) {
                            level.updateNeighborsAt(pos.relative(dir), this);
                        }
                    } finally {
                        updatingPos.set(null);
                    }

                    // Schedule the first countdown tick
                    level.scheduleTick(pos, this, 1);
                }
            }

        } else {
            // POWERED → timer countdown
            int remaining = machineBE.getTimerTicksRemaining();
            remaining--;

            if (remaining <= 0) {
                // Timer expired — turn off
                machineBE.setTimerTicksRemaining(0);

                // Check if input signal is still present for arming
                Direction currentInput = machineBE.getInputFace();
                int remainingSignal = 0;
                if (currentInput != null) {
                    remainingSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, currentInput, shouldSignal);
                }

                if (remainingSignal > 0) {
                    // Signal still present — absorb it (don't re-trigger)
                    machineBE.setInputWasLow(false);
                    LOGGER.debug("[TIMER] expired at {} — absorbing remaining signal on {}", pos, currentInput);
                } else {
                    // Signal gone — clean state
                    machineBE.setInputFace(null);
                    machineBE.setInputWasLow(false);
                    LOGGER.debug("[TIMER] expired at {} — clean", pos);
                }

                updatingPos.set(pos);
                try {
                    level.setBlock(pos, state.setValue(POWERED, false), 3);

                    // FIX 2026-03-29: Notify neighbors of adjacent blocks so power
                    // propagates through solid blocks (like vanilla repeaters do).
                    for (Direction dir : Direction.values()) {
                        level.updateNeighborsAt(pos.relative(dir), this);
                    }
                } finally {
                    updatingPos.set(null);
                }

            } else {
                // Timer still running — save remaining and schedule next tick
                machineBE.setTimerTicksRemaining(remaining);
                level.scheduleTick(pos, this, 1);
            }
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        // FIX 2026-03-31: Added !movedByPiston guard. During piston movement,
        // block entity data is restored via IPistonMovable.loadMovementData.
        // Running the absorption scan would overwrite that restored state.
        if (!oldState.is(state.getBlock()) && !level.isClientSide() && !movedByPiston) {
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
                        LOGGER.debug("[TIMER] placed at {} — absorbed pre-existing signal on {} (strength {})",
                                pos, bestFace, bestSignal);
                    }
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
        // No-op — timer doesn't need placement specialization swap logic.
    }
}
