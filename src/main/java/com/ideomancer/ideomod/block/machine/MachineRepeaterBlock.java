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
import net.minecraft.world.level.block.RedStoneWireBlock;
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
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineRepeaterBlock extends AbstractMachineBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineRepeaterBlock.class);

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final IntegerProperty DELAY = IntegerProperty.create("delay", 1, 4);

    /**
     * Suppresses both isSignalSource and getDirectSignal during our own scan.
     * Prevents us from reading our own output through adjacent solid blocks.
     */
    private static final ThreadLocal<Boolean> shouldSignal = ThreadLocal.withInitial(() -> true);

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
        // Same convention as the working Conductor:
        //   suppress when direction == inputFace.getOpposite()
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

    // ==================== Signal Detection ====================

    /**
     * Read incoming signal from a specific face.
     * Same structure as Conductor.getIncomingSignalFromFace.
     */
    private int getIncomingSignalFromFace(Level level, BlockPos pos, Direction face) {
        BlockPos neighborPos = pos.relative(face);
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();

        if (neighborBlock instanceof MachineConductorBlock) {
            int signal = neighborState.getSignal(level, neighborPos, face);
            return Math.max(0, signal - 1);

        } else if (neighborBlock instanceof MachineRepeaterBlock) {
            if (neighborState.getValue(POWERED)) {
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

            // Input face update (or inputFace is null which shouldn't happen when powered).
            // Check if input signal is gone — but do NOT update inputFace here.
            // Dust may be at an intermediate state (power=0 during recalculation).
            // We just ask: does our current input face still have signal?
            if (inputFace != null) {
                int inputSignal = getIncomingSignalFromFace(level, pos, inputFace);
                if (inputSignal <= 0) {
                    // Input face has lost signal — schedule tick to turn off.
                    // The tick will do a full rescan and may find a different input,
                    // or confirm we should turn off.
                    if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                        level.scheduleTick(pos, this, state.getValue(DELAY) * 2);
                    }
                }
                // If input is still there (> 0), nothing to do — we're correctly powered.
            }

        } else {
            // === UNPOWERED: full scan to find new input ===
            int[] scan = scanAllFaces(level, pos);
            int bestSignal = scan[0];
            Direction bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;

            // Safe to update inputFace now — we're not outputting anything.
            machineBE.setInputFace(bestFace);

            if (bestSignal > 0) {
                // Found a signal — schedule tick to turn on.
                if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                    level.scheduleTick(pos, this, state.getValue(DELAY) * 2);
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
        int beDelay = machineBE.getRepeaterDelay();

        int bestSignal;
        Direction bestFace;

        if (wasPowered) {
            // POWERED: check input face first. Only full-scan if input lost.
            // This prevents output-face dust (which has POWER=15 from us) from
            // being mistaken for a new input source.
            Direction currentInput = machineBE.getInputFace();
            if (currentInput != null) {
                int inputSignal = getIncomingSignalFromFace(level, pos, currentInput);
                if (inputSignal > 0) {
                    // Input still alive — stay powered, don't change anything.
                    bestSignal = inputSignal;
                    bestFace = currentInput;
                } else {
                    // Input face lost signal. DON'T full-scan here because output
                    // dust still has our power. Just turn off. If there's a real
                    // new source, it will trigger neighborChanged when we're unpowered,
                    // and we'll find it in the unpowered full-scan path.
                    bestSignal = 0;
                    bestFace = null;
                }
            } else {
                // Shouldn't happen (powered with no inputFace), but handle gracefully.
                bestSignal = 0;
                bestFace = null;
            }
        } else {
            // UNPOWERED: full scan to find input. Safe because we're not
            // outputting anything, so no dust has power from us.
            int[] scan = scanAllFaces(level, pos);
            bestSignal = scan[0];
            bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;
        }

        machineBE.setInputFace(bestFace);

        boolean shouldPower = bestSignal > 0;

        LOGGER.debug("[REPEATER] tick({}) was={} should={} signal={} face={} delay={}",
                pos, wasPowered, shouldPower, bestSignal, bestFace, beDelay);

        // Set updatingPos to block re-entrant neighborChanged at THIS position only.
        // Adjacent repeaters at different positions still receive their updates normally.
        updatingPos.set(pos);
        try {
            if (wasPowered && !shouldPower) {
                // Turn OFF — flag 3 = UPDATE_NEIGHBORS | UPDATE_CLIENTS
                level.setBlock(pos, state.setValue(POWERED, false).setValue(DELAY, beDelay), 3);

            } else if (!wasPowered && shouldPower) {
                // Turn ON — flag 3 = UPDATE_NEIGHBORS | UPDATE_CLIENTS
                level.setBlock(pos, state.setValue(POWERED, true).setValue(DELAY, beDelay), 3);

                // Vanilla DiodeBlock trick: if input is already gone by the time we turn on,
                // schedule a turn-off tick (handles very short pulses)
                if (bestSignal == 0) {
                    level.scheduleTick(pos, this, beDelay * 2,
                            net.minecraft.world.ticks.TickPriority.VERY_HIGH);
                }

            } else if (state.getValue(DELAY) != beDelay) {
                // Power unchanged, just sync delay
                level.setBlock(pos, state.setValue(DELAY, beDelay), 3);
            }
            // else: no change needed
        } finally {
            updatingPos.set(null);
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
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
