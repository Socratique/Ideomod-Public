package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for Machine Engine specialization.
 *
 * Extends MachineFrameBlockEntity to add:
 * - A fuel slot (slot 1) that holds fuel items
 * - Fuel burning mechanics (litTime, litDuration)
 * - Pulse interval counter (pulseInterval, pulseCounter)
 * - Hopper/pipe automation for the fuel slot
 * - Comparator output based on fuel slot fullness
 *
 * Slot 0: Specialization item (furnace) -- inherited from parent
 * Slot 1: Fuel item -- fuel for burning
 *
 * IMPORTANT: This BE uses ModBlockEntities.MACHINE_ENGINE, NOT MACHINE_FRAME.
 * The engine block is registered with its own BE type so the fuel slot
 * and ticker are only present on engines.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineEngineBlockEntity extends MachineFrameBlockEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineEngineBlockEntity.class);

    /** One-shot flag to log once on first serverTick, confirming the ticker is alive. */
    private boolean tickerConfirmed = false;

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    /** Slot index for the fuel item. */
    public static final int FUEL_SLOT = 1;

    // NBT tags
    private static final String TAG_FUEL_ITEM = "FuelItem";
    private static final String TAG_LIT_TIME = "LitTime";
    private static final String TAG_LIT_DURATION = "LitDuration";
    private static final String TAG_PULSE_INTERVAL = "PulseInterval";
    private static final String TAG_PULSE_COUNTER = "PulseCounter";

    // ========================================================================
    // STATE
    // ========================================================================

    /** The item currently in the fuel slot. */
    private ItemStack fuelItem = ItemStack.EMPTY;

    /** Remaining burn ticks for current fuel item. Decrements by 1 each game tick while > 0. */
    private int litTime = 0;

    /** Total burn time of the current fuel item (for flame progress calc). */
    private int litDuration = 0;

    /** Selected pulse interval in game ticks. Set by GUI buttons. */
    private int pulseInterval;

    /** Ticks since last pulse. Increments each tick while burning + powered. Resets to 0 on pulse. */
    private int pulseCounter = 0;

    /** Capability cache for item handler exposure. */
    private LazyOptional<IItemHandler>[] itemHandlers = null;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public MachineEngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_ENGINE.get(), pos, state);
        // Set default pulse interval from config
        this.pulseInterval = IdeomodConfig.getEngineDefaultDuration();
    }

    // ========================================================================
    // FUEL SLOT ACCESS
    // ========================================================================

    public ItemStack getFuelItem() {
        return this.fuelItem;
    }

    public void setFuelItem(ItemStack stack) {
        this.fuelItem = stack;
        this.setChanged();
    }

    public boolean hasFuel() {
        return !this.fuelItem.isEmpty();
    }

    // ========================================================================
    // FUEL STATE ACCESSORS
    // ========================================================================

    public int getLitTime() {
        return this.litTime;
    }

    public void setLitTime(int litTime) {
        this.litTime = litTime;
    }

    public int getLitDuration() {
        return this.litDuration;
    }

    public void setLitDuration(int litDuration) {
        this.litDuration = litDuration;
    }

    public int getPulseInterval() {
        return this.pulseInterval;
    }

    public void setPulseInterval(int interval) {
        this.pulseInterval = Math.max(1, interval);
        this.setChanged();
    }

    public int getPulseCounter() {
        return this.pulseCounter;
    }

    public void setPulseCounter(int counter) {
        this.pulseCounter = counter;
    }

    // ========================================================================
    // SERVER TICKER
    // ========================================================================

    /**
     * Server-side ticker. Runs every game tick. Implements the pulse tick logic:
     *
     * 1. If litTime > 0 AND powered: litTime-- (fuel freezes when unpowered)
     * 2. If litTime == 0 AND fuel slot has fuel AND engine is powered:
     *    Consume fuel, set litTime = litDuration = burn time
     * 3. If litTime > 0 AND engine is powered:
     *    pulseCounter++; if >= pulseInterval, emit pulse and reset
     * 4. If not powered: reset pulseCounter, force-clear POWERED/LIT states
     */
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        // One-shot confirmation that the ticker is alive
        if (!tickerConfirmed) {
            tickerConfirmed = true;
            LOGGER.debug("[ENGINE] Ticker ALIVE at {} — inputFace={}, fuel={}, litTime={}, interval={}",
                    pos, this.getInputFace(), this.fuelItem, this.litTime, this.pulseInterval);
        }

        boolean changed = false;
        boolean isPowered = this.getInputFace() != null;

        // Step 1: Burn existing fuel — ONLY when powered.
        // Fuel timer freezes when the engine loses signal and resumes when re-powered.
        if (this.litTime > 0 && isPowered) {
            this.litTime--;
            changed = true;
        }

        // Step 2: Auto-feed next fuel item
        if (this.litTime == 0 && !this.fuelItem.isEmpty() && isPowered) {
            int burnTime = ForgeHooks.getBurnTime(this.fuelItem, null);
            if (burnTime > 0) {
                this.litTime = burnTime;
                this.litDuration = burnTime;
                LOGGER.debug("[ENGINE] Fuel consumed at {} — burnTime={}, fuelItem={}", pos, burnTime, this.fuelItem);
                // Consume fuel item
                ItemStack fuel = this.fuelItem;
                if (fuel.getItem() == Items.LAVA_BUCKET) {
                    // Lava bucket: leave empty bucket
                    this.fuelItem = new ItemStack(Items.BUCKET);
                } else {
                    fuel.shrink(1);
                    if (fuel.isEmpty()) {
                        this.fuelItem = ItemStack.EMPTY;
                    }
                }
                changed = true;
            }
        }

        // Step 3: Pulse logic
        boolean isLit = this.litTime > 0;
        if (isLit && isPowered) {
            this.pulseCounter++;
            if (this.pulseCounter >= this.pulseInterval) {
                this.pulseCounter = 0;
                // Get fresh state in case LIT changed
                BlockState currentState = level.getBlockState(pos);
                if (currentState.getBlock() instanceof MachineEngineBlock engineBlock) {
                    LOGGER.debug("[ENGINE] PULSE at {} — litTime={}, interval={}", pos, this.litTime, this.pulseInterval);
                    engineBlock.emitPulse(level, pos, currentState);
                } else {
                    LOGGER.warn("[ENGINE] Block at {} is NOT MachineEngineBlock! Got: {}", pos, currentState.getBlock());
                }
            }
        }

        // Step 4: When not powered — reset counter and force-clear visual states.
        // Belt-and-suspenders: at high pulse rates, rapid POWERED toggling can leave
        // stale visual state if the last scheduled tick races with the signal drop.
        if (!isPowered) {
            this.pulseCounter = 0;

            BlockState currentState = level.getBlockState(pos);
            if (currentState.getBlock() instanceof MachineEngineBlock) {
                boolean needsUpdate = false;
                if (currentState.getValue(MachineEngineBlock.POWERED)) {
                    currentState = currentState.setValue(MachineEngineBlock.POWERED, false);
                    needsUpdate = true;
                }
                if (currentState.getValue(MachineEngineBlock.LIT)) {
                    currentState = currentState.setValue(MachineEngineBlock.LIT, false);
                    needsUpdate = true;
                }
                if (needsUpdate) {
                    level.setBlock(pos, currentState, 3);
                    changed = true;
                }
            }
        } else {
            // Update LIT blockstate (only when powered — unpowered is handled above)
            boolean shouldBeLit = isLit;
            BlockState currentState = level.getBlockState(pos);
            if (currentState.getBlock() instanceof MachineEngineBlock) {
                boolean currentlyLit = currentState.getValue(MachineEngineBlock.LIT);
                if (shouldBeLit != currentlyLit) {
                    level.setBlock(pos, currentState.setValue(MachineEngineBlock.LIT, shouldBeLit), 3);
                    changed = true;
                }
            }
        }

        if (changed) {
            this.setChanged();
        }
    }

    // ========================================================================
    // CONTAINER INTERFACE (expanded for 2 slots)
    // ========================================================================

    @Override
    public int getContainerSize() {
        return 2; // slot 0 = specialization, slot 1 = fuel
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && this.fuelItem.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == FUEL_SLOT) return this.fuelItem;
        return super.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == FUEL_SLOT) {
            ItemStack result = this.fuelItem.split(amount);
            if (!result.isEmpty()) {
                this.setChanged();
            }
            return result;
        }
        return super.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot == FUEL_SLOT) {
            ItemStack result = this.fuelItem;
            this.fuelItem = ItemStack.EMPTY;
            return result;
        }
        return super.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == FUEL_SLOT) {
            this.fuelItem = stack;
            if (stack.getCount() > this.getMaxStackSize(slot)) {
                stack.setCount(this.getMaxStackSize(slot));
            }
            this.setChanged();
            return;
        }
        super.setItem(slot, stack);
    }

    /**
     * Max stack size per slot.
     * Slot 0 (specialization): 1 item.
     * Slot 1 (fuel): 64 (full stack).
     */
    public int getMaxStackSize(int slot) {
        if (slot == FUEL_SLOT) return 64;
        return 1;
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.fuelItem = ItemStack.EMPTY;
    }

    // ========================================================================
    // WORLDLY CONTAINER (hopper/pipe interaction)
    // ========================================================================

    private static final int[] FUEL_SLOTS = {1};

    /**
     * External automation sees only the fuel slot.
     * The specialization slot (0) is player-GUI-only.
     */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return FUEL_SLOTS;
    }

    /**
     * External sources can place fuel items into the fuel slot from any face.
     * Only accepts valid fuel items (burn time > 0).
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        if (slot == FUEL_SLOT) {
            return ForgeHooks.getBurnTime(stack, null) > 0;
        }
        return false;
    }

    /**
     * External extraction from the fuel slot is allowed only for non-fuel items
     * (empty buckets after lava bucket consumption).
     */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        if (slot == FUEL_SLOT) {
            // Only allow extraction of non-fuel items (empty buckets)
            return ForgeHooks.getBurnTime(stack, null) <= 0;
        }
        return false;
    }

    // ========================================================================
    // NBT PERSISTENCE
    // ========================================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.fuelItem.isEmpty()) {
            tag.put(TAG_FUEL_ITEM, this.fuelItem.save(new CompoundTag()));
        }
        tag.putInt(TAG_LIT_TIME, this.litTime);
        tag.putInt(TAG_LIT_DURATION, this.litDuration);
        tag.putInt(TAG_PULSE_INTERVAL, this.pulseInterval);
        tag.putInt(TAG_PULSE_COUNTER, this.pulseCounter);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_FUEL_ITEM)) {
            this.fuelItem = ItemStack.of(tag.getCompound(TAG_FUEL_ITEM));
        } else {
            this.fuelItem = ItemStack.EMPTY;
        }
        this.litTime = tag.getInt(TAG_LIT_TIME);
        this.litDuration = tag.getInt(TAG_LIT_DURATION);
        if (tag.contains(TAG_PULSE_INTERVAL)) {
            this.pulseInterval = Math.max(1, tag.getInt(TAG_PULSE_INTERVAL));
        } else {
            this.pulseInterval = IdeomodConfig.getEngineDefaultDuration();
        }
        this.pulseCounter = tag.getInt(TAG_PULSE_COUNTER);
    }

    // ========================================================================
    // CLIENT SYNC
    // ========================================================================

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (!this.fuelItem.isEmpty()) {
            tag.put(TAG_FUEL_ITEM, this.fuelItem.save(new CompoundTag()));
        }
        tag.putInt(TAG_LIT_TIME, this.litTime);
        tag.putInt(TAG_LIT_DURATION, this.litDuration);
        tag.putInt(TAG_PULSE_INTERVAL, this.pulseInterval);
        return tag;
    }

    // ========================================================================
    // PISTON MOVABLE
    // ========================================================================

    @Override
    public CompoundTag saveMovementData() {
        CompoundTag tag = super.saveMovementData();
        if (!this.fuelItem.isEmpty()) {
            tag.put(TAG_FUEL_ITEM, this.fuelItem.save(new CompoundTag()));
        }
        tag.putInt(TAG_LIT_TIME, this.litTime);
        tag.putInt(TAG_LIT_DURATION, this.litDuration);
        tag.putInt(TAG_PULSE_INTERVAL, this.pulseInterval);
        tag.putInt(TAG_PULSE_COUNTER, this.pulseCounter);
        return tag;
    }

    @Override
    public void loadMovementData(CompoundTag tag) {
        super.loadMovementData(tag);
        if (tag.contains(TAG_FUEL_ITEM)) {
            this.fuelItem = ItemStack.of(tag.getCompound(TAG_FUEL_ITEM));
        } else {
            this.fuelItem = ItemStack.EMPTY;
        }
        this.litTime = tag.getInt(TAG_LIT_TIME);
        this.litDuration = tag.getInt(TAG_LIT_DURATION);
        if (tag.contains(TAG_PULSE_INTERVAL)) {
            this.pulseInterval = Math.max(1, tag.getInt(TAG_PULSE_INTERVAL));
        } else {
            this.pulseInterval = IdeomodConfig.getEngineDefaultDuration();
        }
        this.pulseCounter = tag.getInt(TAG_PULSE_COUNTER);
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
        this.itemHandlers = null;
    }
}
