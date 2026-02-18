package com.ideomancer.ideomod.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent tracking record for a structure that exists in the world.
 *
 * While StructureResult is a stateless scan snapshot, StructureRegistration
 * is the live bookkeeping object the StructureManager maintains. It wraps
 * the most recent valid scan result and adds Manager-level data: a unique ID,
 * expanded bounding box for update detection, and chunk overlap tracking.
 *
 * This class handles its own NBT serialization for persistence in SavedData.
 */
public class StructureRegistration {

    private final UUID id;
    private final String definitionName;
    private StructureResult currentResult;

    // Structure bounding box (copied from result for quick access)
    private BlockPos boundsMin;
    private BlockPos boundsMax;

    // Expanded bounds = structure bounds + 1 block shell on all sides.
    // Any block change inside this zone triggers a rescan.
    private BlockPos expandedMin;
    private BlockPos expandedMax;

    // Every chunk the expanded bounds overlap — used for chunk→structure indexing
    private final Set<ChunkPos> overlappingChunks;

    /**
     * Creates a new registration from a valid scan result.
     */
    public StructureRegistration(UUID id, String definitionName, StructureResult result) {
        this.id = id;
        this.definitionName = definitionName;
        this.currentResult = result;
        this.overlappingChunks = new HashSet<>();
        updateBounds(result);
    }

    /**
     * Updates the registration with a new scan result. Recalculates bounds
     * and chunk overlaps. Called by the Manager after a successful rescan.
     */
    public void updateResult(StructureResult newResult) {
        this.currentResult = newResult;
        updateBounds(newResult);
    }

    /**
     * Recalculates bounding box, expanded bounds, and chunk overlaps
     * from the given result.
     */
    private void updateBounds(StructureResult result) {
        this.boundsMin = result.getBoundsMin();
        this.boundsMax = result.getBoundsMax();

        // Expand by 1 in every direction for the watch zone
        this.expandedMin = boundsMin.offset(-1, -1, -1);
        this.expandedMax = boundsMax.offset(1, 1, 1);

        // Recalculate which chunks the expanded bounds overlap
        this.overlappingChunks.clear();
        int minChunkX = expandedMin.getX() >> 4;
        int maxChunkX = expandedMax.getX() >> 4;
        int minChunkZ = expandedMin.getZ() >> 4;
        int maxChunkZ = expandedMax.getZ() >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                overlappingChunks.add(new ChunkPos(cx, cz));
            }
        }
    }

    /**
     * Checks if a world position falls inside this structure's expanded
     * bounding box (the watch zone). Used by the Manager to decide whether
     * a block change should trigger a rescan.
     *
     * This is 6 integer comparisons — extremely cheap.
     */
    public boolean isInExpandedBounds(BlockPos pos) {
        return pos.getX() >= expandedMin.getX() && pos.getX() <= expandedMax.getX()
            && pos.getY() >= expandedMin.getY() && pos.getY() <= expandedMax.getY()
            && pos.getZ() >= expandedMin.getZ() && pos.getZ() <= expandedMax.getZ();
    }

    /**
     * Checks if a world position falls inside this structure's actual
     * bounding box (not expanded). Used by query API.
     */
    public boolean isInBounds(BlockPos pos) {
        return pos.getX() >= boundsMin.getX() && pos.getX() <= boundsMax.getX()
            && pos.getY() >= boundsMin.getY() && pos.getY() <= boundsMax.getY()
            && pos.getZ() >= boundsMin.getZ() && pos.getZ() <= boundsMax.getZ();
    }

    // ========================================================================
    // Getters
    // ========================================================================

    public UUID getId() {
        return id;
    }

    public String getDefinitionName() {
        return definitionName;
    }

    public StructureResult getCurrentResult() {
        return currentResult;
    }

    public BlockPos getBoundsMin() {
        return boundsMin;
    }

    public BlockPos getBoundsMax() {
        return boundsMax;
    }

    public BlockPos getExpandedMin() {
        return expandedMin;
    }

    public BlockPos getExpandedMax() {
        return expandedMax;
    }

    public Set<ChunkPos> getOverlappingChunks() {
        return overlappingChunks;
    }

    // ========================================================================
    // NBT Serialization
    // ========================================================================

    private static final String TAG_ID_MOST = "IdMost";
    private static final String TAG_ID_LEAST = "IdLeast";
    private static final String TAG_DEFINITION = "Definition";
    private static final String TAG_CORE_POS = "CorePos";
    private static final String TAG_FACING = "Facing";
    private static final String TAG_FURNACE_TYPE = "FurnaceType";
    private static final String TAG_CLUSTER_MIN = "ClusterMin";
    private static final String TAG_CLUSTER_MAX = "ClusterMax";
    private static final String TAG_BOUNDS_MIN = "BoundsMin";
    private static final String TAG_BOUNDS_MAX = "BoundsMax";
    private static final String TAG_EFFECTORS = "Effectors";
    private static final String TAG_CONNECTORS = "Connectors";
    private static final String TAG_AIR = "Air";

    /**
     * Serializes this registration to NBT for SavedData persistence.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        StructureResult result = this.currentResult;

        // UUID — stored as two longs
        tag.putLong(TAG_ID_MOST, id.getMostSignificantBits());
        tag.putLong(TAG_ID_LEAST, id.getLeastSignificantBits());

        // Definition name
        tag.putString(TAG_DEFINITION, definitionName);

        // Core position
        tag.putLong(TAG_CORE_POS, result.getCorePos().asLong());

        // Facing direction (nullable)
        if (result.getFacing() != null) {
            tag.putByte(TAG_FACING, (byte) result.getFacing().get3DDataValue());
        }

        // Furnace type (nullable)
        if (result.getFurnaceType() != null) {
            tag.putString(TAG_FURNACE_TYPE, result.getFurnaceType().toString());
        }

        // Cluster bounds (nullable)
        if (result.getClusterMin() != null && result.getClusterMax() != null) {
            tag.putLong(TAG_CLUSTER_MIN, result.getClusterMin().asLong());
            tag.putLong(TAG_CLUSTER_MAX, result.getClusterMax().asLong());
        }

        // Structure bounds
        if (result.getBoundsMin() != null && result.getBoundsMax() != null) {
            tag.putLong(TAG_BOUNDS_MIN, result.getBoundsMin().asLong());
            tag.putLong(TAG_BOUNDS_MAX, result.getBoundsMax().asLong());
        }

        // Position lists — stored as LongArrayTags for compactness
        tag.put(TAG_EFFECTORS, posListToLongArray(result.getEffectorPositions()));
        tag.put(TAG_CONNECTORS, posListToLongArray(result.getConnectorPositions()));
        tag.put(TAG_AIR, posListToLongArray(result.getAirPositions()));

        return tag;
    }

    /**
     * Deserializes a registration from NBT. Rebuilds the StructureResult
     * and recalculates expanded bounds and chunk overlaps.
     *
     * @return The restored registration, or null if the data is malformed
     */
    @Nullable
    public static StructureRegistration load(CompoundTag tag) {
        try {
            // UUID
            UUID id = new UUID(tag.getLong(TAG_ID_MOST), tag.getLong(TAG_ID_LEAST));

            // Definition name
            String defName = tag.getString(TAG_DEFINITION);
            if (defName.isEmpty()) return null;

            // Core position
            BlockPos corePos = BlockPos.of(tag.getLong(TAG_CORE_POS));

            // Begin building the result
            StructureResult.Builder builder = new StructureResult.Builder(defName, corePos);
            builder.setValid(true);

            // Facing (optional)
            if (tag.contains(TAG_FACING)) {
                builder.setFacing(Direction.from3DDataValue(tag.getByte(TAG_FACING)));
            }

            // Furnace type (optional)
            if (tag.contains(TAG_FURNACE_TYPE)) {
                builder.setFurnaceType(new ResourceLocation(tag.getString(TAG_FURNACE_TYPE)));
            }

            // Cluster bounds (optional)
            if (tag.contains(TAG_CLUSTER_MIN) && tag.contains(TAG_CLUSTER_MAX)) {
                builder.setClusterBounds(
                        BlockPos.of(tag.getLong(TAG_CLUSTER_MIN)),
                        BlockPos.of(tag.getLong(TAG_CLUSTER_MAX))
                );
            }

            // Structure bounds
            if (tag.contains(TAG_BOUNDS_MIN) && tag.contains(TAG_BOUNDS_MAX)) {
                builder.setStructureBounds(
                        BlockPos.of(tag.getLong(TAG_BOUNDS_MIN)),
                        BlockPos.of(tag.getLong(TAG_BOUNDS_MAX))
                );
            }

            // Position lists
            for (BlockPos pos : longArrayToPosList(tag, TAG_EFFECTORS)) {
                builder.addEffector(pos);
            }
            for (BlockPos pos : longArrayToPosList(tag, TAG_CONNECTORS)) {
                builder.addConnector(pos);
            }
            for (BlockPos pos : longArrayToPosList(tag, TAG_AIR)) {
                builder.addAir(pos);
            }

            StructureResult result = builder.build();

            // Reject results without bounds — updateBounds() would NPE
            if (result.getBoundsMin() == null || result.getBoundsMax() == null) {
                return null;
            }

            return new StructureRegistration(id, defName, result);

        } catch (Exception e) {
            // Malformed data — return null and let the Manager skip it
            return null;
        }
    }

    // ========================================================================
    // NBT Helpers
    // ========================================================================

    private static LongArrayTag posListToLongArray(List<BlockPos> positions) {
        long[] longs = new long[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            longs[i] = positions.get(i).asLong();
        }
        return new LongArrayTag(longs);
    }

    private static List<BlockPos> longArrayToPosList(CompoundTag parent, String key) {
        List<BlockPos> list = new ArrayList<>();
        if (parent.contains(key, Tag.TAG_LONG_ARRAY)) {
            long[] longs = parent.getLongArray(key);
            for (long l : longs) {
                list.add(BlockPos.of(l));
            }
        }
        return list;
    }
}
