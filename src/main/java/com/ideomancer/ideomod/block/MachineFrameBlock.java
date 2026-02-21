package com.ideomancer.ideomod.block;

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
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Machine Frame (formerly Machine Block) -- modular mechanical chassis.
 *
 * A block entity with a single item slot that determines its specialization.
 * Right-click to open a GUI. Insert an item to specialize. Remove the item
 * to revert to blank chassis.
 *
 * PISTON MOVEMENT:
 * ================
 * Machine Frame is movable by pistons (PushReaction.NORMAL set in ModBlocks
 * properties + PistonBaseBlockMixin bypasses vanilla's block entity check).
 *
 * Data preservation during moves:
 *   SAVE: PistonBaseBlockMixin (vanilla pistons) or BlockMoveHelper step 2.5
 *         (Repulser/Attractor/Track) saves IPistonMovable data to
 *         MovingBlockEntityData keyed by destination position.
 *   RESTORE: PistonMovingBlockEntityMixin restores data after the 2-tick
 *         animation completes and the fresh block entity exists.
 *
 * IMPORTANT: Do NOT add restoration logic to onPlace(). During setBlock(),
 * onPlace() fires BEFORE the block entity is created by newBlockEntity().
 * Any data retrieved in onPlace() would be consumed but the block entity
 * doesn't exist yet to receive it. The mixin handles restoration at the
 * correct time.
 *
 * REGISTRY ID NOTE:
 * ==================
 * Registry ID remains "machine_block" for world compatibility. Display name
 * and class name changed to "Machine Frame". TODO: Rename registry ID in a
 * future breaking change version.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameBlock extends Block implements EntityBlock {

    public MachineFrameBlock(Properties properties) {
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
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineFrameBlockEntity machineFrame) {
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, machineFrame, pos);
            }
        }

        return InteractionResult.CONSUME;
    }

    // ========================================================================
    // DROP ITEMS ON BREAK
    // ========================================================================

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        // Only drop contents when the block is actually broken, not when
        // being moved by a piston (movedByPiston = true means it's being
        // replaced by MovingPistonBlock during transit)
        if (!state.is(newState.getBlock()) && !movedByPiston) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity machineFrame) {
                Containers.dropContents(level, pos, machineFrame);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ========================================================================
    // MACHINE FAMILY HELPERS (static -- used by all Machine Family blocks)
    // ========================================================================

    /**
     * Check if a block is part of the Machine Family.
     * Machine Family = Machine Frame, Machine Track, Machine Trolley.
     *
     * When adding new Machine Family block types, update this method.
     */
    public static boolean isMachineFamily(Block block) {
        return block instanceof MachineFrameBlock
            || block instanceof MachineTrackBlock
            || block instanceof MachineTrolleyBlock;
    }

    /**
     * Check if a block is a redstone conduit (carries signal with decay).
     * Currently only Machine Track conducts.
     */
    public static boolean isRedstoneConduit(Block block) {
        return block instanceof MachineTrackBlock;
    }

    /**
     * Shared canStickTo logic for all Machine Family blocks.
     */
    public static boolean canMachineFamilyStickTo(BlockState other) {
        Block otherBlock = other.getBlock();
        if (isMachineFamily(otherBlock)) return true;
        if (otherBlock instanceof SlimeBlock) return true;
        if (otherBlock instanceof HoneyBlock) return true;
        if (otherBlock instanceof OddSlimeBlock) return true;
        return false;
    }

    // ========================================================================
    // FEATURE 1: Selective Stickiness (Forge hooks + mixin)
    // ========================================================================

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return canMachineFamilyStickTo(other);
    }
}
