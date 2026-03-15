package com.ideomancer.ideomod.block.machine;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Machine Frame -- the universal connector in the Machine Family.
 *
 * All shared behavior (GUI, drops, clone, block entity, lifecycle hooks,
 * stickiness) is inherited from AbstractMachineBlock. Machine Frame adds
 * only the static utility methods that other systems reference.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFrameBlock extends AbstractMachineBlock {

    public MachineFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.FRAME;
    }

    // ========================================================================
    // MACHINE FAMILY HELPERS
    // ========================================================================

    public static boolean isMachineFamily(Block block) {
        return IMachineFamily.isMachineFamily(block);
    }

    /**
     * Returns true for blocks that carry isolated machine-internal redstone
     * (Track's POWER system). The Conductor is NOT included here because it
     * carries vanilla redstone signal, not Track-internal signal.
     */
    public static boolean isRedstoneConduit(Block block) {
        return block instanceof MachineTrackBlock;
    }
}
