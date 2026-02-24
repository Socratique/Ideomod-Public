package com.ideomancer.ideomod.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Stateless utility class that executes scan algorithms.
 *
 * Given a BlockPos, a StructureDefinition, and a Level, the scanner examines
 * blocks in the world and returns a StructureResult describing what it found.
 *
 * The scanner dispatches to the correct algorithm based on the definition's
 * scan mode. Currently only PATTERN is implemented (for the furnace array).
 * PROXIMITY and PROXIMITY_WALLED are stubs for future structures.
 */
public class StructureScanner {

    /**
     * Main entry point. Dispatches to the correct scan algorithm based on
     * the definition's scan mode.
     *
     * @param corePos    The position that triggered the scan (a core block)
     * @param definition The structure definition describing what to look for
     * @param level      The world
     * @return A StructureResult — check isValid() to see if a structure was found
     */
    public static StructureResult scan(BlockPos corePos, StructureDefinition definition, Level level) {
        return switch (definition.getScanMode()) {
            case PATTERN -> scanPattern(corePos, definition, level);
            case PROXIMITY -> throw new UnsupportedOperationException(
                    "PROXIMITY scan not yet implemented — needed for enchanting table");
            case PROXIMITY_WALLED -> throw new UnsupportedOperationException(
                    "PROXIMITY_WALLED scan not yet implemented — needed for brewery");
        };
    }

    // ========================================================================
    // PATTERN SCAN — rigid 3-layer frame with dynamic cluster sizing
    // Used by: Furnace Array
    // ========================================================================

    /**
     * Pattern scan algorithm for furnace-array-style structures.
     *
     * Phase 1: Flood-fill to find the furnace cluster (same type, same facing)
     * Phase 2: Calculate expected frame positions from cluster dimensions
     * Phase 3: Validate all frame positions against the frame tag
     * Phase 4: Build and return the result
     */
    private static StructureResult scanPattern(BlockPos corePos, StructureDefinition definition, Level level) {
        String defName = definition.getName();
        BlockState coreState = level.getBlockState(corePos);
        ResourceLocation coreBlockId = BuiltInRegistries.BLOCK.getKey(coreState.getBlock());

        // Verify the core block is actually a valid core for this definition
        if (!definition.isCoreBlock(coreBlockId)) {
            return StructureResult.invalid(defName, corePos);
        }

        // Get the furnace facing direction — this determines the structure's orientation
        Direction facing;
        try {
            facing = coreState.getValue(HorizontalDirectionalBlock.FACING);
        } catch (IllegalArgumentException e) {
            // Block doesn't have a FACING property — not a valid core
            return StructureResult.invalid(defName, corePos);
        }

        // ====================================================================
        // Phase 1: Find the cluster via flood-fill
        // ====================================================================

        // Determine the two axes we search in (perpendicular to the facing direction)
        // Height is always Y. Width is the horizontal axis perpendicular to facing.
        // Depth (the facing axis) is NOT searched — the cluster is always 1 block thick.
        Direction widthPositive = getWidthDirection(facing);
        Direction widthNegative = widthPositive.getOpposite();
        Direction heightPositive = Direction.UP;
        Direction heightNegative = Direction.DOWN;

        // Flood-fill from the core, following same-type same-facing blocks
        Set<BlockPos> clusterSet = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(corePos);
        clusterSet.add(corePos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            // Check all 4 directions in the cluster plane (width+, width-, up, down)
            Direction[] searchDirs = { widthPositive, widthNegative, heightPositive, heightNegative };
            for (Direction dir : searchDirs) {
                BlockPos neighbor = current.relative(dir);
                if (clusterSet.contains(neighbor)) continue;

                BlockState neighborState = level.getBlockState(neighbor);
                ResourceLocation neighborId = BuiltInRegistries.BLOCK.getKey(neighborState.getBlock());

                // Must be same block type
                if (!neighborId.equals(coreBlockId)) continue;

                // Must have same facing direction
                try {
                    Direction neighborFacing = neighborState.getValue(HorizontalDirectionalBlock.FACING);
                    if (neighborFacing != facing) continue;
                } catch (IllegalArgumentException e) {
                    continue;
                }

                clusterSet.add(neighbor);
                queue.add(neighbor);
            }
        }

        // ====================================================================
        // Determine cluster bounding rectangle and validate it
        // ====================================================================

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : clusterSet) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        BlockPos clusterMin = new BlockPos(minX, minY, minZ);
        BlockPos clusterMax = new BlockPos(maxX, maxY, maxZ);

        // Calculate cluster dimensions in logical coordinates (width × height)
        int clusterWidth = getWidthSpan(clusterMin, clusterMax, facing);
        int clusterHeight = (maxY - minY) + 1;

        // Size check
        if (clusterWidth > definition.getMaxClusterWidth()
                || clusterHeight > definition.getMaxClusterHeight()) {
            return StructureResult.invalid(defName, corePos);
        }

        // Rectangular fill check: every position in the bounding rect must be a cluster block.
        // The cluster is 1 block thick on the depth axis, so we only check the 2D rectangle.
        int depthCoord = getDepthCoord(corePos, facing);
        int expectedCount = clusterWidth * clusterHeight;
        if (clusterSet.size() != expectedCount) {
            return StructureResult.invalid(defName, corePos);
        }

        // Verify every position in the bounding rect is actually in the cluster
        // (catches L-shapes or holes that happen to have the right count by coincidence)
        for (BlockPos pos : generateClusterPositions(clusterMin, clusterMax, facing)) {
            if (!clusterSet.contains(pos)) {
                return StructureResult.invalid(defName, corePos);
            }
        }

        // ====================================================================
        // Phase 2: Calculate expected frame positions
        // ====================================================================

        // The frame wraps the cluster with 1 block of padding on all 6 sides.
        // Structure is always exactly 3 layers deep:
        //   Layer 0 (back):   solid frame rectangle
        //   Layer 1 (middle): frame ring + furnaces inside
        //   Layer 2 (front):  frame ring + air openings aligned with furnaces

        // Calculate frame bounds in world coordinates
        // Back layer is 1 block behind the cluster (away from facing direction)
        // Front layer is 1 block in front (in the facing direction)
        Direction depthForward = facing;       // toward the front (air openings)
        Direction depthBack = facing.getOpposite(); // toward the back wall

        // Frame extends 1 block beyond cluster on width and height axes
        BlockPos frameCornerA = clusterMin.relative(widthNegative).relative(Direction.DOWN)
                .relative(depthBack);
        BlockPos frameCornerB = clusterMax.relative(widthPositive).relative(Direction.UP)
                .relative(depthForward);

        // Normalize min/max — directional offsets can produce inverted coordinates
        // when facing NORTH (-Z) or WEST (-X). Without this, the bounds check
        // in StructureRegistration.isInExpandedBounds() silently fails (min > max
        // means no position ever passes the >= min && <= max test).
        BlockPos frameMin = new BlockPos(
                Math.min(frameCornerA.getX(), frameCornerB.getX()),
                Math.min(frameCornerA.getY(), frameCornerB.getY()),
                Math.min(frameCornerA.getZ(), frameCornerB.getZ())
        );
        BlockPos frameMax = new BlockPos(
                Math.max(frameCornerA.getX(), frameCornerB.getX()),
                Math.max(frameCornerA.getY(), frameCornerB.getY()),
                Math.max(frameCornerA.getZ(), frameCornerB.getZ())
        );

        // ====================================================================
        // Phase 3: Validate all positions in the 3-layer structure
        // ====================================================================

        TagKey<Block> frameTag = definition.getFrameTag();
        if (frameTag == null) {
            return StructureResult.invalid(defName, corePos);
        }

        StructureResult.Builder resultBuilder = new StructureResult.Builder(defName, clusterMin)
                .setFacing(facing)
                .setFurnaceType(coreBlockId)
                .setClusterBounds(clusterMin, clusterMax)
                .setStructureBounds(frameMin, frameMax);

        // Add all cluster furnaces as effectors
        for (BlockPos pos : clusterSet) {
            resultBuilder.addEffector(pos.immutable());
        }

        // Walk all 3 layers and validate each position
        int frameWidthSize = clusterWidth + 2;
        int frameHeightSize = clusterHeight + 2;

        // We need to iterate positions layer by layer.
        // Layer offsets from the cluster along the depth axis:
        //   Back layer:   cluster depth - 1 step backward
        //   Middle layer: cluster depth (same as furnaces)
        //   Front layer:  cluster depth + 1 step forward

        // The "origin" for iteration is the min corner of the frame on width/height axes
        BlockPos widthHeightOrigin = clusterMin.relative(widthNegative).relative(Direction.DOWN);

        for (int layer = 0; layer < 3; layer++) {
            // Calculate the depth position for this layer
            BlockPos layerOrigin;
            if (layer == 0) {
                // Back layer: 1 block behind cluster
                layerOrigin = offsetInDepth(widthHeightOrigin, facing, -1);
            } else if (layer == 1) {
                // Middle layer: same depth as cluster
                layerOrigin = widthHeightOrigin;
            } else {
                // Front layer: 1 block in front of cluster
                layerOrigin = offsetInDepth(widthHeightOrigin, facing, 1);
            }

            for (int w = 0; w < frameWidthSize; w++) {
                for (int h = 0; h < frameHeightSize; h++) {
                    BlockPos pos = layerOrigin
                            .relative(widthPositive, w)
                            .relative(Direction.UP, h);

                    // Is this position inside the cluster rectangle (in width/height)?
                    boolean isInterior = w >= 1 && w < frameWidthSize - 1
                            && h >= 1 && h < frameHeightSize - 1;

                    if (layer == 0) {
                        // Back layer: everything must be frame
                        if (!isFrameBlock(level, pos, frameTag)) {
                            return StructureResult.invalid(defName, corePos);
                        }
                        resultBuilder.addConnector(pos.immutable());

                    } else if (layer == 1) {
                        // Middle layer: ring is frame, interior is furnaces (already validated)
                        if (isInterior) {
                            // This is a furnace position — already in the cluster set.
                            // Skip; it's been added as an effector above.
                        } else {
                            // Frame ring position
                            if (!isFrameBlock(level, pos, frameTag)) {
                                return StructureResult.invalid(defName, corePos);
                            }
                            resultBuilder.addConnector(pos.immutable());
                        }

                    } else {
                        // Front layer: ring is frame, interior must be air
                        if (isInterior) {
                            // Air opening aligned with furnace
                            if (!level.getBlockState(pos).isAir()) {
                                return StructureResult.invalid(defName, corePos);
                            }
                            resultBuilder.addAir(pos.immutable());
                        } else {
                            // Frame ring position
                            if (!isFrameBlock(level, pos, frameTag)) {
                                return StructureResult.invalid(defName, corePos);
                            }
                            resultBuilder.addConnector(pos.immutable());
                        }
                    }
                }
            }
        }

        // ====================================================================
        // Phase 4: Build and return the valid result
        // ====================================================================

        resultBuilder.setValid(true);
        return resultBuilder.build();
    }

    // ========================================================================
    // Axis mapping helpers
    // ========================================================================

    /**
     * Given a furnace facing direction, returns the positive direction along
     * the "width" axis (the horizontal axis perpendicular to facing).
     *
     * This mapping ensures consistent left-to-right iteration regardless
     * of which way the structure faces.
     *
     *   Facing NORTH → width runs along X → positive is EAST
     *   Facing SOUTH → width runs along X → positive is EAST
     *   Facing EAST  → width runs along Z → positive is SOUTH
     *   Facing WEST  → width runs along Z → positive is SOUTH
     */
    private static Direction getWidthDirection(Direction facing) {
        return switch (facing) {
            case NORTH, SOUTH -> Direction.EAST;
            case EAST, WEST -> Direction.SOUTH;
            default -> Direction.EAST; // Should never happen for horizontal facings
        };
    }

    /**
     * Calculates the width span of a bounding box in logical width units.
     * Width is the horizontal axis perpendicular to the facing direction.
     */
    private static int getWidthSpan(BlockPos min, BlockPos max, Direction facing) {
        return switch (facing) {
            case NORTH, SOUTH -> (max.getX() - min.getX()) + 1;
            case EAST, WEST -> (max.getZ() - min.getZ()) + 1;
            default -> 1;
        };
    }

    /**
     * Gets the coordinate along the depth axis for a given position.
     * Depth is the axis the furnace faces along.
     */
    private static int getDepthCoord(BlockPos pos, Direction facing) {
        return switch (facing) {
            case NORTH, SOUTH -> pos.getZ();
            case EAST, WEST -> pos.getX();
            default -> pos.getZ();
        };
    }

    /**
     * Offsets a position along the depth axis by the given number of steps.
     * Positive offset = toward the facing direction (front).
     * Negative offset = away from facing direction (back).
     */
    private static BlockPos offsetInDepth(BlockPos pos, Direction facing, int steps) {
        return pos.relative(facing, steps);
    }

    /**
     * Generates all BlockPos positions within the cluster bounding rectangle.
     * The cluster is always 1 block thick on the depth axis (only width × height).
     */
    private static Iterable<BlockPos> generateClusterPositions(
            BlockPos clusterMin, BlockPos clusterMax, Direction facing) {

        Set<BlockPos> positions = new HashSet<>();
        for (int x = clusterMin.getX(); x <= clusterMax.getX(); x++) {
            for (int y = clusterMin.getY(); y <= clusterMax.getY(); y++) {
                for (int z = clusterMin.getZ(); z <= clusterMax.getZ(); z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    /**
     * Checks if the block at the given position is a valid frame material
     * (present in the frame block tag).
     */
    private static boolean isFrameBlock(Level level, BlockPos pos, TagKey<Block> frameTag) {
        return level.getBlockState(pos).is(frameTag);
    }
}
