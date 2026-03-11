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
 * Uses PositionBoundContainer instead of holding a direct block entity
 * reference. The container looks up whatever MachineFrameBlockEntity is
 * at the stored position on every call, so it seamlessly survives block
 * swaps without any rebind logic.
 *
 * Both server and client constructors create a PositionBoundContainer
 * pointing at the block position. The server gets the level from the
 * player's level; the client reads the position from the network buffer.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameMenu extends AbstractContainerMenu {

    private final PositionBoundContainer container;
    private final BlockPos blockPos;

    /**
     * Server-side constructor.
     * Called via MenuProvider.createMenu() from the block entity.
     */
    public MachineFrameMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModMenuTypes.MACHINE_FRAME.get(), containerId);

        this.blockPos = pos.immutable();
        this.container = new PositionBoundContainer(playerInv.player.level(), pos);

        this.container.startOpen(playerInv.player);

        // Specialization slot
        this.addSlot(new Slot(this.container, 0, 80, 20));

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
     * Client-side constructor.
     * Reads the block position from the network buffer and creates a
     * PositionBoundContainer pointing at the client's level. Both sides
     * now use the same container type — no dummy SimpleContainer.
     */
    public MachineFrameMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readBlockPos());
    }

    /** The block position this menu is bound to. */
    public BlockPos getBlockPos() {
        return this.blockPos;
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

            if (index == 0) {
                if (!this.moveItemStackTo(slotItem, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
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
