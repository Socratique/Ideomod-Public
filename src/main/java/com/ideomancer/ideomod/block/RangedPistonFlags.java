package com.ideomancer.ideomod.block;

import net.minecraft.core.BlockPos;

/**
 * ThreadLocal flags for coordinating between RangedPistonUtil and the
 * PistonStructureResolver mixin during ranged piston operations.
 * 
 * THE PROBLEM:
 * ============
 * PistonStructureResolver requires a "piston position" in its constructor.
 * It calculates startPos = pistonPos + pushDirection, then walks from there.
 * 
 * For ranged pistons (Repulser/Attractor), the target block can be up to
 * 12 blocks away from the device. If we place a "virtual piston" adjacent
 * to the target, it can overlap with real blocks that should be part of the
 * move group (especially sympathetic Oddstone behind the target in the
 * Attractor case). The resolver treats its pistonPos as sacred ground and
 * won't add blocks at that position.
 * 
 * THE SOLUTION:
 * =============
 * Place the virtual piston at the DEVICE's own position (safe, far away).
 * Store the REAL target position in a ThreadLocal. The mixin intercepts
 * the resolver's startPos calculation and redirects it to the real target.
 * 
 * The resolver thinks the piston is at the device, but starts its line-walk
 * from the actual target block. Everything behind the target (including
 * sympathetic Oddstone) is fair game because pistonPos is far away.
 * 
 * LIFECYCLE:
 * ==========
 * 1. RangedPistonUtil.resolveAndMove() sets the target via setTarget()
 * 2. Creates PistonStructureResolver with pistonPos = devicePos
 * 3. Resolver calls resolve() -> mixin intercepts startPos calculation
 * 4. Mixin reads getTarget(), returns it instead of pistonPos + pushDir
 * 5. Resolver walks from real target, collects full move group
 * 6. RangedPistonUtil calls clear() after resolve completes
 * 
 * This class lives in the block package (not mixin package) so both
 * RangedPistonUtil and the mixin can access it.
 */
public class RangedPistonFlags {

    /**
     * The real target block position for the current ranged piston operation.
     * null when no ranged piston operation is active.
     */
    private static final ThreadLocal<BlockPos> TARGET_POS = new ThreadLocal<>();

    /**
     * Set the real target position before creating the resolver.
     */
    public static void setTarget(BlockPos targetPos) {
        TARGET_POS.set(targetPos);
    }

    /**
     * Get the real target position. Returns null if no ranged operation is active.
     * Called by the mixin to redirect the resolver's startPos.
     */
    public static BlockPos getTarget() {
        return TARGET_POS.get();
    }

    /**
     * Clear the target after resolve completes. Always call this in a finally
     * block to prevent stale state if resolve() throws.
     */
    public static void clear() {
        TARGET_POS.remove();
    }
}
