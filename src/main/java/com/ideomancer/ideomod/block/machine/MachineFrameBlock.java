package com.ideomancer.ideomod.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.frame.FrameActivation;

/**
 * Machine Frame (formerly Machine Block) -- modular mechanical chassis.
 *
 * FRAME ACTIVATION:
 * =================
 * Machine Frames are the SOURCE of activation for Frame Block chains.
 * All lifecycle events call FrameActivation.recalculate() which does a
 * full network wipe-and-rebuild — like redstone signal recalculation.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameBlock extends Block implements EntityBlock, IMachineFamily {

    public MachineFrameBlock(Properties properties) {
        super(properties);
    }

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
        if (!state.is(newState.getBlock()) && !movedByPiston) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity machineFrame) {
                Containers.dropContents(level, pos, machineFrame);
            }
            FrameActivation.recalculate(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide()) {
            FrameActivation.recalculate(level, pos);
        }
    }

    // ========================================================================
    // MACHINE FAMILY HELPERS
    // ========================================================================

    /**
     * Check if a block is part of the Machine Family.
     * Delegates to IMachineFamily.isMachineFamily() for consistency.
     */
    public static boolean isMachineFamily(Block block) {
        return IMachineFamily.isMachineFamily(block);
    }

    public static boolean isRedstoneConduit(Block block) {
        return block instanceof MachineTrackBlock;
    }

    /**
     * Check if this block can stick to another block.
     * Delegates to IMachineFamily.canMachineFamilyStickTo() for consistency.
     */
    public static boolean canMachineFamilyStickTo(BlockState other) {
        return IMachineFamily.canMachineFamilyStickTo(other);
    }

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return canMachineFamilyStickTo(other);
    }
}
