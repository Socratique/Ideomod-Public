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
 * Machine Pulser — fixed-duration pulse generator.
 *
 * Converts any incoming rising-edge signal into a clean, fixed-length pulse.
 * Unlike the Timer (player-configurable duration via GUI buttons), the Pulser
 * has NO GUI controls beyond the specialization slot — its pulse duration is
 * fixed from config ({@code pulser.pulse_duration}, default 2 game ticks).
 *
 * Specialization item: Any vanilla button (tag-based: {@code ideomod:pulser_items}).
 *
 * EDGE DETECTION:
 *   Same rising-edge-only pattern as MachineToggleBlock / MachineTimerBlock.
 *   Uses inputWasLow in MachineFrameBlockEntity to track whether signal has
 *   dropped since last activation. After the pulse expires and output ends,
 *   the block requires a fresh signal (drop + rise) to fire again.
 *
 * PULSE COUNTDOWN:
 *   pulserTicksRemaining (in MachineFrameBlockEntity): counts down each tick.
 *   Duration read from IdeomodConfig.getPulserPulseDuration() each activation.
 *   When POWERED, the block schedules itself every 1 game tick. Each tick
 *   decrements pulserTicksRemaining. When it reaches 0, POWERED -> false.
 *
 * ANTI-FEEDBACK ARCHITECTURE (5 layers, identical to Repeater/Transformer/Timer):
 *   1. getSignal/getDirectSignal: return 0 toward input face, 15 on others.
 *   2. neighborChanged: if POWERED, only respond to input face updates.
 *   3. shouldSignal ThreadLocal: suppresses self-reads through solid blocks.
 *   4. updatingPos ThreadLocal: blocks re-entrant neighborChanged during setBlock.
 *   5. absorbing ThreadLocal: guards onPlace signal snapshot.
 *
 * GUI: No controls beyond the specialization slot (no buttons, no packet).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachinePulserBlock extends AbstractMachineBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachinePulserBlock.class);

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    /**
     * Suppresses both isSignalSource and getDirectSignal during our own scan.
     * Prevents us from reading our own output through adjacent solid blocks.
     * Passed to MachineRedstoneHelper for vanilla signal reads.
     */
    static final ThreadLocal<Boolean> shouldSignal = ThreadLocal.withInitial(() -> true);

    /**
     * Stores the position of the pulser currently inside its tick() setBlock call.
     * neighborChanged at THIS position is blocked (prevents stale dust reads).
     */
    private static final ThreadLocal<BlockPos> updatingPos = ThreadLocal.withInitial(() -> null);

    /**
     * Guard for onPlace signal snapshot. When true, the unpowered path in
     * neighborChanged skips scheduling a turn-on tick because onPlace is
     * handling the initial signal absorption.
     */
    private static final ThreadLocal<Boolean> absorbing = ThreadLocal.withInitial(() -> false);

    public MachinePulserBlock(Properties properties) {
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
        return MachineType.PULSER;
    }

    @Override
    public int getRedstoneOutput(BlockState state) {
        return state.getValue(POWERED) ? 15 : 0;
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
        if (!shouldSignal.get()) return 0;
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

            // While powered (pulse running), track input signal for arming
            // but do NOT affect the countdown — pulse always runs to completion.
            if (inputFace != null) {
                int inputSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, inputFace, shouldSignal);
                if (inputSignal <= 0) {
                    // Signal dropped while pulse is running. Arm for next trigger.
                    if (!machineBE.isInputWasLow()) {
                        machineBE.setInputWasLow(true);
                        LOGGER.debug("[PULSER] input dropped at {} while running — armed", pos);
                    }
                }
            }

        } else {
            // === UNPOWERED: full scan to find new input ===
            if (machineBE.getPulserTicksRemaining() > 0) {
                // Pulse countdown still active but we're unpowered? Shouldn't happen.
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
                    LOGGER.debug("[PULSER] pre-existing signal on {} at {} — ignoring", bestFace, pos);
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
                    LOGGER.debug("[PULSER] signal dropped at {} — now armed", pos);
                }
                machineBE.setInputFace(null);
            }
        }

        FrameActivation.markDirty(level, pos);
    }

    // ==================== tick ====================

    /**
     * Scheduled tick — handles both initial turn-on and pulse countdown.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

        boolean wasPowered = state.getValue(POWERED);

        if (!wasPowered) {
            // UNPOWERED → check if we should turn ON and start pulse.
            Direction currentInput = machineBE.getInputFace();

            if (currentInput != null) {
                int inputSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, currentInput, shouldSignal);

                if (inputSignal > 0) {
                    // Start the pulse — read duration from config
                    int duration = IdeomodConfig.getPulserPulseDuration();
                    machineBE.setPulserTicksRemaining(duration);
                    machineBE.setInputWasLow(false);

                    LOGGER.debug("[PULSER] ON at {} input={} duration={}", pos, currentInput, duration);

                    updatingPos.set(pos);
                    try {
                        level.setBlock(pos, state.setValue(POWERED, true), 3);
                    } finally {
                        updatingPos.set(null);
                    }

                    // Schedule the first countdown tick
                    level.scheduleTick(pos, this, 1);
                }
            }

        } else {
            // POWERED → pulse countdown
            int remaining = machineBE.getPulserTicksRemaining();
            remaining--;

            if (remaining <= 0) {
                // Pulse expired — turn off
                machineBE.setPulserTicksRemaining(0);

                // Check if input signal is still present for arming
                Direction currentInput = machineBE.getInputFace();
                int remainingSignal = 0;
                if (currentInput != null) {
                    remainingSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, currentInput, shouldSignal);
                }

                if (remainingSignal > 0) {
                    // Signal still present — absorb it (don't re-trigger)
                    machineBE.setInputWasLow(false);
                    LOGGER.debug("[PULSER] expired at {} — absorbing remaining signal on {}", pos, currentInput);
                } else {
                    // Signal gone — clean state
                    machineBE.setInputFace(null);
                    machineBE.setInputWasLow(false);
                    LOGGER.debug("[PULSER] expired at {} — clean", pos);
                }

                updatingPos.set(pos);
                try {
                    level.setBlock(pos, state.setValue(POWERED, false), 3);
                } finally {
                    updatingPos.set(null);
                }

            } else {
                // Pulse still running — save remaining and schedule next tick
                machineBE.setPulserTicksRemaining(remaining);
                level.scheduleTick(pos, this, 1);
            }
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
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
                        LOGGER.debug("[PULSER] placed at {} — absorbed pre-existing signal on {} (strength {})",
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
        // No-op — pulser doesn't need placement specialization swap logic.
    }
}
