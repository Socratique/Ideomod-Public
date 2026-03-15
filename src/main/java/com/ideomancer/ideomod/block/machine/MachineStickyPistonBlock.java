package com.ideomancer.ideomod.block.machine;

/**
 * Machine Sticky Piston -- omnidirectional sticky piston as a Machine Family specialization.
 *
 * Extends MachinePistonBlock with sticky retraction behavior:
 * when retracting, pulls the block in front of the head one space backward.
 *
 * All extension behavior, signal detection, block events, and head management
 * are inherited from MachinePistonBlock. Only isSticky() and getMachineType()
 * differ.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineStickyPistonBlock extends MachinePistonBlock {

    public MachineStickyPistonBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.STICKY_PISTON;
    }

    @Override
    public boolean isSticky() {
        return true;
    }
}
