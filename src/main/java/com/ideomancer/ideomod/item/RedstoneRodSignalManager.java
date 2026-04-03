package com.ideomancer.ideomod.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages phantom redstone signals injected by the Redstone Rod.
 *
 * Active pulses are stored in a static map keyed by (dimension, sourcePos, direction).
 * LevelSignalMixin queries this map in Level.getSignal() to make vanilla and modded
 * blocks see the injected signal. A server tick handler counts down each pulse and
 * triggers the "signal off" neighbor update when it expires.
 *
 * Pulse lifetime: 2 game ticks = 1 redstone tick.
 *
 * Thread safety: ConcurrentHashMap handles concurrent reads from the render thread
 * (client-side getSignal calls) and writes from the server tick.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mod.EventBusSubscriber(modid = "ideomod")
public class RedstoneRodSignalManager {

    /** Duration of a rod pulse in game ticks (1 redstone tick = 2 game ticks). */
    private static final int PULSE_DURATION_TICKS = 2;

    /** Signal strength emitted by the rod. */
    private static final int SIGNAL_STRENGTH = 15;

    /** Active pulse registry. */
    private static final Map<PulseKey, PulseData> ACTIVE_PULSES = new ConcurrentHashMap<>();

    // ── Pulse key ───────────────────────────────────────────────────────

    /**
     * Composite key for an active pulse.
     * sourcePos is the position the signal appears to emanate FROM
     * (i.e. the block adjacent to the target on the clicked face side).
     * direction is the Direction parameter that Level.getSignal() will be
     * called with when the target block queries this source position.
     */
    private static final class PulseKey {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        final Direction direction;

        PulseKey(ResourceKey<Level> dimension, BlockPos pos, Direction direction) {
            this.dimension = dimension;
            this.pos = pos;
            this.direction = direction;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PulseKey other)) return false;
            return dimension.equals(other.dimension)
                    && pos.equals(other.pos)
                    && direction == other.direction;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimension, pos, direction);
        }
    }

    /**
     * Data attached to each active pulse: remaining ticks and the target
     * block position (needed for the "off" neighbor update).
     */
    private static final class PulseData {
        int ticksRemaining;
        final BlockPos targetPos;

        PulseData(int ticksRemaining, BlockPos targetPos) {
            this.ticksRemaining = ticksRemaining;
            this.targetPos = targetPos;
        }
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Injects a signal-15 pulse on the given face of the target block.
     * Called from RedstoneRodItem.onItemUseFirst() on the server side.
     *
     * @param level       the server level
     * @param targetPos   the block that was right-clicked
     * @param clickedFace the face of the target that was clicked
     */
    public static void injectPulse(Level level, BlockPos targetPos, Direction clickedFace) {
        // The "source" position: where the signal appears to come from.
        // When the target checks getSignal(target.relative(clickedFace), clickedFace),
        // our mixin intercepts at sourcePos with the matching direction.
        BlockPos sourcePos = targetPos.relative(clickedFace).immutable();
        BlockPos immutableTarget = targetPos.immutable();

        PulseKey key = new PulseKey(level.dimension(), sourcePos, clickedFace);
        ACTIVE_PULSES.put(key, new PulseData(PULSE_DURATION_TICKS, immutableTarget));

        // Trigger the "signal on" update so the target block re-evaluates its state.
        // neighborChanged tells the target "something changed at sourcePos".
        level.neighborChanged(immutableTarget, Blocks.REDSTONE_BLOCK, sourcePos);

        // Notify all blocks around the SOURCE position that a signal source appeared.
        // This triggers neighborChanged on the target (and other adjacent blocks) through
        // vanilla's standard updateNeighborsAt path — a different code path from the
        // direct neighborChanged above. Some blocks (e.g. pistons, observers) may only
        // respond to one notification style or the other.
        level.updateNeighborsAt(sourcePos, Blocks.REDSTONE_BLOCK);

        // Also update blocks around the target that might care about indirect changes
        // (e.g. redstone dust adjacent to a lamp that just turned on).
        level.updateNeighborsAt(immutableTarget, level.getBlockState(immutableTarget).getBlock());
    }

    /**
     * Queries whether there is an active rod pulse at the given position and direction.
     * Called from LevelSignalMixin during Level.getSignal().
     *
     * @param level     the level being queried
     * @param pos       the position being queried (the "source" position)
     * @param direction the direction being queried
     * @return 15 if there is an active pulse, 0 otherwise
     */
    public static int getInjectedSignal(Level level, BlockPos pos, Direction direction) {
        if (ACTIVE_PULSES.isEmpty()) return 0; // fast path
        PulseKey key = new PulseKey(level.dimension(), pos, direction);
        return ACTIVE_PULSES.containsKey(key) ? SIGNAL_STRENGTH : 0;
    }

    // ── Tick handler ────────────────────────────────────────────────────

    /**
     * Server tick handler. Counts down active pulses and triggers "off"
     * neighbor updates when they expire.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ACTIVE_PULSES.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Collect expired pulses
        List<Map.Entry<PulseKey, PulseData>> expired = new ArrayList<>();
        Iterator<Map.Entry<PulseKey, PulseData>> it = ACTIVE_PULSES.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<PulseKey, PulseData> entry = it.next();
            PulseData data = entry.getValue();
            data.ticksRemaining--;
            if (data.ticksRemaining <= 0) {
                expired.add(entry);
                it.remove();
            }
        }

        // Trigger "signal off" updates for expired pulses
        for (Map.Entry<PulseKey, PulseData> entry : expired) {
            PulseKey key = entry.getKey();
            PulseData data = entry.getValue();
            ServerLevel level = server.getLevel(key.dimension);
            if (level != null) {
                // Notify the target block that the signal source has changed
                level.neighborChanged(data.targetPos, Blocks.AIR, key.pos);
                // Notify all blocks around the source position (mirror of injectPulse)
                level.updateNeighborsAt(key.pos, Blocks.AIR);
                level.updateNeighborsAt(data.targetPos, level.getBlockState(data.targetPos).getBlock());
            }
        }
    }

    /**
     * Clears all active pulses. Called on server stop to prevent stale references.
     */
    public static void clearAll() {
        ACTIVE_PULSES.clear();
    }
}
