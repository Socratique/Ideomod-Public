package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for Machine Fluid Pipe specialization.
 *
 * Extends MachineFrameBlockEntity to add:
 * - Fluid type tracking (empty/water/lava) stored as NBT + blockstate
 * - Transfer cooldown (8-tick, same as item pipe)
 * - Round-robin output tracking with input-face-relative priority
 * - IFluidHandler capability for future mod interop
 *
 * Unlike the item pipe, the fluid pipe does NOT have a transit slot visible
 * in the GUI. The fluid state is tracked as BE data, and the block's 4x4
 * fluid window provides the visual feedback.
 *
 * Slot 0: Specialization item (bucket) -- inherited from parent
 * No slot 1 (no transit slot -- fluid state is not an item in a slot)
 *
 * IMPORTANT: This BE uses ModBlockEntities.MACHINE_FLUID_PIPE, NOT MACHINE_FRAME.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFluidPipeBlockEntity extends MachineFrameBlockEntity {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    /** Transfer cooldown in ticks. Reads from config (default matches item pipe speed). */
    public static int getConfigTransferCooldown() {
        return com.ideomancer.ideomod.config.IdeomodConfig.getFluidPipeTransferCooldown();
    }

    // NBT tags
    private static final String TAG_FLUID_TYPE = "FluidType";
    private static final String TAG_COOLDOWN = "TransferCooldown";
    private static final String TAG_ROUND_ROBIN = "RoundRobinIndex";
    private static final String TAG_LAST_INPUT_FACE = "LastInputFace";

    // ========================================================================
    // STATE
    // ========================================================================

    /** The fluid currently held by this pipe's internal bucket. */
    private FluidType fluidType = FluidType.EMPTY;

    /** Ticks remaining before next transfer attempt. */
    private int transferCooldown = 0;

    /** Current index into the output priority list for round-robin. */
    private int roundRobinIndex = 0;

    /**
     * The face from which the last fluid was received. Used to determine
     * output priority order (forward = opposite of input, then right,
     * left, up, down relative to input direction).
     * Null = no input yet (defaults to UP).
     */
    @Nullable
    private Direction lastInputFace = null;

    /** Capability cache for fluid handler exposure. */
    private LazyOptional<IFluidHandler> fluidHandlerOpt = null;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public MachineFluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_FLUID_PIPE.get(), pos, state);
    }

    // ========================================================================
    // FLUID STATE ACCESS
    // ========================================================================

    /** Get the current fluid type held by this pipe. */
    public FluidType getFluidType() {
        return this.fluidType;
    }

    /**
     * Set the fluid type and update both the blockstate and the bucket item
     * in slot 0 to match. This is the central method for changing fluid state
     * -- always use this, never set fluidType directly.
     *
     * The bucket in slot 0 visually reflects the pipe's fluid state:
     * EMPTY -> Items.BUCKET, WATER -> Items.WATER_BUCKET, LAVA -> Items.LAVA_BUCKET.
     * Uses setItemDirect() to avoid triggering a swap check (changing the item
     * to a water/lava bucket would make it an unrecognised specialization item
     * and revert to Machine Frame -- we don't want that).
     */
    public void setFluidType(FluidType type) {
        if (this.fluidType != type) {
            this.fluidType = type;
            // Update the bucket item in slot 0 to match the fluid state
            ItemStack bucketItem = switch (type) {
                case WATER -> new ItemStack(Items.WATER_BUCKET);
                case LAVA -> new ItemStack(Items.LAVA_BUCKET);
                case EMPTY -> new ItemStack(Items.BUCKET);
            };
            this.setItemDirect(bucketItem);
            this.setChanged();
            updateFluidState();
        }
    }

    /**
     * The fluid is also read off the bucket in slot 0, so a pipe placed from a dropped full pipe
     * (which carries only its slot item) or made by putting a water/lava bucket into a Machine
     * Frame holds that fluid. setFluidType() keeps the two in step the other way.
     */
    @Override
    public void setItemDirect(ItemStack stack) {
        super.setItemDirect(stack);
        syncFluidFromItem();
    }

    public void syncFluidFromItem() {
        ItemStack item = this.getSpecializationItem();
        FluidType fromItem = item.is(Items.WATER_BUCKET) ? FluidType.WATER
                : item.is(Items.LAVA_BUCKET) ? FluidType.LAVA : FluidType.EMPTY;
        if (fromItem != this.fluidType) {
            this.fluidType = fromItem;
            this.setChanged();
        }
        updateFluidState();
    }

    /** Check if this pipe currently holds a fluid (water or lava). */
    public boolean hasFluid() {
        return this.fluidType != FluidType.EMPTY;
    }

    /** Check if this pipe has an empty bucket (can accept fluid). */
    public boolean isEmpty() {
        return this.fluidType == FluidType.EMPTY;
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
     * Called when fluid enters the pipe from a specific face.
     * Sets the input face, resets round-robin, and starts cooldown.
     */
    public void onFluidReceived(@Nullable Direction fromFace) {
        Direction effectiveFace = fromFace != null ? fromFace : Direction.UP;
        setLastInputFace(effectiveFace);
        if (this.transferCooldown <= 0) {
            this.transferCooldown = getConfigTransferCooldown();
        }
    }

    // ========================================================================
    // WATER + LAVA COLLISION
    // ========================================================================

    /**
     * Handles the water+lava collision when a fluid pipe pushes into this pipe.
     * Replaces the bucket in slot 0 with obsidian, which triggers
     * MachineSwapHelper to revert this block to a Machine Frame.
     *
     * @return true if a collision occurred (and the block will revert)
     */
    public boolean handleFluidCollision(FluidType incomingFluid) {
        if ((this.fluidType == FluidType.WATER && incomingFluid == FluidType.LAVA) ||
            (this.fluidType == FluidType.LAVA && incomingFluid == FluidType.WATER)) {
            // Replace the bucket with obsidian in slot 0
            // This makes it an invalid specialization item, triggering auto-revert
            this.setItem(0, new ItemStack(Items.OBSIDIAN));
            return true;
        }
        return false;
    }

    // ========================================================================
    // BLOCKSTATE SYNC
    // ========================================================================

    /**
     * Updates the FLUID_TYPE blockstate property to match internal fluid state.
     * Guarded against running during block removal or swap.
     */
    private void updateFluidState() {
        if (this.level != null && !this.level.isClientSide() && !MachineSwapHelper.isSwapping()) {
            BlockState state = this.getBlockState();
            if (state.getBlock() instanceof MachineFluidPipeBlock) {
                FluidType currentBSFluid = state.getValue(MachineFluidPipeBlock.FLUID_TYPE);
                if (currentBSFluid != this.fluidType) {
                    this.level.setBlock(this.worldPosition,
                            state.setValue(MachineFluidPipeBlock.FLUID_TYPE, this.fluidType), 3);
                }
            }
        }
    }

    // ========================================================================
    // NBT PERSISTENCE
    // ========================================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(TAG_FLUID_TYPE, this.fluidType.getSerializedName());
        tag.putInt(TAG_COOLDOWN, this.transferCooldown);
        tag.putInt(TAG_ROUND_ROBIN, this.roundRobinIndex);
        if (this.lastInputFace != null) {
            tag.putInt(TAG_LAST_INPUT_FACE, this.lastInputFace.get3DDataValue());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_FLUID_TYPE)) {
            String typeName = tag.getString(TAG_FLUID_TYPE);
            try {
                this.fluidType = FluidType.valueOf(typeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.fluidType = FluidType.EMPTY;
            }
        } else {
            // a dropped full pipe carries only its slot item: read the fluid off the bucket
            ItemStack item = this.getSpecializationItem();
            this.fluidType = item.is(Items.WATER_BUCKET) ? FluidType.WATER
                    : item.is(Items.LAVA_BUCKET) ? FluidType.LAVA : FluidType.EMPTY;
        }
        updateFluidState();
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
        tag.putString(TAG_FLUID_TYPE, this.fluidType.getSerializedName());
        // Cooldown and round-robin are server-only, no need to sync
        return tag;
    }

    // ========================================================================
    // PISTON MOVABLE
    // ========================================================================

    @Override
    public CompoundTag saveMovementData() {
        CompoundTag tag = super.saveMovementData();
        tag.putString(TAG_FLUID_TYPE, this.fluidType.getSerializedName());
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
        if (tag.contains(TAG_FLUID_TYPE)) {
            String typeName = tag.getString(TAG_FLUID_TYPE);
            try {
                this.fluidType = FluidType.valueOf(typeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.fluidType = FluidType.EMPTY;
            }
        } else {
            this.fluidType = FluidType.EMPTY;
        }
        this.transferCooldown = tag.getInt(TAG_COOLDOWN);
        // Ensure minimum cooldown after piston movement
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
        return new MachineFrameMenu(containerId, playerInv, this.worldPosition);
    }

    // ========================================================================
    // CAPABILITIES (IFluidHandler for Forge fluid interop)
    // ========================================================================

    private LazyOptional<IFluidHandler> getFluidHandlerOpt() {
        if (this.fluidHandlerOpt == null) {
            this.fluidHandlerOpt = LazyOptional.of(() -> new FluidPipeFluidHandler(this));
        }
        return this.fluidHandlerOpt;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return getFluidHandlerOpt().cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        if (this.fluidHandlerOpt != null) {
            this.fluidHandlerOpt.invalidate();
            this.fluidHandlerOpt = null;
        }
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        this.fluidHandlerOpt = null;
    }

    // ========================================================================
    // INNER CLASS: IFluidHandler adapter
    // ========================================================================

    /**
     * Exposes the pipe's fluid state as a Forge IFluidHandler.
     * Reports capacity as 1000mB (1 bucket). Allows fill/drain operations
     * that map to the pipe's internal FluidType state.
     *
     * This is for future mod interop -- other mods that query for
     * IFluidHandler will "see" the pipe's contents.
     */
    private static class FluidPipeFluidHandler implements IFluidHandler {

        private final MachineFluidPipeBlockEntity pipe;

        FluidPipeFluidHandler(MachineFluidPipeBlockEntity pipe) {
            this.pipe = pipe;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            if (tank != 0) return FluidStack.EMPTY;
            return switch (pipe.fluidType) {
                case WATER -> new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
                case LAVA -> new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000);
                case EMPTY -> FluidStack.EMPTY;
            };
        }

        @Override
        public int getTankCapacity(int tank) {
            return 1000; // 1 bucket
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            if (tank != 0) return false;
            return stack.getFluid() == net.minecraft.world.level.material.Fluids.WATER ||
                   stack.getFluid() == net.minecraft.world.level.material.Fluids.LAVA;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !pipe.isEmpty()) return 0;
            if (resource.getAmount() < 1000) return 0;

            FluidType type;
            if (resource.getFluid() == net.minecraft.world.level.material.Fluids.WATER) {
                type = FluidType.WATER;
            } else if (resource.getFluid() == net.minecraft.world.level.material.Fluids.LAVA) {
                type = FluidType.LAVA;
            } else {
                return 0;
            }

            if (action.execute()) {
                pipe.setFluidType(type);
                pipe.syncToClients();
            }
            return 1000;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || pipe.isEmpty()) return FluidStack.EMPTY;
            FluidStack current = getFluidInTank(0);
            if (!current.isFluidEqual(resource)) return FluidStack.EMPTY;
            if (resource.getAmount() < 1000) return FluidStack.EMPTY;

            if (action.execute()) {
                pipe.setFluidType(FluidType.EMPTY);
                pipe.syncToClients();
            }
            return current;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain < 1000 || pipe.isEmpty()) return FluidStack.EMPTY;
            FluidStack current = getFluidInTank(0);

            if (action.execute()) {
                pipe.setFluidType(FluidType.EMPTY);
                pipe.syncToClients();
            }
            return current;
        }
    }
}
