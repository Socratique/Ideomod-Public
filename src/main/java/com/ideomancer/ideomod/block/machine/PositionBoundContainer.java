package com.ideomancer.ideomod.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
 * SPECIALIZATION QUERIES:
 * =======================
 * isRepeater() / getRepeaterDelay() - for Repeater ContainerData sync.
 * isTransformer() / getTransformerLevel() - for Transformer ContainerData sync.
 * isTimer() / getTimerDuration() - for Timer ContainerData sync.
 * getBlockContentHeight() - reads GUI height from the current block class,
 *   enabling fully dynamic GUI resizing without hardcoded type checks.
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

    private MachineFrameBlockEntity getBE() {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MachineFrameBlockEntity mfbe ? mfbe : null;
    }

    private MachinePipeBlockEntity getPipeBE() {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MachinePipeBlockEntity pipe ? pipe : null;
    }

    public boolean isPipe() {
        return getPipeBE() != null;
    }

    public boolean isRepeater() {
        return level.getBlockState(pos).getBlock() instanceof MachineRepeaterBlock;
    }

    public int getRepeaterDelay() {
        MachineFrameBlockEntity be = getBE();
        return be != null ? be.getRepeaterDelay() : 1;
    }

    public boolean isTransformer() {
        return level.getBlockState(pos).getBlock() instanceof MachineTransformerBlock;
    }

    public int getTransformerLevel() {
        MachineFrameBlockEntity be = getBE();
        return be != null ? be.getTransformerLevel() : 1;
    }

    public boolean isTimer() {
        return level.getBlockState(pos).getBlock() instanceof MachineTimerBlock;
    }

    public int getTimerDuration() {
        MachineFrameBlockEntity be = getBE();
        return be != null ? be.getTimerDuration() : 20;
    }

    /**
     * Get the GUI content height declared by the current block at this position.
     * Reads from AbstractMachineBlock.getGuiContentHeight() on the live block.
     * Returns the default height if the block is not an AbstractMachineBlock.
     *
     * This is the single entry point for dynamic GUI height. Each specialization
     * declares its own height by overriding getGuiContentHeight() on its block
     * class. No hardcoded type checks needed here or in the menu.
     */
    public int getBlockContentHeight() {
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof AbstractMachineBlock machineBlock) {
            return machineBlock.getGuiContentHeight();
        }
        return AbstractMachineBlock.DEFAULT_GUI_CONTENT_HEIGHT;
    }

    // ========================================================================
    // CONTAINER FORWARDING
    // ========================================================================

    @Override public int getContainerSize() { return 2; }

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

    @Override public int getMaxStackSize() { return 64; }

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
            return getPipeBE() != null;
        }
        MachineFrameBlockEntity be = getBE();
        return be != null && be.canPlaceItem(slot, stack);
    }
}
