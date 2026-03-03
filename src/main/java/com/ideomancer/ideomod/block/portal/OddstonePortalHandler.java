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

/**
 * Handles Oddstone Portal activation and deactivation logic.
 *
 * ACTIVATION MODEL (ender pearl ritual):
 * =======================================
 * Portals are activated by throwing an ender pearl into a valid frame interior,
 * analogous to lighting a nether portal with flint and steel. No redstone
 * required — just build the frame and throw the pearl.
 *
 * The ender pearl event is handled by EnderPearlPortalHandler, which calls
 * tryActivate() on this class when a pearl lands inside a valid frame.
 *
 * DEACTIVATION:
 * =============
 * Portals deactivate when:
 * - A frame Oddstone is physically broken (onBlockRemoved → immediate deactivation)
 * - A corner block is broken (detected via neighborChanged → validateOrDeactivate)
 * - A frame Oddstone is removed by other means (entity, explosion, etc.)
 *
 * No redstone involvement in deactivation either. The frame must simply
 * remain structurally intact.
 *
 * All methods are static — this is a utility class, not a block.
 * Called by OddstoneBlock.onRemove(), OddstoneBlock.neighborChanged(),
 * and EnderPearlPortalHandler.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class OddstonePortalHandler {

    /**
     * Re-entrancy guard. When tryActivate() is filling portal blocks into
     * the frame interior, each setBlock() triggers neighborChanged on adjacent
     * Oddstone blocks. Without this guard, those calls would enter
     * validateOrDeactivate() mid-fill, detect an incomplete portal, and
     * immediately deactivate the portal we're in the process of creating.
     */
    private static boolean activating = false;

    // ========================================================================
    // NEIGHBOR CHANGE — called by OddstoneBlock.neighborChanged()
    // ========================================================================

    /**
     * Handles an Oddstone block's neighbor changing.
     *
     * Since activation is now ender-pearl-only, neighborChanged only handles
     * deactivation validation: if this Oddstone is adjacent to a lit portal,
     * re-validate the entire frame structure. Only deactivates if the frame
     * is genuinely broken (Oddstone missing or corner block missing).
     */
    public static void onNeighborChanged(ServerLevel level, BlockPos pos,
                                          BlockPos neighborPos) {
        if (activating) return; // Mid-fill — don't validate during portal creation

        boolean adjacentToPortal = isAdjacentToPortal(level, pos);

        if (adjacentToPortal) {
            // Re-validate the entire frame. Checks:
            // - All Oddstone blocks still present
            // - All corner blocks still present
            // Does NOT check redstone — portals don't need power.
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
    // ACTIVATION — called by EnderPearlPortalHandler
    // ========================================================================

    /**
     * Attempts to activate a portal from an Oddstone frame block's position.
     * Runs frame detection, registers the address, and fills the interior
     * with portal blocks.
     *
     * Called by EnderPearlPortalHandler when an ender pearl lands inside
     * a valid frame. The handler finds a frame Oddstone adjacent to the
     * landing position and calls this method.
     *
     * @return true if the portal was successfully activated
     */
    public static boolean tryActivate(ServerLevel level, BlockPos oddstonePos) {
        OddstonePortalFrameDetector.FrameResult frame =
                OddstonePortalFrameDetector.detectFrame(level, oddstonePos);

        if (frame == null) return false;

        // Check if already lit
        for (BlockPos interior : frame.interiorPositions) {
            if (level.getBlockState(interior).is(ModBlocks.ODDSTONE_PORTAL.get())) {
                return false;
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
                        oddstonePos, frame.address);
                level.playSound(null, oddstonePos, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 0.5F, 1.0F);
                return false;

            case FAILED_ALREADY_EXISTS:
                return false;

            case REGISTERED_UNLINKED:
            case REGISTERED_LINKED:
                Direction.Axis portalAxis = frame.axis;
                BlockState portalState = ModBlocks.ODDSTONE_PORTAL.get().defaultBlockState()
                        .setValue(OddstonePortalBlock.AXIS, portalAxis);

                // Guard: prevent neighborChanged from triggering validateOrDeactivate
                // during the fill loop. Each setBlock fires neighborChanged on adjacent
                // Oddstone blocks, which would otherwise see a partially-filled portal
                // and deactivate it.
                activating = true;
                try {
                    for (BlockPos interior : frame.interiorPositions) {
                        // Flag 18 = UPDATE_CLIENTS (2) | UPDATE_KNOWN_SHAPE (16)
                        // UPDATE_KNOWN_SHAPE suppresses updateShape() on neighboring
                        // blocks, preventing partially-filled portal blocks from
                        // cascade-breaking when they see air neighbors that haven't
                        // been filled yet. This is the same flag vanilla uses when
                        // filling nether portals.
                        level.setBlock(interior, portalState, 2 | 16);
                    }
                } finally {
                    activating = false;
                }

                level.playSound(null, oddstonePos, SoundEvents.FLINTANDSTEEL_USE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);

                if (result == OddstonePortalSavedData.RegisterResult.REGISTERED_LINKED) {
                    Ideomod.LOGGER.info("Oddstone Portal activated and LINKED at {} [{}]",
                            registrationPos, frame.address);
                } else {
                    Ideomod.LOGGER.info("Oddstone Portal activated (unlinked) at {} [{}]",
                            registrationPos, frame.address);
                }
                return true;

            default:
                return false;
        }
    }

    // ========================================================================
    // DEACTIVATION
    // ========================================================================

    /**
     * Re-validates the portal frame that this Oddstone is part of.
     * If the frame is no longer structurally valid (Oddstone missing
     * or corner missing), deactivates the portal.
     *
     * No redstone checks — portals don't need power to stay active.
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

        // Detect geometry from the portal block
        PortalGeometry geom = PortalGeometry.detect(level, portalBlockPos);
        if (geom == null) {
            unregisterAndClear(level, portalBlockPos, portalState);
            return;
        }

        // Check all four corners are still valid corner blocks
        BlockPos[] corners = geom.getFrameCorners();
        if (!PortalUtils.isCornerBlock(level, corners[0])
            || !PortalUtils.isCornerBlock(level, corners[1])
            || !PortalUtils.isCornerBlock(level, corners[2])
            || !PortalUtils.isCornerBlock(level, corners[3])) {
            Ideomod.LOGGER.debug("Portal frame invalid - corner block missing");
            unregisterAndClear(level, portalBlockPos, portalState);
            return;
        }

        // Check all Oddstone in the frame are still present (no power check)
        final boolean[] frameValid = {true};
        geom.forEachOddstonePosition(pos -> {
            if (!isValidFrameOddstone(level, pos)) {
                frameValid[0] = false;
            }
        });

        if (!frameValid[0]) {
            unregisterAndClear(level, portalBlockPos, portalState);
            return;
        }

        // Frame is still valid — do nothing
    }

    /**
     * Checks that a position contains an Oddstone block.
     * No redstone power check — portals don't need power.
     */
    private static boolean isValidFrameOddstone(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.ODDSTONE.get())) {
            Ideomod.LOGGER.debug("Portal frame invalid - expected Oddstone at {}", pos);
            return false;
        }
        return true;
    }

    /**
     * Unregisters a portal from saved data and clears all its portal blocks.
     *
     * This is the single deactivation exit point — all paths that kill a
     * portal (corner break, oddstone break, frame break) come through here.
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
