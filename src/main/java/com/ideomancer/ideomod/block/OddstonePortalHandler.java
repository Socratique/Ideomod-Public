package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
 * All methods are static — this is a utility class, not a block.
 * Called by OddstoneBlock.neighborChanged() and OddstoneBlock.onRemove().
 */
public class OddstonePortalHandler {

    // ========================================================================
    // NEIGHBOR CHANGE — called by OddstoneBlock.neighborChanged()
    // ========================================================================

    /**
     * Handles an Oddstone block's neighbor changing. Decides whether
     * to try activating or deactivating a portal based on the current
     * power state and adjacency to existing portal blocks.
     * 
     * Three triggers for deactivation:
     * 1. This Oddstone lost redstone power while adjacent to a lit portal
     * 2. A corner block was broken (detected by checking what the changed
     *    neighbor block is now — if it's not oddstone, corner, or portal,
     *    something in the frame was destroyed)
     * 
     * One trigger for activation:
     * 1. This Oddstone gained redstone power and isn't already part of a portal
     */
    public static void onNeighborChanged(ServerLevel level, BlockPos pos,
                                          BlockPos neighborPos) {
        boolean hasPower = level.hasNeighborSignal(pos);
        boolean adjacentToPortal = isAdjacentToPortal(level, pos);

        if (hasPower && !adjacentToPortal) {
            tryActivate(level, pos);
        } else if (!hasPower && adjacentToPortal) {
            tryDeactivate(level, pos);
        }

        // Corner block broken check
        if (adjacentToPortal) {
            BlockState changedState = level.getBlockState(neighborPos);
            if (!changedState.is(ModBlocks.ODDSTONE.get())
                && !PortalUtils.isCornerBlock(changedState)
                && !changedState.is(ModBlocks.ODDSTONE_PORTAL.get())) {
                tryDeactivate(level, pos);
            }
        }
    }

    // ========================================================================
    // BLOCK REMOVAL — called by OddstoneBlock.onRemove()
    // ========================================================================

    /**
     * When an Oddstone frame block is broken, deactivate any adjacent portal.
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
     * Finds an adjacent portal block and deactivates the entire portal.
     */
    private static void tryDeactivate(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);

            if (neighborState.is(ModBlocks.ODDSTONE_PORTAL.get())) {
                unregisterAndClear(level, neighbor, neighborState);
                return;
            }
        }
    }

    /**
     * Unregisters a portal from saved data and clears all its portal blocks.
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
