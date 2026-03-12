package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.google.common.collect.Sets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
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
public class MachineConductorBlock extends Block implements IMachineFamily, EntityBlock {

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
     */
    private boolean shouldSignal = true;

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

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return IMachineFamily.canMachineFamilyStickTo(MachineType.CONDUCTOR, other);
    }

    // ==================== EntityBlock (shared BE) ====================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineFrameBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineFrameBlockEntity machineFrame) {
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, machineFrame, pos);
            }
        }
        return InteractionResult.CONSUME;
    }

    // ==================== Redstone Signal ====================

    /**
     * Reports whether this block is a signal source.
     * Returns false when shouldSignal is false (during our own scan),
     * which prevents solid blocks from even querying our signal.
     */
    @Override
    public boolean isSignalSource(BlockState state) {
        return this.shouldSignal;
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
     *
     * DEBUG: Logging included for first test. Remove once direction
     * mapping is confirmed correct.
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
                    // Suppress output toward the input face.
                    // direction = the direction from querier to us.
                    // If our input face is NORTH (source to our north),
                    // the source queries with direction SOUTH.
                    // Suppress when direction == inputFace.getOpposite().
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
        if (!this.shouldSignal) return 0;

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
     *   shouldSignal=false during this read so our getDirectSignal returns 0,
     *   preventing Conductor→SolidBlock→Conductor power-through.
     */
    private int getIncomingSignalFromFace(Level level, BlockPos pos, Direction face) {
        BlockPos neighborPos = pos.relative(face);
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();

        if (neighborBlock instanceof MachineConductorBlock) {
            // Wire type: read POWER directly, decay by 1.
            // Only count it if the other Conductor is actually outputting toward us.
            // If the other Conductor's input face is toward us, it won't output
            // to us — but we can't easily check that here without reading its BE.
            // Instead, just read the power. If it's outputting 0 toward us via
            // getSignal, the direct POWER read may be higher than what it would
            // actually give us. But since getSignal handles the suppression,
            // let's use getSignal for Conductors too — with the face direction.
            //
            // Actually: for Conductor-to-Conductor, use getSignal which respects
            // the other Conductor's input face suppression. The direction param
            // from us to the neighbor: we're going in direction 'face' to reach
            // the neighbor. The neighbor's getSignal receives direction = face.
            // Wait — from getBestNeighborSignal pattern: getSignal(neighborPos, face).
            // So the neighbor receives direction = face.
            //
            // This is correct: if we're checking our NORTH face, the neighbor is
            // to our north, and it receives direction=NORTH (same as vanilla pattern).
            // The neighbor's getSignal checks if NORTH.getOpposite()=SOUTH is its
            // input face, and suppresses if so.
            int signal = neighborState.getSignal(level, neighborPos, face);
            return Math.max(0, signal - 1);

        } else if (neighborBlock instanceof RedStoneWireBlock) {
            // Redstone dust: wire type, decay by 1.
            // Read POWER directly since dust's getSignal is directional and may
            // return 0 if the dust isn't "pointing" at us.
            int power = neighborState.getValue(RedStoneWireBlock.POWER);
            return Math.max(0, power - 1);

        } else {
            // Non-wire: source type, no decay.
            // Use vanilla signal query. The 'face' direction here follows the
            // same convention as getBestNeighborSignal: getSignal(neighborPos, face).
            //
            // shouldSignal=false during this read so that if the neighbor is a
            // solid block, and that solid block checks getDirectSignalTo, and one
            // of its faces has another Conductor — that Conductor's getDirectSignal
            // returns 0. This prevents Conductor→SolidBlock→Conductor power-through,
            // matching how vanilla dust can't power other dust through solid blocks.
            this.shouldSignal = false;
            int signal = level.getSignal(neighborPos, face);
            this.shouldSignal = true;
            return signal;
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block sourceBlock,
                                 BlockPos sourcePos, boolean movedByPiston) {
        if (!level.isClientSide()) {
            updatePowerStrength(level, pos, state);
            FrameActivation.recalculate(level, pos);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
            updatePowerStrength(level, pos, state);

            level.updateNeighborsAt(pos, this);
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }

            FrameActivation.recalculate(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, movedByPiston);

            if (!level.isClientSide()) {
                for (Direction dir : Direction.values()) {
                    level.updateNeighborsAt(pos.relative(dir), this);
                }
                FrameActivation.recalculate(level, pos);
            }
        } else {
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    // ==================== Drops ====================

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (MachineSwapHelper.isSwapping()) return drops;

        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof MachineFrameBlockEntity machineEntity
                && !machineEntity.getSpecializationItem().isEmpty()) {
            drops.clear();
            drops.add(MachineSwapHelper.createDrop(this, machineEntity));
        }
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target,
                                        BlockGetter level, BlockPos pos, Player player) {
        return MachineSwapHelper.createCloneStack(this);
    }
}
