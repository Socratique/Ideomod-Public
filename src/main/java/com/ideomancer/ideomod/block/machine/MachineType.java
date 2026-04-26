package com.ideomancer.ideomod.block.machine;

/**
 * Identifies each Machine Family specialization type for stickiness grouping.
 *
 * Stickiness rule: a specialization sticks to blocks of the SAME type,
 * to FRAME (the universal connector), and to Frame Family blocks (when active).
 * FRAME sticks to ALL machine types.
 *
 * Adding a new specialization: add an enum value here, register the
 * item-to-block mapping in MachineSwapHelper, and implement IMachineFamily
 * with getMachineType() returning the new value.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public enum MachineType {

    /** Machine Frame -- universal connector. Sticks to all machine types. */
    FRAME,

    /** Machine Conductor -- pure signal/stickiness relay. */
    CONDUCTOR,

    /** Machine Pipe -- directional item transport. */
    PIPE,

    /** Machine Fluid Pipe -- fluid vacuum and depositor. */
    FLUID_PIPE,

    /** Machine Track -- pushes adjacent Trolleys on redstone signal. */
    TRACK,

    /** Machine Trolley -- passive cargo carrier. */
    TROLLEY,

    /** Machine Brace -- structural reinforcement, increases push limit. */
    BRACE,

    /** Machine Repeater -- three-axis redstone repeater. */
    REPEATER,

    /** Machine Transformer -- configurable signal level setter. */
    TRANSFORMER,

    /** Machine Timer -- edge-triggered timed output. */
    TIMER,

    /** Machine Clock -- configurable redstone pulse emitter. */
    CLOCK,

    /** Machine Pulser -- single-shot redstone pulse. */
    PULSER,

    /** Machine Toggle -- flip-flop redstone switch. */
    TOGGLE,

    /** Machine Piston -- piston as Machine Family member. */
    PISTON,

    /** Machine Sticky Piston -- sticky piston as Machine Family member. */
    STICKY_PISTON,

    /** Machine Engine -- fuel-burning pulse clock. */
    ENGINE,

    /** Machine Slime Grabber -- toggleable slime-type sticky block. */
    SLIME_GRABBER,

    /** Machine Honey Grabber -- toggleable honey-type sticky block. */
    HONEY_GRABBER,

    /** Machine Transmitter -- wireless redstone via Oddstone channels. */
    TRANSMITTER;

    /**
     * Can this type stick to another machine type?
     * FRAME is the universal connector -- it sticks to everything.
     * All other types only stick to themselves or to FRAME.
     */
    public boolean canStickToMachineType(MachineType other) {
        if (this == FRAME || other == FRAME) return true;
        return this == other;
    }
}
