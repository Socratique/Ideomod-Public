package com.ideomancer.ideomod.block.frame;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;

/**
 * Handles activation and deactivation of Frame Block networks.
 *
 * Works like redstone dust: Machine Blocks are signal sources (like torches),
 * conductors carry and decay the signal, and the signal has a maximum range
 * of 15 blocks from source.
 *
 * ACTIVATION SIGNAL:
 * ==================
 * - Unpowered Machine Block emits activation strength 15
 * - Each conductor block decays the signal by 1
 * - A block is activated if it receives strength > 0
 * - Maximum range: 15 blocks from source
 *
 * ANTI-ACTIVATION SIGNAL:
 * =======================
 * - ONLY MachineFrameBlock can emit anti-signal. No other Machine Family
 *   member (Track, Trolley, future specializations) ever suppresses frames.
 * - A Machine Frame receiving redstone power becomes a SUPPRESSOR:
 *   - It stops emitting activation signal entirely
 *   - It emits an anti-activation signal with strength = redstone level
 *   - Anti-signal decays by 1 per conductor block, same as activation
 * - Any block reached by anti-signal (strength > 0) is DEACTIVATED,
 *   regardless of how strong the activation signal from other sources is.
 *   Anti-signal is a binary override, not subtraction.
 *
 * CONDUCTOR TYPES:
 * ================
 * 1. FRAME BLOCKS (IFrameBlock): ACTIVE blockstate property.
 *    Conduct for their getFrameMaterial() material.
 *
 * 2. FRAMED BLOCKS with INNER_CONDUCTOR: If the inner conductor is a
 *    different material than the frame, the block is a MEDIATOR — when
 *    activated, it passes ALL materials (universal bridge).
 *
 * 3. VANILLA METAL BLOCKS: No ACTIVE property. Activation tracked in
 *    a concurrent set of packed BlockPos longs. Each recalculate() only
 *    touches entries belonging to its own network (remove-then-add),
 *    preserving vanilla metals from other networks.
 *    Queryable via isVanillaMetalActivated(pos).
 *
 * THE ALGORITHM:
 * ==============
 * 1. DISCOVER: BFS from change point through all conductors.
 * 2. WIPE: Set all IFrameBlock to ACTIVE=false, remove this network's
 *    vanilla metals from the global set.
 * 3. FIND SOURCES: Find Machine Blocks adjacent to network.
 * 4. ACTIVATE: BFS from activation sources. Machine Frames only activate
 *    when unpowered; all other Machine Family blocks always activate.
 *    Strength 15 decaying by 1 per conductor.
 *    Each entry carries available materials + signal strength.
 *    Each block records the max strength received.
 * 5. SUPPRESS: BFS from powered Machine Frames ONLY, anti-strength =
 *    redstone level, decaying by 1. Any block reached by anti-signal
 *    is deactivated. Other Machine Family blocks never suppress.
 * 6. APPLY: Set ACTIVE=true only on blocks with activation > 0 and
 *    no anti-signal. Add activated vanilla metals back to the global set.
 *
 * BATCHING (Audit #14):
 * =====================
 * Instead of running recalculate() immediately on every block change,
 * callers use markDirty(level, pos) which queues the position. At the
 * end of each server tick, processDirtyNetworks() runs one recalculation
 * per connected dirty network. This batches all changes in a tick into
 * a single pass — placing 50 frame blocks triggers 1 recalculation
 * instead of 50.
 *
 * THREAD SAFETY (Audit #B, updated for Issue #15 fix):
 * ====================================================
 * activatedVanillaMetals uses a ConcurrentHashMap-backed key set.
 * recalculate() performs per-network remove-then-add: it removes only
 * the vanilla metal positions discovered in THIS network, then adds back
 * the ones that should be active. This prevents one network's recalculation
 * from wiping vanilla metals belonging to a different network — the root
 * cause of Issue #15 (first-push failure after frame network change).
 *
 * Concurrent reads from stickiness checks (e.g., during piston resolution)
 * see a consistent view because ConcurrentHashMap.newKeySet() provides
 * thread-safe individual add/remove/contains operations.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FrameActivation {

    /**
     * Maximum activation signal strength. Matches redstone's max of 15.
     */
    public static final int MAX_SIGNAL_STRENGTH = 15;

    // ========================================================================
    // DIRTY NETWORK BATCHING (Audit #14)
    // ========================================================================

    /**
     * Dirty positions queued for recalculation, keyed by dimension.
     * Multiple positions in the same network will be discovered and merged
     * during processDirtyNetworks(). ConcurrentHashMap for safe access
     * from potential parallel forge event dispatch.
     */
    private static final Map<ServerLevel, Set<BlockPos>> dirtyPositions = new ConcurrentHashMap<>();

    /**
     * Marks a position as needing frame activation recalculation.
     * The actual recalculation is deferred to the end of the server tick,
     * batching all changes in a single tick into one pass per network.
     *
     * This is the primary entry point for all block lifecycle hooks
     * (onPlace, onRemove, neighborChanged) that previously called
     * recalculate() directly.
     */
    public static void markDirty(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        dirtyPositions.computeIfAbsent(serverLevel, k -> ConcurrentHashMap.newKeySet())
                      .add(pos.immutable());
    }

    /**
     * Processes all dirty positions, running one recalculation per connected
     * network. Called at the end of each server tick.
     *
     * Algorithm:
     * 1. Drain the dirty set for each dimension
     * 2. For each dirty position, discover its network (BFS)
     * 3. Remove all positions in that network from the remaining dirty set
     *    (they'll be covered by this single recalculation)
     * 4. Run recalculate() once for the network
     */
    public static void processDirtyNetworks() {
        for (Map.Entry<ServerLevel, Set<BlockPos>> entry : dirtyPositions.entrySet()) {
            ServerLevel level = entry.getKey();
            Set<BlockPos> dirty = entry.getValue();

            if (dirty.isEmpty()) continue;

            // Drain the set — take a snapshot and clear
            Set<BlockPos> pending = new HashSet<>(dirty);
            dirty.clear();

            // Process each dirty position, deduplicating by network
            while (!pending.isEmpty()) {
                BlockPos changePos = pending.iterator().next();
                pending.remove(changePos);

                // Discover the network from this position
                Set<BlockPos> network = discoverNetwork(level, changePos);

                // Remove all network positions from pending (they share a network)
                pending.removeAll(network);

                // Also remove direct neighbors of the change point (they triggered
                // from the same event and the network covers them)
                for (Direction dir : Direction.values()) {
                    pending.remove(changePos.relative(dir));
                }

                // Run one recalculation for this entire network
                recalculate(level, changePos);
            }
        }
    }

    /**
     * Immediately flushes any pending dirty positions for the given level.
     * Call this before any code that reads activation state and cannot
     * tolerate stale data (e.g., piston structure resolution).
     *
     * This preserves the performance benefit of batching for normal gameplay
     * (construction, redstone updates) while ensuring correctness for
     * time-critical reads like piston moves.
     */
    public static void flushIfDirty(Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        Set<BlockPos> dirty = dirtyPositions.get(serverLevel);
        if (dirty == null || dirty.isEmpty()) return;

        // Drain and process
        Set<BlockPos> pending = new HashSet<>(dirty);
        dirty.clear();

        while (!pending.isEmpty()) {
            BlockPos changePos = pending.iterator().next();
            pending.remove(changePos);

            Set<BlockPos> network = discoverNetwork(serverLevel, changePos);
            pending.removeAll(network);
            for (Direction dir : Direction.values()) {
                pending.remove(changePos.relative(dir));
            }

            recalculate(serverLevel, changePos);
        }
    }

    /**
     * Forge event handler for end-of-tick processing.
     * Register an instance of this on MinecraftForge.EVENT_BUS.
     */
    public static class TickHandler {
        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                processDirtyNetworks();
            }
        }
    }

    // ========================================================================
    // VANILLA METAL ACTIVATION TRACKING
    // ========================================================================

    /**
     * Ephemeral set of activated vanilla metal positions (packed longs).
     * Updated per-network during recalculate(): each call removes only the
     * vanilla metals belonging to its discovered network, then adds back the
     * ones that should be active. This preserves metals from other networks.
     *
     * THREAD SAFETY: ConcurrentHashMap.newKeySet() provides thread-safe
     * individual add/remove/contains. Concurrent reads from stickiness
     * checks always see a consistent per-element view.
     *
     * HISTORY: Previously used volatile atomic-swap of an immutable set,
     * which was safe for single-network worlds but caused Issue #15 —
     * when multiple networks were recalculated in one flush cycle, each
     * recalculate() replaced the ENTIRE global set with only its own
     * network's metals, wiping metals from previously-processed networks.
     * (Issue #15 fix, 2026-03-15)
     */
    private static final Set<Long> activatedVanillaMetals = ConcurrentHashMap.newKeySet();

    /**
     * Query whether a vanilla metal block at the given position is
     * currently activated (connected to a machine source, not suppressed).
     */
    public static boolean isVanillaMetalActivated(BlockPos pos) {
        return activatedVanillaMetals.contains(pos.asLong());
    }

    /**
     * Check if a block at a position is an activated conductor of any kind.
     * Covers IFrameBlocks (via ACTIVE property) and vanilla metals (via set).
     */
    public static boolean isActivatedConductor(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof IFrameBlock) {
            return isActive(state);
        }
        if (FrameMaterial.fromVanillaBlock(state.getBlock()) != null) {
            return isVanillaMetalActivated(pos);
        }
        return false;
    }

    // ========================================================================
    // RECALCULATE (internal — called by processDirtyNetworks)
    // ========================================================================

    /**
     * Full wipe-and-rebuild with signal strength propagation.
     * Called internally by processDirtyNetworks(). External callers should
     * use markDirty() to batch changes.
     *
     * Vanilla metal tracking uses per-network remove-then-add:
     * 1. WIPE removes only vanilla metals found in THIS network
     * 2. APPLY adds back only the ones that should be active
     * This preserves vanilla metals belonging to other networks.
     */
    static void recalculate(Level level, BlockPos changePos) {
        if (level.isClientSide()) return;

        // Step 1: Discover the entire conductor network
        Set<BlockPos> network = discoverNetwork(level, changePos);
        if (network.isEmpty()) return;

        // Step 2: Wipe all activation states
        // -- IFrameBlocks: set ACTIVE=false
        // -- Vanilla metals: remove only THIS network's entries from global set
        for (BlockPos pos : network) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof IFrameBlock && isActive(state)) {
                level.setBlock(pos, state.setValue(FrameBlock.ACTIVE, false),
                        Block.UPDATE_CLIENTS);
            }
            // Remove this network's vanilla metals from global set
            if (FrameMaterial.fromVanillaBlock(state.getBlock()) != null) {
                activatedVanillaMetals.remove(pos.asLong());
            }
        }

        // Step 3: Find all Machine Blocks adjacent to any network block
        Set<BlockPos> machineSources = new HashSet<>();
        for (BlockPos pos : network) {
            findAdjacentMachines(level, pos, machineSources);
        }
        findAdjacentMachines(level, changePos, machineSources);

        BlockState changeState = level.getBlockState(changePos);
        if (MachineFrameBlock.isMachineFamily(changeState.getBlock())) {
            machineSources.add(changePos);
        }

        if (machineSources.isEmpty()) {
            // No sources — vanilla metals for this network already removed in step 2
            Ideomod.LOGGER.debug("Frame recalculate at {}: network={}, no sources",
                    changePos, network.size());
            return;
        }

        // Step 4: Propagate activation signal from machines
        Map<Long, Integer> activationStrength = new HashMap<>();

        for (BlockPos machinePos : machineSources) {
            BlockState machineState = level.getBlockState(machinePos);
            boolean isMachineFrame = machineState.getBlock() instanceof MachineFrameBlock;

            if (isMachineFrame) {
                int redstonePower = level.getBestNeighborSignal(machinePos);
                if (redstonePower == 0) {
                    propagateSignal(level, machinePos, MAX_SIGNAL_STRENGTH, activationStrength);
                }
            } else {
                propagateSignal(level, machinePos, MAX_SIGNAL_STRENGTH, activationStrength);
            }
        }

        // Step 5: Propagate anti-activation signal from powered Machine Frames ONLY
        Map<Long, Integer> antiStrength = new HashMap<>();

        for (BlockPos machinePos : machineSources) {
            BlockState machineState = level.getBlockState(machinePos);
            if (!(machineState.getBlock() instanceof MachineFrameBlock)) continue;

            int redstonePower = level.getBestNeighborSignal(machinePos);
            if (redstonePower > 0) {
                propagateSignal(level, machinePos, redstonePower, antiStrength);
            }
        }

        // Step 6: Apply — activate blocks that have signal and no anti-signal
        for (BlockPos pos : network) {
            long packed = pos.asLong();
            int activation = activationStrength.getOrDefault(packed, 0);
            int anti = antiStrength.getOrDefault(packed, 0);

            boolean shouldActivate = activation > 0 && anti == 0;

            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof IFrameBlock) {
                if (shouldActivate && !isActive(state)) {
                    level.setBlock(pos, state.setValue(FrameBlock.ACTIVE, true),
                            Block.UPDATE_CLIENTS);
                }
            } else if (FrameMaterial.fromVanillaBlock(state.getBlock()) != null) {
                if (shouldActivate) {
                    // Add back to global set — only this network's activated metals
                    activatedVanillaMetals.add(packed);
                }
                // If not shouldActivate, the entry was already removed in step 2
            }
        }

        Ideomod.LOGGER.debug("Frame recalculate at {}: network={}, sources={}, powered={}",
                changePos, network.size(), machineSources.size(),
                machineSources.stream().filter(p -> level.getBestNeighborSignal(p) > 0).count());
    }

    // ========================================================================
    // SIGNAL PROPAGATION
    // ========================================================================

    /**
     * BFS queue entry: position, available materials, and signal strength.
     */
    private record SignalEntry(BlockPos pos, EnumSet<FrameMaterial> availableMaterials, int strength) {}

    /**
     * Propagate a signal (activation or anti-activation) from a machine
     * block through the conductor network. Each conductor decays the
     * signal by 1. Records the maximum strength at each position.
     *
     * Material rules apply: a conductor only accepts signal if its
     * conductive materials overlap with the available materials.
     * Mediators pass ALL materials when reached.
     *
     * @param level         The level
     * @param machinePos    The machine block position (source)
     * @param startStrength The initial signal strength (1-15)
     * @param strengthMap   Output: packed pos -> max strength received
     */
    private static void propagateSignal(Level level, BlockPos machinePos,
                                          int startStrength,
                                          Map<Long, Integer> strengthMap) {
        if (startStrength <= 0) return;

        Queue<SignalEntry> queue = new ArrayDeque<>();
        Map<Long, Integer> bestQueued = new HashMap<>();

        EnumSet<FrameMaterial> allMaterials = EnumSet.allOf(FrameMaterial.class);

        // Seed: all conductor neighbors of the machine
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = machinePos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (isConductor(neighborState)) {
                long packed = neighborPos.asLong();
                int newStrength = startStrength;
                if (!bestQueued.containsKey(packed) || bestQueued.get(packed) < newStrength) {
                    bestQueued.put(packed, newStrength);
                    queue.add(new SignalEntry(neighborPos, allMaterials, newStrength));
                }
            }
        }

        while (!queue.isEmpty()) {
            SignalEntry entry = queue.poll();
            BlockPos current = entry.pos();
            EnumSet<FrameMaterial> available = entry.availableMaterials();
            int strength = entry.strength();

            if (strength <= 0) continue;

            long packed = current.asLong();

            int recorded = strengthMap.getOrDefault(packed, 0);
            if (recorded >= strength) continue;

            BlockState currentState = level.getBlockState(current);
            if (!isConductor(currentState)) continue;

            // Check material compatibility
            EnumSet<FrameMaterial> blockMaterials = getConductiveMaterials(currentState);
            EnumSet<FrameMaterial> accepted = EnumSet.copyOf(blockMaterials);
            accepted.retainAll(available);
            if (accepted.isEmpty()) continue;

            // Record this strength
            strengthMap.put(packed, strength);

            // Determine outgoing materials
            EnumSet<FrameMaterial> outgoing;
            if (isMediator(currentState)) {
                outgoing = EnumSet.allOf(FrameMaterial.class);
            } else {
                outgoing = blockMaterials;
            }

            // Decay and continue
            int nextStrength = strength - 1;
            if (nextStrength <= 0) continue;

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = current.relative(dir);
                long neighborPacked = neighborPos.asLong();

                if (!bestQueued.containsKey(neighborPacked) || bestQueued.get(neighborPacked) < nextStrength) {
                    BlockState neighborState = level.getBlockState(neighborPos);
                    if (isConductor(neighborState)) {
                        bestQueued.put(neighborPacked, nextStrength);
                        queue.add(new SignalEntry(neighborPos, outgoing, nextStrength));
                    }
                }
            }
        }
    }

    // ========================================================================
    // TYPE CHECK HELPERS
    // ========================================================================

    public static boolean isFrameFamily(Block block) {
        return block instanceof IFrameBlock;
    }

    public static FrameMaterial getFrameMaterial(BlockState state) {
        if (state.getBlock() instanceof IFrameBlock frameBlock) {
            return frameBlock.getFrameMaterial();
        }
        return null;
    }

    public static boolean isActive(BlockState state) {
        if (state.getBlock() instanceof IFrameBlock) {
            return state.getValue(FrameBlock.ACTIVE);
        }
        return false;
    }

    /**
     * Checks if a block is any kind of conductor in the activation network.
     */
    private static boolean isConductor(BlockState state) {
        if (state.getBlock() instanceof IFrameBlock) return true;
        return FrameMaterial.fromVanillaBlock(state.getBlock()) != null;
    }

    /**
     * Checks if a block is a MEDIATOR — a framed block whose inner conductor
     * is a DIFFERENT material than its frame. When activated, mediators
     * become universal conductors that pass ALL materials.
     */
    private static boolean isMediator(BlockState state) {
        if (!(state.getBlock() instanceof FramedBlock)) return false;
        int innerConductor = state.getValue(FramedBlock.INNER_CONDUCTOR);
        if (innerConductor == 0) return false;
        FrameMaterial innerMat = FrameMaterial.fromConductorValue(innerConductor);
        if (innerMat == null) return false;
        FrameMaterial frameMat = ((IFrameBlock) state.getBlock()).getFrameMaterial();
        return innerMat != frameMat;
    }

    /**
     * Returns all materials a conductor block handles.
     */
    private static EnumSet<FrameMaterial> getConductiveMaterials(BlockState state) {
        EnumSet<FrameMaterial> materials = EnumSet.noneOf(FrameMaterial.class);

        if (state.getBlock() instanceof IFrameBlock frameBlock) {
            materials.add(frameBlock.getFrameMaterial());

            if (state.getBlock() instanceof FramedBlock) {
                FrameMaterial innerMat = FrameMaterial.fromConductorValue(
                    state.getValue(FramedBlock.INNER_CONDUCTOR));
                if (innerMat != null) {
                    materials.add(innerMat);
                }
            }
        }

        FrameMaterial vanillaMat = FrameMaterial.fromVanillaBlock(state.getBlock());
        if (vanillaMat != null) {
            materials.add(vanillaMat);
        }

        return materials;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static void findAdjacentMachines(Level level, BlockPos pos, Set<BlockPos> machineSources) {
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (MachineFrameBlock.isMachineFamily(neighborState.getBlock())) {
                machineSources.add(neighborPos);
            }
        }
    }

    // ========================================================================
    // STEP 1: DISCOVER NETWORK
    // ========================================================================

    /**
     * Flood-fill from changePos to find all connected conductor blocks.
     * Crosses material boundaries — discovery doesn't care about materials.
     */
    private static Set<BlockPos> discoverNetwork(Level level, BlockPos changePos) {
        Set<BlockPos> network = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        visited.add(changePos);

        BlockState changeState = level.getBlockState(changePos);
        if (isConductor(changeState)) {
            network.add(changePos);
            queue.add(changePos);
        }

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = changePos.relative(dir);
            if (visited.add(neighborPos)) {
                BlockState neighborState = level.getBlockState(neighborPos);
                if (isConductor(neighborState)) {
                    network.add(neighborPos);
                    queue.add(neighborPos);
                }
            }
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = current.relative(dir);
                if (visited.add(neighborPos)) {
                    BlockState neighborState = level.getBlockState(neighborPos);
                    if (isConductor(neighborState)) {
                        network.add(neighborPos);
                        queue.add(neighborPos);
                    }
                }
            }
        }

        return network;
    }
}
