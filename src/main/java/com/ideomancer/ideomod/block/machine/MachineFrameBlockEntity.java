package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.ideomancer.ideomod.block.movement.IPistonMovable;

/**
 * Shared block entity for ALL Machine Family blocks.
 *
 * Stores a single item that determines the block's specialization. When
 * the slot contents change, calls MachineSwapHelper to check if the block
 * type should change. The menu uses PositionBoundContainer so it
 * automatically sees the new block entity after a swap — no rebind needed.
 *
 * Also stores an inputFace for redstone-active specializations (Conductor,
 * Repeater, Toggle, Timer, Transformer, future Pulser/Arm). The input face
 * is the direction from which signal is received; the block outputs on all
 * other faces. Specializations that don't use redstone (Track, Trolley, Frame)
 * ignore this field. inputFace is reset to null on every specialization swap
 * to prevent stale directional data from leaking between specialization types.
 *
 * Also stores repeaterDelay for the Machine Repeater specialization (1-4
 * redstone ticks). Reset to 1 on every specialization swap.
 *
 * Also stores transformerLevel for the Machine Transformer specialization
 * (1-15 signal strength output). Reset to 1 on every specialization swap.
 *
 * Also stores inputWasLow for edge-triggered specializations (Toggle, Timer).
 * Tracks whether the input signal has dropped to 0 since the last activation,
 * arming the block for the next rising edge. Reset to false on every swap.
 *
 * Also stores timerDuration and timerTicksRemaining for the Machine Timer
 * specialization. timerDuration is the player-configured output duration
 * in ticks. timerTicksRemaining counts down while the timer is active.
 * Both reset on specialization swap.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, IPistonMovable {

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private static final int[] SLOTS = {0};

    private static final String TAG_INPUT_FACE = "InputFace";
    private static final String TAG_REPEATER_DELAY = "RepeaterDelay";
    private static final String TAG_TRANSFORMER_LEVEL = "TransformerLevel";
    private static final String TAG_INPUT_WAS_LOW = "InputWasLow";
    private static final String TAG_TIMER_DURATION = "TimerDuration";
    private static final String TAG_TIMER_TICKS_REMAINING = "TimerTicksRemaining";

    /**
     * The face from which this block receives redstone signal.
     * Null = no input (unpowered). Used by Conductor, Repeater, Toggle,
     * Timer, Transformer, and future redstone specializations. Signal is
     * output on all faces EXCEPT this one.
     *
     * IMPORTANT: This field is reset to null whenever the specialization
     * item changes (via triggerSwapCheck) to prevent stale directional
     * data from a previous specialization affecting the new one.
     */
    @Nullable
    private Direction inputFace = null;

    /**
     * Delay setting for the Machine Repeater, in redstone ticks (1-4).
     * Each redstone tick = 2 game ticks. Default 1 (fastest).
     * Only meaningful when the block is a MachineRepeaterBlock; other
     * specializations ignore this field.
     *
     * Reset to 1 on every specialization swap (via triggerSwapCheck).
     */
    private int repeaterDelay = 1;

    /**
     * Output level setting for the Machine Transformer (1-15).
     * When powered, the Transformer outputs this signal strength on all
     * non-input faces. Default 1 (minimum).
     * Only meaningful when the block is a MachineTransformerBlock; other
     * specializations ignore this field.
     *
     * Reset to 1 on every specialization swap (via triggerSwapCheck).
     */
    private int transformerLevel = 1;

    /**
     * Edge detection state for edge-triggered specializations (Toggle, Timer).
     * Tracks whether the input signal has been seen at 0 since the last
     * activation event. When true, the next rising edge on the input face
     * triggers the block's action.
     *
     * Reset to false on every specialization swap (via triggerSwapCheck).
     */
    private boolean inputWasLow = false;

    /**
     * Configured output duration for the Machine Timer, in game ticks.
     * Default comes from config (first entry in timer durations array).
     * Only meaningful when the block is a MachineTimerBlock.
     *
     * Reset to config default on every specialization swap.
     */
    private int timerDuration = 20;

    /**
     * Countdown ticks remaining for the Machine Timer. When the timer
     * turns on, this is set to timerDuration and decrements each tick.
     * When it reaches 0, the timer turns off.
     *
     * Reset to 0 on every specialization swap.
     */
    private int timerTicksRemaining = 0;

    // ========================================================================
    // ARM ANIMATION STATE (for Machine Piston / Sticky Piston)
    // ========================================================================
    // Per-face animation for omnidirectional piston arms.
    // animDirection: +1 = extending, -1 = retracting, 0 = idle
    // progress: 0.0 to 1.0 (advances 0.5 per tick, 2 ticks total)
    // progressO: previous tick's progress (for partialTick interpolation)
    // These fields are transient — not saved to NBT. Animation is short
    // enough (2 ticks) that it doesn't survive chunk unload/reload.
    // Block events re-trigger on chunk load via neighborChanged anyway.

    private final float[] armProgress = new float[6];
    private final float[] armProgressO = new float[6];
    private final int[] armAnimDirection = new int[6]; // +1, -1, or 0

    public MachineFrameBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.MACHINE_FRAME.get(), pos, state);
    }

    /**
     * Protected constructor for subclasses (e.g. MachinePipeBlockEntity)
     * that need to register with their own BlockEntityType.
     */
    protected MachineFrameBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                       BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ========================================================================
    // INPUT FACE (for redstone specializations)
    // ========================================================================

    @Nullable
    public Direction getInputFace() {
        return this.inputFace;
    }

    public void setInputFace(@Nullable Direction face) {
        if (this.inputFace != face) {
            this.inputFace = face;
            this.setChanged();
        }
    }

    // ========================================================================
    // REPEATER DELAY (for Machine Repeater)
    // ========================================================================

    /**
     * Get the repeater delay in redstone ticks (1-4).
     */
    public int getRepeaterDelay() {
        return this.repeaterDelay;
    }

    /**
     * Set the repeater delay in redstone ticks (1-4).
     * Clamps to valid range. Syncs DELAY blockstate if the block is a Repeater.
     */
    public void setRepeaterDelay(int delay) {
        delay = Math.max(1, Math.min(4, delay));
        if (this.repeaterDelay != delay) {
            this.repeaterDelay = delay;
            this.setChanged();

            // Sync the DELAY blockstate property on the block
            if (this.level != null && !this.level.isClientSide()) {
                BlockState state = this.getBlockState();
                if (state.getBlock() instanceof MachineRepeaterBlock) {
                    this.level.setBlock(this.worldPosition,
                            state.setValue(MachineRepeaterBlock.DELAY, delay), 2);
                }
            }
        }
    }

    // ========================================================================
    // TRANSFORMER LEVEL (for Machine Transformer)
    // ========================================================================

    /**
     * Get the transformer output level (1-15).
     */
    public int getTransformerLevel() {
        return this.transformerLevel;
    }

    /**
     * Set the transformer output level (1-15).
     * Clamps to valid range. Syncs OUTPUT_LEVEL blockstate if the block is a Transformer.
     */
    public void setTransformerLevel(int level) {
        level = Math.max(1, Math.min(15, level));
        if (this.transformerLevel != level) {
            this.transformerLevel = level;
            this.setChanged();

            // Sync the OUTPUT_LEVEL blockstate property on the block
            if (this.level != null && !this.level.isClientSide()) {
                BlockState state = this.getBlockState();
                if (state.getBlock() instanceof MachineTransformerBlock) {
                    this.level.setBlock(this.worldPosition,
                            state.setValue(MachineTransformerBlock.OUTPUT_LEVEL, level), 3);
                }
            }
        }
    }

    // ========================================================================
    // TOGGLE / TIMER EDGE DETECTION
    // ========================================================================

    /**
     * Whether the input signal has been seen at 0 since the last activation.
     * Used by Toggle (toggle-off arming) and Timer (re-trigger arming).
     */
    public boolean isInputWasLow() {
        return this.inputWasLow;
    }

    /**
     * Set the edge detection arm state.
     */
    public void setInputWasLow(boolean value) {
        if (this.inputWasLow != value) {
            this.inputWasLow = value;
            this.setChanged();
        }
    }

    // ========================================================================
    // TIMER DURATION AND COUNTDOWN (for Machine Timer)
    // ========================================================================

    /**
     * Get the configured timer duration in game ticks.
     */
    public int getTimerDuration() {
        return this.timerDuration;
    }

    /**
     * Set the configured timer duration in game ticks.
     * Does NOT affect a currently-running timer — only the next activation.
     */
    public void setTimerDuration(int duration) {
        duration = Math.max(1, duration);
        if (this.timerDuration != duration) {
            this.timerDuration = duration;
            this.setChanged();
        }
    }

    /**
     * Get the remaining countdown ticks. 0 = timer not running.
     */
    public int getTimerTicksRemaining() {
        return this.timerTicksRemaining;
    }

    /**
     * Set the remaining countdown ticks.
     */
    public void setTimerTicksRemaining(int ticks) {
        this.timerTicksRemaining = Math.max(0, ticks);
        this.setChanged();
    }

    // ========================================================================
    // ARM ANIMATION ACCESSORS
    // ========================================================================

    public float getArmProgress(Direction dir) {
        return armProgress[dir.get3DDataValue()];
    }

    public void setArmProgress(Direction dir, float value) {
        armProgress[dir.get3DDataValue()] = value;
    }

    public float getArmProgressO(Direction dir) {
        return armProgressO[dir.get3DDataValue()];
    }

    public void setArmProgressO(Direction dir, float value) {
        armProgressO[dir.get3DDataValue()] = value;
    }

    public int getArmAnimDirection(Direction dir) {
        return armAnimDirection[dir.get3DDataValue()];
    }

    public void setArmAnimDirection(Direction dir, int value) {
        armAnimDirection[dir.get3DDataValue()] = value;
    }

    /**
     * Returns the interpolated arm progress for rendering.
     * Uses partialTick for smooth sub-tick animation.
     */
    public float getArmRenderProgress(Direction dir, float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, armProgressO[dir.get3DDataValue()], armProgress[dir.get3DDataValue()]);
    }

    // ========================================================================
    // NBT PERSISTENCE
    // ========================================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, this.items, true);
        if (this.inputFace != null) {
            tag.putInt(TAG_INPUT_FACE, this.inputFace.get3DDataValue());
        }
        if (this.repeaterDelay != 1) {
            tag.putInt(TAG_REPEATER_DELAY, this.repeaterDelay);
        }
        if (this.transformerLevel != 1) {
            tag.putInt(TAG_TRANSFORMER_LEVEL, this.transformerLevel);
        }
        if (this.inputWasLow) {
            tag.putBoolean(TAG_INPUT_WAS_LOW, true);
        }
        if (this.timerDuration != 20) {
            tag.putInt(TAG_TIMER_DURATION, this.timerDuration);
        }
        if (this.timerTicksRemaining > 0) {
            tag.putInt(TAG_TIMER_TICKS_REMAINING, this.timerTicksRemaining);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items);
        if (tag.contains(TAG_INPUT_FACE)) {
            this.inputFace = Direction.from3DDataValue(tag.getInt(TAG_INPUT_FACE));
        } else {
            this.inputFace = null;
        }
        if (tag.contains(TAG_REPEATER_DELAY)) {
            this.repeaterDelay = Math.max(1, Math.min(4, tag.getInt(TAG_REPEATER_DELAY)));
        } else {
            this.repeaterDelay = 1;
        }
        if (tag.contains(TAG_TRANSFORMER_LEVEL)) {
            this.transformerLevel = Math.max(1, Math.min(15, tag.getInt(TAG_TRANSFORMER_LEVEL)));
        } else {
            this.transformerLevel = 1;
        }
        this.inputWasLow = tag.getBoolean(TAG_INPUT_WAS_LOW);
        if (tag.contains(TAG_TIMER_DURATION)) {
            this.timerDuration = Math.max(1, tag.getInt(TAG_TIMER_DURATION));
        } else {
            this.timerDuration = 20;
        }
        if (tag.contains(TAG_TIMER_TICKS_REMAINING)) {
            this.timerTicksRemaining = Math.max(0, tag.getInt(TAG_TIMER_TICKS_REMAINING));
        } else {
            this.timerTicksRemaining = 0;
        }
    }

    // ========================================================================
    // CLIENT SYNC
    // ========================================================================

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        ContainerHelper.saveAllItems(tag, this.items, true);
        if (this.inputFace != null) {
            tag.putInt(TAG_INPUT_FACE, this.inputFace.get3DDataValue());
        }
        if (this.repeaterDelay != 1) {
            tag.putInt(TAG_REPEATER_DELAY, this.repeaterDelay);
        }
        if (this.transformerLevel != 1) {
            tag.putInt(TAG_TRANSFORMER_LEVEL, this.transformerLevel);
        }
        if (this.inputWasLow) {
            tag.putBoolean(TAG_INPUT_WAS_LOW, true);
        }
        if (this.timerDuration != 20) {
            tag.putInt(TAG_TIMER_DURATION, this.timerDuration);
        }
        if (this.timerTicksRemaining > 0) {
            tag.putInt(TAG_TIMER_TICKS_REMAINING, this.timerTicksRemaining);
        }
        return tag;
    }

    public void syncToClients() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition,
                    this.getBlockState(), this.getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    // ========================================================================
    // IPISTON MOVABLE
    // ========================================================================

    @Override
    public CompoundTag saveMovementData() {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, this.items, true);
        if (this.inputFace != null) {
            tag.putInt(TAG_INPUT_FACE, this.inputFace.get3DDataValue());
        }
        if (this.repeaterDelay != 1) {
            tag.putInt(TAG_REPEATER_DELAY, this.repeaterDelay);
        }
        if (this.transformerLevel != 1) {
            tag.putInt(TAG_TRANSFORMER_LEVEL, this.transformerLevel);
        }
        if (this.inputWasLow) {
            tag.putBoolean(TAG_INPUT_WAS_LOW, true);
        }
        if (this.timerDuration != 20) {
            tag.putInt(TAG_TIMER_DURATION, this.timerDuration);
        }
        if (this.timerTicksRemaining > 0) {
            tag.putInt(TAG_TIMER_TICKS_REMAINING, this.timerTicksRemaining);
        }
        return tag;
    }

    @Override
    public void loadMovementData(CompoundTag tag) {
        this.items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items);
        if (tag.contains(TAG_INPUT_FACE)) {
            this.inputFace = Direction.from3DDataValue(tag.getInt(TAG_INPUT_FACE));
        } else {
            this.inputFace = null;
        }
        if (tag.contains(TAG_REPEATER_DELAY)) {
            this.repeaterDelay = Math.max(1, Math.min(4, tag.getInt(TAG_REPEATER_DELAY)));
        } else {
            this.repeaterDelay = 1;
        }
        if (tag.contains(TAG_TRANSFORMER_LEVEL)) {
            this.transformerLevel = Math.max(1, Math.min(15, tag.getInt(TAG_TRANSFORMER_LEVEL)));
        } else {
            this.transformerLevel = 1;
        }
        this.inputWasLow = tag.getBoolean(TAG_INPUT_WAS_LOW);
        if (tag.contains(TAG_TIMER_DURATION)) {
            this.timerDuration = Math.max(1, tag.getInt(TAG_TIMER_DURATION));
        } else {
            this.timerDuration = 20;
        }
        if (tag.contains(TAG_TIMER_TICKS_REMAINING)) {
            this.timerTicksRemaining = Math.max(0, tag.getInt(TAG_TIMER_TICKS_REMAINING));
        } else {
            this.timerTicksRemaining = 0;
        }
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
    public int getContainerSize() { return 1; }

    @Override
    public boolean isEmpty() { return this.items.get(0).isEmpty(); }

    @Override
    public ItemStack getItem(int slot) { return this.items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
        if (!result.isEmpty()) {
            this.setChanged();
            triggerSwapCheck();
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
        triggerSwapCheck();
    }

    /**
     * Sets the item WITHOUT triggering a swap check. Used by
     * MachineSwapHelper after it has already performed the block swap.
     */
    public void setItemDirect(ItemStack stack) {
        this.items.set(0, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    private void triggerSwapCheck() {
        // Clear stale directional data from the previous specialization.
        this.inputFace = null;
        // Reset repeater delay to default (prevents stale delay leaking)
        this.repeaterDelay = 1;
        // Reset transformer level to default (prevents stale level leaking)
        this.transformerLevel = 1;
        // Reset toggle/timer edge detection state
        this.inputWasLow = false;
        // Reset timer fields to defaults
        int[] durations = IdeomodConfig.getTimerDurations();
        int defaultIdx = IdeomodConfig.getTimerDefaultIndex();
        this.timerDuration = (defaultIdx >= 0 && defaultIdx < durations.length)
                ? durations[defaultIdx] : 20;
        this.timerTicksRemaining = 0;

        if (this.level != null && !this.level.isClientSide()) {
            MachineSwapHelper.checkAndSwap(this.level, this.worldPosition, this.items.get(0));
        }
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
    public void clearContent() { this.items.clear(); }

    @Override
    public int getMaxStackSize() { return 1; }

    // ========================================================================
    // WORLDLY CONTAINER
    // ========================================================================

    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) { return SLOTS; }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable net.minecraft.core.Direction direction) {
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction direction) {
        return true;
    }

    // ========================================================================
    // MENU PROVIDER
    // ========================================================================

    @Override
    public Component getDisplayName() {
        return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new MachineFrameMenu(containerId, playerInv, this.worldPosition);
    }
}
