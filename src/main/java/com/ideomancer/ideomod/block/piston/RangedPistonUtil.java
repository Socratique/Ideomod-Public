package com.ideomancer.ideomod.block.piston;

import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Predicate;
import com.ideomancer.ideomod.block.movement.BlockMoveHelper;

/**
 * Scanning and dispatch utility for the Repulser/Attractor family of ranged
 * piston blocks. Handles line scanning to find targets, then delegates the
 * actual block movement to {@link BlockMoveHelper}.
 *
 * SCANNING MODES:
 * ===============
 *   - scanForNearest: Repulser (push closest target away)
 *   - scanForFarthest: Attractor (pull farthest target toward device)
 *
 * FILTER PREDICATES:
 * ==================
 *   - ODDSTONE_ONLY: Oddstone Repulser/Attractor
 *   - anyMovable(level): Arcane Repulser/Attractor (future)
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class RangedPistonUtil {

    /** How far the device scans for targets. */
    public static final int SCAN_RANGE = 12;

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Unified block movement handler for both Repulser (push) and Attractor (pull).
     *
     * @param level The level
     * @param devicePos The position of the device
     * @param facing The direction the device faces
     * @param filter The predicate to match target blocks
     * @param repulse true for Repulser (push in facing direction),
     *                false for Attractor (pull in opposite direction)
     * @return true if a target was found and moved, false otherwise
     */
    private static boolean tryMove(Level level, BlockPos devicePos, Direction facing,
                                    Predicate<BlockState> filter, boolean repulse) {
        BlockPos targetPos = repulse
                ? scanForNearest(level, devicePos, facing, filter)
                : scanForFarthest(level, devicePos, facing, filter);
        if (targetPos == null) return false;
        Direction pushDir = repulse ? facing : facing.getOpposite();
        return BlockMoveHelper.moveBlockAt(level, devicePos, targetPos, pushDir);
    }

    /**
     * Repulser: find nearest target, push away from device.
     */
    public static boolean tryRepulse(Level level, BlockPos devicePos,
                                      Direction facing, Predicate<BlockState> filter) {
        return tryMove(level, devicePos, facing, filter, true);
    }

    /**
     * Attractor: find farthest target, pull toward device.
     * Implemented as a "reverse push" -- pushDir points toward the device.
     */
    public static boolean tryAttract(Level level, BlockPos devicePos,
                                      Direction facing, Predicate<BlockState> filter) {
        return tryMove(level, devicePos, facing, filter, false);
    }

    // ========================================================================
    // SCANNING
    // ========================================================================

    /**
     * Walk a line from devicePos in the facing direction, return the nearest
     * block that matches the filter. Used by Repulser.
     */
    public static BlockPos scanForNearest(Level level, BlockPos devicePos,
                                           Direction facing, Predicate<BlockState> filter) {
        for (int dist = 1; dist <= SCAN_RANGE; dist++) {
            BlockPos checkPos = devicePos.relative(facing, dist);
            if (filter.test(level.getBlockState(checkPos))) return checkPos;
        }
        return null;
    }

    /**
     * Walk a line from devicePos in the facing direction, return the farthest
     * block that matches the filter. Used by Attractor.
     */
    public static BlockPos scanForFarthest(Level level, BlockPos devicePos,
                                            Direction facing, Predicate<BlockState> filter) {
        BlockPos farthest = null;
        for (int dist = 1; dist <= SCAN_RANGE; dist++) {
            BlockPos checkPos = devicePos.relative(facing, dist);
            if (filter.test(level.getBlockState(checkPos))) farthest = checkPos;
        }
        return farthest;
    }

    // ========================================================================
    // FILTER PREDICATES
    // ========================================================================

    /** Only Oddstone blocks. Used by Oddstone Repulser/Attractor. */
    public static final Predicate<BlockState> ODDSTONE_ONLY =
            state -> state.is(ModBlocks.ODDSTONE.get());

    /**
     * Any piston-movable block. Used by Arcane variants (future).
     *
     * Returns a Predicate that captures the Level reference, avoiding the
     * null BlockGetter crash that the old static ANY_MOVABLE had.
     * getDestroySpeed() requires a valid BlockGetter -- passing null would
     * NPE on any block whose implementation uses the level parameter.
     *
     * Usage: RangedPistonUtil.anyMovable(level) instead of ANY_MOVABLE.
     *
     * @param level the world to check destroy speed against
     * @return predicate that matches any non-air, non-indestructible, movable block
     */
    public static Predicate<BlockState> anyMovable(Level level) {
        return state -> {
            if (state.isAir()) return false;
            if (state.getDestroySpeed(level, BlockPos.ZERO) < 0) return false;
            PushReaction reaction = state.getPistonPushReaction();
            return reaction != PushReaction.BLOCK && reaction != PushReaction.IGNORE;
        };
    }
}
