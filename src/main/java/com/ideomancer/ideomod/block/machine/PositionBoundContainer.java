package com.ideomancer.ideomod.block.machine;

import net.minecraft.core.BlockPos;
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
 * If no valid block entity exists at the position (block was destroyed,
 * chunk unloaded, etc.), methods return safe defaults (empty slots, invalid
 * for interaction) and stillValid() returns false, which closes the GUI.
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
     * Returns null if not present or not a MachineFrameBlockEntity.
     */
    private MachineFrameBlockEntity getBE() {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MachineFrameBlockEntity mfbe ? mfbe : null;
    }

    // ========================================================================
    // CONTAINER FORWARDING
    // ========================================================================

    @Override
    public int getContainerSize() {
        return 1; // Always 1 — all machine blocks have a single slot
    }

    @Override
    public boolean isEmpty() {
        MachineFrameBlockEntity be = getBE();
        return be == null || be.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        MachineFrameBlockEntity be = getBE();
        return be != null ? be.getItem(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        MachineFrameBlockEntity be = getBE();
        return be != null ? be.removeItem(slot, amount) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        MachineFrameBlockEntity be = getBE();
        return be != null ? be.removeItemNoUpdate(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
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
        return 1;
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
        MachineFrameBlockEntity be = getBE();
        return be != null && be.canPlaceItem(slot, stack);
    }
}
