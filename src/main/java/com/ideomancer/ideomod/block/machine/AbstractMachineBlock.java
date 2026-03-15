package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Abstract base class for all Machine Family blocks.
 *
 * Consolidates shared logic across machine specialization blocks:
 * - Block entity management (shared MachineFrameBlockEntity)
 * - GUI interaction (right-click to open screen)
 * - Drop handling (specialization-aware NBT drops)
 * - Creative middle-click (registry-based clone)
 * - Lifecycle hooks (FrameActivation recalculation)
 * - Stickiness (type-aware via IMachineFamily)
 * - Specialization swap on placement
 *
 * Specializations override getMachineType() and add their unique behavior.
 * Lifecycle hooks (onPlace/onRemove/neighborChanged) can be overridden by
 * calling super to preserve FrameActivation recalculation, or handled
 * entirely by the subclass if custom lifecycle logic is needed.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public abstract class AbstractMachineBlock extends Block implements EntityBlock, IMachineFamily {

    public AbstractMachineBlock(Properties properties) {
        super(properties);
    }

    // ========================================================================
    // BLOCK ENTITY
    // ========================================================================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineFrameBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ========================================================================
    // GUI INTERACTION
    // ========================================================================

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineFrameBlockEntity machineFrame) {
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, machineFrame, pos);
            }
        }
        return InteractionResult.CONSUME;
    }

    // ========================================================================
    // STICKINESS
    // ========================================================================

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return IMachineFamily.canMachineFamilyStickTo(getMachineType(), other);
    }

    // ========================================================================
    // LIFECYCLE -- FrameActivation recalculation
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
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                FrameActivation.markDirty(level, pos);
            }
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

    /**
     * Handles specialization swap on placement. Machine blocks placed with
     * a specialization item in their BE will swap to the correct block type.
     * Shared by MachineFrameBlock, MachineTrackBlock, and MachineTrolleyBlock.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                             @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity machineEntity) {
                ItemStack stored = machineEntity.getSpecializationItem();
                if (!stored.isEmpty()) {
                    MachineSwapHelper.checkAndSwap(level, pos, stored);
                }
            }
        }
    }

    // ========================================================================
    // DROPS
    // ========================================================================

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

    /**
     * Creative middle-click. Uses registry lookup (not BE read) because
     * this runs client-side where the BE inventory isn't synced.
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target,
                                        BlockGetter level, BlockPos pos, Player player) {
        return MachineSwapHelper.createCloneStack(this);
    }
}
