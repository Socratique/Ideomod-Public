package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side menu (container) for the Machine Frame's single-slot GUI.
 *
 * Layout: one specialization slot centered above the player inventory,
 * similar to a single-slot hopper or beacon interface.
 *
 * The slot accepts any item with a max stack size of 1. This is enforced
 * by the block entity's getMaxStackSize() returning 1.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameMenu extends AbstractContainerMenu {

    private final Container container;

    /**
     * Server-side constructor -- called when opening the GUI from the block entity.
     */
    public MachineFrameMenu(int containerId, Inventory playerInv, Container container) {
        super(ModMenuTypes.MACHINE_FRAME.get(), containerId);
        this.container = container;

        checkContainerSize(container, 1);
        container.startOpen(playerInv.player);

        // Specialization slot: centered at x=80, y=20 in the GUI
        // (matches position in MachineFrameScreen's background texture)
        this.addSlot(new Slot(container, 0, 80, 20));

        // Player inventory (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 51 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 109));
        }
    }

    /**
     * Client-side constructor -- called when the packet arrives from the server.
     * Creates a dummy SimpleContainer since the real one lives on the server.
     */
    public MachineFrameMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, new SimpleContainer(1));
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /**
     * Shift-click transfer logic.
     * If clicking in the specialization slot -> move to player inventory.
     * If clicking in player inventory -> try to move into specialization slot.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotItem = slot.getItem();
            result = slotItem.copy();

            if (index == 0) {
                // Specialization slot -> player inventory
                if (!this.moveItemStackTo(slotItem, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Player inventory -> specialization slot
                if (!this.moveItemStackTo(slotItem, 0, 1, false)) {
                    return ItemStack.EMPTY;
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
}
