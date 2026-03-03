package com.ideomancer.ideomod.block.portal;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects valid Oddstone portal frames and reads their corner addresses.
 *
 * Scans from interior air blocks outward (nether portal style).
 *
 * Frame validation checks structural integrity only:
 * - Oddstone blocks form the frame edges
 * - Corner blocks are valid mineral blocks
 * - Interior is clear (air or existing portal blocks)
 * - Dimensions within min/max bounds
 *
 * No redstone power checks — portals are activated by ender pearl,
 * not by redstone signal.
 *
 * Corner blocks are crafted mineral blocks (iron_block, diamond_block, etc.)
 * 14 types = 14^4 = 38,416 unique addresses per dimension.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class OddstonePortalFrameDetector {

    private static final int MIN_INTERIOR = 3;
    private static final int MAX_INTERIOR = 19;

    public static class FrameResult {
        public final String address;
        public final List<BlockPos> interiorPositions;
        public final List<BlockPos> framePositions;
        /** All Oddstone positions in the frame (excludes corners) */
        public final List<BlockPos> oddstonePositions;
        public final BlockPos[] corners; // TL, TR, BR, BL
        public final Direction.Axis axis;

        public FrameResult(String address, List<BlockPos> interiorPositions,
                           List<BlockPos> framePositions, List<BlockPos> oddstonePositions,
                           BlockPos[] corners, Direction.Axis axis) {
            this.address = address;
            this.interiorPositions = interiorPositions;
            this.framePositions = framePositions;
            this.oddstonePositions = oddstonePositions;
            this.corners = corners;
            this.axis = axis;
        }
    }

    @Nullable
    public static FrameResult detectFrame(Level level, BlockPos oddstonePos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = oddstonePos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            if (!neighborState.isAir() && !neighborState.is(ModBlocks.ODDSTONE_PORTAL.get())) {
                continue;
            }

            FrameResult result = tryFromInterior(level, neighborPos, Direction.Axis.X);
            if (result != null) return result;

            result = tryFromInterior(level, neighborPos, Direction.Axis.Z);
            if (result != null) return result;
        }

        return null;
    }

    @Nullable
    private static FrameResult tryFromInterior(Level level, BlockPos interiorPos, Direction.Axis portalAxis) {
        Direction hDir = PortalUtils.getHorizontalDir(portalAxis);
        Direction hDirNeg = hDir.getOpposite();

        int leftDist = scanForFrame(level, interiorPos, hDirNeg);
        if (leftDist < 0) return null;

        int rightDist = scanForFrame(level, interiorPos, hDir);
        if (rightDist < 0) return null;

        int downDist = scanForFrame(level, interiorPos, Direction.DOWN);
        if (downDist < 0) return null;

        int upDist = scanForFrame(level, interiorPos, Direction.UP);
        if (upDist < 0) return null;

        int interiorWidth = leftDist + rightDist - 1;
        int interiorHeight = downDist + upDist - 1;

        if (interiorWidth < MIN_INTERIOR || interiorWidth > MAX_INTERIOR) return null;
        if (interiorHeight < MIN_INTERIOR || interiorHeight > MAX_INTERIOR) return null;

        BlockPos interiorBL = interiorPos.relative(hDirNeg, leftDist - 1).relative(Direction.DOWN, downDist - 1);
        BlockPos frameBL = interiorBL.relative(hDirNeg).relative(Direction.DOWN);
        BlockPos frameBR = frameBL.relative(hDir, interiorWidth + 1);
        BlockPos frameTL = frameBL.relative(Direction.UP, interiorHeight + 1);
        BlockPos frameTR = frameTL.relative(hDir, interiorWidth + 1);

        int outerWidth = interiorWidth + 2;
        int outerHeight = interiorHeight + 2;

        // Validate frame structure AND collect oddstone positions
        List<BlockPos> oddstonePositions = new ArrayList<>();
        if (!validateFrame(level, frameBL, outerWidth, outerHeight, hDir, oddstonePositions)) {
            return null;
        }

        // Validate interior
        List<BlockPos> interiorPositions = new ArrayList<>();
        for (int h = 0; h < interiorWidth; h++) {
            for (int v = 0; v < interiorHeight; v++) {
                BlockPos pos = interiorBL.relative(hDir, h).relative(Direction.UP, v);
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && !state.is(ModBlocks.ODDSTONE_PORTAL.get())) {
                    return null;
                }
                interiorPositions.add(pos);
            }
        }

        // Check corners are valid corner blocks
        if (!isCornerBlock(level, frameBL) || !isCornerBlock(level, frameBR) ||
            !isCornerBlock(level, frameTL) || !isCornerBlock(level, frameTR)) {
            return null;
        }

        // No redstone power check — portals are activated by ender pearl

        String address = readAddress(level, frameTL, frameTR, frameBR, frameBL);

        // Collect all frame positions (oddstone + corners)
        List<BlockPos> framePositions = new ArrayList<>(oddstonePositions);
        framePositions.add(frameBL);
        framePositions.add(frameBR);
        framePositions.add(frameTL);
        framePositions.add(frameTR);

        BlockPos[] corners = new BlockPos[]{frameTL, frameTR, frameBR, frameBL};

        Ideomod.LOGGER.debug("Valid portal frame detected: {}x{} outer, axis={}, address=[{}]",
                outerWidth, outerHeight, portalAxis, address);

        return new FrameResult(address, interiorPositions, framePositions, oddstonePositions, corners, portalAxis);
    }

    private static int scanForFrame(Level level, BlockPos start, Direction dir) {
        for (int i = 1; i <= MAX_INTERIOR; i++) {
            BlockPos check = start.relative(dir, i);
            BlockState state = level.getBlockState(check);

            if (isOddstone(level, check) || isCornerBlock(level, check)) {
                return i;
            }

            if (!state.isAir() && !state.is(ModBlocks.ODDSTONE_PORTAL.get())) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Validates frame structure and collects Oddstone positions.
     * Corners must be corner blocks, everything else must be Oddstone.
     */
    private static boolean validateFrame(Level level, BlockPos bottomLeft,
                                          int width, int height, Direction hDir,
                                          List<BlockPos> oddstonePositions) {
        // Bottom row
        for (int h = 0; h < width; h++) {
            BlockPos pos = bottomLeft.relative(hDir, h);
            if (h == 0 || h == width - 1) {
                if (!isCornerBlock(level, pos)) return false;
            } else {
                if (!isOddstone(level, pos)) return false;
                oddstonePositions.add(pos);
            }
        }

        // Top row
        for (int h = 0; h < width; h++) {
            BlockPos pos = bottomLeft.relative(Direction.UP, height - 1).relative(hDir, h);
            if (h == 0 || h == width - 1) {
                if (!isCornerBlock(level, pos)) return false;
            } else {
                if (!isOddstone(level, pos)) return false;
                oddstonePositions.add(pos);
            }
        }

        // Left column (excluding corners)
        for (int v = 1; v < height - 1; v++) {
            BlockPos pos = bottomLeft.relative(Direction.UP, v);
            if (!isOddstone(level, pos)) return false;
            oddstonePositions.add(pos);
        }

        // Right column (excluding corners)
        for (int v = 1; v < height - 1; v++) {
            BlockPos pos = bottomLeft.relative(hDir, width - 1).relative(Direction.UP, v);
            if (!isOddstone(level, pos)) return false;
            oddstonePositions.add(pos);
        }

        return true;
    }

    // ========================================================================
    // ADDRESS
    // ========================================================================

    private static String readAddress(Level level, BlockPos tl, BlockPos tr,
                                       BlockPos br, BlockPos bl) {
        String sTL = getBlockId(level, tl);
        String sTR = getBlockId(level, tr);
        String sBR = getBlockId(level, br);
        String sBL = getBlockId(level, bl);

        String front = sTL + "," + sTR + "," + sBR + "," + sBL;
        String back = sTR + "," + sTL + "," + sBL + "," + sBR;

        return front.compareTo(back) <= 0 ? front : back;
    }

    private static String getBlockId(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return ForgeRegistries.BLOCKS.getKey(block).toString();
    }

    // ========================================================================
    // BLOCK TYPE CHECKS
    // ========================================================================

    private static boolean isOddstone(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.ODDSTONE.get());
    }

    /**
     * Valid corner blocks - crafted mineral/metal blocks.
     * 14 types = 14^4 = 38,416 unique addresses per dimension.
     * Delegates to PortalUtils for the single source of truth.
     */
    private static boolean isCornerBlock(Level level, BlockPos pos) {
        return PortalUtils.isCornerBlock(level, pos);
    }
}
