package com.ideomancer.ideomod.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Stateless utility class that executes scan algorithms.
 *
 * Given a BlockPos, a StructureDefinition, and a Level, the scanner examines
 * blocks in the world and returns a StructureResult describing what it found.
 *
 * The scanner dispatches to the correct algorithm via a strategy registry.
 * Currently only PATTERN is implemented (for the furnace array).
 * PROXIMITY and PROXIMITY_WALLED are stubs for future structures.
 *
 * Updated by Claude to use strategy pattern for modularity.
 */
public class StructureScanner {

    private static final java.util.Map<StructureDefinition.ScanMode, ScanStrategy> strategies =
            new java.util.EnumMap<>(StructureDefinition.ScanMode.class);

    static {
        strategies.put(StructureDefinition.ScanMode.PATTERN, new PatternScanStrategy());
    }

    /**
     * Register a custom scan strategy for a given mode.
     * Called at mod init to install future scan modes.
     */
    public static void registerStrategy(StructureDefinition.ScanMode mode, ScanStrategy strategy) {
        strategies.put(mode, strategy);
    }

    /**
     * Main entry point. Dispatches to the correct scan algorithm based on
     * the definition's scan mode, using the registered strategy.
     *
     * @param corePos    The position that triggered the scan (a core block)
     * @param definition The structure definition describing what to look for
     * @param level      The world
     * @return A StructureResult — check isValid() to see if a structure was found
     */
    public static StructureResult scan(BlockPos corePos, StructureDefinition definition, Level level) {
        ScanStrategy strategy = strategies.get(definition.getScanMode());
        if (strategy == null) {
            throw new UnsupportedOperationException(
                    definition.getScanMode() + " scan not yet implemented");
        }
        return strategy.scan(corePos, definition, level);
    }

}
