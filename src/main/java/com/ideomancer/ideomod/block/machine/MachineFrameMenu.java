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
 * FIXED CONTENT HEIGHT:
 * =====================
 * All Machine Family GUIs use the same fixed content height (89px), sized
 * to fit the tallest specialization (Timer: label + 2 rows of buttons).
 * Shorter specializations (Frame, Pipe, Repeater, Transformer, etc.) have
 * extra grey background space between their controls and the player inventory.
 *
 * This avoids needing to reposition slots at runtime (Slot.y is final in
 * Forge 1.20.1) and keeps the GUI architecture simple — no dynamic height,
 * no reflection, no AT. The PositionBoundContainer seamlessly handles block
 * swaps while the GUI stays open.
 *
 * DATA SYNC:
 * Server constructor: live-reading ContainerData from BE.
 * Client constructor: SimpleContainerData receiving synced values.
 * Same pattern as vanilla AbstractFurnaceMenu.
 *
 * ContainerData layout:
 *   0 = repeater delay (1-4)
 *   1 = is repeater (0 or 1)
 *   2 = transformer level (1-15)
 *   3 = is transformer (0 or 1)
 *   4 = timer duration (ticks)
 *   5 = is timer (0 or 1)
 *   6 = engine litTime (current burn ticks remaining)
 *   7 = engine litDuration (total burn time for flame progress)
 *   8 = engine pulseInterval (selected interval)
 *   9 = is engine (0 or 1)
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameMenu extends AbstractContainerMenu {

    private final PositionBoundContainer container;
    private final BlockPos blockPos;
    private final ContainerData machineData;

    /**
     * Fixed content height for ALL Machine Family GUIs. Sized to fit the
     * tallest specialization (Timer: label + 2 rows of 8 buttons = 89px).
     * Shorter specializations get extra background space — visually clean.
     */
    public static final int CONTENT_HEIGHT = 89;

    private static final int TOP_BORDER = 4;
    private static final int INV_SECTION = 90;
    private static final int BOTTOM_BORDER = 4;
    private static final int INV_SLOT_OFFSET = 12;
    private static final int HOTBAR_OFFSET = 70;

    /** Total GUI height: 4 + 89 + 90 + 4 = 187px. */
    public static final int GUI_HEIGHT = TOP_BORDER + CONTENT_HEIGHT + INV_SECTION + BOTTOM_BORDER;

    public static final int SPEC_SLOT_INDEX = 0;
    public static final int TRANSIT_SLOT_INDEX = 1;
    public static final int PLAYER_INV_START = 2;
    public static final int PLAYER_INV_END = 38;

    private static final int DATA_REPEATER_DELAY = 0;
    private static final int DATA_IS_REPEATER = 1;
    private static final int DATA_TRANSFORMER_LEVEL = 2;
    private static final int DATA_IS_TRANSFORMER = 3;
    private static final int DATA_TIMER_DURATION = 4;
    private static final int DATA_IS_TIMER = 5;
    private static final int DATA_ENGINE_LIT_TIME = 6;
    private static final int DATA_ENGINE_LIT_DURATION = 7;
    private static final int DATA_ENGINE_INTERVAL = 8;
    private static final int DATA_IS_ENGINE = 9;
    private static final int DATA_SIZE = 10;

    /** Server-side constructor. */
    public MachineFrameMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModMenuTypes.MACHINE_FRAME.get(), containerId);
        this.blockPos = pos.immutable();
        this.container = new PositionBoundContainer(playerInv.player.level(), pos);
        this.container.startOpen(playerInv.player);

        this.machineData = new ContainerData() {
            @Override
            public int get(int index) {
                if (index == DATA_REPEATER_DELAY) return container.getRepeaterDelay();
                if (index == DATA_IS_REPEATER) return container.isRepeater() ? 1 : 0;
                if (index == DATA_TRANSFORMER_LEVEL) return container.getTransformerLevel();
                if (index == DATA_IS_TRANSFORMER) return container.isTransformer() ? 1 : 0;
                if (index == DATA_TIMER_DURATION) return container.getTimerDuration();
                if (index == DATA_IS_TIMER) return container.isTimer() ? 1 : 0;
                if (index == DATA_ENGINE_LIT_TIME) return container.getEngineLitTime();
                if (index == DATA_ENGINE_LIT_DURATION) return container.getEngineLitDuration();
                if (index == DATA_ENGINE_INTERVAL) return container.getEnginePulseInterval();
                if (index == DATA_IS_ENGINE) return container.isEngine() ? 1 : 0;
                return 0;
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return DATA_SIZE; }
        };
        this.addDataSlots(this.machineData);
        addAllSlots(playerInv);
    }

    /** Client-side constructor. */
    public MachineFrameMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        super(ModMenuTypes.MACHINE_FRAME.get(), containerId);
        BlockPos pos = extraData.readBlockPos();
        this.blockPos = pos.immutable();
        this.container = new PositionBoundContainer(playerInv.player.level(), pos);
        this.container.startOpen(playerInv.player);

        this.machineData = new SimpleContainerData(DATA_SIZE);
        this.addDataSlots(this.machineData);
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
    public boolean isRepeater() { return this.machineData.get(DATA_IS_REPEATER) == 1; }
    public boolean isTransformer() { return this.machineData.get(DATA_IS_TRANSFORMER) == 1; }
    public boolean isTimer() { return this.machineData.get(DATA_IS_TIMER) == 1; }
    public boolean isEngine() { return this.machineData.get(DATA_IS_ENGINE) == 1; }

    public int getEngineLitTime() {
        return this.machineData.get(DATA_ENGINE_LIT_TIME);
    }

    public int getEngineLitDuration() {
        return this.machineData.get(DATA_ENGINE_LIT_DURATION);
    }

    public int getEnginePulseInterval() {
        int interval = this.machineData.get(DATA_ENGINE_INTERVAL);
        return interval >= 1 ? interval : 10;
    }

    public int getRepeaterDelay() {
        int delay = this.machineData.get(DATA_REPEATER_DELAY);
        return delay >= 1 && delay <= 4 ? delay : 1;
    }

    public int getTransformerLevel() {
        int level = this.machineData.get(DATA_TRANSFORMER_LEVEL);
        return level >= 1 && level <= 15 ? level : 1;
    }

    public int getTimerDuration() {
        int duration = this.machineData.get(DATA_TIMER_DURATION);
        return duration >= 1 ? duration : 20;
    }

    public int getContentHeight() { return CONTENT_HEIGHT; }

    public int getGuiHeight() { return GUI_HEIGHT; }

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
                if (!moved && isEngine() && net.minecraftforge.common.ForgeHooks.getBurnTime(slotItem, null) > 0)
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
        @Override public boolean mayPlace(ItemStack stack) {
            if (container.isPipe()) return true;
            if (container.isEngine()) return net.minecraftforge.common.ForgeHooks.getBurnTime(stack, null) > 0;
            return false;
        }
        @Override public boolean mayPickup(Player player) {
            return (container.isPipe() || container.isEngine()) && this.hasItem();
        }
        @Override public boolean isActive() { return container.isPipe() || container.isEngine(); }
    }
}
