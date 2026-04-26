package com.ideomancer.ideomod.block.movement;

import com.ideomancer.ideomod.block.ISympathetic;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.config.IdeomodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Shared utility for movement-related block scanning algorithms.
 *
 * Consolidates three scanning patterns that are duplicated across:
 * - PistonBaseBlockMixin (server-side piston data saving)
 * - PistonBaseBlockClientMixin (client-side frame capture)
 * - PistonStructureResolverMixin (resolver sympathetic search)
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MovementScanHelper {

    /** Base push limit — reads from config. Kept as method for dynamic access. */
    public static int getBasePushLimit() {
        return IdeomodConfig.getBasePushLimit();
    }

    // ========================================================================
    // SYMPATHETIC CHANNEL DETECTION
    // ========================================================================

    /**
     * Returns the sympathetic channel of the block at pos, or null if
     * the block does not participate in sympathetic movement.
     *
     * Checks:
     *   - ISympathetic blocks (OddstoneBlock, CobbledOddstoneBlock,
     *     OddstoneBricksBlock, SympatheticWallBlock, etc.)
     *   - FramedBlock whose inner block implements ISympathetic
     */
    public static String getSympatheticChannel(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof ISympathetic sympathetic) {
            return sympathetic.getSympatheticChannel();
        }

        if (block instanceof FramedBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FramedBlockEntity fbe) {
                ResourceLocation innerBlockId = fbe.getInnerBlockId();
                if (innerBlockId != null) {
                    Block innerBlock = ForgeRegistries.BLOCKS.getValue(innerBlockId);
                    if (innerBlock instanceof ISympathetic sympathetic) {
                        return sympathetic.getSympatheticChannel();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Legacy compatibility: Returns true if the block at pos participates
     * in ANY sympathetic channel. Replaces the old oddstone-only check.
     */
    public static boolean isOddstoneOrFramedOddstone(Level level, BlockPos pos) {
        return getSympatheticChannel(level, pos) != null;
    }

    // ========================================================================
    // SYMPATHETIC SCAN (CHANNEL-AWARE)
    // ========================================================================

    /**
     * Scans for all sympathetic blocks of a specific channel reachable from
     * startPositions via sympathetic links (line-of-sight at range, first hit only).
     *
     * Uses a BFS frontier queue: when a new block on the channel is found,
     * only its neighbors are checked next, not the entire accumulated set.
     * O(n) where n is the number of sympathetic blocks in the chain.
     *
     * @param level          the world
     * @param startPositions seed positions (only those matching channel are used)
     * @param radius         max scan distance per direction
     * @param channel        the sympathetic channel to scan for
     * @return all found positions (including seeds that match the channel)
     */
    public static Set<BlockPos> scanSympatheticChannel(Level level, Set<BlockPos> startPositions,
                                                         int radius, String channel) {
        Set<BlockPos> found = new HashSet<>();

        if (radius < 1) return found;

        // Seed the frontier with blocks matching the requested channel
        java.util.Queue<BlockPos> frontier = new java.util.ArrayDeque<>();
        for (BlockPos pos : startPositions) {
            String posChannel = getSympatheticChannel(level, pos);
            if (channel.equals(posChannel)) {
                found.add(pos);
                frontier.add(pos);
            }
        }

        while (!frontier.isEmpty()) {
            BlockPos sourcePos = frontier.poll();

            for (Direction face : Direction.values()) {
                for (int dist = 1; dist <= radius; dist++) {
                    BlockPos checkPos = sourcePos.relative(face, dist);

                    String checkChannel = getSympatheticChannel(level, checkPos);
                    if (channel.equals(checkChannel)) {
                        if (found.add(checkPos)) {
                            // Newly discovered — add to frontier for transitive scan
                            frontier.add(checkPos);
                        }
                        // Found the first block on this channel in this direction — stop
                        break;
                    }
                    // Non-matching blocks (solid or air) do NOT block the scan.
                    // Sympathetic links work through any intervening blocks.
                }
            }
        }

        return found;
    }

    /**
     * Scans ALL sympathetic channels present in the start positions.
     * Returns a map of channel -> discovered positions (including seeds).
     *
     * Used by the PistonStructureResolverMixin to perform per-channel
     * sympathetic scanning in a single pass over toPush.
     */
    public static Map<String, Set<BlockPos>> scanAllChannels(Level level, Set<BlockPos> startPositions, int radius) {
        // Group start positions by channel
        Map<String, Set<BlockPos>> channelSeeds = new HashMap<>();
        for (BlockPos pos : startPositions) {
            String channel = getSympatheticChannel(level, pos);
            if (channel != null) {
                channelSeeds.computeIfAbsent(channel, k -> new HashSet<>()).add(pos);
            }
        }

        // Scan each channel independently
        Map<String, Set<BlockPos>> results = new HashMap<>();
        for (Map.Entry<String, Set<BlockPos>> entry : channelSeeds.entrySet()) {
            String channel = entry.getKey();
            Set<BlockPos> seeds = entry.getValue();
            Set<BlockPos> found = scanSympatheticChannel(level, seeds, radius, channel);
            // Only include if new positions were discovered beyond the seeds
            results.put(channel, found);
        }

        return results;
    }

    /**
     * Legacy compatibility: scans for all sympathetic blocks (any channel)
     * reachable from startPositions. Used by old callers that don't need
     * channel isolation. Delegates to scanAllChannels and merges results.
     */
    public static Set<BlockPos> scanSympathetic(Level level, Set<BlockPos> startPositions, int radius) {
        Map<String, Set<BlockPos>> channelResults = scanAllChannels(level, startPositions, radius);
        Set<BlockPos> merged = new HashSet<>(startPositions);
        for (Set<BlockPos> channelPositions : channelResults.values()) {
            merged.addAll(channelPositions);
        }
        return merged;
    }

    // ========================================================================
    // STICKY GROUP SCAN
    // ========================================================================

    /**
     * Breadth-first scan of blocks connected to startPos via stickiness.
     *
     * Returns the complete set of positions connected via mutual stickiness,
     * including the start position. A block is included if it's stuck to
     * at least one block already in the group (bidirectional canStickTo),
     * OR if it's connected via vanilla metal conductor chains.
     *
     * Limited to maxSize (vanilla piston limit).
     *
     * IMPORTANT: Positions that fail the stickiness check are NOT marked
     * as permanently visited. They may be reachable via a different group
     * member that hasn't been processed yet. For example, in this layout:
     *
     *     Frame — Frame — Frame
     *       |       |       |
     *     Track — Trolley — Track
     *
     * The Trolley fails the stickiness check when tested from Track
     * (different MachineTypes), but succeeds when tested from the Frame
     * above it (Frame sticks to all Machine Family). If we marked Trolley
     * as visited on the Track failure, the Frame path would never recheck
     * it and the Trolley's block entity data would be lost during retraction.
     *
     * FIX 2026-03-09: Changed visited tracking so only air blocks and
     * blocks successfully added to the group are marked visited. Failed
     * stickiness checks leave the position eligible for re-evaluation
     * from other group members.
     */
    public static Set<BlockPos> scanStickyGroup(Level level, BlockPos startPos, int maxSize) {
        Set<BlockPos> group = new HashSet<>();
        // Tracks positions we've definitively resolved (in group, or air/unreachable)
        Set<BlockPos> settled = new HashSet<>();
        LinkedList<BlockPos> queue = new LinkedList<>();

        group.add(startPos);
        settled.add(startPos);

        // Seed with neighbors of the start block
        for (Direction dir : Direction.values()) {
            queue.add(startPos.relative(dir));
        }

        int count = 0;

        while (!queue.isEmpty() && count < maxSize) {
            BlockPos pos = queue.poll();

            // Skip if already in group (settled as member)
            if (group.contains(pos)) continue;

            BlockState state = level.getBlockState(pos);

            // Air is permanently settled — never revisit
            if (state.isAir()) {
                settled.add(pos);
                continue;
            }

            // Check if this block is stuck to any block already in the group
            boolean stuckToGroup = false;
            for (Direction dir : Direction.values()) {
                BlockPos adjacentPos = pos.relative(dir);
                if (!group.contains(adjacentPos)) continue;

                BlockState adjacentState = level.getBlockState(adjacentPos);

                // Standard stickiness check (both directions)
                if (adjacentState.isStickyBlock()
                        && adjacentState.canStickTo(state)
                        && state.canStickTo(adjacentState)) {
                    stuckToGroup = true;
                    break;
                }
            }

            if (!stuckToGroup) {
                // DON'T mark as settled — this position might be reachable
                // via a different group member that hasn't been processed yet.
                // It will be re-queued when that member expands.
                continue;
            }

            group.add(pos);
            settled.add(pos);
            count++;

            if (state.isStickyBlock()) {
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = pos.relative(dir);
                    // Only queue if not already settled (in group or air)
                    if (!settled.contains(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        return group;
    }
}
