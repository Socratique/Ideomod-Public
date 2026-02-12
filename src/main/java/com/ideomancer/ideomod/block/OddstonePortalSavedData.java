package com.ideomancer.ideomod.block;

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
 * 
 * RULES:
 * - An address can have at most 2 portals (a linked pair)
 * - If a portal tries to register an address that already has a pair, it fails
 * - When a portal is destroyed, it unregisters and frees the address
 */
public class OddstonePortalSavedData extends SavedData {

    private static final String DATA_NAME = "ideomod_oddstone_portals";

    /**
     * Represents a single registered portal.
     */
    public static class PortalEntry {
        public final String address;
        public final BlockPos pos;
        public final String axis; // "X" or "Z"
        @Nullable public BlockPos partnerPos;

        public PortalEntry(String address, BlockPos pos, String axis, @Nullable BlockPos partnerPos) {
            this.address = address;
            this.pos = pos;
            this.axis = axis;
            this.partnerPos = partnerPos;
        }

        public boolean isLinked() {
            return partnerPos != null;
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
     * Attempts to register a new portal.
     * 
     * @return RegisterResult indicating what happened:
     *   REGISTERED_UNLINKED - Portal registered, no partner found
     *   REGISTERED_LINKED   - Portal registered AND linked to existing partner
     *   FAILED_ADDRESS_FULL - Address already has a linked pair, cannot register
     *   FAILED_ALREADY_EXISTS - A portal is already registered at this position
     */
    public RegisterResult registerPortal(String address, BlockPos pos, String axis) {
        // Check if there's already a portal at this position
        if (portalsByPosition.containsKey(pos)) {
            return RegisterResult.FAILED_ALREADY_EXISTS;
        }

        // Check the address
        BlockPos[] existing = portalsByAddress.get(address);

        if (existing == null) {
            // First portal with this address - register as unlinked
            PortalEntry entry = new PortalEntry(address, pos, axis, null);
            portalsByPosition.put(pos, entry);
            portalsByAddress.put(address, new BlockPos[]{pos, null});
            setDirty();

            Ideomod.LOGGER.info("Portal registered at {} with address [{}] - unlinked",
                    pos, address);
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
        PortalEntry newEntry = new PortalEntry(address, pos, axis, partnerPos);
        portalsByPosition.put(pos, newEntry);

        // Update partner to link back to us
        partnerEntry.partnerPos = pos;

        // Update address lookup
        existing[1] = pos;

        setDirty();

        Ideomod.LOGGER.info("Portal registered at {} with address [{}] - LINKED to partner at {}",
                pos, address, partnerPos);
        return RegisterResult.REGISTERED_LINKED;
    }

    /**
     * Unregisters a portal (when its frame is broken or deactivated).
     * If it was linked, the partner becomes unlinked again.
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
            if (pos.equals(addressEntries[0]) && pos.equals(addressEntries[1])) {
                // Both entries are this pos (shouldn't happen but safety)
                portalsByAddress.remove(entry.address);
            } else if (pos.equals(addressEntries[0])) {
                addressEntries[0] = addressEntries[1];
                addressEntries[1] = null;
                if (addressEntries[0] == null) {
                    portalsByAddress.remove(entry.address);
                }
            } else if (pos.equals(addressEntries[1])) {
                addressEntries[1] = null;
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

            PortalEntry entry = new PortalEntry(address, pos, axis, partnerPos);
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
