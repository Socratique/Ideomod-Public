package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Universal menu for all Machine Family blocks.
 *
 * REPEATER DATA SYNC:
 * Server constructor: live-reading ContainerData from BE.
 * Client constructor: SimpleContainerData receiving synced values.
 * Same pattern as vanilla AbstractFurnaceMenu.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameMenu extends AbstractContainerMenu {

    private final PositionBoundContainer container;
    private final BlockPos blockPos;
    private final ContainerData repeaterData;

    public static final int CONTENT_HEIGHT = 53;
    private static final int TOP_BORDER = 4;
    private static final int INV_SECTION = 90;
    private static final int BOTTOM_BORDER = 4;
    private static final int INV_SLOT_OFFSET = 12;
    private static final int HOTBAR_OFFSET = 70;

    public static final int SPEC_SLOT_INDEX = 0;
    public static final int TRANSIT_SLOT_INDEX = 1;
    public static final int PLAYER_INV_START = 2;
    public static final int PLAYER_INV_END = 38;

    private static final int DATA_REPEATER_DELAY = 0;
    private static final int DATA_IS_REPEATER = 1;
    private static final int DATA_SIZE = 2;

    /** Server-side constructor. */
    public MachineFrameMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModMenuTypes.MACHINE_FRAME.get(), containerId);
        this.blockPos = pos.immutable();
        this.container = new PositionBoundContainer(playerInv.player.level(), pos);
        this.container.startOpen(playerInv.player);

        this.repeaterData = new ContainerData() {
            @Override
            public int get(int index) {
                if (index == DATA_REPEATER_DELAY) return container.getRepeaterDelay();
                if (index == DATA_IS_REPEATER) return container.isRepeater() ? 1 : 0;
                return 0;
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return DATA_SIZE; }
        };
        this.addDataSlots(this.repeaterData);
        addAllSlots(playerInv);
    }

    /** Client-side constructor. */
    public MachineFrameMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        super(ModMenuTypes.MACHINE_FRAME.get(), containerId);
        BlockPos pos = extraData.readBlockPos();
        this.blockPos = pos.immutable();
        this.container = new PositionBoundContainer(playerInv.player.level(), pos);
        this.container.startOpen(playerInv.player);

        this.repeaterData = new SimpleContainerData(DATA_SIZE);
        this.addDataSlots(this.repeaterData);
        addAllSlots(playerInv);
    }

    private void addAllSlots(Inventory playerInv) {
        int invBaseY = TOP_BORDER + CONTENT_HEIGHT;
        this.addSlot(new SpecSlot(this.container, 0, 80, 20));
        this.addSlot(new TransitSlot(this.container, 1, 80, 38));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        8 + col * 18, invBaseY + INV_SLOT_OFFSET + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, invBaseY + HOTBAR_OFFSET));
        }
    }

    public BlockPos getBlockPos() { return this.blockPos; }
    public boolean isPipe() { return this.container.isPipe(); }
    public boolean isRepeater() { return this.repeaterData.get(DATA_IS_REPEATER) == 1; }

    public int getRepeaterDelay() {
        int delay = this.repeaterData.get(DATA_REPEATER_DELAY);
        return delay >= 1 && delay <= 4 ? delay : 1;
    }

    public int getContentHeight() { return CONTENT_HEIGHT; }

    public int getGuiHeight() {
        return TOP_BORDER + CONTENT_HEIGHT + INV_SECTION + BOTTOM_BORDER;
    }

    @Override
    public boolean stillValid(Player player) { return this.container.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotItem = slot.getItem();
            result = slotItem.copy();
            if (index < PLAYER_INV_START) {
                if (!this.moveItemStackTo(slotItem, PLAYER_INV_START, PLAYER_INV_END, true))
                    return ItemStack.EMPTY;
            } else {
                boolean moved = false;
                if (isPipe())
                    moved = this.moveItemStackTo(slotItem, TRANSIT_SLOT_INDEX, TRANSIT_SLOT_INDEX + 1, false);
                if (!moved)
                    if (!this.moveItemStackTo(slotItem, SPEC_SLOT_INDEX, SPEC_SLOT_INDEX + 1, false))
                        return ItemStack.EMPTY;
            }
            if (slotItem.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            if (slotItem.getCount() == result.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, slotItem);
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    private static class SpecSlot extends Slot {
        public SpecSlot(PositionBoundContainer container, int slot, int x, int y) { super(container, slot, x, y); }
        @Override public int getMaxStackSize() { return 1; }
    }

    private static class TransitSlot extends Slot {
        private final PositionBoundContainer container;
        public TransitSlot(PositionBoundContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.container = container;
        }
        @Override public boolean mayPlace(ItemStack stack) { return container.isPipe(); }
        @Override public boolean mayPickup(Player player) { return container.isPipe() && this.hasItem(); }
        @Override public boolean isActive() { return container.isPipe(); }
    }
}
