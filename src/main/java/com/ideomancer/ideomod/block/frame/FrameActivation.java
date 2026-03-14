package com.ideomancer.ideomod.block.frame;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
 *    ephemeral Set<Long> rebuilt every recalculate(). Conduct
 *    for their material only. Queryable via isVanillaMetalActivated(pos).
 *
 * THE ALGORITHM:
 * ==============
 * 1. DISCOVER: BFS from change point through all conductors.
 * 2. WIPE: Set all IFrameBlock to ACTIVE=false, clear vanilla metal map.
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
 *    no anti-signal.
 *
 * THREAD SAFETY (Audit #B):
 * =========================
 * activatedVanillaMetals uses volatile reference swapping rather than
 * clear-and-rebuild on a shared mutable set. During recalculate(), a new
 * set is built locally and then atomically swapped in. Readers always see
 * a consistent snapshot. This prevents stale reads during concurrent
 * stickiness checks on different code paths (e.g., chunk loading).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FrameActivation {

    /**
     * Maximum activation signal strength. Matches redstone's max of 15.
     */
    public static final int MAX_SIGNAL_STRENGTH = 15;

    // ========================================================================
    // VANILLA METAL ACTIVATION TRACKING
    // ========================================================================

    /**
     * Ephemeral set of activated vanilla metal positions (packed longs).
     * Rebuilt every recalculate(). Vanilla blocks have no ACTIVE property,
     * so we track them here.
     *
     * THREAD SAFETY: This is a volatile reference that gets atomically swapped
     * to a new set during recalculate(). Readers see a consistent snapshot.
     * Previously was a plain HashSet that was cleared and rebuilt in place,
     * risking stale/partial reads from concurrent stickiness checks.
     * (Audit item #B, fixed 2026-03-13)
     */
    private static volatile Set<Long> activatedVanillaMetals = Collections.emptySet();

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
    // THE ONE PUBLIC METHOD
    // ========================================================================

    /**
     * Called when anything changes that might affect frame activation.
     * Full wipe-and-rebuild with signal strength propagation.
     */
    public static void recalculate(Level level, BlockPos changePos) {
        if (level.isClientSide()) return;

        // Step 1: Discover the entire conductor network
        Set<BlockPos> network = discoverNetwork(level, changePos);
        if (network.isEmpty()) return;

        // Step 2: Wipe all activation states
        // Build a local set of vanilla metal positions to remove, then swap atomically
        Set<Long> currentVanillaMetals = activatedVanillaMetals;
        for (BlockPos pos : network) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof IFrameBlock && isActive(state)) {
                level.setBlock(pos, state.setValue(FrameBlock.ACTIVE, false),
                        Block.UPDATE_CLIENTS);
            }
            // Vanilla metals: we'll build a fresh set below, no need to remove individually
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
            // No sources found — ensure vanilla metals are cleared
            activatedVanillaMetals = Collections.emptySet();
            Ideomod.LOGGER.debug("Frame recalculate at {}: network={}, no sources",
                    changePos, network.size());
            return;
        }

        // Step 4: Propagate activation signal from machines
        // Each position maps to its maximum received signal strength.
        //
        // SUPPRESSION RULE: Only MachineFrameBlock can suppress frame networks.
        // All other Machine Family blocks (Track, Trolley, future specializations)
        // ALWAYS emit activation regardless of their redstone state. This prevents
        // Track redstone conduction, Clock pulses, Repeater signals, etc. from
        // accidentally deactivating frame networks they're adjacent to.
        Map<Long, Integer> activationStrength = new HashMap<>();

        for (BlockPos machinePos : machineSources) {
            BlockState machineState = level.getBlockState(machinePos);
            boolean isMachineFrame = machineState.getBlock() instanceof MachineFrameBlock;

            if (isMachineFrame) {
                // Machine Frame: only emits activation when NOT receiving redstone
                int redstonePower = level.getBestNeighborSignal(machinePos);
                if (redstonePower == 0) {
                    propagateSignal(level, machinePos, MAX_SIGNAL_STRENGTH, activationStrength);
                }
            } else {
                // All other Machine Family blocks: always emit activation
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
                // Powered Machine Frame: emit anti-signal at redstone strength
                propagateSignal(level, machinePos, redstonePower, antiStrength);
            }
        }

        // Step 6: Apply — activate blocks that have signal and no anti-signal
        // Build new vanilla metal set locally, then swap atomically
        Set<Long> newVanillaMetals = new HashSet<>();

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
                // Already wiped to false in step 2, so no else needed
            } else if (FrameMaterial.fromVanillaBlock(state.getBlock()) != null) {
                if (shouldActivate) {
                    newVanillaMetals.add(packed);
                }
            }
        }

        // Atomic swap: readers now see the complete new set
        activatedVanillaMetals = Collections.unmodifiableSet(newVanillaMetals);

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
        // Track the best strength we've queued for each position to avoid
        // re-processing with weaker signals
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

            // Skip if we've already processed this position with equal or
            // better strength (from a different source or path)
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

                // Only queue if this would be a stronger signal than what
                // we've already queued for this neighbor
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
