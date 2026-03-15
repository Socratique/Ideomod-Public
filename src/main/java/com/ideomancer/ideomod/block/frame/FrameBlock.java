package com.ideomancer.ideomod.block.frame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;

/**
 * Base class for empty Frame Blocks (Iron Frame, Copper Frame).
 *
 * Passive activation: all lifecycle events call FrameActivation.markDirty()
 * which does a full network wipe-and-rebuild from Machine Block sources.
 * Frames never decide their own activation state.
 *
 * WATERLOGGING:
 * =============
 * Empty frames can be waterlogged -- water flows through the open frame.
 * Implements SimpleWaterloggedBlock for standard waterlogging behavior:
 *   - Placed in water -> auto-waterlogged
 *   - Bucket interaction to add/remove water
 *   - Water flows out to neighbors naturally
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FrameBlock extends Block implements IFrameBlock, SimpleWaterloggedBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final FrameMaterial frameMaterial;

    public FrameBlock(FrameMaterial material, Properties properties) {
        super(properties);
        this.frameMaterial = material;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    public FrameMaterial getFrameMaterial() {
        return frameMaterial;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, WATERLOGGED);
    }

    // ========================================================================
    // PLACEMENT — auto-waterlog when placed in water
    // ========================================================================

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    // ========================================================================
    // WATERLOGGING — fluid behavior
    // ========================================================================

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /**
     * When a neighbor changes (block placed/broken adjacent), schedule a
     * fluid tick so water flows out naturally. This is the standard pattern
     * for waterlogged blocks (stairs, slabs, fences all do this).
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ========================================================================
    // STICKINESS
    // ========================================================================

    @Override
    public boolean isStickyBlock(BlockState state) {
        return state.getValue(ACTIVE);
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        Block otherBlock = other.getBlock();

        // --- Frame family: same-material match, both must be active ---
        if (otherBlock instanceof IFrameBlock otherFrame) {
            // Other frame must be active to stick
            if (!IFrameBlock.isActive(other)) {
                // Fall through — might have inner stickiness (framed slime etc.)
            } else {
                if (otherFrame.getFrameMaterial() == this.frameMaterial) return true;

                // Cross-frame mediation: if other is a FramedBlock with an inner
                // conductor matching OUR frame material, we stick to it.
                // Example: iron frame sticks to copper-framed iron block (inner=IRON)
                if (otherBlock instanceof FramedBlock) {
                    FrameMaterial otherInnerMat = FrameMaterial.fromConductorValue(
                        other.getValue(FramedBlock.INNER_CONDUCTOR));
                    if (otherInnerMat == this.frameMaterial) return true;
                }
            }

            // Cross-material: DON'T return false here. The other block might
            // have inner stickiness (e.g. iron-framed slime) that should still
            // be able to grab us. Fall through to permissive checks below.
        }

        // --- Machine family ---
        if (MachineFrameBlock.isMachineFamily(otherBlock)) return true;

        // --- Vanilla metal conductor: same-material match ---
        FrameMaterial otherVanillaMat = FrameMaterial.fromVanillaBlock(otherBlock);
        if (otherVanillaMat == this.frameMaterial) return true;

        // --- Permissive: allow vanilla stickies to grab us ---
        if (otherBlock instanceof SlimeBlock) return true;
        if (otherBlock instanceof HoneyBlock) return true;
        if (otherBlock instanceof OddSlimeBlock) return true;

        // --- Permissive: allow framed blocks with inner stickiness to grab us ---
        // Without this, a framed slime (cross-material) can't grab an empty frame
        // because the IFrameBlock check above falls through on material mismatch,
        // and nothing else matches. This is the passive "yes, you can grab me" side
        // of the bidirectional canStickTo check.
        if (otherBlock instanceof FramedBlock && other.getValue(FramedBlock.INNER_STICKY) > 0) return true;

        return false;
    }

    // ========================================================================
    // LIFECYCLE — all trigger full network recalculation
    // ========================================================================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            FrameActivation.markDirty(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !movedByPiston) {
            FrameActivation.markDirty(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide()) {
            FrameActivation.markDirty(level, pos);
        }
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
