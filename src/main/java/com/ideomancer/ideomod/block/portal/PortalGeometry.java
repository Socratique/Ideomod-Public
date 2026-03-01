package com.ideomancer.ideomod.block.portal;

import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Value object representing the geometry of a detected Oddstone portal.
 * Consolidates portal dimension scanning and frame iteration logic
 * that was previously duplicated across OddstonePortalBlock,
 * OddstonePortalHandler, and PortalUtils.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public record PortalGeometry(
    BlockPos interiorBottomLeft,
    int interiorWidth,
    int interiorHeight,
    Direction.Axis axis
) {
    /**
     * Detect portal geometry from any portal block within the interior.
     * Navigates to bottom-left, measures width and height.
     */
    @Nullable
    public static PortalGeometry detect(Level level, BlockPos anyPortalBlock) {
        Direction.Axis axis = level.getBlockState(anyPortalBlock)
            .getValue(OddstonePortalBlock.AXIS);
        Direction hDir = PortalUtils.getHorizontalDir(axis);

        BlockPos bottomLeft = PortalUtils.findBottomLeft(level, anyPortalBlock, hDir);

        int width = 0;
        while (level.getBlockState(bottomLeft.relative(hDir, width))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            width++;
        }
        int height = 0;
        while (level.getBlockState(bottomLeft.above(height))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            height++;
        }

        if (width == 0 || height == 0) return null;

        return new PortalGeometry(bottomLeft, width, height, axis);
    }

    /** Get the frame corner (BL of frame, used as registration key) */
    public BlockPos getFrameCorner() {
        Direction hDir = PortalUtils.getHorizontalDir(axis);
        return PortalUtils.getFrameCorner(interiorBottomLeft, hDir);
    }

    /** Get horizontal direction for this portal's axis */
    public Direction getHorizontalDir() {
        return PortalUtils.getHorizontalDir(axis);
    }

    /** Get outer width (interior + 2 frame blocks) */
    public int getOuterWidth() { return interiorWidth + 2; }

    /** Get outer height (interior + 2 frame blocks) */
    public int getOuterHeight() { return interiorHeight + 2; }

    /** Get the four frame corners: BL, BR, TL, TR */
    public BlockPos[] getFrameCorners() {
        Direction hDir = getHorizontalDir();
        Direction hDirNeg = hDir.getOpposite();
        BlockPos frameBL = interiorBottomLeft.relative(hDirNeg).below();
        BlockPos frameBR = frameBL.relative(hDir, interiorWidth + 1);
        BlockPos frameTL = frameBL.above(interiorHeight + 1);
        BlockPos frameTR = frameTL.relative(hDir, interiorWidth + 1);
        return new BlockPos[]{frameBL, frameBR, frameTL, frameTR};
    }

    /**
     * Iterate all Oddstone positions in the frame perimeter (excluding corners).
     * Visits bottom row, top row, left column, right column.
     */
    public void forEachOddstonePosition(java.util.function.Consumer<BlockPos> visitor) {
        Direction hDir = getHorizontalDir();
        BlockPos[] corners = getFrameCorners();
        BlockPos frameBL = corners[0];
        BlockPos frameTL = corners[2];
        BlockPos frameBR = corners[1];

        int outerW = getOuterWidth();
        int outerH = getOuterHeight();

        // Bottom row (excluding corners)
        for (int h = 1; h < outerW - 1; h++) {
            visitor.accept(frameBL.relative(hDir, h));
        }
        // Top row (excluding corners)
        for (int h = 1; h < outerW - 1; h++) {
            visitor.accept(frameTL.relative(hDir, h));
        }
        // Left column (excluding corners)
        for (int v = 1; v < outerH - 1; v++) {
            visitor.accept(frameBL.above(v));
        }
        // Right column (excluding corners)
        for (int v = 1; v < outerH - 1; v++) {
            visitor.accept(frameBR.above(v));
        }
    }

    /**
     * Parse axis string ("X" or "Z") to Direction.Axis enum.
     */
    public static Direction.Axis parseAxis(String axisStr) {
        return axisStr.equals("X") ? Direction.Axis.X : Direction.Axis.Z;
    }
}
