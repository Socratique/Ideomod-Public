package com.ideomancer.ideomod.block.machine;

/**
 * Machine Brace -- structural reinforcement that increases piston push limits.
 *
 * When a Machine Brace is found within a piston push group, the push limit
 * for that group increases by 6 (from the base 12). Each additional Brace
 * found within the expanded limit adds another 6, up to a hard cap of 60
 * (requiring 8 Braces). The cascading discovery is automatic: the limit
 * grows as Braces are found, but a Brace must be within the current limit
 * to count.
 *
 * All shared behavior (GUI, drops, clone, block entity, lifecycle hooks,
 * stickiness, specialization swap) is inherited from AbstractMachineBlock.
 * Brace has no unique behavior beyond its machine type -- the push limit
 * increase is implemented in PistonStructureResolverMixin via @ModifyConstant.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineBraceBlock extends AbstractMachineBlock {

    /** Push limit increase per Brace found in the push group. */
    public static final int PUSH_LIMIT_INCREASE_PER_BRACE = 6;

    /** Maximum push limit with Braces (8 Braces * 6 = 48 + 12 base = 60). */
    public static final int MAX_PUSH_LIMIT = 60;

    /** Maximum number of Braces that contribute to the push limit. */
    public static final int MAX_BRACES = 8;

    public MachineBraceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.BRACE;
    }
}
