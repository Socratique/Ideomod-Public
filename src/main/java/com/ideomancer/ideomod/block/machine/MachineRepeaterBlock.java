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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Machine Repeater — omnidirectional redstone repeater.
 *
 * Anti-feedback strategy (derived from vanilla DiodeBlock analysis):
 *
 * Vanilla repeaters prevent feedback through static geometry: they only read
 * from FACING (back) and only output on FACING.getOpposite() (front).
 *
 * Our repeater is omnidirectional — it picks its input face dynamically and
 * outputs on all other 5 faces. We achieve the same property through:
 *
 *   1. getSignal/getDirectSignal: return 0 toward input face, 15 on others.
 *
 *   2. neighborChanged: if POWERED and update comes from an OUTPUT face,
 *      RETURN IMMEDIATELY. Only input-face updates proceed.
 *
 *   3. neighborChanged NEVER modifies inputFace when powered. Only tick() does.
 *      This prevents intermediate dust states (power flicking to 0 during
 *      recalculation) from corrupting our inputFace to null, which would
 *      disable both the getSignal suppression and the early-return guard.
 *
 *   4. No manual updateNeighborsAt anywhere except onRemove cleanup.
 *      tick() uses setBlock flag 3 (UPDATE_NEIGHBORS | UPDATE_CLIENTS)
 *      which lets vanilla handle neighbor notification. The output-face
 *      early-return in neighborChanged prevents cascading.
 *
 *   5. shouldSignal ThreadLocal suppresses self-reads through solid blocks.
 *
 * Signal detection delegated to MachineRedstoneHelper (shared single source
 * of truth for all Machine Family redstone specializations).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineRepeaterBlock extends AbstractMachineBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineRepeaterBlock.class);

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final IntegerProperty DELAY = IntegerProperty.create("delay", 1, 4);

    /**
     * Suppresses both isSignalSource and getDirectSignal during our own scan.
     * Prevents us from reading our own output through adjacent solid blocks.
     * Passed to MachineRedstoneHelper for vanilla signal reads.
     */
    static final ThreadLocal<Boolean> shouldSignal = ThreadLocal.withInitial(() -> true);

    /**
     * Stores the position of the repeater currently inside its tick() setBlock call.
     * neighborChanged at THIS position is blocked (prevents stale dust reads).
     * neighborChanged at OTHER positions (e.g. adjacent repeaters) proceeds normally.
     */
    private static final ThreadLocal<BlockPos> updatingPos = ThreadLocal.withInitial(() -> null);

    public MachineRepeaterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, false)
                .setValue(DELAY, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, DELAY);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.REPEATER;
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

            // Input face update (or inputFace is null which shouldn't happen when powered).
            if (inputFace != null) {
                int inputSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, inputFace, shouldSignal);
                if (inputSignal <= 0) {
                    if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                        level.scheduleTick(pos, this, state.getValue(DELAY) * 2);
                    }
                }
            }

        } else {
            // === UNPOWERED: full scan to find new input ===
            int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
            int bestSignal = scan[0];
            Direction bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;

            machineBE.setInputFace(bestFace);

            if (bestSignal > 0) {
                if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                    level.scheduleTick(pos, this, state.getValue(DELAY) * 2);
                }
            }
        }

        FrameActivation.markDirty(level, pos);
    }

    // ==================== tick ====================

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

        boolean wasPowered = state.getValue(POWERED);
        int beDelay = machineBE.getRepeaterDelay();

        int bestSignal;
        Direction bestFace;

        if (wasPowered) {
            Direction currentInput = machineBE.getInputFace();
            if (currentInput != null) {
                int inputSignal = MachineRedstoneHelper.getIncomingSignalFromFace(level, pos, currentInput, shouldSignal);
                if (inputSignal > 0) {
                    bestSignal = inputSignal;
                    bestFace = currentInput;
                } else {
                    bestSignal = 0;
                    bestFace = null;
                }
            } else {
                bestSignal = 0;
                bestFace = null;
            }
        } else {
            int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
            bestSignal = scan[0];
            bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;
        }

        machineBE.setInputFace(bestFace);

        boolean shouldPower = bestSignal > 0;

        LOGGER.debug("[REPEATER] tick({}) was={} should={} signal={} face={} delay={}",
                pos, wasPowered, shouldPower, bestSignal, bestFace, beDelay);

        updatingPos.set(pos);
        try {
            if (wasPowered && !shouldPower) {
                level.setBlock(pos, state.setValue(POWERED, false).setValue(DELAY, beDelay), 3);
            } else if (!wasPowered && shouldPower) {
                level.setBlock(pos, state.setValue(POWERED, true).setValue(DELAY, beDelay), 3);
                if (bestSignal == 0) {
                    level.scheduleTick(pos, this, beDelay * 2,
                            net.minecraft.world.ticks.TickPriority.VERY_HIGH);
                }
            } else if (state.getValue(DELAY) != beDelay) {
                level.setBlock(pos, state.setValue(DELAY, beDelay), 3);
            }
        } finally {
            updatingPos.set(null);
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
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
        // No-op
    }
}
