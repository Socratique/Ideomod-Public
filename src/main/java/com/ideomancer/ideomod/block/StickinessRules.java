package com.ideomancer.ideomod.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Registry for one-way (asymmetric) and directional stickiness rules.
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
 * Additionally, Odd Slime needs DIRECTIONAL stickiness — it only grabs
 * when the piston's push direction matches the block's FACING property.
 * This requires knowing the push direction, which standard rules don't have.
 * 
 * THE SOLUTION:
 * =============
 * Two rule types:
 * 
 * 1. GRAB IMMUNITY rules (original): (grabberState, targetState) -> blocked?
 *    Used for one-way stickiness (slime can't grab Oddstone, etc.)
 * 
 * 2. DIRECTIONAL GRAB rules (new): (grabberState, pushDirection) -> blocked?
 *    Used for blocks whose stickiness depends on push direction.
 *    If any rule returns true, the block acts non-sticky for this push.
 * 
 * Our mixin in PistonStructureResolver checks both rule types.
 * 
 * NOTE: This class lives in the block package, NOT the mixin package.
 * The mixin system locks down its package - no regular classes allowed
 * in there, and normal code can't reference classes in the mixin package.
 * This class needs to be called by both Ideomod.java (to register rules)
 * and the mixin (to check rules), so it must be in a normal package.
 */
public class StickinessRules {

    // ========================================================================
    // GRAB IMMUNITY RULES (type-based, no direction)
    // ========================================================================

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

    // ========================================================================
    // DIRECTIONAL GRAB RULES (direction-aware)
    // ========================================================================

    /**
     * Each rule tests (expandingBlockState, pushDirection).
     * Returns true if the block should NOT be sticky for this push direction.
     * 
     * This is separate from grab immunity because it doesn't care about
     * the neighbor — it cares about the direction of movement. A block
     * either IS or ISN'T sticky for a given push, regardless of what's
     * next to it. (What it sticks TO is still handled by canStickTo.)
     */
    private static final List<BiPredicate<BlockState, Direction>> directionalRules = new ArrayList<>();

    /**
     * Register a directional stickiness rule.
     * 
     * @param rule (blockState, pushDirection) -> true if stickiness should be DISABLED
     */
    public static void registerDirectionalRule(BiPredicate<BlockState, Direction> rule) {
        directionalRules.add(rule);
    }

    /**
     * Check if a block's stickiness is disabled for the given push direction.
     * Called by the PistonStructureResolver mixin during branching.
     * 
     * @param state         The sticky block being checked
     * @param pushDirection The direction the piston is pushing
     * @return true if this block should act NON-STICKY for this push
     */
    public static boolean isDirectionalGrabBlocked(BlockState state, Direction pushDirection) {
        for (BiPredicate<BlockState, Direction> rule : directionalRules) {
            if (rule.test(state, pushDirection)) {
                return true;
            }
        }
        return false;
    }
}
