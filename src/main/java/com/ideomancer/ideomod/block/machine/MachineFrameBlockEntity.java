package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
    private static final String TAG_PULSER_TICKS_REMAINING = "PulserTicksRemaining";
    private static final String TAG_TRANSMITTER_SIGNAL = "TransmitterSignal";
    private static final String TAG_MOVEMENT_POWERED = "MovementPowered";

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

    /**
     * Countdown ticks remaining for the Machine Pulser. When the pulser
     * activates, this is set from config (IdeomodConfig.getPulserPulseDuration())
     * and decrements each tick. When it reaches 0, the pulser turns off.
     * No per-block-entity duration field needed — reads from config each time.
     *
     * Reset to 0 on every specialization swap.
     */
    private int pulserTicksRemaining = 0;

    /**
     * Signal strength stored by Machine Transmitter. For a SOURCE, this
     * is the external signal strength received. For a RECEIVER, this is
     * the output strength (source strength minus 1). Other specializations
     * ignore this field.
     *
     * Reset to 0 on every specialization swap.
     */
    private int transmitterSignalStrength = 0;

    /**
     * Transient field used only during piston movement to preserve the
     * POWERED blockstate across the move. Saved in saveMovementData(),
     * restored in loadMovementData(), and applied to the blockstate in
     * onMovementRestored(). Critical for the Toggle (a latch that holds
     * ON without continuous input) — without this, movement resets the
     * default blockstate to POWERED=false.
     */
    private boolean movementPowered = false;

    /**
     * Transient flag: true for 1 tick after this block is placed by piston
     * movement. While true, edge-triggered machines (Grabber) suppress
     * neighborChanged processing to avoid spurious toggles from intermediate
     * states during the push group settlement period (blocks are placed one
     * by one, so some neighbors are briefly missing). Cleared by scheduled
     * tick. NOT saved to disk — only relevant during the settlement window.
     */
    private boolean pistonSettling = false;

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
    // PULSER COUNTDOWN (for Machine Pulser)
    // ========================================================================

    /**
     * Get the remaining countdown ticks for the Pulser. 0 = not pulsing.
     */
    public int getPulserTicksRemaining() {
        return this.pulserTicksRemaining;
    }

    /**
     * Set the remaining countdown ticks for the Pulser.
     */
    public void setPulserTicksRemaining(int ticks) {
        this.pulserTicksRemaining = Math.max(0, ticks);
        this.setChanged();
    }

    // ========================================================================
    // TRANSMITTER SIGNAL STRENGTH
    // ========================================================================

    /**
     * Get the stored signal strength for the Machine Transmitter.
     * For sources: the external strength received.
     * For receivers: the output strength (source strength - 1).
     */
    public int getTransmitterSignalStrength() {
        return this.transmitterSignalStrength;
    }

    /**
     * Set the stored signal strength for the Machine Transmitter.
     * Clamped to 0-15.
     */
    public void setTransmitterSignalStrength(int strength) {
        int clamped = Math.max(0, Math.min(15, strength));
        if (this.transmitterSignalStrength != clamped) {
            this.transmitterSignalStrength = clamped;
            this.setChanged();
            syncToClients(); // Push updated strength to client for indicator tint
        }
    }

    // ========================================================================
    // PISTON SETTLEMENT
    // ========================================================================

    /**
     * Whether this block entity is in the 1-tick piston settlement window.
     * Edge-triggered machines should suppress neighborChanged while true.
     */
    public boolean isPistonSettling() {
        return this.pistonSettling;
    }

    /**
     * Set the piston settling flag. Called by onPlace(movedByPiston=true)
     * to begin the settlement window, and by the scheduled tick to end it.
     */
    public void setPistonSettling(boolean settling) {
        this.pistonSettling = settling;
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
        if (this.pulserTicksRemaining > 0) {
            tag.putInt(TAG_PULSER_TICKS_REMAINING, this.pulserTicksRemaining);
        }
        if (this.transmitterSignalStrength > 0) {
            tag.putInt(TAG_TRANSMITTER_SIGNAL, this.transmitterSignalStrength);
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
        if (tag.contains(TAG_PULSER_TICKS_REMAINING)) {
            this.pulserTicksRemaining = Math.max(0, tag.getInt(TAG_PULSER_TICKS_REMAINING));
        } else {
            this.pulserTicksRemaining = 0;
        }
        if (tag.contains(TAG_TRANSMITTER_SIGNAL)) {
            this.transmitterSignalStrength = Math.max(0, Math.min(15, tag.getInt(TAG_TRANSMITTER_SIGNAL)));
        } else {
            this.transmitterSignalStrength = 0;
        }
    }

    // ========================================================================
    // CLIENT SYNC
    // ========================================================================

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Called on the CLIENT when a block entity data packet arrives
     * (triggered by syncToClients / sendBlockUpdated on the server).
     *
     * Compares transmitter signal strength before and after loading
     * the new data. If the strength changed, requests a model data
     * update which triggers a chunk section rebuild — this causes
     * the block color handler to re-query the BE and update the
     * indicator tint without needing a blockstate property.
     */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        int oldStrength = this.transmitterSignalStrength;
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
        if (level != null && level.isClientSide() && this.transmitterSignalStrength != oldStrength) {
            requestModelDataUpdate();
        }
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
        if (this.pulserTicksRemaining > 0) {
            tag.putInt(TAG_PULSER_TICKS_REMAINING, this.pulserTicksRemaining);
        }
        if (this.transmitterSignalStrength > 0) {
            tag.putInt(TAG_TRANSMITTER_SIGNAL, this.transmitterSignalStrength);
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
        if (this.pulserTicksRemaining > 0) {
            tag.putInt(TAG_PULSER_TICKS_REMAINING, this.pulserTicksRemaining);
        }
        if (this.transmitterSignalStrength > 0) {
            tag.putInt(TAG_TRANSMITTER_SIGNAL, this.transmitterSignalStrength);
        }
        // Save the POWERED blockstate so it survives piston movement.
        // Critical for the Toggle (a latch), but saved generically for all
        // machine blocks so none of them flicker OFF during movement.
        if (this.level != null) {
            BlockState currentState = this.level.getBlockState(this.getBlockPos());
            if (currentState.getBlock() instanceof AbstractMachineBlock) {
                tag.putBoolean(TAG_MOVEMENT_POWERED,
                        ((AbstractMachineBlock) currentState.getBlock()).getRedstoneOutput(currentState) > 0);
            }
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
        if (tag.contains(TAG_PULSER_TICKS_REMAINING)) {
            this.pulserTicksRemaining = Math.max(0, tag.getInt(TAG_PULSER_TICKS_REMAINING));
        } else {
            this.pulserTicksRemaining = 0;
        }
        if (tag.contains(TAG_TRANSMITTER_SIGNAL)) {
            this.transmitterSignalStrength = Math.max(0, Math.min(15, tag.getInt(TAG_TRANSMITTER_SIGNAL)));
        } else {
            this.transmitterSignalStrength = 0;
        }
        this.movementPowered = tag.getBoolean(TAG_MOVEMENT_POWERED);
        this.setChanged();
    }

    @Override
    public void onMovementRestored(Level level, BlockPos pos) {
        // Scheduled ticks are lost during piston movement. If the block at
        // this position is a redstone machine that uses tick-based processing
        // (Repeater, Transformer, Toggle, Timer, Pulser), schedule a tick so
        // it can resume its countdown or re-evaluate its power state.
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof AbstractMachineBlock) {
                boolean skipTick = false;

                // FIX 2026-03-31c: Restore POWERED blockstate for ALL machine
                // blocks that have it, not just Toggle. Vanilla preserves the
                // original blockstate through movedState, but neighbor updates
                // from piston head removal (which fires before vanilla captures
                // blockstates) can cause transient state changes. Explicitly
                // restoring from movementPowered acts as a safety net.
                //
                // Toggle special case: must also restore INPUT_FACE property
                // and skip the tick (tick() on a POWERED toggle means "toggle OFF").
                if (block instanceof MachineToggleBlock && this.movementPowered) {
                    MachineToggleBlock.InputFace inputFaceEnum = this.inputFace != null
                            ? MachineToggleBlock.InputFace.fromDirection(this.inputFace)
                            : MachineToggleBlock.InputFace.NONE;
                    BlockState restoredState = state
                            .setValue(MachineToggleBlock.POWERED, true)
                            .setValue(MachineToggleBlock.INPUT_FACE, inputFaceEnum);
                    if (!restoredState.equals(state)) {
                        level.setBlock(pos, restoredState, 2); // UPDATE_CLIENTS only
                    }
                    skipTick = true; // latch holds — no re-evaluation needed

                } else if (this.movementPowered) {
                    // Non-toggle machine blocks with POWERED property:
                    // Pulser, Timer, Repeater, Transformer, Engine.
                    // Restore POWERED=true and let the tick continue their
                    // countdown / re-evaluation. Use property lookup so this
                    // works generically for any machine block with a "powered"
                    // property, without needing instanceof checks for each type.
                    net.minecraft.world.level.block.state.properties.Property<?> poweredProp =
                            state.getBlock().getStateDefinition().getProperty("powered");
                    if (poweredProp instanceof net.minecraft.world.level.block.state.properties.BooleanProperty bp) {
                        if (!state.getValue(bp)) {
                            // movedState had POWERED=false but we saved POWERED=true.
                            // Restore it.
                            BlockState restoredState = state.setValue(bp, true);
                            level.setBlock(pos, restoredState, 2);
                            state = restoredState; // update local ref for tick scheduling
                        }
                    }
                }
                this.movementPowered = false; // consumed

                if (!skipTick && !serverLevel.getBlockTicks().willTickThisTick(pos, block)) {
                    // FIX 2026-03-31d: Stagger tick delays to preserve pulse cascades.
                    // POWERED blocks (mid-countdown) get delay 2 so that UNPOWERED
                    // neighbors can process first at delay 1 and detect the signal
                    // before the sender's countdown expires.  Without this stagger,
                    // both fire at delay 1 and tick ordering is non-deterministic —
                    // the sender can expire before the receiver sees the signal.
                    boolean currentlyPowered = false;
                    net.minecraft.world.level.block.state.properties.Property<?> tickProp =
                            state.getBlock().getStateDefinition().getProperty("powered");
                    if (tickProp instanceof net.minecraft.world.level.block.state.properties.BooleanProperty tbp) {
                        currentlyPowered = state.getValue(tbp);
                    }
                    serverLevel.scheduleTick(pos, block, currentlyPowered ? 2 : 1);
                }
            }
        }
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
        ItemStack oldStack = this.items.get(slot).copy();
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }

        // PRE-SWAP: Let the current block clean up state tied to the old item
        // (e.g., transmitter turning off old-channel receivers). Must happen
        // BEFORE triggerSwapCheck, which may replace the block entirely.
        if (this.level != null && !this.level.isClientSide()
                && !ItemStack.isSameItemSameTags(oldStack, stack)) {
            Block block = this.level.getBlockState(this.worldPosition).getBlock();
            if (block instanceof AbstractMachineBlock machineBlock) {
                machineBlock.onSpecItemChanging(this.level, this.worldPosition, oldStack);
            }
        }

        this.setChanged();
        triggerSwapCheck();

        // POST-SWAP: Let the block at this position re-evaluate for the new
        // item. This works for BOTH cases:
        //   - Same block type (oddstone variant swap): this BE is still alive,
        //     re-evaluates for the new channel.
        //   - Cross block type (e.g., conductor → transmitter): checkAndSwap
        //     replaced the block and set the item via setItemDirect. The NEW
        //     block's onSpecItemChanged runs and can now see the spec item.
        if (this.level != null && !this.level.isClientSide()
                && !ItemStack.isSameItemSameTags(oldStack, stack)) {
            Block block = this.level.getBlockState(this.worldPosition).getBlock();
            if (block instanceof AbstractMachineBlock machineBlock) {
                machineBlock.onSpecItemChanged(this.level, this.worldPosition, stack);
            }
        }
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
        // Reset pulser countdown
        this.pulserTicksRemaining = 0;
        // Reset transmitter signal strength
        this.transmitterSignalStrength = 0;

        // Reset engine fields (if switching away from engine)
        // Note: MachineEngineBlockEntity has its own fuel fields that are
        // not stored here; the block entity itself is replaced on swap.
        // This reset is a safety net for the base class fields.

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
