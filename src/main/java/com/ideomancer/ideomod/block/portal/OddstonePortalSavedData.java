package com.ideomancer.ideomod.block.portal;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores Oddstone Portal registrations per dimension.
 * 
 * HOW IT WORKS:
 * =============
 * Each dimension (overworld, nether, end) has its own instance of this data.
 * When a portal lights up, it registers its address and position here.
 * When another portal with the same address lights up, the system finds
 * the first one and links them together.
 * 
 * The data is saved to the world folder automatically by Minecraft's
 * SavedData system, so it persists across server restarts and chunk
 * unloading. This is important because the two linked portals could
 * be thousands of blocks apart in unloaded chunks.
 * 
 * DATA STRUCTURE:
 * Each portal entry has:
 * - address: String (the corner ore code, e.g. "diamond_ore,iron_ore,gold_ore,coal_ore")
 * - pos: BlockPos (bottom-left corner of the frame, used as the reference point)
 * - partnerPos: BlockPos or null (position of linked partner, null if unlinked)
 * - axis: String ("X" or "Z") - which plane the portal is in
 * - Cached geometry: interiorBottomLeft, interiorWidth, interiorHeight
 *   (passed in at registration time from the frame detector, avoids
 *   re-scanning during teleport)
 * 
 * RULES:
 * - An address can have at most 2 portals (a linked pair)
 * - If a portal tries to register an address that already has a pair, it fails
 * - When a portal is destroyed, it unregisters and frees the address
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class OddstonePortalSavedData extends SavedData {

    private static final String DATA_NAME = "ideomod_oddstone_portals";

    /**
     * Represents a single registered portal.
     * 
     * Includes cached geometry data (interior bottom-left position, width, height)
     * so that teleportation can calculate relative positions without re-scanning
     * the portal frame every time. The geometry is passed in at registration
     * time from the frame detector and discarded when the portal unregisters.
     */
    public static class PortalEntry {
        public final String address;
        public final BlockPos pos;
        public final String axis; // "X" or "Z"
        @Nullable private BlockPos partnerPos;

        // Cached geometry — passed in at registration, avoids PortalGeometry.detect() on teleport
        @Nullable private BlockPos interiorBottomLeft;
        private int interiorWidth;
        private int interiorHeight;

        public PortalEntry(String address, BlockPos pos, String axis, @Nullable BlockPos partnerPos) {
            this.address = address;
            this.pos = pos;
            this.axis = axis;
            this.partnerPos = partnerPos;
            this.interiorBottomLeft = null;
            this.interiorWidth = 0;
            this.interiorHeight = 0;
        }

        public PortalEntry(String address, BlockPos pos, String axis, @Nullable BlockPos partnerPos,
                           @Nullable BlockPos interiorBottomLeft, int interiorWidth, int interiorHeight) {
            this.address = address;
            this.pos = pos;
            this.axis = axis;
            this.partnerPos = partnerPos;
            this.interiorBottomLeft = interiorBottomLeft;
            this.interiorWidth = interiorWidth;
            this.interiorHeight = interiorHeight;
        }

        public boolean isLinked() {
            return partnerPos != null;
        }

        @Nullable
        public BlockPos getPartnerPos() {
            return partnerPos;
        }

        /**
         * Parse the axis string ("X" or "Z") to Direction.Axis enum.
         * Replaces the scattered inline checks across portal code.
         */
        public net.minecraft.core.Direction.Axis getAxisAsEnum() {
            return PortalGeometry.parseAxis(this.axis);
        }

        // -- Cached geometry accessors --

        @Nullable
        public BlockPos getInteriorBottomLeft() {
            return interiorBottomLeft;
        }

        public int getInteriorWidth() {
            return interiorWidth;
        }

        public int getInteriorHeight() {
            return interiorHeight;
        }

        /**
         * Returns true if this entry has cached geometry data.
         * False for entries loaded from old saves before caching was added.
         */
        public boolean hasGeometry() {
            return interiorBottomLeft != null && interiorWidth > 0 && interiorHeight > 0;
        }
    }

    // All registered portals, keyed by their position (bottom-left corner)
    private final Map<BlockPos, PortalEntry> portalsByPosition = new HashMap<>();

    // Quick lookup: address -> list of positions with that address
    // An address can have 0, 1, or 2 entries
    private final Map<String, BlockPos[]> portalsByAddress = new HashMap<>();

    /**
     * Gets the saved data for a given dimension, creating it if it doesn't exist.
     */
    public static OddstonePortalSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                OddstonePortalSavedData::load,
                OddstonePortalSavedData::new,
                DATA_NAME
        );
    }

    public OddstonePortalSavedData() {
        // New empty data
    }

    /**
     * Attempts to register a new portal with pre-computed geometry.
     * 
     * Geometry is passed directly from the frame detector (which already
     * knows the interior dimensions from its scan). This avoids re-scanning
     * the frame — and sidesteps the ordering problem where portal blocks
     * don't exist yet at registration time.
     * 
     * @param interiorBottomLeft The bottom-left position of the portal interior
     * @param interiorWidth      Interior width in blocks
     * @param interiorHeight     Interior height in blocks
     * @return RegisterResult indicating what happened
     */
    public RegisterResult registerPortal(String address, BlockPos pos, String axis,
                                          BlockPos interiorBottomLeft, int interiorWidth,
                                          int interiorHeight) {
        // Check if there's already a portal at this position
        if (portalsByPosition.containsKey(pos)) {
            return RegisterResult.FAILED_ALREADY_EXISTS;
        }

        // Check the address
        BlockPos[] existing = portalsByAddress.get(address);

        if (existing == null) {
            // First portal with this address - register as unlinked
            PortalEntry entry = new PortalEntry(address, pos, axis, null,
                    interiorBottomLeft, interiorWidth, interiorHeight);
            portalsByPosition.put(pos, entry);
            portalsByAddress.put(address, new BlockPos[]{pos, null});
            setDirty();

            Ideomod.LOGGER.info("Portal registered at {} with address [{}] - unlinked (geometry: {}x{})",
                    pos, address, interiorWidth, interiorHeight);
            return RegisterResult.REGISTERED_UNLINKED;
        }

        if (existing[0] != null && existing[1] != null) {
            // Address already has two portals - FULL
            Ideomod.LOGGER.info("Portal registration FAILED at {} - address [{}] already has a linked pair",
                    pos, address);
            return RegisterResult.FAILED_ADDRESS_FULL;
        }

        // There's one existing portal - link them!
        BlockPos partnerPos = existing[0]; // The existing portal
        PortalEntry partnerEntry = portalsByPosition.get(partnerPos);

        // Create our entry, linked to the partner
        PortalEntry newEntry = new PortalEntry(address, pos, axis, partnerPos,
                interiorBottomLeft, interiorWidth, interiorHeight);
        portalsByPosition.put(pos, newEntry);

        // Update partner to link back to us
        partnerEntry.partnerPos = pos;

        // Update address lookup
        existing[1] = pos;

        setDirty();

        Ideomod.LOGGER.info("Portal registered at {} with address [{}] - LINKED to partner at {} (geometry: {}x{})",
                pos, address, partnerPos, interiorWidth, interiorHeight);
        return RegisterResult.REGISTERED_LINKED;
    }

    /**
     * Legacy overload without geometry. Used by old code paths or when
     * geometry is unavailable. Teleportation will fall back to live
     * PortalGeometry.detect() calls.
     */
    public RegisterResult registerPortal(String address, BlockPos pos, String axis) {
        return registerPortal(address, pos, axis, null, 0, 0);
    }

    /**
     * Unregisters a portal (when its frame is broken or deactivated).
     * If it was linked, the partner becomes unlinked again.
     * Cached geometry is discarded with the entry.
     */
    public void unregisterPortal(BlockPos pos) {
        PortalEntry entry = portalsByPosition.remove(pos);
        if (entry == null) return;

        // If linked, unlink the partner
        if (entry.partnerPos != null) {
            PortalEntry partner = portalsByPosition.get(entry.partnerPos);
            if (partner != null) {
                partner.partnerPos = null;
            }
        }

        // Update address lookup
        BlockPos[] addressEntries = portalsByAddress.get(entry.address);
        if (addressEntries != null) {
            if (pos.equals(addressEntries[0]) || pos.equals(addressEntries[1])) {
                // Clean up: remove our position from whichever slot(s) it's in
                if (pos.equals(addressEntries[0])) {
                    addressEntries[0] = addressEntries[1];
                    addressEntries[1] = null;
                } else {
                    addressEntries[1] = null;
                }
                // If both slots are now null, remove the address entirely
                if (addressEntries[0] == null) {
                    portalsByAddress.remove(entry.address);
                }
            }
        }

        setDirty();
        Ideomod.LOGGER.info("Portal unregistered at {} (address [{}])", pos, entry.address);
    }

    /**
     * Gets the portal entry at a given position, if any.
     */
    @Nullable
    public PortalEntry getPortal(BlockPos pos) {
        return portalsByPosition.get(pos);
    }

    // ========================================================================
    // SAVE / LOAD (NBT serialization)
    // ========================================================================

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag portalList = new ListTag();

        for (PortalEntry entry : portalsByPosition.values()) {
            CompoundTag portalTag = new CompoundTag();
            portalTag.putString("address", entry.address);
            portalTag.putInt("x", entry.pos.getX());
            portalTag.putInt("y", entry.pos.getY());
            portalTag.putInt("z", entry.pos.getZ());
            portalTag.putString("axis", entry.axis);

            if (entry.partnerPos != null) {
                portalTag.putInt("px", entry.partnerPos.getX());
                portalTag.putInt("py", entry.partnerPos.getY());
                portalTag.putInt("pz", entry.partnerPos.getZ());
                portalTag.putBoolean("linked", true);
            } else {
                portalTag.putBoolean("linked", false);
            }

            // Save cached geometry if present
            if (entry.interiorBottomLeft != null) {
                portalTag.putInt("iblX", entry.interiorBottomLeft.getX());
                portalTag.putInt("iblY", entry.interiorBottomLeft.getY());
                portalTag.putInt("iblZ", entry.interiorBottomLeft.getZ());
                portalTag.putInt("iWidth", entry.interiorWidth);
                portalTag.putInt("iHeight", entry.interiorHeight);
                portalTag.putBoolean("hasGeom", true);
            } else {
                portalTag.putBoolean("hasGeom", false);
            }

            portalList.add(portalTag);
        }

        tag.put("portals", portalList);
        return tag;
    }

    public static OddstonePortalSavedData load(CompoundTag tag) {
        OddstonePortalSavedData data = new OddstonePortalSavedData();

        ListTag portalList = tag.getList("portals", Tag.TAG_COMPOUND);
        for (int i = 0; i < portalList.size(); i++) {
            CompoundTag portalTag = portalList.getCompound(i);

            String address = portalTag.getString("address");
            BlockPos pos = new BlockPos(
                    portalTag.getInt("x"),
                    portalTag.getInt("y"),
                    portalTag.getInt("z")
            );
            String axis = portalTag.getString("axis");

            BlockPos partnerPos = null;
            if (portalTag.getBoolean("linked")) {
                partnerPos = new BlockPos(
                        portalTag.getInt("px"),
                        portalTag.getInt("py"),
                        portalTag.getInt("pz")
                );
            }

            // Load cached geometry if present (backward-compatible with old saves)
            BlockPos interiorBL = null;
            int iWidth = 0;
            int iHeight = 0;
            if (portalTag.getBoolean("hasGeom")) {
                interiorBL = new BlockPos(
                        portalTag.getInt("iblX"),
                        portalTag.getInt("iblY"),
                        portalTag.getInt("iblZ")
                );
                iWidth = portalTag.getInt("iWidth");
                iHeight = portalTag.getInt("iHeight");
            }

            PortalEntry entry = new PortalEntry(address, pos, axis, partnerPos,
                    interiorBL, iWidth, iHeight);
            data.portalsByPosition.put(pos, entry);

            // Rebuild address index
            BlockPos[] addressSlots = data.portalsByAddress.computeIfAbsent(
                    address, k -> new BlockPos[]{null, null});
            if (addressSlots[0] == null) {
                addressSlots[0] = pos;
            } else {
                addressSlots[1] = pos;
            }
        }

        Ideomod.LOGGER.info("Loaded {} Oddstone portal registrations", data.portalsByPosition.size());
        return data;
    }

    // ========================================================================
    // RESULT ENUM
    // ========================================================================

    public enum RegisterResult {
        REGISTERED_UNLINKED,
        REGISTERED_LINKED,
        FAILED_ADDRESS_FULL,
        FAILED_ALREADY_EXISTS
    }
}
