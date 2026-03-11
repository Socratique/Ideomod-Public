package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.frame.FrameActivation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Interface for all Machine Family blocks (Machine Frame, Track, Trolley,
 * and future specializations).
 *
 * Provides type-aware stickiness behavior and family membership check.
 * Parallel to IFrameBlock for the frame family.
 *
 * STICKINESS MODEL:
 * =================
 * - Machine Frame (FRAME type) sticks to ALL machine types.
 * - Specializations stick to SAME type + FRAME only.
 * - All types stick to Frame Family (when active).
 * - All types are permissive to Slime/Honey/OddSlime (so grab immunity
 *   rules can selectively block the reverse direction).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public interface IMachineFamily {

    /**
     * Returns this block's machine type for stickiness grouping.
     * Machine Frame returns FRAME (universal connector).
     * Each specialization returns its specific type.
     */
    MachineType getMachineType();

    /**
     * Check if a block is part of the Machine Family.
     */
    static boolean isMachineFamily(Block block) {
        return block instanceof IMachineFamily;
    }

    /**
     * Get the MachineType of a block, or null if not a Machine Family block.
     */
    static MachineType getMachineType(Block block) {
        if (block instanceof IMachineFamily machine) {
            return machine.getMachineType();
        }
        return null;
    }

    /**
     * Type-aware canStickTo logic for all Machine Family members.
     *
     * @param myType The MachineType of the block asking "can I stick to other?"
     * @param other  The blockstate of the neighbor being checked
     * @return true if the pair should stick
     */
    static boolean canMachineFamilyStickTo(MachineType myType, BlockState other) {
        Block otherBlock = other.getBlock();

        // Machine Family: type-aware check
        if (otherBlock instanceof IMachineFamily otherMachine) {
            return myType.canStickToMachineType(otherMachine.getMachineType());
        }

        // Frame Family: must be active
        if (FrameActivation.isFrameFamily(otherBlock)) {
            return FrameActivation.isActive(other);
        }

        // Slime types: permissive (grab immunity rules handle the restriction)
        if (otherBlock instanceof SlimeBlock) return true;
        if (otherBlock instanceof HoneyBlock) return true;
        if (otherBlock instanceof OddSlimeBlock) return true;

        return false;
    }
}
