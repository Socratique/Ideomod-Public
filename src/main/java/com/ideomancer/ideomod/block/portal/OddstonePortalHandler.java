package com.ideomancer.ideomod.block.portal;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.ideomancer.ideomod.block.OddstoneBlock;

/**
 * Handles Oddstone Portal activation and deactivation logic.
 * 
 * Extracted from OddstoneBlock to keep that class focused on the
 * block's own properties (stickiness, mob avoidance, etc.) while
 * this class manages the portal lifecycle:
 * 
 * - Activation: frame detection → registration → filling interior
 * - Deactivation: unregistration → clearing portal blocks
 * - Neighbor change handling: power gained/lost, corner block broken
 * - Block removal handling: frame block broken
 * 
 * DEACTIVATION STRATEGY:
 * Rather than reacting to a single Oddstone's momentary state during
 * neighborChanged (which can false-trigger from unrelated block updates),
 * deactivation re-validates the ENTIRE frame. If any Oddstone in the
 * frame has lost power or any corner block is missing, the portal is
 * deactivated. This mirrors the activation path, which also validates
 * the entire frame before lighting.
 * 
 * All methods are static — this is a utility class, not a block.
 * Called by OddstoneBlock.neighborChanged() and OddstoneBlock.onRemove().
 */
public class OddstonePortalHandler {

    // ========================================================================
    // NEIGHBOR CHANGE — called by OddstoneBlock.neighborChanged()
    // ========================================================================

    /**
     * Handles an Oddstone block's neighbor changing. Decides whether
     * to try activating or deactivating a portal.
     * 
     * Activation: if this Oddstone has power and isn't already part of
     * a lit portal, attempt frame detection and activation.
     * 
     * Deactivation: if this Oddstone is adjacent to a lit portal,
     * re-validate the entire frame. Only deactivates if the frame
     * is genuinely broken (Oddstone unpowered or corner missing).
     * This prevents false deactivation from unrelated neighbor
     * updates like placing blocks near the frame exterior.
     */
    public static void onNeighborChanged(ServerLevel level, BlockPos pos,
                                          BlockPos neighborPos) {
        boolean hasPower = level.hasNeighborSignal(pos);
        boolean adjacentToPortal = isAdjacentToPortal(level, pos);

        if (hasPower && !adjacentToPortal) {
            tryActivate(level, pos);
        } else if (adjacentToPortal) {
            // Re-validate the entire frame instead of reacting to this
            // single block's state. The frame check handles all cases:
            // - Oddstone lost power
            // - Corner block broken
            // - Frame Oddstone broken (covered by onBlockRemoved, but
            //   this catches edge cases from neighbor cascades)
            validateOrDeactivate(level, pos);
        }
    }

    // ========================================================================
    // BLOCK REMOVAL — called by OddstoneBlock.onRemove()
    // ========================================================================

    /**
     * When an Oddstone frame block is broken, deactivate any adjacent portal.
     * This is immediate — no re-validation needed because we KNOW a frame
     * block is gone.
     */
    public static void onBlockRemoved(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.is(ModBlocks.ODDSTONE_PORTAL.get())) {
                unregisterAndClear(level, neighbor, neighborState);
                break;
            }
        }
    }

    // ========================================================================
    // ACTIVATION
    // ========================================================================

    /**
     * Attempts to activate a portal from this Oddstone's position.
     * Runs frame detection, validates all Oddstone blocks are powered,
     * registers the address, and fills the interior with portal blocks.
     */
    private static void tryActivate(ServerLevel level, BlockPos pos) {
        OddstonePortalFrameDetector.FrameResult frame =
                OddstonePortalFrameDetector.detectFrame(level, pos);

        if (frame == null) return;

        // Check if already lit
        for (BlockPos interior : frame.interiorPositions) {
            if (level.getBlockState(interior).is(ModBlocks.ODDSTONE_PORTAL.get())) {
                return;
            }
        }

        // Try to register
        OddstonePortalSavedData data = OddstonePortalSavedData.get(level);
        BlockPos registrationPos = frame.corners[3]; // BL corner
        String axisStr = frame.axis == Direction.Axis.X ? "X" : "Z";

        OddstonePortalSavedData.RegisterResult result =
                data.registerPortal(frame.address, registrationPos, axisStr);

        switch (result) {
            case FAILED_ADDRESS_FULL:
                Ideomod.LOGGER.info("Oddstone Portal at {} failed to light - address [{}] is taken",
                        pos, frame.address);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 0.5F, 1.0F);
                return;

            case FAILED_ALREADY_EXISTS:
                return;

            case REGISTERED_UNLINKED:
            case REGISTERED_LINKED:
                Direction.Axis portalAxis = frame.axis;
                BlockState portalState = ModBlocks.ODDSTONE_PORTAL.get().defaultBlockState()
                        .setValue(OddstonePortalBlock.AXIS, portalAxis);

                for (BlockPos interior : frame.interiorPositions) {
                    level.setBlock(interior, portalState, 2);
                }

                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);

                if (result == OddstonePortalSavedData.RegisterResult.REGISTERED_LINKED) {
                    Ideomod.LOGGER.info("Oddstone Portal activated and LINKED at {} [{}]",
                            registrationPos, frame.address);
                } else {
                    Ideomod.LOGGER.info("Oddstone Portal activated (unlinked) at {} [{}]",
                            registrationPos, frame.address);
                }
                break;
        }
    }

    // ========================================================================
    // DEACTIVATION
    // ========================================================================

    /**
     * Re-validates the portal frame that this Oddstone is part of.
     * If the frame is no longer valid (power lost or corner missing),
     * deactivates the portal. If the frame is still valid, does nothing.
     * 
     * This is the key fix for false deactivation: instead of assuming
     * the portal is broken because ONE Oddstone got a neighborChanged,
     * we walk the entire frame and check everything.
     */
    private static void validateOrDeactivate(ServerLevel level, BlockPos oddstonePos) {
        // Find an adjacent portal block to start navigation
        BlockPos portalBlockPos = null;
        BlockState portalState = null;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = oddstonePos.relative(dir);
            BlockState state = level.getBlockState(neighbor);
            if (state.is(ModBlocks.ODDSTONE_PORTAL.get())) {
                portalBlockPos = neighbor;
                portalState = state;
                break;
            }
        }
        if (portalBlockPos == null) return;

        Direction.Axis axis = portalState.getValue(OddstonePortalBlock.AXIS);
        Direction hDir = PortalUtils.getHorizontalDir(axis);

        // Navigate to bottom-left of interior
        BlockPos bottomLeft = PortalUtils.findBottomLeft(level, portalBlockPos, hDir);

        // Measure portal interior dimensions
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
        if (width == 0 || height == 0) {
            unregisterAndClear(level, portalBlockPos, portalState);
            return;
        }

        // Derive the four frame corners from the interior
        Direction hDirNeg = hDir.getOpposite();
        BlockPos frameBL = bottomLeft.relative(hDirNeg).below();
        BlockPos frameBR = frameBL.relative(hDir, width + 1);
        BlockPos frameTL = frameBL.above(height + 1);
        BlockPos frameTR = frameTL.relative(hDir, width + 1);

        int outerWidth = width + 2;
        int outerHeight = height + 2;

        // Check all four corners are still valid corner blocks
        if (!PortalUtils.isCornerBlock(level, frameBL)
            || !PortalUtils.isCornerBlock(level, frameBR)
            || !PortalUtils.isCornerBlock(level, frameTL)
            || !PortalUtils.isCornerBlock(level, frameTR)) {
            Ideomod.LOGGER.debug("Portal frame invalid - corner block missing");
            unregisterAndClear(level, portalBlockPos, portalState);
            return;
        }

        // Check all Oddstone in the frame are still Oddstone AND powered
        // Bottom row (excluding corners)
        for (int h = 1; h < outerWidth - 1; h++) {
            BlockPos pos = frameBL.relative(hDir, h);
            if (!isValidFrameOddstone(level, pos)) {
                unregisterAndClear(level, portalBlockPos, portalState);
                return;
            }
        }
        // Top row (excluding corners)
        for (int h = 1; h < outerWidth - 1; h++) {
            BlockPos pos = frameTL.relative(hDir, h);
            if (!isValidFrameOddstone(level, pos)) {
                unregisterAndClear(level, portalBlockPos, portalState);
                return;
            }
        }
        // Left column (excluding corners)
        for (int v = 1; v < outerHeight - 1; v++) {
            BlockPos pos = frameBL.above(v);
            if (!isValidFrameOddstone(level, pos)) {
                unregisterAndClear(level, portalBlockPos, portalState);
                return;
            }
        }
        // Right column (excluding corners)
        for (int v = 1; v < outerHeight - 1; v++) {
            BlockPos pos = frameBR.above(v);
            if (!isValidFrameOddstone(level, pos)) {
                unregisterAndClear(level, portalBlockPos, portalState);
                return;
            }
        }

        // Frame is still valid — do nothing
    }

    /**
     * Checks that a position is Oddstone AND receiving a redstone signal.
     */
    private static boolean isValidFrameOddstone(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.ODDSTONE.get())) {
            Ideomod.LOGGER.debug("Portal frame invalid - expected Oddstone at {}", pos);
            return false;
        }
        if (!level.hasNeighborSignal(pos)) {
            Ideomod.LOGGER.debug("Portal frame invalid - Oddstone at {} has no redstone signal", pos);
            return false;
        }
        return true;
    }

    /**
     * Unregisters a portal from saved data and clears all its portal blocks.
     * 
     * This is the single deactivation exit point — all paths that kill a
     * portal (corner break, power loss, frame break) come through here.
     * 
     * Break particles and sound come from PortalUtils.clearPortalBlocks(),
     * which uses level.destroyBlock() — same visual/audio as when portal
     * blocks cascade-break via updateShape when a frame Oddstone is mined.
     */
    private static void unregisterAndClear(ServerLevel level, BlockPos portalBlockPos,
                                            BlockState portalState) {
        OddstonePortalSavedData data = OddstonePortalSavedData.get(level);
        Direction.Axis axis = portalState.getValue(OddstonePortalBlock.AXIS);
        Direction hDir = PortalUtils.getHorizontalDir(axis);

        BlockPos bottomLeft = PortalUtils.findBottomLeft(level, portalBlockPos, hDir);
        BlockPos frameCorner = PortalUtils.getFrameCorner(bottomLeft, hDir);
        data.unregisterPortal(frameCorner);

        PortalUtils.clearPortalBlocks(level, bottomLeft, hDir);

        Ideomod.LOGGER.info("Oddstone Portal deactivated (frame corner {})", frameCorner);
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Checks if this Oddstone is adjacent to any portal blocks.
     */
    private static boolean isAdjacentToPortal(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).is(ModBlocks.ODDSTONE_PORTAL.get())) {
                return true;
            }
        }
        return false;
    }
}
