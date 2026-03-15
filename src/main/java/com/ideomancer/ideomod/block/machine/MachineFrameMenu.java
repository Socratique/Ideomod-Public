package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Universal menu for all Machine Family blocks.
 *
 * Uses PositionBoundContainer backed by Level+BlockPos. Survives block
 * swaps seamlessly -- the container resolves to whatever block entity
 * exists at the position on every call.
 *
 * DYNAMIC TRANSIT SLOT:
 * =====================
 * Always creates 2 block slots + standard player inventory:
 * - Slot 0: specialization item (max stack 1)
 * - Slot 1: transit item (only interactable when BE is a pipe)
 *
 * The transit slot uses TransitSlot which checks isPipe() on every
 * interaction. When inactive (non-pipe), it's invisible and rejects items.
 *
 * LAYOUT SYSTEM:
 * ==============
 * GUI height is dynamic:
 *   height = 4 (top border) + contentHeight + 90 (player inv) + 4 (bottom border)
 *   1 slot:  contentHeight=35 -> 133px
 *   2 slots: contentHeight=53 -> 151px
 *
 * Slot Y coordinates:
 *   Spec slot:  y=20 (fixed)
 *   Transit:    y=38 (fixed position, only visible when pipe)
 *   Player inv: y = 4 + contentHeight + 12 (dynamic)
 *   Hotbar:     y = 4 + contentHeight + 70 (dynamic)
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameMenu extends AbstractContainerMenu {

    private final PositionBoundContainer container;
    private final BlockPos blockPos;

    // Content area heights
    // Content area height -- fixed for all machine types.
    // The 2-slot height is always used so the GUI never resizes during
    // specialization swaps. Non-pipe machines have empty space where the
    // transit slot would be (the slot tile just isn't rendered).
    public static final int CONTENT_HEIGHT = 53;
    private static final int TOP_BORDER = 4;
    private static final int INV_SECTION = 90;
    private static final int BOTTOM_BORDER = 4;

    // Offsets within the player inventory section
    private static final int INV_SLOT_OFFSET = 12;
    private static final int HOTBAR_OFFSET = 70;

    // Slot indices
    public static final int SPEC_SLOT_INDEX = 0;
    public static final int TRANSIT_SLOT_INDEX = 1;
    public static final int PLAYER_INV_START = 2;
    public static final int PLAYER_INV_END = 38; // exclusive (2 + 36)

    /**
     * Server-side constructor.
     */
    public MachineFrameMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModMenuTypes.MACHINE_FRAME.get(), containerId);

        this.blockPos = pos.immutable();
        this.container = new PositionBoundContainer(playerInv.player.level(), pos);

        this.container.startOpen(playerInv.player);

        // Fixed layout height for all machine types
        int contentH = CONTENT_HEIGHT;
        int invBaseY = TOP_BORDER + contentH;

        // Slot 0: Specialization item (max stack 1)
        this.addSlot(new SpecSlot(this.container, 0, 80, 20));

        // Slot 1: Transit item (only active when pipe)
        this.addSlot(new TransitSlot(this.container, 1, 80, 38));

        // Player inventory (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        8 + col * 18, invBaseY + INV_SLOT_OFFSET + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, invBaseY + HOTBAR_OFFSET));
        }
    }

    /**
     * Client-side constructor.
     */
    public MachineFrameMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readBlockPos());
    }

    /** The block position this menu is bound to. */
    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    /** Whether the current BE is a pipe (has transit slot). Checked live. */
    public boolean isPipe() {
        return this.container.isPipe();
    }

    /** Content area height (fixed for all machine types). */
    public int getContentHeight() {
        return CONTENT_HEIGHT;
    }

    /** Total GUI height for current layout. */
    public int getGuiHeight() {
        return TOP_BORDER + getContentHeight() + INV_SECTION + BOTTOM_BORDER;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotItem = slot.getItem();
            result = slotItem.copy();

            if (index < PLAYER_INV_START) {
                // Moving from block slot to player inventory
                if (!this.moveItemStackTo(slotItem, PLAYER_INV_START, PLAYER_INV_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving from player inventory to block slots
                // Try transit slot first (if pipe), then spec slot
                boolean moved = false;
                if (isPipe()) {
                    moved = this.moveItemStackTo(slotItem, TRANSIT_SLOT_INDEX, TRANSIT_SLOT_INDEX + 1, false);
                }
                if (!moved) {
                    if (!this.moveItemStackTo(slotItem, SPEC_SLOT_INDEX, SPEC_SLOT_INDEX + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (slotItem.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (slotItem.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, slotItem);
        }

        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    // ========================================================================
    // CUSTOM SLOT TYPES
    // ========================================================================

    /** Specialization slot -- max stack size 1. */
    private static class SpecSlot extends Slot {
        public SpecSlot(PositionBoundContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /**
     * Transit slot -- only active when the BE is a MachinePipeBlockEntity.
     * When inactive, returns empty and rejects all items. isActive() returns
     * false so vanilla skips rendering the slot highlight and tooltip.
     */
    private static class TransitSlot extends Slot {
        private final PositionBoundContainer container;

        public TransitSlot(PositionBoundContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.container = container;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.isPipe();
        }

        @Override
        public boolean mayPickup(Player player) {
            return container.isPipe() && this.hasItem();
        }

        @Override
        public boolean isActive() {
            return container.isPipe();
        }
    }
}
