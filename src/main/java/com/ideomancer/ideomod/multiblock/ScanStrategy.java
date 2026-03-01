package com.ideomancer.ideomod.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Strategy interface for multiblock structure scanning algorithms.
 * Each scan mode implements this interface to provide its own detection logic.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@FunctionalInterface
public interface ScanStrategy {
    StructureResult scan(BlockPos corePos, StructureDefinition definition, Level level);
}
