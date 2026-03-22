package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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
 * Block entity for Machine Pipe specialization.
 *
 * Extends MachineFrameBlockEntity to add:
 * - A transit slot (slot 1) that holds items being transported
 * - Transfer cooldown (8-tick hopper speed)
 * - Round-robin output tracking with input-face-relative priority
 * - Redstone locking (powered = frozen, like hoppers)
 *
 * Slot 0: Specialization item (hopper) -- inherited from parent
 * Slot 1: Transit item -- the item being transported through the pipe
 *
 * IMPORTANT: This BE uses ModBlockEntities.MACHINE_PIPE, NOT MACHINE_FRAME.
 * The pipe block is registered with its own BE type so the transit slot
 * and ticker are only present on pipes.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachinePipeBlockEntity extends MachineFrameBlockEntity {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    /** Transfer cooldown in ticks. Reads from config (default matches vanilla hopper speed). */
    public static int getConfigTransferCooldown() {
        return com.ideomancer.ideomod.config.IdeomodConfig.getItemPipeTransferCooldown();
    }

    /** Slot index for the transit item. */
    public static final int TRANSIT_SLOT = 1;

    // NBT tags
    private static final String TAG_TRANSIT_ITEM = "TransitItem";
    private static final String TAG_COOLDOWN = "TransferCooldown";
    private static final String TAG_ROUND_ROBIN = "RoundRobinIndex";
    private static final String TAG_LAST_INPUT_FACE = "LastInputFace";

    // ========================================================================
    // STATE
    // ========================================================================

    /** The item currently in transit through this pipe. */
    private ItemStack transitItem = ItemStack.EMPTY;

    /** Ticks remaining before next transfer attempt. */
    private int transferCooldown = 0;

    /** Current index into the output priority list for round-robin. */
    private int roundRobinIndex = 0;

    /**
     * The face from which the last item was received. Used to determine
     * output priority order (forward = opposite of input, then right,
     * left, up, down relative to input direction).
     * Null = no input yet (defaults to DOWN as input, i.e. player hand = top).
     */
    @Nullable
    private Direction lastInputFace = null;

    /** Capability cache for item handler exposure. */
    private LazyOptional<IItemHandler>[] itemHandlers = null;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public MachinePipeBlockEntity(BlockPos pos, BlockState state) {
        // Passes MACHINE_PIPE type to the protected parent constructor.
        // See integration guide: MachineFrameBlockEntity needs a protected
        // constructor that accepts BlockEntityType for subclass support.
        super(ModBlockEntities.MACHINE_PIPE.get(), pos, state);
    }

    // ========================================================================
    // TRANSIT SLOT ACCESS
    // ========================================================================

    /** Get the item currently in the transit slot. */
    public ItemStack getTransitItem() {
        return this.transitItem;
    }

    /** Set the transit item directly (no swap check). */
    public void setTransitItem(ItemStack stack) {
        this.transitItem = stack;
        this.setChanged();
        updateFilledState();
    }

    /** Check if the transit slot has any items. */
    public boolean hasTransitItem() {
        return !this.transitItem.isEmpty();
    }

    // ========================================================================
    // TRANSFER STATE
    // ========================================================================

    public int getTransferCooldown() {
        return this.transferCooldown;
    }

    public void setTransferCooldown(int cooldown) {
        this.transferCooldown = cooldown;
    }

    public int getRoundRobinIndex() {
        return this.roundRobinIndex;
    }

    public void setRoundRobinIndex(int index) {
        this.roundRobinIndex = index;
    }

    @Nullable
    public Direction getLastInputFace() {
        return this.lastInputFace;
    }

    public void setLastInputFace(@Nullable Direction face) {
        if (this.lastInputFace != face) {
            this.lastInputFace = face;
            this.roundRobinIndex = 0; // Reset round-robin on input face change
            this.setChanged();
        }
    }

    /**
     * Called when an item enters the transit slot from a specific face.
     * Sets the input face and resets round-robin if the face changed.
     * Player hand insertion counts as Direction.UP (top of block).
     */
    public void onItemReceived(@Nullable Direction fromFace) {
        Direction effectiveFace = fromFace != null ? fromFace : Direction.UP;
        setLastInputFace(effectiveFace);
        // Start cooldown for the push
        if (this.transferCooldown <= 0) {
            this.transferCooldown = getConfigTransferCooldown();
        }
    }

    // ========================================================================
    // FILLED STATE SYNC
    // ========================================================================

    /**
     * Updates the FILLED blockstate property to match transit slot contents.
     * This drives the texture swap between empty and filled pipe.
     * Guarded against running during block removal or swap to prevent
     * re-placing the block.
     */
    private void updateFilledState() {
        if (this.level != null && !this.level.isClientSide() && !MachineSwapHelper.isSwapping()) {
            BlockState state = this.getBlockState();
            if (state.getBlock() instanceof MachinePipeBlock) {
                boolean shouldBeFilled = !this.transitItem.isEmpty();
                boolean currentlyFilled = state.getValue(MachinePipeBlock.FILLED);
                if (shouldBeFilled != currentlyFilled) {
                    this.level.setBlock(this.worldPosition,
                            state.setValue(MachinePipeBlock.FILLED, shouldBeFilled), 3);
                }
            }
        }
    }

    // ========================================================================
    // CONTAINER INTERFACE (expanded for 2 slots)
    // ========================================================================

    @Override
    public int getContainerSize() {
        return 2; // slot 0 = specialization, slot 1 = transit
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && this.transitItem.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == TRANSIT_SLOT) return this.transitItem;
        return super.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == TRANSIT_SLOT) {
            ItemStack result = this.transitItem.split(amount);
            if (!result.isEmpty()) {
                this.setChanged();
                updateFilledState();
            }
            return result;
        }
        return super.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot == TRANSIT_SLOT) {
            ItemStack result = this.transitItem;
            this.transitItem = ItemStack.EMPTY;
            return result;
        }
        return super.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == TRANSIT_SLOT) {
            boolean wasEmpty = this.transitItem.isEmpty();
            this.transitItem = stack;
            if (stack.getCount() > this.getMaxStackSize(slot)) {
                stack.setCount(this.getMaxStackSize(slot));
            }
            this.setChanged();
            updateFilledState();

            // If an item was inserted (not removed), record the input face.
            // pendingInputFace is set by canPlaceItemThroughFace() for hopper/automation.
            // For GUI insertion, PositionBoundContainer.setItem() calls onItemReceived() directly.
            if (wasEmpty && !stack.isEmpty()) {
                Direction face = pendingInputFace.get();
                if (face != null) {
                    pendingInputFace.remove();
                    onItemReceived(face);
                }
            }
            return;
        }
        super.setItem(slot, stack);
    }

    /**
     * Max stack size per slot.
     * Slot 0 (specialization): 1 item.
     * Slot 1 (transit): 64 (full stack).
     */
    public int getMaxStackSize(int slot) {
        if (slot == TRANSIT_SLOT) return 64;
        return 1;
    }

    @Override
    public int getMaxStackSize() {
        // Container-wide max. The menu enforces per-slot limits.
        return 64;
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.transitItem = ItemStack.EMPTY;
    }

    // ========================================================================
    // WORLDLY CONTAINER (hopper/pipe interaction faces)
    // ========================================================================

    private static final int[] TRANSIT_SLOTS = {1};
    private static final int[] SPEC_SLOTS = {0};

    /**
     * External automation (hoppers, other pipes) sees only the transit slot.
     * The specialization slot (0) is player-GUI-only.
     */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return TRANSIT_SLOTS;
    }

    /**
     * Tracks the face from which an external source (hopper, pipe) is inserting
     * an item. Set in canPlaceItemThroughFace(), read and cleared in setItem().
     * This bridges the gap between the WorldlyContainer check (which knows the
     * direction) and the Container setter (which doesn't).
     */
    private static final ThreadLocal<Direction> pendingInputFace = new ThreadLocal<>();

    /**
     * External sources can place items into the transit slot from any face.
     * The specialization slot is NOT accessible externally.
     * Records the insertion face so setItem() can call onItemReceived().
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        if (slot == TRANSIT_SLOT) {
            pendingInputFace.set(direction);
            return true;
        }
        return false;
    }

    /**
     * External extraction from the transit slot is allowed from any face.
     * This lets hoppers pull from pipes if desired.
     */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return slot == TRANSIT_SLOT;
    }

    // ========================================================================
    // NBT PERSISTENCE
    // ========================================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Save transit item separately (parent saves slot 0 via ContainerHelper)
        if (!this.transitItem.isEmpty()) {
            tag.put(TAG_TRANSIT_ITEM, this.transitItem.save(new CompoundTag()));
        }
        tag.putInt(TAG_COOLDOWN, this.transferCooldown);
        tag.putInt(TAG_ROUND_ROBIN, this.roundRobinIndex);
        if (this.lastInputFace != null) {
            tag.putInt(TAG_LAST_INPUT_FACE, this.lastInputFace.get3DDataValue());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // Load transit item
        if (tag.contains(TAG_TRANSIT_ITEM)) {
            this.transitItem = ItemStack.of(tag.getCompound(TAG_TRANSIT_ITEM));
        } else {
            this.transitItem = ItemStack.EMPTY;
        }
        this.transferCooldown = tag.getInt(TAG_COOLDOWN);
        this.roundRobinIndex = tag.getInt(TAG_ROUND_ROBIN);
        if (tag.contains(TAG_LAST_INPUT_FACE)) {
            this.lastInputFace = Direction.from3DDataValue(tag.getInt(TAG_LAST_INPUT_FACE));
        } else {
            this.lastInputFace = null;
        }
    }

    // ========================================================================
    // CLIENT SYNC
    // ========================================================================

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (!this.transitItem.isEmpty()) {
            tag.put(TAG_TRANSIT_ITEM, this.transitItem.save(new CompoundTag()));
        }
        // Cooldown and round-robin are server-only, no need to sync
        return tag;
    }

    // ========================================================================
    // PISTON MOVABLE
    // ========================================================================

    @Override
    public CompoundTag saveMovementData() {
        CompoundTag tag = super.saveMovementData();
        if (!this.transitItem.isEmpty()) {
            tag.put(TAG_TRANSIT_ITEM, this.transitItem.save(new CompoundTag()));
        }
        tag.putInt(TAG_COOLDOWN, this.transferCooldown);
        tag.putInt(TAG_ROUND_ROBIN, this.roundRobinIndex);
        if (this.lastInputFace != null) {
            tag.putInt(TAG_LAST_INPUT_FACE, this.lastInputFace.get3DDataValue());
        }
        return tag;
    }

    @Override
    public void loadMovementData(CompoundTag tag) {
        super.loadMovementData(tag);
        if (tag.contains(TAG_TRANSIT_ITEM)) {
            this.transitItem = ItemStack.of(tag.getCompound(TAG_TRANSIT_ITEM));
        } else {
            this.transitItem = ItemStack.EMPTY;
        }
        this.transferCooldown = tag.getInt(TAG_COOLDOWN);
        // Ensure minimum cooldown after piston movement to prevent
        // items from being pushed on the very first tick at the new position.
        // This gives the world state time to settle after the move.
        if (this.transferCooldown < getConfigTransferCooldown()) {
            this.transferCooldown = getConfigTransferCooldown();
        }
        this.roundRobinIndex = tag.getInt(TAG_ROUND_ROBIN);
        if (tag.contains(TAG_LAST_INPUT_FACE)) {
            this.lastInputFace = Direction.from3DDataValue(tag.getInt(TAG_LAST_INPUT_FACE));
        } else {
            this.lastInputFace = null;
        }
    }

    // ========================================================================
    // MENU PROVIDER
    // ========================================================================

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        // Uses the unified MachineFrameMenu. The menu's PositionBoundContainer
        // detects that this is a pipe BE and enables the transit slot dynamically.
        return new MachineFrameMenu(containerId, playerInv, this.worldPosition);
    }

    // ========================================================================
    // CAPABILITIES (IItemHandler for Forge item transfer)
    // ========================================================================

    @SuppressWarnings("unchecked")
    private LazyOptional<IItemHandler>[] getItemHandlers() {
        if (this.itemHandlers == null) {
            this.itemHandlers = new LazyOptional[Direction.values().length];
            for (int i = 0; i < Direction.values().length; i++) {
                Direction dir = Direction.values()[i];
                this.itemHandlers[i] = LazyOptional.of(
                        () -> new SidedInvWrapper(this, dir));
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
            for (LazyOptional<IItemHandler> handler : this.itemHandlers) {
                handler.invalidate();
            }
            this.itemHandlers = null;
        }
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        this.itemHandlers = null; // Will be recreated on next access
    }
}
