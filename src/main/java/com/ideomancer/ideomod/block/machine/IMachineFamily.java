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
 * Provides common stickiness behavior and family membership check.
 * Parallel to IFrameBlock for the frame family.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public interface IMachineFamily {

    /**
     * Check if a block is part of the Machine Family.
     */
    static boolean isMachineFamily(Block block) {
        return block instanceof IMachineFamily;
    }

    /**
     * Shared canStickTo logic for all Machine Family members.
     * Frame family blocks must be ACTIVE to stick to machine blocks.
     */
    static boolean canMachineFamilyStickTo(BlockState other) {
        Block otherBlock = other.getBlock();
        if (isMachineFamily(otherBlock)) return true;
        if (FrameActivation.isFrameFamily(otherBlock)) {
            return FrameActivation.isActive(other);
        }
        if (otherBlock instanceof SlimeBlock) return true;
        if (otherBlock instanceof HoneyBlock) return true;
        if (otherBlock instanceof OddSlimeBlock) return true;
        return false;
    }
}
