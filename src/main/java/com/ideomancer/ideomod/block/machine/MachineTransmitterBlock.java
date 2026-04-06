package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.ideomancer.ideomod.config.IdeomodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.ticks.TickPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Machine Transmitter — wireless redstone via Oddstone channels.
 *
 * Specialization item: any Oddstone block or variant. The specific
 * variant determines the wireless channel. Two transmitters match
 * if and only if they have the exact same Oddstone type in their
 * spec slot.
 *
 * SIGNAL FLOW:
 * ============
 * A transmitter operates in one of two mutually exclusive modes:
 *
 * 1. SOURCE (POWERED=true, SOURCE=true):
 *    Receiving external redstone → broadcasts wirelessly to matching
 *    transmitters in range. Does NOT output any redstone signal.
 *    Stores the received signal strength in the block entity.
 *    Activation is IMMEDIATE in neighborChanged (no tick delay) so
 *    even 1-tick pulses are captured and transmitted.
 *
 * 2. RECEIVER (POWERED=true, SOURCE=false):
 *    Receiving wireless signal from a source → outputs redstone
 *    signal at (source strength - 1) on all faces, simulating
 *    signal attenuation as if there were a direct connection.
 *    Does NOT accept external redstone input. Does NOT re-broadcast
 *    wirelessly (no chaining).
 *
 * ANTI-FEEDBACK:
 * ==============
 * The SOURCE blockstate property enforces mode exclusivity:
 * - getSignal returns 0 when SOURCE=true (source never outputs)
 * - neighborChanged is ignored when POWERED && !SOURCE (receiver
 *   never reads external input)
 * - On power-off, receivers are turned off BEFORE the source
 *   notifies its own neighbors, preventing stale receiver output
 *   from feeding back through dust.
 * Additionally, shouldSignal ThreadLocal suppresses self-reads
 * through solid blocks during external signal checks.
 *
 * RADIUS:
 * =======
 * Uses the Oddstone sympathetic radius from config (default 5).
 * Range is cubic (not spherical) for simplicity and consistency
 * with the sympathetic movement system.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineTransmitterBlock extends AbstractMachineBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineTransmitterBlock.class);

    /** Whether this transmitter is active (either source or receiver). */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    /**
     * Whether this transmitter is in SOURCE mode (receiving external
     * redstone input and broadcasting wirelessly). When true, the block
     * does NOT output any redstone signal. When false and POWERED=true,
     * the block is in RECEIVER mode and outputs signal.
     */
    public static final BooleanProperty SOURCE = BooleanProperty.create("source");

    /**
     * Position-based anti-feedback for signal reads through solid blocks.
     * When non-null, getSignal/getDirectSignal return 0 at the scanning
     * position, preventing feedback through solid block conductors.
     */
    static final ThreadLocal<BlockPos> shouldSignal = ThreadLocal.withInitial(() -> null);

    /**
     * Stores the position of the transmitter currently inside its tick()
     * or neighborChanged setBlock call. neighborChanged at THIS position
     * is blocked to prevent stale reads during state transitions.
     */
    private static final ThreadLocal<BlockPos> updatingPos = ThreadLocal.withInitial(() -> null);

    public MachineTransmitterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, false)
                .setValue(SOURCE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, SOURCE);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.TRANSMITTER;
    }

    @Override
    public int getRedstoneOutput(BlockState state) {
        // Rough indicator only — actual output uses getSignal which reads from BE.
        return (state.getValue(POWERED) && !state.getValue(SOURCE)) ? 1 : 0;
    }

    // ==================== Redstone Signal Output ====================

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * Only outputs redstone when in RECEIVER mode (POWERED && !SOURCE).
     * Returns the stored signal strength from the block entity (which is
     * source_strength - 1, simulating attenuation).
     */
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (pos.equals(shouldSignal.get())) return 0; // Suppress self-reads
        if (!state.getValue(POWERED)) return 0;
        if (state.getValue(SOURCE)) return 0;          // Sources don't output redstone

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineFrameBlockEntity machineBE) {
            return machineBE.getTransmitterSignalStrength();
        }
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (pos.equals(shouldSignal.get())) return 0;
        if (!state.getValue(POWERED)) return 0;
        if (state.getValue(SOURCE)) return 0;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineFrameBlockEntity machineBE) {
            return machineBE.getTransmitterSignalStrength();
        }
        return 0;
    }

    // ==================== Signal Detection ====================

    /**
     * Returns the strongest external redstone signal level (0-15)
     * reaching this transmitter, with self-read suppression.
     */
    private int getExternalSignalStrength(Level level, BlockPos pos) {
        shouldSignal.set(pos);
        try {
            return level.getBestNeighborSignal(pos);
        } finally {
            shouldSignal.set(null);
        }
    }

    /**
     * Returns the strongest wireless signal strength from any matching
     * SOURCE transmitter in range. Reads the source's stored signal
     * strength from its block entity. Returns 0 if no matching source.
     */
    private int getWirelessSignalStrength(Level level, BlockPos pos) {
        String myChannel = getChannel(level, pos);
        if (myChannel == null) return 0;

        int maxStrength = 0;
        int radius = getTransmitterRadius();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    mutable.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);

                    BlockState otherState = level.getBlockState(mutable);
                    if (!(otherState.getBlock() instanceof MachineTransmitterBlock)) continue;

                    // Check if the other transmitter is a SOURCE
                    if (otherState.getValue(POWERED) && otherState.getValue(SOURCE)) {
                        String otherChannel = getChannel(level, mutable.immutable());
                        if (myChannel.equals(otherChannel)) {
                            // Read the source's stored signal strength
                            BlockEntity be = level.getBlockEntity(mutable.immutable());
                            if (be instanceof MachineFrameBlockEntity machineBE) {
                                maxStrength = Math.max(maxStrength,
                                        machineBE.getTransmitterSignalStrength());
                            }
                        }
                    }
                }
            }
        }
        return maxStrength;
    }

    /**
     * Notifies all matching transmitters in range to re-evaluate their
     * power state via scheduled tick. Uses the channel of the block at pos.
     */
    private void notifyMatchingTransmitters(ServerLevel level, BlockPos pos) {
        String myChannel = getChannel(level, pos);
        if (myChannel == null) return;
        notifyMatchingTransmittersOnChannel(level, pos, myChannel);
    }

    /**
     * Notifies all transmitters on the specified channel in range.
     * Explicit-channel overload for use during channel changes, where
     * getChannel(pos) already returns the NEW channel but we need to
     * notify the OLD channel.
     */
    private void notifyMatchingTransmittersOnChannel(ServerLevel level, BlockPos pos, String channel) {
        int radius = getTransmitterRadius();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    mutable.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);

                    BlockState otherState = level.getBlockState(mutable);
                    if (!(otherState.getBlock() instanceof MachineTransmitterBlock)) continue;

                    String otherChannel = getChannel(level, mutable.immutable());
                    if (!channel.equals(otherChannel)) continue;

                    BlockPos immutable = mutable.immutable();
                    if (!level.getBlockTicks().willTickThisTick(immutable, otherState.getBlock())) {
                        level.scheduleTick(immutable, otherState.getBlock(), 2, TickPriority.NORMAL);
                    }
                }
            }
        }
    }

    /**
     * Immediately turns off all matching RECEIVER transmitters in range.
     * Uses the channel of the block at pos.
     */
    private void turnOffMatchingReceivers(ServerLevel level, BlockPos pos) {
        String myChannel = getChannel(level, pos);
        if (myChannel == null) return;
        turnOffReceiversOnChannel(level, pos, myChannel);
    }

    /**
     * Immediately turns off all RECEIVER transmitters on the specified channel.
     * Explicit-channel overload for use during channel changes.
     *
     * Called BEFORE the source notifies its own neighbors on power-off,
     * so that stale receiver output can't feed back through dust.
     *
     * Resets each receiver's stored signal strength to 0 and notifies
     * their neighbors so connected redstone updates correctly.
     */
    private void turnOffReceiversOnChannel(ServerLevel level, BlockPos pos, String channel) {
        int radius = getTransmitterRadius();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    mutable.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);

                    BlockState otherState = level.getBlockState(mutable);
                    if (!(otherState.getBlock() instanceof MachineTransmitterBlock)) continue;

                    // Only turn off receivers on same channel
                    if (!otherState.getValue(POWERED) || otherState.getValue(SOURCE)) continue;

                    String otherChannel = getChannel(level, mutable.immutable());
                    if (!channel.equals(otherChannel)) continue;

                    BlockPos immutable = mutable.immutable();

                    // Reset stored signal strength
                    BlockEntity be = level.getBlockEntity(immutable);
                    if (be instanceof MachineFrameBlockEntity machineBE) {
                        machineBE.setTransmitterSignalStrength(0);
                    }

                    level.setBlock(immutable, otherState
                            .setValue(POWERED, false), 3);

                    LOGGER.debug("[TRANSMITTER] Receiver at {} turned off (source at {} powering down)",
                            immutable, pos);

                    // Notify receiver's neighbors so dust de-powers
                    for (Direction dir : Direction.values()) {
                        level.updateNeighborsAt(immutable.relative(dir), otherState.getBlock());
                    }
                }
            }
        }
    }

    /**
     * Gets the wireless channel for a transmitter at the given position.
     * The channel is the registry name of the item in the spec slot.
     * Returns null if no spec item is present.
     */
    private String getChannel(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MachineFrameBlockEntity machineBE)) return null;
        ItemStack spec = machineBE.getSpecializationItem();
        if (spec.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(spec.getItem()).toString();
    }

    /**
     * Derives the wireless channel from an ItemStack directly, without
     * needing a block entity lookup. Used during spec item changes where
     * the old item is no longer in the slot.
     */
    private static String getChannelFromItem(ItemStack item) {
        if (item.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
    }

    /**
     * Returns the wireless transmission radius. Uses the Oddstone
     * sympathetic radius from config for consistency.
     */
    private int getTransmitterRadius() {
        return IdeomodConfig.getSympatheticRadius();
    }

    // ==================== neighborChanged ====================

    /**
     * Monitors external redstone changes.
     *
     * - OFF state: external signal appears → immediately become SOURCE,
     *   store signal strength (no tick delay, so even 1-tick pulses transmit).
     * - SOURCE state: external signal drops → schedule tick to re-evaluate.
     * - SOURCE state: external signal changes strength → update stored value
     *   and notify receivers.
     * - RECEIVER state: completely ignored — receivers don't read input.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block sourceBlock, BlockPos sourcePos, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (pos.equals(updatingPos.get())) return;

        boolean isPowered = state.getValue(POWERED);
        boolean isSource = state.getValue(SOURCE);

        // RECEIVER mode: ignore all external signal changes
        if (isPowered && !isSource) {
            FrameActivation.markDirty(level, pos);
            return;
        }

        int externalStrength = getExternalSignalStrength(level, pos);

        if (externalStrength > 0 && !isSource) {
            // External signal appeared on unpowered transmitter → become
            // SOURCE immediately. Store signal strength in BE.
            LOGGER.debug("[TRANSMITTER] {} at {} -> SOURCE (strength={}, immediate)",
                    getChannel(level, pos), pos, externalStrength);

            // Store the received signal strength
            if (level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be) {
                be.setTransmitterSignalStrength(externalStrength);
            }

            updatingPos.set(pos);
            try {
                level.setBlock(pos, state
                        .setValue(POWERED, true)
                        .setValue(SOURCE, true), 3);
            } finally {
                updatingPos.set(null);
            }

            // Notify matching transmitters to become receivers
            if (level instanceof ServerLevel serverLevel) {
                notifyMatchingTransmitters(serverLevel, pos);
            }
        } else if (externalStrength > 0 && isSource) {
            // Source still active but strength may have changed — update BE
            if (level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be) {
                int oldStrength = be.getTransmitterSignalStrength();
                if (oldStrength != externalStrength) {
                    be.setTransmitterSignalStrength(externalStrength);
                    LOGGER.debug("[TRANSMITTER] {} at {} strength updated {} -> {}",
                            getChannel(level, pos), pos, oldStrength, externalStrength);
                    // Notify receivers so they update their output strength
                    if (level instanceof ServerLevel serverLevel) {
                        notifyMatchingTransmitters(serverLevel, pos);
                    }
                }
            }
        } else if (externalStrength == 0 && isSource) {
            // External signal dropped on source → schedule tick to re-evaluate.
            if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                level.scheduleTick(pos, this, 2, TickPriority.HIGH);
            }
        }

        FrameActivation.markDirty(level, pos);
    }

    // ==================== tick ====================

    /**
     * Re-evaluates transmitter state. Called when:
     * - A source's external signal dropped (needs to check if should turn off)
     * - A receiver is notified by a source (needs to check wireless strength)
     *
     * On source power-off: turns off matching receivers BEFORE updating
     * own state, so stale receiver output can't feed back through dust.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean wasSource = state.getValue(SOURCE);
        boolean wasPowered = state.getValue(POWERED);

        // CRITICAL: Receivers must NEVER check external signal in tick().
        // They sit next to dust they're powering, so getBestNeighborSignal
        // would read their own output back, flip them to SOURCE (output=0),
        // and collapse the whole circuit. Receivers only re-evaluate wireless.
        int externalStrength = (wasPowered && !wasSource)
                ? 0
                : getExternalSignalStrength(level, pos);

        boolean newSource;
        boolean newPowered;
        int newOutputStrength;

        if (externalStrength > 0) {
            // SOURCE mode: receiving external input, broadcast wirelessly
            newSource = true;
            newPowered = true;
            newOutputStrength = externalStrength; // Store received strength

            // Update stored strength in BE
            if (level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be) {
                be.setTransmitterSignalStrength(externalStrength);
            }
        } else {
            // Check for wireless signal (only non-sources can be receivers)
            int wirelessStrength = getWirelessSignalStrength(level, pos);
            newSource = false;
            newPowered = wirelessStrength > 0;
            newOutputStrength = Math.max(0, wirelessStrength - 1); // Attenuate by 1

            // Store output strength in receiver's BE
            if (level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be) {
                be.setTransmitterSignalStrength(newOutputStrength);
            }
        }

        boolean stateChanged = (newPowered != wasPowered) || (newSource != wasSource);
        if (!stateChanged) {
            // State didn't change but strength might have — check if receiver
            // output needs updating (e.g., source changed its strength)
            if (newPowered && !newSource) {
                // Notify DIRECT neighbors first (dust touching us) since
                // there's no setBlock call to do it automatically.
                level.updateNeighborsAt(pos, this);
                // Then notify through solid blocks for strong-signal propagation
                for (Direction dir : Direction.values()) {
                    level.updateNeighborsAt(pos.relative(dir), this);
                }
            }
            return;
        }

        LOGGER.debug("[TRANSMITTER] {} at {} external={} -> POWERED={} SOURCE={} strength={}",
                getChannel(level, pos), pos, externalStrength, newPowered, newSource, newOutputStrength);

        // If a source is turning off, turn off all matching receivers FIRST
        // so their stale output doesn't feed back through dust.
        boolean sourceTurningOff = wasSource && !newSource;
        if (sourceTurningOff) {
            turnOffMatchingReceivers(level, pos);
        }

        updatingPos.set(pos);
        try {
            level.setBlock(pos, state
                    .setValue(POWERED, newPowered)
                    .setValue(SOURCE, newSource), 3);

            // Notify neighbors for redstone propagation
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }
        } finally {
            updatingPos.set(null);
        }

        // Notify matching transmitters if source status changed
        // (but NOT if we already turned off receivers above — they're handled)
        if (!sourceTurningOff && (newSource != wasSource)) {
            notifyMatchingTransmitters(level, pos);
        }
    }

    // ==================== Spec Item Change ====================

    /**
     * PRE-SWAP: Called BEFORE triggerSwapCheck when the spec item is about
     * to change. Cleans up the old channel — turns off receivers, notifies
     * peers, and resets to unpowered state.
     *
     * This fires for ALL item changes (same-block and cross-block). For
     * cross-block changes, the block will be replaced immediately after,
     * so this is the LAST chance to clean up the old channel.
     */
    @Override
    public void onSpecItemChanging(Level level, BlockPos pos, ItemStack oldItem) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockState state = level.getBlockState(pos);
        boolean wasPowered = state.getValue(POWERED);
        boolean wasSource = state.getValue(SOURCE);

        // Clean up old channel
        String oldChannel = getChannelFromItem(oldItem);
        if (oldChannel != null && wasPowered) {
            if (wasSource) {
                turnOffReceiversOnChannel(serverLevel, pos, oldChannel);
            }
            notifyMatchingTransmittersOnChannel(serverLevel, pos, oldChannel);
        }

        // Reset to unpowered state
        updatingPos.set(pos);
        try {
            level.setBlock(pos, state
                    .setValue(POWERED, false)
                    .setValue(SOURCE, false), 3);

            if (wasPowered) {
                for (Direction dir : Direction.values()) {
                    level.updateNeighborsAt(pos.relative(dir), this);
                }
            }
        } finally {
            updatingPos.set(null);
        }
    }

    /**
     * POST-SWAP: Called AFTER triggerSwapCheck when the spec item changed
     * but the block type stayed the same (oddstone variant swap).
     * Re-evaluates for the new channel.
     */
    @Override
    public void onSpecItemChanged(Level level, BlockPos pos, ItemStack newItem) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        reevaluateAfterChange(serverLevel, pos);
    }

    /**
     * Shared re-evaluation logic: checks external signal first (become source),
     * then wireless signal (become receiver). Used by both onSpecItemChanged
     * and onPlace to avoid code duplication.
     */
    private void reevaluateAfterChange(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // Check for external redstone signal → become SOURCE
        int externalStrength = getExternalSignalStrength(level, pos);
        if (externalStrength > 0) {
            if (level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be) {
                be.setTransmitterSignalStrength(externalStrength);
            }

            updatingPos.set(pos);
            try {
                level.setBlock(pos, state
                        .setValue(POWERED, true)
                        .setValue(SOURCE, true), 3);
            } finally {
                updatingPos.set(null);
            }

            notifyMatchingTransmitters(level, pos);
            LOGGER.debug("[TRANSMITTER] {} at {} -> SOURCE (strength={}) after re-evaluation",
                    getChannel(level, pos), pos, externalStrength);
            return;
        }

        // Check for wireless signal from existing source → become RECEIVER
        int wirelessStrength = getWirelessSignalStrength(level, pos);
        if (wirelessStrength > 0) {
            int outputStrength = Math.max(0, wirelessStrength - 1);
            if (level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be) {
                be.setTransmitterSignalStrength(outputStrength);
            }

            updatingPos.set(pos);
            try {
                level.setBlock(pos, state
                        .setValue(POWERED, true)
                        .setValue(SOURCE, false), 3);

                for (Direction dir : Direction.values()) {
                    level.updateNeighborsAt(pos.relative(dir), this);
                }
            } finally {
                updatingPos.set(null);
            }

            LOGGER.debug("[TRANSMITTER] {} at {} -> RECEIVER (strength={}) after re-evaluation",
                    getChannel(level, pos), pos, outputStrength);
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(oldState.getBlock()) && !movedByPiston) {
            if (!MachineSwapHelper.isSwapping()) {
                // Normal placement (from player/piston/world gen): spec item
                // is already in the BE (loaded from NBT). Evaluate now.
                if (level instanceof ServerLevel serverLevel) {
                    reevaluateAfterChange(serverLevel, pos);
                }
            }
            // During GUI swap: skip — the spec item hasn't been written to
            // the new BE yet. onSpecItemChanged will evaluate after
            // setItemDirect puts the item in.
            FrameActivation.markDirty(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && !movedByPiston) {
                if (state.getValue(POWERED)) {
                    // If we were a source, turn off receivers first
                    if (state.getValue(SOURCE) && level instanceof ServerLevel serverLevel) {
                        turnOffMatchingReceivers(serverLevel, pos);
                    }

                    // Notify our neighbors
                    for (Direction dir : Direction.values()) {
                        level.updateNeighborsAt(pos.relative(dir), this);
                    }

                    // Notify matching transmitters that we're gone
                    if (level instanceof ServerLevel serverLevel) {
                        notifyMatchingTransmitters(serverLevel, pos);
                    }
                }
                FrameActivation.markDirty(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                             @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity placer,
                             ItemStack stack) {
        // No-op — transmitter doesn't need placement specialization swap logic.
    }
}
