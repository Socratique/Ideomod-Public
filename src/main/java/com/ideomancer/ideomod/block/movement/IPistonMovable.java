package com.ideomancer.ideomod.block.movement;

import net.minecraft.nbt.CompoundTag;

/**
 * Marker interface for block entities that can survive piston movement.
 *
 * Vanilla treats all block entities as immovable by pistons. When a block
 * with a block entity IS forcibly moved (by mods), the block entity is
 * destroyed at the source and a fresh empty one is created at the destination.
 * Any stored data (inventory, settings, etc.) is lost.
 *
 * Block entities that implement this interface opt into Ideomod's block entity
 * movement system in BlockMoveHelper. During a piston move:
 *   1. BEFORE the move: BlockMoveHelper calls saveMovementData() to capture
 *      the block entity's state as NBT
 *   2. The block is moved via vanilla's MovingPistonBlock animation (2 ticks)
 *   3. AFTER the move: When the block lands at its destination and a new block
 *      entity is created, BlockMoveHelper restores the saved NBT via
 *      loadMovementData()
 *
 * This is an OPT-IN system. Only Ideomod block entities that implement this
 * interface get the save/restore treatment. Vanilla block entities (chests,
 * furnaces, etc.) are completely unaffected -- they follow normal vanilla
 * rules (immovable by default).
 *
 * IMPORTANT: The block's class must also override getPistonPushReaction()
 * to return PushReaction.NORMAL. Without that, vanilla won't even attempt
 * to move the block, and this interface never comes into play.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public interface IPistonMovable {

    /**
     * Save this block entity's data for transit during a piston move.
     * Called by BlockMoveHelper BEFORE the block is replaced with
     * MovingPistonBlock.
     *
     * Implementations should save all data that needs to survive the move.
     * For MachineFrameBlockEntity, this is the specialization item.
     *
     * @return CompoundTag containing the block entity's movement-critical data
     */
    CompoundTag saveMovementData();

    /**
     * Restore this block entity's data after arriving at its destination.
     * Called by BlockMoveHelper's restoration system after the
     * MovingPistonBlock animation completes and the real block is placed.
     *
     * @param tag The CompoundTag previously returned by saveMovementData()
     */
    void loadMovementData(CompoundTag tag);
}
