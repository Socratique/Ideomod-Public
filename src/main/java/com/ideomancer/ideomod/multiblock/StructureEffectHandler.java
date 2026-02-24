package com.ideomancer.ideomod.multiblock;

import net.minecraft.server.level.ServerLevel;

/**
 * Interface for structure types that need to actively modify the world
 * when their lifecycle state changes.
 *
 * Most structure types do NOT need an EffectHandler — they exist as passive
 * registrations that other systems query via the StructureManager's API.
 * Only implement this when the structure must push state changes into block
 * entities or the world at formation/deregistration time.
 *
 * Example: The furnace array uses an EffectHandler to stamp an NBT flag onto
 * furnace block entities so the speed-boost mixin can read it cheaply per tick.
 * A brewery would NOT need one — the brewing GUI system queries the Manager
 * on-demand when the player opens the brewing stand.
 */
public interface StructureEffectHandler {

    /**
     * Called when a valid structure is first detected and registered.
     *
     * @param result The scan result describing the newly formed structure
     * @param level  The server level containing the structure
     */
    void onFormed(StructureResult result, ServerLevel level);

    /**
     * Called when a registered structure is rescanned and still valid,
     * but the scan result has changed (different component positions,
     * different contributor totals, etc.).
     *
     * The handler should diff old vs new and adjust accordingly —
     * clearing effects on components that left, applying effects on
     * components that joined.
     *
     * @param oldResult The previous scan result before the change
     * @param newResult The new scan result after the change
     * @param level     The server level containing the structure
     */
    void onUpdated(StructureResult oldResult, StructureResult newResult, ServerLevel level);

    /**
     * Called when a structure becomes invalid and is being deregistered.
     * The handler should clean up all effects it applied — remove NBT flags,
     * clear custom names, etc.
     *
     * @param lastResult The last valid scan result before deregistration
     * @param level      The server level containing the structure
     */
    void onRemoved(StructureResult lastResult, ServerLevel level);
}
