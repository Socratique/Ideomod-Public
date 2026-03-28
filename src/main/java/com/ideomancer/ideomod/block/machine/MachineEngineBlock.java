package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.ticks.TickPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Machine Engine — fuel-burning pulse clock.
 *
 * Combines a furnace-style fuel system with a configurable tick interval
 * to produce periodic single-tick redstone pulses at strength 15.
 *
 * Three prerequisites to pulse:
 *   1. Redstone signal on at least one face (input face)
 *   2. Fuel burning (litTime > 0)
 *   3. Pulse interval elapsed (pulseCounter >= pulseInterval)
 *
 * Specialization item: Vanilla Furnace (Items.FURNACE).
 *
 * ANTI-FEEDBACK ARCHITECTURE (5 layers, same as Repeater/Transformer/Timer):
 *   1. getSignal/getDirectSignal: return 0 toward input face, 15 on others.
 *   2. neighborChanged: if POWERED, only respond to input face updates.
 *   3. shouldSignal ThreadLocal: suppresses self-reads through solid blocks.
 *   4. updatingPos ThreadLocal: blocks re-entrant neighborChanged during setBlock.
 *   5. absorbing ThreadLocal: guards onPlace signal snapshot.
 *
 * BLOCKSTATE PROPERTIES:
 *   LIT: sustained — burning fuel AND receiving signal (outer texture swap)
 *   POWERED: transient — 1-tick pulse output state (indicator tint)
 *
 * GUI: fuel slot, flame indicator, 8 interval buttons in 1 row.
 *      Content height: 89px (matches Timer/all specializations).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineEngineBlock extends AbstractMachineBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineEngineBlock.class);

    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    /**
     * Suppresses both isSignalSource and getDirectSignal during our own scan.
     * Prevents us from reading our own output through adjacent solid blocks.
     */
    static final ThreadLocal<Boolean> shouldSignal = ThreadLocal.withInitial(() -> true);

    /**
     * Stores the position of the engine currently inside its setBlock call.
     * neighborChanged at THIS position is blocked (prevents stale dust reads).
     */
    private static final ThreadLocal<BlockPos> updatingPos = ThreadLocal.withInitial(() -> null);

    /**
     * Guard for onPlace signal snapshot. When true, the unpowered path in
     * neighborChanged skips scheduling a turn-on because onPlace is handling
     * the initial signal absorption.
     */
    private static final ThreadLocal<Boolean> absorbing = ThreadLocal.withInitial(() -> false);

    public MachineEngineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIT, false)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, POWERED);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.ENGINE;
    }

    @Override
    public int getRedstoneOutput(BlockState state) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getGuiContentHeight() {
        return 89; // Fuel slot + flame + label + 1 row of interval buttons
    }

    // ==================== Block Entity ====================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineEngineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == ModBlockEntities.MACHINE_ENGINE.get()
                ? (lvl, pos, st, be) -> ((MachineEngineBlockEntity) be).serverTick(lvl, pos, st)
                : null;
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
        if (!(be instanceof MachineEngineBlockEntity engineBE)) return;

        boolean currentlyPowered = state.getValue(POWERED);

        if (currentlyPowered) {
            // === POWERED (pulse active): only respond to input face updates ===
            Direction inputFace = engineBE.getInputFace();
            if (inputFace != null) {
                Direction sourceFace = MachineRedstoneHelper.getFaceFromSourcePos(pos, sourcePos);
                if (sourceFace != null && sourceFace != inputFace) {
                    return; // Output face update — ignore
                }
            }
        }

        // Scan for signal
        int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
        int bestSignal = scan[0];
        Direction bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;

        if (bestSignal > 0 && bestFace != null) {
            Direction currentInputFace = engineBE.getInputFace();

            if (currentInputFace != null && currentInputFace == bestFace
                    && !engineBE.isInputWasLow()) {
                // Pre-existing signal on same face — absorb (placement absorption)
                return;
            }

            // New signal or signal returned after drop
            engineBE.setInputFace(bestFace);
            engineBE.setInputWasLow(false);
            LOGGER.info("[ENGINE] Signal detected at {} — face={}, strength={}", pos, bestFace, bestSignal);

            // Update LIT state: needs fuel AND signal
            updateLitState(level, pos, state, engineBE);

        } else if (bestSignal <= 0) {
            // Signal dropped
            if (engineBE.getInputFace() != null) {
                engineBE.setInputWasLow(true);
            }
            engineBE.setInputFace(null);

            // Reset pulse counter on signal loss
            engineBE.setPulseCounter(0);

            // Turn off POWERED if active
            if (currentlyPowered) {
                updatingPos.set(pos);
                try {
                    level.setBlock(pos, state.setValue(POWERED, false).setValue(LIT, false), 3);
                } finally {
                    updatingPos.set(null);
                }
            } else {
                // Just update LIT
                updateLitState(level, pos, state, engineBE);
            }
        }

        FrameActivation.markDirty(level, pos);
    }

    /**
     * Updates the LIT blockstate based on current conditions.
     * LIT = litTime > 0 AND has redstone input.
     */
    void updateLitState(Level level, BlockPos pos, BlockState state, MachineEngineBlockEntity engineBE) {
        boolean shouldBeLit = engineBE.getLitTime() > 0 && engineBE.getInputFace() != null;
        boolean currentlyLit = state.getValue(LIT);
        if (shouldBeLit != currentlyLit) {
            updatingPos.set(pos);
            try {
                level.setBlock(pos, state.setValue(LIT, shouldBeLit), 3);
            } finally {
                updatingPos.set(null);
            }
        }
    }

    // ==================== Pulse Management (called from BE ticker) ====================

    /**
     * Called by MachineEngineBlockEntity.serverTick() to emit a 1-redstone-tick pulse.
     * Sets POWERED=true and schedules a 2-game-tick (1 redstone tick) turn-off.
     */
    void emitPulse(Level level, BlockPos pos, BlockState state) {
        if (!state.getValue(POWERED)) {
            LOGGER.info("[ENGINE] emitPulse at {} — setting POWERED=true, scheduling 2-tick off", pos);
            updatingPos.set(pos);
            try {
                level.setBlock(pos, state.setValue(POWERED, true), 3);
            } finally {
                updatingPos.set(null);
            }
            // Schedule tick in 2 game ticks (1 redstone tick) to turn POWERED off
            // 1 game tick is too short for client-side rendering of dust/tint changes
            level.scheduleTick(pos, this, 2, TickPriority.HIGH);
        }
    }

    // ==================== tick ====================

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Scheduled tick: turn off POWERED after 2-game-tick (1 redstone tick) pulse
        if (state.getValue(POWERED)) {
            updatingPos.set(pos);
            try {
                level.setBlock(pos, state.setValue(POWERED, false), 3);
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
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineEngineBlockEntity engineBE) {
                absorbing.set(true);
                try {
                    int[] scan = MachineRedstoneHelper.scanAllFaces(level, pos, shouldSignal);
                    int bestSignal = scan[0];
                    Direction bestFace = scan[1] >= 0 ? Direction.values()[scan[1]] : null;

                    if (bestSignal > 0 && bestFace != null) {
                        engineBE.setInputFace(bestFace);
                        engineBE.setInputWasLow(false);
                        LOGGER.debug("[ENGINE] placed at {} — absorbed pre-existing signal on {} (strength {})",
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
            // Drop fuel slot contents ONLY when block is actually broken/destroyed.
            // Skip during: swap (isSwapping), piston movement (movedByPiston).
            // Piston movement preserves items via IPistonMovable.saveMovementData().
            // Swap ejects items via MachineSwapHelper.ejectSecondaryItems().
            if (!MachineSwapHelper.isSwapping() && !movedByPiston) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MachineEngineBlockEntity engineBE) {
                    ItemStack fuelItem = engineBE.getFuelItem();
                    if (!fuelItem.isEmpty()) {
                        net.minecraft.world.Containers.dropItemStack(
                                level, pos.getX(), pos.getY(), pos.getZ(), fuelItem);
                    }
                }
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
            if (!level.isClientSide() && (state.getValue(POWERED) || state.getValue(LIT))) {
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
                             @Nullable net.minecraft.world.entity.LivingEntity placer,
                             ItemStack stack) {
        // No-op — engine doesn't need placement specialization swap logic.
    }

    // ==================== Comparator ====================

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineEngineBlockEntity engineBE) {
            ItemStack fuelItem = engineBE.getFuelItem();
            if (fuelItem.isEmpty()) return 0;
            // Scale 1-15 by stack size relative to max stack size
            return 1 + (fuelItem.getCount() * 14) / fuelItem.getMaxStackSize();
        }
        return 0;
    }

    // ==================== Particles ====================

    /**
     * Spawn furnace-style particles when the engine is actively burning.
     * Flame particles on all 6 faces, campfire cosy smoke rising from the top.
     * Only shows when the LIT blockstate is true (powered AND burning).
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Use LIT blockstate — only true when powered AND burning.
        // This correctly hides particles when fuel is frozen (unpowered).
        if (!state.getValue(LIT)) return;

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        // Flame particles on each face (random chance per tick, like vanilla furnace)
        if (random.nextFloat() < 0.3F) {
            for (Direction dir : Direction.values()) {
                double ox = dir.getStepX() * 0.52;
                double oy = dir.getStepY() * 0.52;
                double oz = dir.getStepZ() * 0.52;
                double rx = (random.nextFloat() - 0.5) * 0.4;
                double ry = (random.nextFloat() - 0.5) * 0.4;
                double rz = (random.nextFloat() - 0.5) * 0.4;

                // Offset randomness perpendicular to the face
                double px = x + ox + (dir.getAxis() == Direction.Axis.X ? 0 : rx);
                double py = y + oy + (dir.getAxis() == Direction.Axis.Y ? 0 : ry);
                double pz = z + oz + (dir.getAxis() == Direction.Axis.Z ? 0 : rz);

                level.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.0, 0.0);
            }
        }

        // Campfire cosy smoke rising from the top face
        if (random.nextFloat() < 0.4F) {
            double smokeX = x + (random.nextFloat() - 0.5) * 0.4;
            double smokeY = pos.getY() + 1.05;
            double smokeZ = z + (random.nextFloat() - 0.5) * 0.4;
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, smokeX, smokeY, smokeZ,
                    0.0, 0.03 + random.nextFloat() * 0.02, 0.0);
        }
    }
}
