package com.ideomancer.ideomod.block.frame;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;

/**
 * Handles activation and deactivation of Frame Block chains.
 *
 * Works like redstone: any change triggers a full network wipe-and-rebuild.
 * Machine Blocks are signal sources, Frame Blocks are passive conductors.
 *
 * THE ALGORITHM:
 * ==============
 * When ANYTHING changes (machine or frame placed/broken/neighbor updated):
 *
 *   1. DISCOVER: From the change point, flood-fill through all connected
 *      frame blocks (any material, active or inactive). Finds the entire
 *      frame network connected to the event.
 *
 *   2. WIPE: Set every discovered frame to ACTIVE=false.
 *      Uses UPDATE_CLIENTS only (flag 2) — no neighborChanged cascade.
 *
 *   3. FIND SOURCES: For every frame in the network AND for the change
 *      position itself, check 6 neighbors for Machine Blocks.
 *
 *   4. RE-ACTIVATE: From each Machine Block, flood-fill activation through
 *      same-material frame chains, capped at MAX_ACTIVATION_RANGE.
 *
 * This is the "turn it off and on again" approach. Simple, correct,
 * handles every case: machine removed, frame broken mid-chain, frame
 * placed bridging two chains, etc. No special cases needed.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FrameActivation {

    /**
     * Maximum number of frame blocks a single activation flood-fill can reach
     * from one Machine Block. Matches the vanilla piston push limit.
     */
    public static final int MAX_ACTIVATION_RANGE = 12;

    // ========================================================================
    // THE ONE PUBLIC METHOD
    // ========================================================================

    /**
     * Called when anything changes that might affect frame activation.
     * Discovers the entire frame network, wipes it, finds Machine Block
     * sources, and rebuilds activation from each source.
     *
     * Safe to call from anywhere — Machine Block or Frame Block lifecycle.
     */
    public static void recalculate(Level level, BlockPos changePos) {
        if (level.isClientSide()) return;

        // Step 1: Discover the entire frame network connected to changePos
        Set<BlockPos> network = discoverNetwork(level, changePos);

        if (network.isEmpty()) return;

        // Step 2: Wipe all frames to inactive (silent — no neighbor cascade)
        for (BlockPos framePos : network) {
            BlockState state = level.getBlockState(framePos);
            if (isActive(state)) {
                level.setBlock(framePos, state.setValue(FrameBlock.ACTIVE, false),
                        Block.UPDATE_CLIENTS);
            }
        }

        // Step 3: Find all Machine Blocks adjacent to any frame in the network
        // or adjacent to the change position itself
        Set<BlockPos> machineSources = new HashSet<>();

        for (BlockPos framePos : network) {
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = framePos.relative(dir);
                BlockState neighborState = level.getBlockState(neighborPos);
                if (MachineFrameBlock.isMachineFamily(neighborState.getBlock())) {
                    machineSources.add(neighborPos);
                }
            }
        }

        // Also check direct neighbors of changePos
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = changePos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (MachineFrameBlock.isMachineFamily(neighborState.getBlock())) {
                machineSources.add(neighborPos);
            }
        }

        // Also check if changePos itself is a Machine Block
        BlockState changeState = level.getBlockState(changePos);
        if (MachineFrameBlock.isMachineFamily(changeState.getBlock())) {
            machineSources.add(changePos);
        }

        // Step 4: Re-activate from each Machine Block source
        for (BlockPos machinePos : machineSources) {
            activateFromMachine(level, machinePos);
        }

        Ideomod.LOGGER.debug("Frame recalculate at {}: network={}, sources={}",
                changePos, network.size(), machineSources.size());
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

    static boolean isActive(BlockState state) {
        if (state.getBlock() instanceof IFrameBlock) {
            return state.getValue(FrameBlock.ACTIVE);
        }
        return false;
    }

    // ========================================================================
    // STEP 1: DISCOVER NETWORK
    // ========================================================================

    /**
     * Flood-fill from changePos to find all connected frame blocks.
     * Crosses material boundaries. Traverses both active and inactive frames.
     * The changePos itself may or may not be a frame.
     */
    private static Set<BlockPos> discoverNetwork(Level level, BlockPos changePos) {
        Set<BlockPos> network = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        visited.add(changePos);

        // If changePos is itself a frame, include it
        BlockState changeState = level.getBlockState(changePos);
        if (getFrameMaterial(changeState) != null) {
            network.add(changePos);
            queue.add(changePos);
        }

        // Seed from changePos neighbors
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = changePos.relative(dir);
            if (visited.add(neighborPos)) {
                BlockState neighborState = level.getBlockState(neighborPos);
                if (getFrameMaterial(neighborState) != null) {
                    network.add(neighborPos);
                    queue.add(neighborPos);
                }
            }
        }

        // BFS through all connected frames
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = current.relative(dir);
                if (visited.add(neighborPos)) {
                    BlockState neighborState = level.getBlockState(neighborPos);
                    if (getFrameMaterial(neighborState) != null) {
                        network.add(neighborPos);
                        queue.add(neighborPos);
                    }
                }
            }
        }

        return network;
    }

    // ========================================================================
    // STEP 4: ACTIVATE FROM MACHINE
    // ========================================================================

    /**
     * From a Machine Block, flood-fill activation outward through each
     * adjacent material chain independently, capped at MAX_ACTIVATION_RANGE.
     */
    private static void activateFromMachine(Level level, BlockPos machinePos) {
        EnumSet<FrameMaterial> processed = EnumSet.noneOf(FrameMaterial.class);

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = machinePos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            FrameMaterial material = getFrameMaterial(neighborState);

            if (material != null && processed.add(material)) {
                activateChain(level, machinePos, material);
            }
        }
    }

    /**
     * Flood-fill activation through a same-material frame chain.
     * Starts from a Machine Block position (skips the machine itself).
     * Capped at MAX_ACTIVATION_RANGE.
     */
    private static void activateChain(Level level, BlockPos machinePos, FrameMaterial material) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(machinePos);

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = machinePos.relative(dir);
            if (visited.add(neighborPos)) {
                BlockState neighborState = level.getBlockState(neighborPos);
                if (getFrameMaterial(neighborState) == material) {
                    queue.add(neighborPos);
                }
            }
        }

        int activated = 0;

        while (!queue.isEmpty() && activated < MAX_ACTIVATION_RANGE) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);

            if (getFrameMaterial(currentState) != material) continue;

            if (!isActive(currentState)) {
                level.setBlock(current, currentState.setValue(FrameBlock.ACTIVE, true),
                        Block.UPDATE_CLIENTS);
                activated++;
            }

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = current.relative(dir);
                if (visited.add(neighborPos)) {
                    BlockState neighborState = level.getBlockState(neighborPos);
                    if (getFrameMaterial(neighborState) == material) {
                        queue.add(neighborPos);
                    }
                }
            }
        }
    }
}
