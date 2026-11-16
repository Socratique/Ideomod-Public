package com.ideomancer.ideomod.block.movement;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * Implemented by vanilla's PistonMovingBlockEntity (through PistonMovingBlockEntityMixin).
 *
 * A moving block's machine data normally waits in MovingBlockEntityData, which lives only in
 * memory. The moving block also writes that data into its own saved NBT, so a world saved
 * and closed mid-move (or a chunk unloaded mid-move) still has it when the block lands.
 * This is the copy read back from the save.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public interface MovingDataHolder {

    /** The machine data this moving block was saved with, or null. */
    @Nullable
    CompoundTag ideomod$getSavedMoveData();

    void ideomod$clearSavedMoveData();
}
