package com.ideomancer.ideomod.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for framed block data during piston transit.
 *
 * When a piston starts moving a FramedBlock, PistonBaseBlockClientMixin
 * captures the inner block ID and facing direction BEFORE vanilla destroys
 * the block entity, and stores them here keyed by destination position.
 *
 * PistonMovingBlockEntityRendererMixin reads from this cache to know which
 * inner block to render and what facing to use during the 2-tick sliding
 * animation.
 *
 * CLIENT-SIDE ONLY. Never access from server code.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public final class MovingFrameData {

    /**
     * Data stored per framed block in transit.
     * @param innerBlockId Registry name of the inner block (e.g. "minecraft:stone")
     * @param facing       The framed block's FACING direction (for directional inner blocks)
     * @param innerSticky  The framed block's INNER_STICKY value (for constructing correct inner state)
     */
    public record FrameTransitInfo(ResourceLocation innerBlockId, Direction facing, int innerSticky) {}

    /**
     * Map of destination position -> transit info.
     * ConcurrentHashMap because render thread and main thread may access
     * simultaneously.
     */
    private static final Map<BlockPos, FrameTransitInfo> TRANSIT_DATA = new ConcurrentHashMap<>();

    private MovingFrameData() {}

    /**
     * Store transit data for a framed block about to enter piston transit.
     *
     * @param destPos      The position where the block will arrive after the move
     * @param innerBlockId The inner block's registry name
     * @param facing       The framed block's FACING property value
     * @param innerSticky  The framed block's INNER_STICKY property value
     */
    public static void store(BlockPos destPos, ResourceLocation innerBlockId,
                              Direction facing, int innerSticky) {
        TRANSIT_DATA.put(destPos.immutable(), new FrameTransitInfo(innerBlockId, facing, innerSticky));
    }

    /**
     * Look up transit data for a position currently in piston transit.
     *
     * @param pos The position of the MovingPistonBlock (destination position)
     * @return The transit info, or null if not a framed block
     */
    @Nullable
    public static FrameTransitInfo get(BlockPos pos) {
        return TRANSIT_DATA.get(pos);
    }

    /**
     * Remove data for a position.
     */
    public static void remove(BlockPos pos) {
        TRANSIT_DATA.remove(pos);
    }

    /**
     * Clear all transit data.
     */
    public static void clearAll() {
        TRANSIT_DATA.clear();
    }

    public static int size() {
        return TRANSIT_DATA.size();
    }
}
