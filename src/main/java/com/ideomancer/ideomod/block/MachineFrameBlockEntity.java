package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for Machine Frame -- stores a single item that determines
 * the block's specialization.
 *
 * Implements WorldlyContainer for hopper interaction and IPistonMovable
 * for surviving piston movement with data intact.
 *
 * PISTON MOVEMENT:
 * The specialization item is serialized to NBT before the move and
 * restored after the 2-tick animation completes. This is handled by
 * BlockMoveHelper (save phase) and MachineFrameBlock.onPlace() (restore
 * phase) via the MovingBlockEntityData registry.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, IPistonMovable {

    /** Single-slot inventory. Index 0 = the specialization item. */
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    /** Slot indices accessible from all faces (just slot 0). */
    private static final int[] SLOTS = {0};

    public MachineFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_FRAME.get(), pos, state);
    }

    // ========================================================================
    // NBT PERSISTENCE
    // ========================================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, this.items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items);
    }

    // ========================================================================
    // IPISTON MOVABLE -- piston movement data save/restore
    // ========================================================================

    @Override
    public CompoundTag saveMovementData() {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, this.items);
        return tag;
    }

    @Override
    public void loadMovementData(CompoundTag tag) {
        this.items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items);
        this.setChanged();
    }

    // ========================================================================
    // SPECIALIZATION QUERY
    // ========================================================================

    public ItemStack getSpecializationItem() {
        return this.items.get(0);
    }

    public boolean hasSpecialization() {
        return !this.items.get(0).isEmpty();
    }

    // ========================================================================
    // CONTAINER INTERFACE
    // ========================================================================

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
        if (!result.isEmpty()) {
            this.setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    // ========================================================================
    // WORLDLY CONTAINER (sided access for hoppers)
    // ========================================================================

    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable net.minecraft.core.Direction direction) {
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction direction) {
        return true;
    }

    // ========================================================================
    // MENU PROVIDER (opens GUI when right-clicked)
    // ========================================================================

    @Override
    public Component getDisplayName() {
        return Component.translatable("ideomod.container.machine_frame");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new MachineFrameMenu(containerId, playerInv, this);
    }
}
