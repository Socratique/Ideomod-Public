package com.ideomancer.ideomod.block.machine;

/**
 * Machine Trolley -- passive cargo carrier pushed by Tracks.
 *
 * All shared behavior (GUI, drops, clone, block entity, lifecycle hooks,
 * stickiness, specialization swap) is inherited from AbstractMachineBlock.
 * Trolley has no unique behavior beyond its machine type.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class MachineTrolleyBlock extends AbstractMachineBlock {

    public MachineTrolleyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.TROLLEY;
    }
}
