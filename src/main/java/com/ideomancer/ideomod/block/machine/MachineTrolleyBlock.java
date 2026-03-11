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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Machine Trolley -- passive cargo carrier pushed by Tracks.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class MachineTrolleyBlock extends Block implements EntityBlock, IMachineFamily {

    public MachineTrolleyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MachineType getMachineType() { return MachineType.TROLLEY; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineFrameBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

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

    @Override
    public boolean isStickyBlock(BlockState state) { return true; }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return IMachineFamily.canMachineFamilyStickTo(MachineType.TROLLEY, other);
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            FrameActivation.recalculate(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            FrameActivation.recalculate(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide()) FrameActivation.recalculate(level, pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                             @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity me && !me.getSpecializationItem().isEmpty()) {
                MachineSwapHelper.checkAndSwap(level, pos, me.getSpecializationItem());
            }
        }
    }

    // ========================================================================
    // DROPS + CLONE
    // ========================================================================

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (MachineSwapHelper.isSwapping()) return drops;
        BlockEntity be = builder.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (be instanceof MachineFrameBlockEntity me && !me.getSpecializationItem().isEmpty()) {
            drops.clear();
            drops.add(MachineSwapHelper.createDrop(this, me));
        }
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target,
                                        BlockGetter level, BlockPos pos, Player player) {
        return MachineSwapHelper.createCloneStack(this);
    }
}
