package com.ideomancer.ideomod.block.piston;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The odd force beams currently showing on the client: one per Repulser/Attractor
 * that has just fired. Plain data with no client classes, so the shared firing
 * code can call {@link #start} safely on either side; on the server it does nothing.
 *
 * Drawn by {@code client.render.OddForceBeamRenderer}.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public final class OddForceBeams {

    /** How long a beam shows, in ticks: the length of the push or pull (the 2-tick slide). */
    public static final int LIFETIME = 2;

    /**
     * @param pos       the device
     * @param facing    the direction it scans
     * @param repulse   true for a Repulser (force away from the device), false for an Attractor
     * @param startTick the client game time it fired
     */
    public record Beam(BlockPos pos, Direction facing, boolean repulse, long startTick) {}

    private static final Map<BlockPos, Beam> ACTIVE = new ConcurrentHashMap<>();

    private OddForceBeams() {}

    /** Called when a device fires and finds Oddstone in its line. Client side only. */
    public static void start(Level level, BlockPos pos, Direction facing, boolean repulse) {
        if (!level.isClientSide) return;
        BlockPos key = pos.immutable();
        ACTIVE.put(key, new Beam(key, facing, repulse, level.getGameTime()));
    }

    public static Collection<Beam> active() {
        return ACTIVE.values();
    }

    /** Drops beams that have finished, or that belong to another world's clock. */
    public static void prune(long gameTime) {
        ACTIVE.values().removeIf(b -> gameTime < b.startTick() || gameTime - b.startTick() > LIFETIME);
    }
}
