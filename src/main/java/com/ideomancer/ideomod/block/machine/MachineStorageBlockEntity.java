package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the Machine Storage.
 *
 * Slot 0: specialization item (barrel) -- inherited.
 * Slots 1-9: the 3x3 store.
 *
 * Remembers, one flag per face, which faces have given it items. Only items coming
 * through the item handler count (hoppers, pipes, another Storage); the GUI does not.
 * The flags travel with it when it is moved, and a flag clears only when nothing that
 * holds items is left on that face.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineStorageBlockEntity extends MachineFrameBlockEntity {

    public static final int FIRST_SLOT = 1;
    public static final int STORAGE_SIZE = 9;

    private static final int[] STORAGE_SLOTS = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final String TAG_STORAGE = "Storage";
    private static final String TAG_FED_FACES = "FedFaces";
    private static final String TAG_ROUND_ROBIN = "RoundRobinIndex";

    private NonNullList<ItemStack> contents = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);

    /** One bit per face (Direction.get3DDataValue) that has given this block items. */
    private int fedFaces = 0;

    private int roundRobinIndex = 0;

    private LazyOptional<IItemHandler>[] itemHandlers = null;

    public MachineStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_STORAGE.get(), pos, state);
    }

    // ========================================================================
    // FEEDERS AND TURNS
    // ========================================================================

    public boolean wasFedFrom(Direction face) {
        return (this.fedFaces & (1 << face.get3DDataValue())) != 0;
    }

    private void markFedFrom(Direction face) {
        int bit = 1 << face.get3DDataValue();
        if ((this.fedFaces & bit) == 0) {
            this.fedFaces |= bit;
            this.setChanged();
        }
    }

    /** Clears the flag of any face where nothing that holds items is left. Blocks in mid-move don't count as gone. */
    public void forgetGoneFeeders(Level level, BlockPos pos) {
        if (this.fedFaces == 0) return;
        for (Direction face : Direction.values()) {
            if (!wasFedFrom(face)) continue;
            BlockPos neighbour = pos.relative(face);
            if (level.getBlockState(neighbour).is(Blocks.MOVING_PISTON)) continue;
            BlockEntity be = level.getBlockEntity(neighbour);
            if (be == null || !be.getCapability(ForgeCapabilities.ITEM_HANDLER, face.getOpposite()).isPresent()) {
                this.fedFaces &= ~(1 << face.get3DDataValue());
                this.setChanged();
            }
        }
    }

    public int getRoundRobinIndex() {
        return this.roundRobinIndex;
    }

    public void setRoundRobinIndex(int index) {
        this.roundRobinIndex = index;
    }

    // ========================================================================
    // CONTAINER
    // ========================================================================

    @Override
    public int getContainerSize() {
        return FIRST_SLOT + STORAGE_SIZE;
    }

    @Override
    public boolean isEmpty() {
        if (!super.isEmpty()) return false;
        for (ItemStack stack : this.contents) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= FIRST_SLOT ? this.contents.get(slot - FIRST_SLOT) : super.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < FIRST_SLOT) return super.removeItem(slot, amount);
        ItemStack result = ContainerHelper.removeItem(this.contents, slot - FIRST_SLOT, amount);
        if (!result.isEmpty()) this.setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < FIRST_SLOT) return super.removeItemNoUpdate(slot);
        return ContainerHelper.takeItem(this.contents, slot - FIRST_SLOT);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < FIRST_SLOT) {
            super.setItem(slot, stack);
            return;
        }
        this.contents.set(slot - FIRST_SLOT, stack);
        if (stack.getCount() > this.getMaxStackSize()) stack.setCount(this.getMaxStackSize());
        this.setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 64; // the menu holds the specialization slot to 1
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.contents.clear();
    }

    // ========================================================================
    // AUTOMATION: every face reaches the store, never the specialization slot
    // ========================================================================

    @Override
    public int[] getSlotsForFace(Direction side) {
        return STORAGE_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return slot >= FIRST_SLOT;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return slot >= FIRST_SLOT;
    }

    /** An item handler for one face, which notes that face as a feeder when items really come in. */
    private class FaceHandler extends SidedInvWrapper {
        private final Direction face;

        FaceHandler(Direction face) {
            super(MachineStorageBlockEntity.this, face);
            this.face = face;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            ItemStack rest = super.insertItem(slot, stack, simulate);
            if (!simulate && rest.getCount() < stack.getCount()) markFedFrom(this.face);
            return rest;
        }
    }

    @SuppressWarnings("unchecked")
    private LazyOptional<IItemHandler>[] getItemHandlers() {
        if (this.itemHandlers == null) {
            this.itemHandlers = new LazyOptional[Direction.values().length];
            for (Direction dir : Direction.values()) {
                this.itemHandlers[dir.ordinal()] = LazyOptional.of(() -> new FaceHandler(dir));
            }
        }
        return this.itemHandlers;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && side != null) {
            return getItemHandlers()[side.ordinal()].cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        if (this.itemHandlers != null) {
            for (LazyOptional<IItemHandler> handler : this.itemHandlers) handler.invalidate();
            this.itemHandlers = null;
        }
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        this.itemHandlers = null;
    }

    // ========================================================================
    // SAVING, CLIENT SYNC, PISTON MOVES
    // ========================================================================

    private void writeStorage(CompoundTag tag) {
        tag.put(TAG_STORAGE, ContainerHelper.saveAllItems(new CompoundTag(), this.contents, true));
    }

    private void readStorage(CompoundTag tag) {
        this.contents = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound(TAG_STORAGE), this.contents);
    }

    private void writeServerState(CompoundTag tag) {
        tag.putInt(TAG_FED_FACES, this.fedFaces);
        tag.putInt(TAG_ROUND_ROBIN, this.roundRobinIndex);
    }

    private void readServerState(CompoundTag tag) {
        this.fedFaces = tag.getInt(TAG_FED_FACES);
        this.roundRobinIndex = tag.getInt(TAG_ROUND_ROBIN);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeStorage(tag);
        writeServerState(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readStorage(tag);
        readServerState(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeStorage(tag); // load() replaces the contents, so a sync must carry them
        return tag;
    }

    @Override
    public CompoundTag saveMovementData() {
        CompoundTag tag = super.saveMovementData();
        writeStorage(tag);
        writeServerState(tag);
        return tag;
    }

    @Override
    public void loadMovementData(CompoundTag tag) {
        super.loadMovementData(tag);
        readStorage(tag);
        readServerState(tag);
        this.setChanged();
    }
}
