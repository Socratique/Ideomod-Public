package com.ideomancer.ideomod.block.portal;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
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
 * DEBUG STICK:
 * ============
 * Players holding the Structure Debug Stick receive chat notifications for
 * portal registration, linking, and deactivation events. Same pattern as
 * StructureManager's debug notifications.
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
     * Runs frame detection, registers the address (with geometry from the
     * frame detector), and fills the interior with portal blocks.
     *
     * Geometry is computed directly from the FrameResult's corner positions
     * rather than re-scanning with PortalGeometry.detect(), because portal
     * blocks don't exist yet at registration time.
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

        // Compute interior geometry from the frame corners.
        // corners[3] = BL (frame), corners[1] = TR (frame)
        // Interior is 1 block inside the frame on each side.
        // Frame corners: [TL, TR, BR, BL]
        BlockPos frameBL = frame.corners[3];
        BlockPos frameTR = frame.corners[1];
        Direction hDir = PortalUtils.getHorizontalDir(frame.axis);

        // Interior bottom-left is 1 in from frame BL horizontally and 1 up
        BlockPos interiorBL = frameBL.relative(hDir, 1).above(1);

        // Interior dimensions: frame span minus 2 (one frame block each side)
        int interiorWidth;
        int interiorHeight;
        if (frame.axis == Direction.Axis.X) {
            // Portal extends along Z axis
            interiorWidth = Math.abs(frameTR.getZ() - frameBL.getZ()) - 1;
        } else {
            // Portal extends along X axis
            interiorWidth = Math.abs(frameTR.getX() - frameBL.getX()) - 1;
        }
        interiorHeight = frameTR.getY() - frameBL.getY() - 1;

        // Register with geometry passed directly
        OddstonePortalSavedData data = OddstonePortalSavedData.get(level);
        BlockPos registrationPos = frameBL; // BL corner is the registration key
        String axisStr = frame.axis == Direction.Axis.X ? "X" : "Z";

        OddstonePortalSavedData.RegisterResult result =
                data.registerPortal(frame.address, registrationPos, axisStr,
                        interiorBL, interiorWidth, interiorHeight);

        switch (result) {
            case FAILED_ADDRESS_FULL:
                Ideomod.LOGGER.info("Oddstone Portal at {} failed to light - address [{}] is taken",
                        oddstonePos, frame.address);
                level.playSound(null, oddstonePos, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 0.5F, 1.0F);

                notifyDebugPlayers(level, Component.literal("[Portal] ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal("FAILED: ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("address full").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" at ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(registrationPos.toShortString()).withStyle(ChatFormatting.AQUA)));
                return false;

            case FAILED_ALREADY_EXISTS:
                return false;

            case REGISTERED_UNLINKED:
            case REGISTERED_LINKED:
                Direction.Axis portalAxis = frame.axis;
                BlockState portalState = ModBlocks.ODDSTONE_PORTAL.get().defaultBlockState()
                        .setValue(OddstonePortalBlock.AXIS, portalAxis);

                activating = true;
                try {
                    for (BlockPos interior : frame.interiorPositions) {
                        level.setBlock(interior, portalState, 2 | 16);
                    }
                } finally {
                    activating = false;
                }

                level.playSound(null, oddstonePos, SoundEvents.FLINTANDSTEEL_USE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);

                // Get the entry back to read cached data for debug display
                OddstonePortalSavedData.PortalEntry entry = data.getPortal(registrationPos);

                if (result == OddstonePortalSavedData.RegisterResult.REGISTERED_LINKED) {
                    Ideomod.LOGGER.info("Oddstone Portal activated and LINKED at {} [{}]",
                            registrationPos, frame.address);

                    notifyDebugPlayers(level, Component.literal("[Portal] ")
                            .withStyle(ChatFormatting.GREEN)
                            .append(Component.literal("ACTIVATED + LINKED").withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" at ").withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(registrationPos.toShortString()).withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(entry != null && entry.getPartnerPos() != null
                                    ? entry.getPartnerPos().toShortString() : "?").withStyle(ChatFormatting.AQUA))
                            .append(buildGeometryDebug(entry))
                            .append(buildAddressDebug(frame.address)));
                } else {
                    Ideomod.LOGGER.info("Oddstone Portal activated (unlinked) at {} [{}]",
                            registrationPos, frame.address);

                    notifyDebugPlayers(level, Component.literal("[Portal] ")
                            .withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("ACTIVATED (unlinked)").withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" at ").withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(registrationPos.toShortString()).withStyle(ChatFormatting.AQUA))
                            .append(buildGeometryDebug(entry))
                            .append(buildAddressDebug(frame.address)));
                }
                return true;

            default:
                return false;
        }
    }

    // ========================================================================
    // DEACTIVATION
    // ========================================================================

    private static void validateOrDeactivate(ServerLevel level, BlockPos oddstonePos) {
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

        PortalGeometry geom = PortalGeometry.detect(level, portalBlockPos);
        if (geom == null) {
            unregisterAndClear(level, portalBlockPos, portalState);
            return;
        }

        BlockPos[] corners = geom.getFrameCorners();
        if (!PortalUtils.isCornerBlock(level, corners[0])
            || !PortalUtils.isCornerBlock(level, corners[1])
            || !PortalUtils.isCornerBlock(level, corners[2])
            || !PortalUtils.isCornerBlock(level, corners[3])) {
            Ideomod.LOGGER.debug("Portal frame invalid - corner block missing");
            unregisterAndClear(level, portalBlockPos, portalState);
            return;
        }

        final boolean[] frameValid = {true};
        geom.forEachOddstonePosition(pos -> {
            if (!isValidFrameOddstone(level, pos)) {
                frameValid[0] = false;
            }
        });

        if (!frameValid[0]) {
            unregisterAndClear(level, portalBlockPos, portalState);
        }
    }

    private static boolean isValidFrameOddstone(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.ODDSTONE.get())) {
            Ideomod.LOGGER.debug("Portal frame invalid - expected Oddstone at {}", pos);
            return false;
        }
        return true;
    }

    private static void unregisterAndClear(ServerLevel level, BlockPos portalBlockPos,
                                            BlockState portalState) {
        OddstonePortalSavedData data = OddstonePortalSavedData.get(level);
        Direction.Axis axis = portalState.getValue(OddstonePortalBlock.AXIS);
        Direction hDir = PortalUtils.getHorizontalDir(axis);

        BlockPos bottomLeft = PortalUtils.findBottomLeft(level, portalBlockPos, hDir);
        BlockPos frameCorner = PortalUtils.getFrameCorner(bottomLeft, hDir);

        // Read entry before unregistering so we can display info in debug
        OddstonePortalSavedData.PortalEntry entry = data.getPortal(frameCorner);

        data.unregisterPortal(frameCorner);

        PortalUtils.clearPortalBlocks(level, bottomLeft, hDir);

        Ideomod.LOGGER.info("Oddstone Portal deactivated (frame corner {})", frameCorner);

        notifyDebugPlayers(level, Component.literal("[Portal] ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal("DEACTIVATED").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" at ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(frameCorner.toShortString()).withStyle(ChatFormatting.AQUA))
                .append(entry != null
                        ? Component.literal(" [" + shortenAddress(entry.address) + "]")
                                .withStyle(ChatFormatting.GRAY)
                        : Component.literal(" [unknown]").withStyle(ChatFormatting.DARK_GRAY)));
    }

    // ========================================================================
    // DEBUG STICK HELPERS
    // ========================================================================

    private static void notifyDebugPlayers(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            if (isHoldingDebugStick(player)) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static boolean isHoldingDebugStick(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        return mainHand.is(ModItems.STRUCTURE_DEBUG_STICK.get())
            || offHand.is(ModItems.STRUCTURE_DEBUG_STICK.get());
    }

    private static Component buildGeometryDebug(
            @javax.annotation.Nullable OddstonePortalSavedData.PortalEntry entry) {
        if (entry == null || !entry.hasGeometry()) {
            return Component.literal(" (no geometry)").withStyle(ChatFormatting.DARK_GRAY);
        }
        return Component.literal(" (" + entry.getInteriorWidth() + "x"
                + entry.getInteriorHeight() + ")")
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component buildAddressDebug(String address) {
        return Component.literal(" [" + shortenAddress(address) + "]")
                .withStyle(ChatFormatting.DARK_AQUA);
    }

    private static String shortenAddress(String address) {
        return address
                .replace("minecraft:", "")
                .replace("_block", "");
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static boolean isAdjacentToPortal(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).is(ModBlocks.ODDSTONE_PORTAL.get())) {
                return true;
            }
        }
        return false;
    }
}
