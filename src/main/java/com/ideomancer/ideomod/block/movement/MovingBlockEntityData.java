package com.ideomancer.ideomod.block.movement;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static registry for block entity data that is in transit during piston moves.
 *
 * When a block with an IPistonMovable block entity is moved by a piston,
 * the block entity's NBT is saved here keyed by DESTINATION position.
 * After the 2-tick animation, PistonMovingBlockEntityMixin retrieves
 * and restores the data into the fresh block entity.
 *
 * CRITICAL: Only PistonMovingBlockEntityMixin should call retrieve().
 * retrieve() CONSUMES (removes) the data. If anything else calls it
 * first (e.g. Block.onPlace()), the data is lost before the mixin
 * can restore it. This was the cause of the original data loss bug.
 *
 * CRITICAL: Only SERVER-side code should call store().
 * MovingBlockEntityData is a shared static map. If client-side code
 * stores data (e.g. from a client block entity with empty inventory),
 * it overwrites the server's valid data. This was the cause of the
 * "client overwrites server" bug.
 *
 * KEY FORMAT (Audit #K):
 * ======================
 * Keys use pipe '|' delimiters between dimension and coordinates, and
 * semicolons ';' between coordinate components:
 *   "minecraft:overworld|0;64;0"
 * Previously used ":" and "," which could collide with modded dimension
 * names containing those characters (e.g., "mymod:my:dimension").
 * Pipe and semicolon are not valid in ResourceLocation strings, so
 * collisions are impossible.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MovingBlockEntityData {

    private static final Map<String, CompoundTag> pendingData = new ConcurrentHashMap<>();

    public static void store(String dimensionKey, BlockPos destPos, CompoundTag data) {
        String key = makeKey(dimensionKey, destPos);
        pendingData.put(key, data);
        Ideomod.LOGGER.debug("[MovingBEData] STORE key={} dataSize={} mapSize={}",
                key, data.size(), pendingData.size());
    }

    public static CompoundTag retrieve(String dimensionKey, BlockPos pos) {
        String key = makeKey(dimensionKey, pos);
        CompoundTag result = pendingData.remove(key);
        if (result != null) {
            Ideomod.LOGGER.debug("[MovingBEData] RETRIEVE SUCCESS key={} dataSize={}",
                    key, result.size());
        }
        return result;
    }

    public static boolean hasPending(String dimensionKey, BlockPos pos) {
        return pendingData.containsKey(makeKey(dimensionKey, pos));
    }

    /**
     * Build a unique key from dimension + position.
     * Uses '|' between dimension and coords, ';' between x/y/z.
     * Neither character is valid in ResourceLocation strings, so
     * no modded dimension name can create ambiguous keys.
     */
    private static String makeKey(String dimensionKey, BlockPos pos) {
        return dimensionKey + "|" + pos.getX() + ";" + pos.getY() + ";" + pos.getZ();
    }
}
