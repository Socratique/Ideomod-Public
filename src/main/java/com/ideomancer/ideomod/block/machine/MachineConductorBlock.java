package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.ideomancer.ideomod.item.RedstoneRodSignalManager;
import com.google.common.collect.Sets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Machine Conductor — solid-block redstone wire.
 *
 * A Machine Family specialization that carries vanilla redstone signal (0-15, decays by 1)
 * through a full solid block. Connects to vanilla redstone components on all 6 faces.
 * Outputs weak power only (like dust — powers blocks but not through them).
 *
 * Created by inserting Redstone Dust into a Machine Frame.
 *
 * === INPUT/OUTPUT FACE MODEL ===
 *
 * The Conductor receives signal on ONE face (the input face) and outputs on the
 * other 5 faces. This naturally prevents feedback because the block never outputs
 * back toward its source.
 *
 * Input face selection:
 * - Determined by the strongest incoming signal across all 6 faces
 * - If multiple faces tie, first found wins (arbitrary but stable)
 * - If the current input face still has the strongest signal, it stays
 * - If input source turns off, all faces are rescanned for a new input
 * - If no signal on any face, inputFace = null, POWER = 0
 *
 * Signal types:
 * - Source blocks (levers, repeaters, redstone blocks, etc.): no decay.
 *   A lever at 15 directly on the Conductor gives POWER = 15.
 * - Wire blocks (dust, other Conductors): decay by 1.
 *   Dust at 14 into the Conductor gives POWER = 13.
 *
 * The inputFace is stored in the shared MachineFrameBlockEntity and persists
 * through saves and piston movement. Future redstone specializations (Toggle,
 * Pulser, Clock) will use the same input face field with their own rules.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineConductorBlock extends AbstractMachineBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineConductorBlock.class);

    /** Redstone signal strength, 0-15. Exposed to vanilla redstone system. */
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

    /**
     * When false, getDirectSignal() returns 0. Used during our own power scan
     * to prevent reading our own strong power through solid blocks.
     *
     * This is the same pattern vanilla dust uses. Without this, the path
     * Conductor -> Solid Block -> Conductor would let Conductors power each
     * other through solid blocks (which vanilla dust does not do).
     *
     * Only affects getDirectSignal (strong power), NOT getSignal (weak power).
     * getSignal is already protected by the input face model.
     *
     * ThreadLocal because blocks in Minecraft are singletons — there is one
     * MachineConductorBlock object shared by every conductor in the world.
     * A plain boolean would race if two conductors run getIncomingSignalFromFace()
     * on different threads (e.g., during chunk loading or parallel neighbor updates).
     */
    /**
     * Class-wide anti-feedback for signal reads through solid blocks.
     * When non-null (any Conductor is scanning), ALL Conductor instances
     * suppress their signal — this is intentional wire behavior, matching
     * how vanilla redstone dust works (all dust shares one block instance
     * so shouldSignal suppresses ALL dust during any dust scan).
     *
     * FIX 2026-03-29: Changed from ThreadLocal<Boolean> to ThreadLocal<BlockPos>
     * to match the new type used by MachineRedstoneHelper. Conductor keeps
     * class-wide suppression (null-check) unlike device blocks (pos-check).
     */
    private static final ThreadLocal<BlockPos> shouldSignal = ThreadLocal.withInitial(() -> null);

    public MachineConductorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    // ==================== IMachineFamily ====================

    @Override
    public MachineType getMachineType() {
        return MachineType.CONDUCTOR;
    }

    // ==================== Redstone Signal ====================

    /**
     * Reports whether this block is a signal source.
     * Returns false when shouldSignal is false (during our own scan),
     * which prevents solid blocks from even querying our signal.
     */
    @Override
    public boolean isSignalSource(BlockState state) {
        return shouldSignal.get() == null; // Suppress ALL instances during any Conductor scan (wire behavior)
    }

    /**
     * Weak power output. Returns our POWER level on output faces,
     * and 0 on the input face.
     *
     * Direction parameter: the direction FROM the querying block TO us.
     * getBestNeighborSignal calls getSignal(pos.relative(dir), dir).
     * So if a block to our NORTH queries us, direction = SOUTH
     * (it went SOUTH from itself to reach us).
     *
     * Our input face is the face of OUR block that receives signal.
     * If inputFace = NORTH, the source is to our NORTH.
     * That source queries us with direction = SOUTH.
     * To suppress: return 0 when direction == inputFace.getOpposite().
     */
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        int power = state.getValue(POWER);
        if (power == 0) return 0;

        // Get input face from block entity
        if (level instanceof Level realLevel) {
            BlockEntity be = realLevel.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity machineBE) {
                Direction inputFace = machineBE.getInputFace();
                if (inputFace != null) {
                    if (direction == inputFace.getOpposite()) {
                        return 0;
                    }
                }
            }
        }

        return power;
    }

    /**
     * Strong (direct) power output. Returns our POWER level on output faces,
     * and 0 on the input face. Also returns 0 when shouldSignal is false
     * (during our own power scan, preventing Conductor→SolidBlock→Conductor).
     *
     * This is what allows Conductor -> Solid Block -> Piston/Repeater/etc.
     * The Conductor strongly powers the solid block, and the solid block
     * then weakly powers everything touching it.
     *
     * Safe from Conductor-through-block feedback because shouldSignal=false
     * during our scan makes getDirectSignalTo see 0 from us, so solid blocks
     * powered by us report 0 to other Conductors during their scans.
     */
    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (shouldSignal.get() != null) return 0; // Suppress ALL instances during any Conductor scan (wire behavior)

        int power = state.getValue(POWER);
        if (power == 0) return 0;

        // Input face suppression — same as getSignal
        if (level instanceof Level realLevel) {
            BlockEntity be = realLevel.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity machineBE) {
                Direction inputFace = machineBE.getInputFace();
                if (inputFace != null) {
                    if (direction == inputFace.getOpposite()) {
                        return 0;
                    }
                }
            }
        }

        return power;
    }

    // ==================== Signal Propagation ====================

    /**
     * Core power update with input/output face model.
     *
     * 1. Scan all 6 faces for incoming signal, categorized as source or wire.
     * 2. Pick the strongest signal and which face it comes from.
     * 3. Update inputFace in BE and POWER in blockstate.
     * 4. Notify neighbors.
     */
    private void updatePowerStrength(Level level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

        int oldPower = state.getValue(POWER);
        Direction oldInputFace = machineBE.getInputFace();

        // Scan all 6 faces for the strongest incoming signal
        int bestSignal = 0;
        Direction bestFace = null;

        for (Direction dir : Direction.values()) {
            int signal = getIncomingSignalFromFace(level, pos, dir);
            if (signal > bestSignal) {
                bestSignal = signal;
                bestFace = dir;
            }
        }

        // Update input face in BE
        machineBE.setInputFace(bestFace);

        // Update power in blockstate
        if (oldPower != bestSignal) {
            level.setBlock(pos, state.setValue(POWER, bestSignal), 2);

            // Notify neighbors of the change
            Set<BlockPos> toUpdate = Sets.newHashSet();
            toUpdate.add(pos);
            for (Direction dir : Direction.values()) {
                toUpdate.add(pos.relative(dir));
            }
            for (BlockPos updatePos : toUpdate) {
                level.updateNeighborsAt(updatePos, this);
            }
        } else if (oldInputFace != bestFace) {
            // Power didn't change but input face did — neighbors on the old
            // and new input faces need to know (one lost our output, one gained it)
            if (oldInputFace != null) {
                level.updateNeighborsAt(pos.relative(oldInputFace), this);
            }
            if (bestFace != null) {
                level.updateNeighborsAt(pos.relative(bestFace), this);
            }
        }
    }

    /**
     * Get the incoming signal strength from a specific face.
     *
     * Checks the block on that face and categorizes it:
     * - MachineConductorBlock: wire type, read via getSignal (respects their
     *   input face suppression), subtract 1 (decay)
     * - RedStoneWireBlock: wire type, read POWER directly, subtract 1 (decay)
     * - Anything else: source type, read via vanilla signal query, no decay.
     *   shouldSignal set to our pos during this read, which makes ALL
     *   Conductor getDirectSignal calls return 0 (class-wide suppression,
     *   matching vanilla dust behavior), preventing Conductor→SolidBlock→Conductor.
     */
    private int getIncomingSignalFromFace(Level level, BlockPos pos, Direction face) {
        BlockPos neighborPos = pos.relative(face);

        // --- Redstone Rod phantom signal (highest priority) ---
        // Same as MachineRedstoneHelper: the special-case paths below bypass
        // BlockStateBase.getSignal(), so we check the rod manager directly.
        int rodSignal = RedstoneRodSignalManager.getInjectedSignal(level, neighborPos, face);
        if (rodSignal > 0) {
            return rodSignal;
        }

        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();

        if (neighborBlock instanceof MachineConductorBlock) {
            int signal = neighborState.getSignal(level, neighborPos, face);
            return Math.max(0, signal - 1);

        } else if (neighborBlock instanceof RedStoneWireBlock) {
            int power = neighborState.getValue(RedStoneWireBlock.POWER);
            return Math.max(0, power - 1);

        } else {
            shouldSignal.set(pos);
            try {
                int signal = level.getSignal(neighborPos, face);
                return signal;
            } finally {
                shouldSignal.set(null);
            }
        }
    }

    // ==================== Lifecycle (overrides base) ====================

    /**
     * Conductor overrides neighborChanged to update power strength before
     * the standard FrameActivation recalculation.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block sourceBlock,
                                 BlockPos sourcePos, boolean movedByPiston) {
        if (!level.isClientSide()) {
            updatePowerStrength(level, pos, state);
            FrameActivation.markDirty(level, pos);
        }
    }

    /**
     * Conductor overrides onPlace to update power strength and notify neighbors
     * before the standard FrameActivation recalculation.
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        // FIX 2026-03-31: Added !movedByPiston guard. During piston movement,
        // block entity data is restored via IPistonMovable.loadMovementData.
        // onMovementRestored handles rescheduling evaluation.
        if (!oldState.is(state.getBlock()) && !level.isClientSide() && !movedByPiston) {
            updatePowerStrength(level, pos, state);

            level.updateNeighborsAt(pos, this);
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }

            FrameActivation.markDirty(level, pos);
        }
    }

    /**
     * Conductor overrides onRemove to notify neighbors and update
     * FrameActivation when the block is removed.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, movedByPiston);

            // FIX 2026-03-29: Added !movedByPiston guard. During piston movement,
            // blocks are temporarily removed — firing neighbor updates here would
            // cause unnecessary redstone recalculations mid-move.
            if (!level.isClientSide() && !movedByPiston) {
                for (Direction dir : Direction.values()) {
                    level.updateNeighborsAt(pos.relative(dir), this);
                }
            }
        } else {
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    /**
     * Conductor does not use the default setPlacedBy specialization swap.
     * Conductors are created by inserting redstone into a Machine Frame,
     * not by placing a conductor block directly.
     */
    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                             @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity placer,
                             ItemStack stack) {
        // No-op: Conductor doesn't use specialization swap on placement
    }
}
