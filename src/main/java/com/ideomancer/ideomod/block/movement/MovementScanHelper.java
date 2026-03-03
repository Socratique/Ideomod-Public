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
     *
     * Consolidated from three copies in PistonBaseBlockMixin,
     * PistonBaseBlockClientMixin, and PistonStructureResolverMixin.
     */
    public static boolean isOddstoneOrFramedOddstone(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // Direct Oddstone
        if (block instanceof OddstoneBlock) return true;

        // Framed Oddstone -- check the block entity for inner block type
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
     * Iterates while new blocks are found, building transitive chains:
     * oddstone A sees oddstone B at range, then B sees C, and so on.
     *
     * Returns the complete set of oddstone/framed-oddstone positions found,
     * including the starting positions.
     *
     * Consolidated from PistonBaseBlockMixin.scanSympathetic and
     * PistonBaseBlockClientMixin.ideomod$captureSympathetic.
     */
    public static Set<BlockPos> scanSympathetic(Level level, Set<BlockPos> startPositions, int radius) {
        Set<BlockPos> found = new HashSet<>(startPositions);

        if (radius <= 1) return found;

        java.util.List<BlockPos> snapshot = new java.util.ArrayList<>(found);
        boolean foundNew = true;

        // Iterate until no new blocks found (chain sympathetic links)
        while (foundNew) {
            foundNew = false;
            java.util.List<BlockPos> newFound = new java.util.ArrayList<>();

            for (BlockPos oddPos : snapshot) {
                if (!isOddstoneOrFramedOddstone(level, oddPos)) continue;

                for (Direction face : Direction.values()) {
                    for (int dist = 2; dist <= radius; dist++) {
                        BlockPos checkPos = oddPos.relative(face, dist);
                        BlockState checkState = level.getBlockState(checkPos);

                        if (isOddstoneOrFramedOddstone(level, checkPos)) {
                            if (!found.contains(checkPos)) {
                                found.add(checkPos);
                                newFound.add(checkPos);
                                foundNew = true;
                            }
                            break; // Stop scanning further — first hit only
                        }

                        // If non-air non-oddstone block in the way, stop
                        if (!checkState.isAir()) break;
                    }
                }
            }

            // Add newly found positions for next iteration
            snapshot = newFound;
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
     * at least one block already in the group (with both blocks passing
     * canStickTo checks in both directions), OR if it's connected via
     * vanilla metal conductor chains (iron/copper blocks bridging frame
     * family members).
     *
     * Limited to maxSize (vanilla piston limit).
     *
     * Consolidated from PistonBaseBlockMixin.scanStickyGroup and
     * PistonBaseBlockClientMixin.ideomod$scanStickyGroupInto.
     */
    public static Set<BlockPos> scanStickyGroup(Level level, BlockPos startPos, int maxSize) {
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> group = new HashSet<>();
        LinkedList<BlockPos> queue = new LinkedList<>();

        visited.add(startPos);
        group.add(startPos);

        // Seed with neighbors of the start block
        for (Direction dir : Direction.values()) {
            queue.add(startPos.relative(dir));
        }

        int count = 0;

        while (!queue.isEmpty() && count < maxSize) {
            BlockPos pos = queue.poll();
            if (visited.contains(pos)) continue;
            visited.add(pos);

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

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
                // If the adjacent block in group is a vanilla metal, check if
                // this block should stick to it (frame family connection)
                FrameMaterial adjacentMetal = FrameMaterial.fromVanillaBlock(adjacentState.getBlock());
                if (adjacentMetal != null && isVanillaMetalConnection(adjacentMetal, state, adjacentPos, pos)) {
                    stuckToGroup = true;
                    break;
                }

                // Reverse: if THIS block is a vanilla metal, check if the
                // adjacent block in group is something it should stick to
                FrameMaterial thisMetal = FrameMaterial.fromVanillaBlock(state.getBlock());
                if (thisMetal != null && isVanillaMetalConnection(thisMetal, adjacentState, pos, adjacentPos)) {
                    stuckToGroup = true;
                    break;
                }
            }

            if (!stuckToGroup) continue;
            group.add(pos);
            count++;

            // Expand BFS if this block can pull neighbors:
            // - Normal sticky blocks
            // - Vanilla metal conductors (they bridge frame families)
            boolean shouldExpand = state.isStickyBlock()
                    || FrameMaterial.fromVanillaBlock(state.getBlock()) != null;

            if (shouldExpand) {
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = pos.relative(dir);
                    if (!visited.contains(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        return group;
    }

    /**
     * Checks if a vanilla metal block of the given material should connect
     * to the given other block for sticky group scanning purposes.
     * Only connects when the vanilla metal is ACTIVATED (part of a frame
     * network connected to a machine source).
     *
     * A vanilla iron block connects to:
     * - Active iron frames/framed blocks (same material)
     * - Active framed blocks with INNER_CONDUCTOR matching (cross-frame mediation)
     * - Machine Family blocks
     * - Other activated vanilla iron blocks
     */
    private static boolean isVanillaMetalConnection(FrameMaterial metalMat, BlockState otherState,
                                                      BlockPos metalPos, BlockPos otherPos) {
        // Vanilla metal must be activated to form connections
        if (!FrameActivation.isVanillaMetalActivated(metalPos)) return false;

        // Same-material active IFrameBlock
        if (otherState.getBlock() instanceof IFrameBlock otherFrame) {
            if (!IFrameBlock.isActive(otherState)) return false;
            if (otherFrame.getFrameMaterial() == metalMat) return true;
            // Cross-frame mediation
            if (otherState.getBlock() instanceof FramedBlock) {
                FrameMaterial innerMat = FrameMaterial.fromConductorValue(
                    otherState.getValue(FramedBlock.INNER_CONDUCTOR));
                if (innerMat == metalMat) return true;
            }
            return false;
        }
        // Machine Family
        if (MachineFrameBlock.isMachineFamily(otherState.getBlock())) return true;
        // Same-material activated vanilla metal
        FrameMaterial otherMetal = FrameMaterial.fromVanillaBlock(otherState.getBlock());
        if (otherMetal == metalMat) {
            return FrameActivation.isVanillaMetalActivated(otherPos);
        }
        return false;
    }
}
