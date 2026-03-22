package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.config.IdeomodConfig;

/**
 * Machine Brace -- structural reinforcement that increases piston push limits.
 *
 * When a Machine Brace is found within a piston push group, the push limit
 * for that group increases (from the base push limit). Each additional Brace
 * found within the expanded limit adds more, up to a hard cap. The cascading
 * discovery is automatic: the limit grows as Braces are found, but a Brace
 * must be within the current limit to count.
 *
 * All values are now read from IdeomodConfig for easy tuning.
 *
 * All shared behavior (GUI, drops, clone, block entity, lifecycle hooks,
 * stickiness, specialization swap) is inherited from AbstractMachineBlock.
 * Brace has no unique behavior beyond its machine type -- the push limit
 * increase is implemented in PistonStructureResolverMixin via @ModifyConstant.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineBraceBlock extends AbstractMachineBlock {

    /** Push limit increase per Brace found in the push group. Reads from config. */
    public static int getPushLimitPerBrace() {
        return IdeomodConfig.getPushLimitPerBrace();
    }

    /** Maximum push limit with Braces. Reads from config. */
    public static int getMaxPushLimit() {
        return IdeomodConfig.getMaxPushLimit();
    }

    /** Maximum number of Braces that contribute to the push limit. Reads from config. */
    public static int getMaxBraces() {
        return IdeomodConfig.getMaxBraces();
    }

    public MachineBraceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.BRACE;
    }
}
