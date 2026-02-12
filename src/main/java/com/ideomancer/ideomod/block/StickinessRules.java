package com.ideomancer.ideomod.block;

import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Registry for one-way (asymmetric) stickiness rules.
 * 
 * THE PROBLEM:
 * ============
 * Stickiness is normally mutual. If slime is next to Oddstone and gets
 * pushed by a piston, slime grabs Oddstone because slime sticks to
 * everything except honey. There's no vanilla way to prevent this.
 * 
 * But we want Oddstone and Machine Block to have asymmetric stickiness
 * with slime - one direction works, the other doesn't.
 * 
 * THE SOLUTION:
 * =============
 * Register "grab immunity" rules here. Each rule says:
 * "When THIS type of block is expanding and finds THAT type as a
 * neighbor, the grab should be BLOCKED."
 * 
 * Our mixin in PistonStructureResolver checks these rules and cancels
 * grabs when a rule matches.
 * 
 * NOTE: This class lives in the block package, NOT the mixin package.
 * The mixin system locks down its package - no regular classes allowed
 * in there, and normal code can't reference classes in the mixin package.
 * This class needs to be called by both Ideomod.java (to register rules)
 * and the mixin (to check rules), so it must be in a normal package.
 */
public class StickinessRules {

    /**
     * Each rule tests (expandingBlockState, neighborBlockState).
     * Returns true if the grab should be BLOCKED.
     * 
     * "Expanding" = the sticky block whose neighbors are being checked
     * "Neighbor"  = the adjacent block being considered for the grab
     */
    private static final List<BiPredicate<BlockState, BlockState>> grabImmunityRules = new ArrayList<>();

    /**
     * Register a grab immunity rule.
     * 
     * @param rule (expandingBlockState, neighborBlockState) -> true if grab should be BLOCKED
     */
    public static void registerGrabImmunity(BiPredicate<BlockState, BlockState> rule) {
        grabImmunityRules.add(rule);
    }

    /**
     * Check if any rule prevents the expanding block from grabbing the neighbor.
     * Called by the PistonStructureResolver mixin.
     * 
     * @param expandingState The sticky block doing the grabbing
     * @param neighborState  The block being grabbed
     * @return true if the grab should be BLOCKED
     */
    public static boolean isGrabBlocked(BlockState expandingState, BlockState neighborState) {
        for (BiPredicate<BlockState, BlockState> rule : grabImmunityRules) {
            if (rule.test(expandingState, neighborState)) {
                return true;
            }
        }
        return false;
    }
}
