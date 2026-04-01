package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.ticks.TickPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Machine Transformer — omnidirectional redstone signal level setter.
 *
 * Receives signal on one face (input), outputs a user-configured signal
 * strength (1-15) on all other 5 faces. The input signal acts purely as
 * an on/off gate — the output level is always whatever the player sets
 * in the GUI, regardless of input strength.
 *
 * Anti-feedback architecture is identical to the Machine Repeater (5 layers,
 * derived from vanilla DiodeBlock analysis). The only behavioral difference
 * is the output value: Repeater always outputs 15, Transformer outputs
 * OUTPUT_LEVEL.
 *
 * No delay — responds on a 1-tick schedule (same as Toggle), since the
 * Transformer's purpose is level-setting, not timing.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineTransformerBlock extends AbstractMachineBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineTransformerBlock.class);

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final IntegerProperty OUTPUT_LEVEL = IntegerProperty.create("output_level", 1, 15);

    /**
     * Suppresses both isSignalSource and getDirectSignal during our own scan.
     * Prevents us from reading our own output through adjacent solid blocks.
     */
    /**
     * Position-based anti-feedback. See MachineRepeaterBlock for full Javadoc.
     * FIX 2026-03-29: Changed from Boolean to BlockPos for same-type power through solid blocks.
     */
    private static final ThreadLocal<BlockPos> shouldSignal = ThreadLocal.withInitial(() -> null);

    /**
     * Stores the position of the transformer currently inside its tick() setBlock call.
     * neighborChanged at THIS position is blocked (prevents stale dust reads).
     * neighborChanged at OTHER positions (e.g. adjacent machines) proceeds normally.
     */
    private static final ThreadLocal<BlockPos> updatingPos = ThreadLocal.withInitial(() -> null);

    public MachineTransformerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, false)
                .setValue(OUTPUT_LEVEL, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, OUTPUT_LEVEL);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.TRANSFORMER;
    }

    @Override
    public int getRedstoneOutput(BlockState state) {
        return state.getValue(POWERED) ? state.getValue(OUTPUT_LEVEL) : 0;
    }

    @Override
    public int getGuiContentHeight() {
        return 71; // 2 rows of level buttons
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
        if (level instanceof Level realLevel) {
            BlockEntity be = realLevel.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity machineBE) {
                Direction inputFace = machineBE.getInputFace();
                if (inputFace != null && direction == inputFace.getOpposite()) {
                    return 0;
                }
            }
        }

        return state.getValue(OUTPUT_LEVEL);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (pos.equals(shouldSignal.get())) return 0; // Suppress self-reads only
        return getSignal(state, level, pos, direction);
    }

    // ==================== Signal Detection ====================
    // Delegated to MachineRedstoneHelper (shared single source of truth).

    // ==================== neighborChanged ====================

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block sourceBlock, BlockPos sourcePos, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (pos.equals(updatingPos.get())) return; // Re-entrant call from our own tick — ignore

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

        boolean currentlyPowered = state.getValue(POWERED);

        if (currentlyPowered) {
            // === POWERED: only respond to input face updates ===
            Direction inputFace = machineBE.getInputFace();
            if (inputFace != null) {
                Direction sourceFace = MachineRedstoneHelper.getFaceFromSourcePos(pos, sourcePos);
                if (sourceFace != null && sourceFace != inputFace) {
                    // Output face update — ignore completely.
                    return;
                }
            }

            // Input face update. Check if input signal is gone.
            if (inputFace != null) {
                int inputSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, inputFace, shouldSignal);
                if (inputSignal <= 0) {
                    // Input face has lost signal — schedule tick to turn off.
                    if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                        level.scheduleTick(pos, this, 2, TickPriority.HIGH); // 1 redstone tick = 2 game ticks
                    }
                }
            }

        } else {
            // === UNPOWERED: full scan to find new input ===
            int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
            int bestSignal = scan[0];
            Direction bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;

            // Safe to update inputFace now — we're not outputting anything.
            machineBE.setInputFace(bestFace);

            if (bestSignal > 0) {
                // Found a signal — schedule tick to turn on.
                if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                    level.scheduleTick(pos, this, 2, TickPriority.HIGH); // 1 redstone tick = 2 game ticks
                }
            }
        }

        FrameActivation.markDirty(level, pos);
    }

    // ==================== tick ====================

    /**
     * Scheduled tick — the ONLY place where POWERED state changes and
     * inputFace is updated while powered.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

        boolean wasPowered = state.getValue(POWERED);
        int outputLevel = machineBE.getTransformerLevel();

        int bestSignal;
        Direction bestFace;

        if (wasPowered) {
            // POWERED: check input face first. Only full-scan if input lost.
            Direction currentInput = machineBE.getInputFace();
            if (currentInput != null) {
                int inputSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, currentInput, shouldSignal);
                if (inputSignal > 0) {
                    bestSignal = inputSignal;
                    bestFace = currentInput;
                } else {
                    // Input face lost signal. Just turn off.
                    bestSignal = 0;
                    bestFace = null;
                }
            } else {
                bestSignal = 0;
                bestFace = null;
            }
        } else {
            // UNPOWERED: full scan to find input.
            int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
            bestSignal = scan[0];
            bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;
        }

        machineBE.setInputFace(bestFace);

        boolean shouldPower = bestSignal > 0;

        LOGGER.debug("[TRANSFORMER] tick({}) was={} should={} signal={} face={} outputLevel={}",
                pos, wasPowered, shouldPower, bestSignal, bestFace, outputLevel);

        // Set updatingPos to block re-entrant neighborChanged at THIS position only.
        updatingPos.set(pos);
        try {
            boolean poweredChanged = false;
            if (wasPowered && !shouldPower) {
                // Turn OFF
                level.setBlock(pos, state.setValue(POWERED, false).setValue(OUTPUT_LEVEL, outputLevel), 3);
                poweredChanged = true;

            } else if (!wasPowered && shouldPower) {
                // Turn ON
                level.setBlock(pos, state.setValue(POWERED, true).setValue(OUTPUT_LEVEL, outputLevel), 3);
                poweredChanged = true;

                // Vanilla DiodeBlock trick: handle very short pulses
                if (bestSignal == 0) {
                    level.scheduleTick(pos, this, 2,
                            net.minecraft.world.ticks.TickPriority.VERY_HIGH);
                }

            } else if (state.getValue(OUTPUT_LEVEL) != outputLevel) {
                // Power unchanged, just sync output level — neighbors still
                // need to know the output strength changed.
                level.setBlock(pos, state.setValue(OUTPUT_LEVEL, outputLevel), 3);
                poweredChanged = true;
            }
            // else: no change needed

            // FIX 2026-03-29: Notify neighbors of adjacent blocks so power
            // propagates through solid blocks (like vanilla repeaters do).
            if (poweredChanged) {
                for (Direction dir : Direction.values()) {
                    level.updateNeighborsAt(pos.relative(dir), this);
                }
            }
        } finally {
            updatingPos.set(null);
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        // FIX 2026-03-31: Added !movedByPiston guard. During piston movement,
        // onMovementRestored already handles rescheduling the evaluation tick.
        if (!oldState.is(state.getBlock()) && !level.isClientSide() && !movedByPiston) {
            // Schedule a tick to evaluate initial power state.
            if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                level.scheduleTick(pos, this, 1);
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
        // No-op — AbstractMachineBlock.setPlacedBy handles swap, but Transformer
        // doesn't need it since this block IS the post-swap target.
    }
}
