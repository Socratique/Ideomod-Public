package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.registry.ModTags;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Shared utility methods for the Oddstone Portal system.
 * 
 * Used by OddstoneBlock, OddstonePortalBlock, and OddstonePortalFrameDetector
 * to avoid duplicating the same logic in multiple places.
 * 
 * Contains:
 * - Corner block validation (the 14 mineral block types that form portal addresses)
 * - Portal interior navigation (finding bottom-left of a portal's interior)
 * - Frame corner derivation (from bottom-left interior to the frame's BL corner)
 * - Portal clearing (removing all portal blocks in an interior)
 * - Horizontal direction lookup from portal axis
 */
public class PortalUtils {

    // ========================================================================
    // CORNER BLOCK VALIDATION
    // ========================================================================

    /**
     * Checks if a block state is a valid portal corner block.
     * 
     * Corner blocks are crafted mineral/metal blocks that sit at
     * the four corners of an Oddstone portal frame. The combination
     * of corner types defines the portal's "address."
     * 
     * 14 types = 14^4 = 38,416 unique addresses per dimension.
     * 
     * Called by:
     * - OddstonePortalFrameDetector (frame validation)
     * - OddstonePortalBlock (updateShape cascade check)
     * - OddstoneBlock (corner-broken detection in neighborChanged)
     */
    public static boolean isCornerBlock(BlockState state) {
        return state.is(ModTags.PORTAL_CORNER_BLOCKS);
    }

    /**
     * Convenience overload: checks corner block by position.
     * Looks up the block state from the level first.
     */
    public static boolean isCornerBlock(Level level, BlockPos pos) {
        return isCornerBlock(level.getBlockState(pos));
    }

    // ========================================================================
    // PORTAL INTERIOR NAVIGATION
    // ========================================================================

    /**
     * Starting from any portal block in an interior, navigates to the
     * bottom-left portal block by scanning down then scanning in the
     * negative horizontal direction.
     * 
     * "Bottom-left" means the portal block at the lowest Y and the
     * most negative horizontal position (south-most for X-axis portals,
     * east-most... no: the hDir.getOpposite() direction).
     * 
     * Visual example (portal interior, X-axis portal, hDir = SOUTH):
     * 
     *   [P] [P] [P]    ← top row
     *   [P] [*] [P]    ← * = starting portalBlock
     *   [B] [P] [P]    ← B = bottom-left (returned)
     * 
     * @param level      The world
     * @param portalBlock Any portal block inside the portal
     * @param hDir       The horizontal direction of the portal
     *                   (SOUTH for X-axis, EAST for Z-axis)
     * @return The bottom-left portal block position
     */
    public static BlockPos findBottomLeft(Level level, BlockPos portalBlock, Direction hDir) {
        // Scan down
        BlockPos bottom = portalBlock;
        while (level.getBlockState(bottom.below()).is(ModBlocks.ODDSTONE_PORTAL.get())) {
            bottom = bottom.below();
        }

        // Scan in the negative horizontal direction
        BlockPos bottomLeft = bottom;
        while (level.getBlockState(bottomLeft.relative(hDir.getOpposite()))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            bottomLeft = bottomLeft.relative(hDir.getOpposite());
        }

        return bottomLeft;
    }

    /**
     * Derives the frame corner position (bottom-left corner of the FRAME,
     * not the interior) from the bottom-left interior portal block.
     * 
     * The frame corner is one block below and one block in the negative
     * horizontal direction from the bottom-left interior block.
     * 
     * This is the position used as the portal's registration key in
     * OddstonePortalSavedData.
     * 
     *   [Corner] [Oddstone] [Oddstone] [Corner]   ← frame top
     *   [Oddst]  [Portal]   [Portal]   [Oddst]
     *   [Oddst]  [Portal]   [Portal]   [Oddst]
     *   [Corner] [Oddstone] [Oddstone] [Corner]   ← frame bottom
     *    ^ frameCorner       ^ bottomLeft interior is one up+right from here
     */
    public static BlockPos getFrameCorner(BlockPos bottomLeft, Direction hDir) {
        return bottomLeft.below().relative(hDir.getOpposite());
    }

    // ========================================================================
    // PORTAL CLEARING
    // ========================================================================

    /**
     * Clears all portal blocks in a portal interior, replacing them with air.
     * Scans from the bottom-left to determine width and height, then removes
     * all portal blocks in that rectangle.
     * 
     * @param level      The world (must be server-side)
     * @param bottomLeft Bottom-left portal block of the interior
     * @param hDir       Horizontal direction (SOUTH for X-axis, EAST for Z-axis)
     */
    public static void clearPortalBlocks(Level level, BlockPos bottomLeft, Direction hDir) {
        // Measure width
        int width = 0;
        while (level.getBlockState(bottomLeft.relative(hDir, width))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            width++;
        }

        // Measure height
        int height = 0;
        while (level.getBlockState(bottomLeft.above(height))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            height++;
        }

        // Clear all portal blocks in the rectangle
        for (int h = 0; h < width; h++) {
            for (int v = 0; v < height; v++) {
                BlockPos portalPos = bottomLeft.relative(hDir, h).above(v);
                if (level.getBlockState(portalPos).is(ModBlocks.ODDSTONE_PORTAL.get())) {
                    level.setBlock(portalPos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    // ========================================================================
    // AXIS HELPERS
    // ========================================================================

    /**
     * Returns the horizontal direction for a given portal axis.
     * 
     * Portal axis is the direction the portal is THIN along:
     * - X-axis portal: thin along X, extends along Z → hDir = SOUTH
     * - Z-axis portal: thin along Z, extends along X → hDir = EAST
     */
    public static Direction getHorizontalDir(Direction.Axis portalAxis) {
        return (portalAxis == Direction.Axis.X) ? Direction.SOUTH : Direction.EAST;
    }
}
