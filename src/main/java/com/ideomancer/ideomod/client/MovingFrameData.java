package com.ideomancer.ideomod.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for framed block inner block IDs during piston transit.
 *
 * When a piston starts moving a FramedBlock, PistonBaseBlockClientMixin
 * captures the inner block ID BEFORE vanilla destroys the block entity,
 * and stores it here keyed by destination position.
 *
 * PistonMovingBlockEntityRendererMixin reads from this cache to know which
 * inner block texture to render during the 2-tick sliding animation.
 *
 * Data is consumed (removed) when the animation completes and the real
 * block is placed back. A safety sweep clears stale entries periodically
 * in case of edge cases (e.g., piston retraction cancels mid-animation).
 *
 * This is completely separate from the server-side MovingBlockEntityData
 * system. That system preserves data for block entity restoration after
 * the move completes. This system only exists so the CLIENT RENDERER
 * can draw the inner texture during transit.
 *
 * CLIENT-SIDE ONLY. Never access from server code.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public final class MovingFrameData {

    /**
     * Map of destination position -> inner block ResourceLocation.
     * ConcurrentHashMap because render thread and main thread may access
     * simultaneously (Minecraft's render loop is on a separate thread
     * from the game loop in some configurations).
     */
    private static final Map<BlockPos, ResourceLocation> TRANSIT_DATA = new ConcurrentHashMap<>();

    private MovingFrameData() {} // Static utility, no instances

    /**
     * Store the inner block ID for a framed block about to enter piston transit.
     * Called by PistonBaseBlockClientMixin at HEAD of moveBlocks() on the client.
     *
     * @param destPos      The position where the block will arrive after the move
     * @param innerBlockId The inner block's registry name (e.g. "minecraft:stone")
     */
    public static void store(BlockPos destPos, ResourceLocation innerBlockId) {
        // Use immutable copy of BlockPos -- the BlockPos passed in may be mutable
        TRANSIT_DATA.put(destPos.immutable(), innerBlockId);
    }

    /**
     * Look up the inner block ID for a position currently in piston transit.
     * Called by PistonMovingBlockEntityRendererMixin during rendering.
     *
     * @param pos The position of the MovingPistonBlock (destination position)
     * @return The inner block ResourceLocation, or null if not a framed block
     */
    @Nullable
    public static ResourceLocation get(BlockPos pos) {
        return TRANSIT_DATA.get(pos);
    }

    /**
     * Remove data for a position. Called when the piston animation completes
     * and the real block is placed back (the BESR takes over rendering again).
     *
     * @param pos The position to clear
     */
    public static void remove(BlockPos pos) {
        TRANSIT_DATA.remove(pos);
    }

    /**
     * Clear all transit data. Safety valve for dimension changes, world unloads,
     * or other situations where stale data might accumulate.
     */
    public static void clearAll() {
        TRANSIT_DATA.clear();
    }

    /**
     * Check if there's any transit data stored (useful for debug).
     */
    public static int size() {
        return TRANSIT_DATA.size();
    }
}
