package com.ideomancer.ideomod.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A Container backed by a world position rather than a specific block entity
 * instance. Every method call looks up whatever MachineFrameBlockEntity
 * currently exists at the stored position and forwards to it.
 *
 * This means the menu seamlessly survives block swaps (Machine Frame <->
 * specializations) because the swap replaces the block and block entity
 * at the position, and next time the menu reads a slot it just finds the
 * new block entity sitting there.
 *
 * DYNAMIC SLOT SUPPORT:
 * =====================
 * Reports 2 slots. Slot 0 (specialization) works with any Machine Family BE.
 * Slot 1 (transit) only works when the BE is a MachinePipeBlockEntity.
 * When the BE is a standard MachineFrameBlockEntity, slot 1 returns empty
 * and is non-interactable. This lets the unified menu show/hide the transit
 * slot dynamically without closing the GUI on specialization swaps.
 *
 * When a player inserts an item into slot 1 via GUI, this is treated as
 * input from Direction.UP (top of block) for the pipe's round-robin system.
 *
 * REPEATER SUPPORT:
 * =================
 * isRepeater() checks if the current block is a MachineRepeaterBlock.
 * getRepeaterDelay() reads the delay from the BE for ContainerData sync.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class PositionBoundContainer implements Container {

    private final Level level;
    private final BlockPos pos;

    public PositionBoundContainer(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    /**
     * Look up the current block entity at our position.
     * Returns null if not present or not a MachineFrameBlockEntity (or subclass).
     */
    private MachineFrameBlockEntity getBE() {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MachineFrameBlockEntity mfbe ? mfbe : null;
    }

    /**
     * Get the pipe-specific BE, or null if the block is not a pipe.
     */
    private MachinePipeBlockEntity getPipeBE() {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MachinePipeBlockEntity pipe ? pipe : null;
    }

    /**
     * Check if the current BE is a pipe (has transit slot).
     */
    public boolean isPipe() {
        return getPipeBE() != null;
    }

    /**
     * Check if the current block is a Machine Repeater.
     */
    public boolean isRepeater() {
        return level.getBlockState(pos).getBlock() instanceof MachineRepeaterBlock;
    }

    /**
     * Get the repeater delay from the BE. Returns 1 if not a repeater.
     */
    public int getRepeaterDelay() {
        MachineFrameBlockEntity be = getBE();
        return be != null ? be.getRepeaterDelay() : 1;
    }

    // ========================================================================
    // CONTAINER FORWARDING
    // ========================================================================

    @Override
    public int getContainerSize() {
        return 2; // Always 2 — slot 1 is no-op when not a pipe
    }

    @Override
    public boolean isEmpty() {
        MachineFrameBlockEntity be = getBE();
        return be == null || be.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == MachinePipeBlockEntity.TRANSIT_SLOT) {
            MachinePipeBlockEntity pipe = getPipeBE();
            return pipe != null ? pipe.getTransitItem() : ItemStack.EMPTY;
        }
        MachineFrameBlockEntity be = getBE();
        return be != null ? be.getItem(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == MachinePipeBlockEntity.TRANSIT_SLOT) {
            MachinePipeBlockEntity pipe = getPipeBE();
            return pipe != null ? pipe.removeItem(slot, amount) : ItemStack.EMPTY;
        }
        MachineFrameBlockEntity be = getBE();
        return be != null ? be.removeItem(slot, amount) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot == MachinePipeBlockEntity.TRANSIT_SLOT) {
            MachinePipeBlockEntity pipe = getPipeBE();
            return pipe != null ? pipe.removeItemNoUpdate(slot) : ItemStack.EMPTY;
        }
        MachineFrameBlockEntity be = getBE();
        return be != null ? be.removeItemNoUpdate(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == MachinePipeBlockEntity.TRANSIT_SLOT) {
            MachinePipeBlockEntity pipe = getPipeBE();
            if (pipe != null) {
                pipe.setItem(slot, stack);
                // Player GUI insertion into transit slot = input from top
                if (!stack.isEmpty()) {
                    pipe.onItemReceived(Direction.UP);
                }
            }
            return;
        }
        MachineFrameBlockEntity be = getBE();
        if (be != null) be.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        MachineFrameBlockEntity be = getBE();
        if (be != null) be.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        MachineFrameBlockEntity be = getBE();
        if (be == null) return false;
        return be.stillValid(player);
    }

    @Override
    public void clearContent() {
        MachineFrameBlockEntity be = getBE();
        if (be != null) be.clearContent();
    }

    @Override
    public int getMaxStackSize() {
        return 64; // Transit slot allows 64; spec slot enforces 1 via Slot subclass
    }

    @Override
    public void startOpen(Player player) {
        MachineFrameBlockEntity be = getBE();
        if (be != null) be.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        MachineFrameBlockEntity be = getBE();
        if (be != null) be.stopOpen(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == MachinePipeBlockEntity.TRANSIT_SLOT) {
            // Transit slot only accepts items when the BE is a pipe
            return getPipeBE() != null;
        }
        MachineFrameBlockEntity be = getBE();
        return be != null && be.canPlaceItem(slot, stack);
    }
}
