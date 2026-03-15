package com.ideomancer.ideomod.block.movement;

import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.ideomancer.ideomod.block.frame.FrameMaterial;
import com.ideomancer.ideomod.block.frame.IFrameBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.LinkedList;
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

    private static final int MAX_PUSH_BLOCKS = 12;

    // ========================================================================
    // ODDSTONE DETECTION
    // ========================================================================

    /**
     * Returns true if the block at pos is:
     *   - A direct OddstoneBlock
     *   - A FramedBlock whose inner block is OddstoneBlock
     */
    public static boolean isOddstoneOrFramedOddstone(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof OddstoneBlock) return true;

        if (block instanceof FramedBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FramedBlockEntity fbe) {
                ResourceLocation innerBlockId = fbe.getInnerBlockId();
                if (innerBlockId != null) {
                    Block innerBlock = ForgeRegistries.BLOCKS.getValue(innerBlockId);
                    return innerBlock instanceof OddstoneBlock;
                }
            }
        }

        return false;
    }

    // ========================================================================
    // SYMPATHETIC SCAN
    // ========================================================================

    /**
     * Scans for all Oddstone/Framed Oddstone blocks reachable from startPositions
     * via sympathetic links (line-of-sight at range, following first hit only).
     *
     * Uses a BFS frontier queue: when a new oddstone is found, only its
     * neighbors are checked next, not the entire accumulated set. This
     * converts the algorithm from O(n²) to O(n) where n is the number of
     * oddstones in the chain. (Audit item #15)
     */
    public static Set<BlockPos> scanSympathetic(Level level, Set<BlockPos> startPositions, int radius) {
        Set<BlockPos> found = new HashSet<>(startPositions);

        if (radius <= 1) return found;

        // Seed the frontier with oddstone blocks from the start set
        java.util.Queue<BlockPos> frontier = new java.util.ArrayDeque<>();
        for (BlockPos pos : startPositions) {
            if (isOddstoneOrFramedOddstone(level, pos)) {
                frontier.add(pos);
            }
        }

        while (!frontier.isEmpty()) {
            BlockPos oddPos = frontier.poll();

            for (Direction face : Direction.values()) {
                for (int dist = 2; dist <= radius; dist++) {
                    BlockPos checkPos = oddPos.relative(face, dist);
                    BlockState checkState = level.getBlockState(checkPos);

                    if (isOddstoneOrFramedOddstone(level, checkPos)) {
                        if (found.add(checkPos)) {
                            // Newly discovered — add to frontier for transitive scan
                            frontier.add(checkPos);
                        }
                        break;
                    }

                    if (!checkState.isAir()) break;
                }
            }
        }

        return found;
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

                // Vanilla metal conductor check:
                // Adjacent block in group is a vanilla metal
                FrameMaterial adjacentMetal = FrameMaterial.fromVanillaBlock(adjacentState.getBlock());
                if (adjacentMetal != null && isVanillaMetalConnection(adjacentMetal, state, adjacentPos, pos)) {
                    stuckToGroup = true;
                    break;
                }

                // Reverse: this block is a vanilla metal
                FrameMaterial thisMetal = FrameMaterial.fromVanillaBlock(state.getBlock());
                if (thisMetal != null && isVanillaMetalConnection(thisMetal, adjacentState, pos, adjacentPos)) {
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

            // Expand BFS if this block can pull neighbors
            boolean shouldExpand = state.isStickyBlock()
                    || FrameMaterial.fromVanillaBlock(state.getBlock()) != null;

            if (shouldExpand) {
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

    /**
     * Checks if a vanilla metal block should connect to the given other block.
     * Only connects when the vanilla metal is ACTIVATED.
     */
    private static boolean isVanillaMetalConnection(FrameMaterial metalMat, BlockState otherState,
                                                      BlockPos metalPos, BlockPos otherPos) {
        if (!FrameActivation.isVanillaMetalActivated(metalPos)) return false;

        if (otherState.getBlock() instanceof IFrameBlock otherFrame) {
            if (!IFrameBlock.isActive(otherState)) return false;
            if (otherFrame.getFrameMaterial() == metalMat) return true;
            if (otherState.getBlock() instanceof FramedBlock) {
                FrameMaterial innerMat = FrameMaterial.fromConductorValue(
                    otherState.getValue(FramedBlock.INNER_CONDUCTOR));
                if (innerMat == metalMat) return true;
            }
            return false;
        }
        if (MachineFrameBlock.isMachineFamily(otherState.getBlock())) return true;
        FrameMaterial otherMetal = FrameMaterial.fromVanillaBlock(otherState.getBlock());
        if (otherMetal == metalMat) {
            return FrameActivation.isVanillaMetalActivated(otherPos);
        }
        return false;
    }
}
