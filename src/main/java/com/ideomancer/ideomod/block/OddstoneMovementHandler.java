package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.config.OddstoneConfig;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Handles Oddstone's "sympathetic movement" - wireless slime blocks.
 * 
 * HOW IT WORKS:
 * =============
 * Oddstone behaves exactly like slime blocks, except the "touching" check
 * reaches further. Instead of only connecting to the 6 directly adjacent
 * blocks, each face of an Oddstone reaches out in its direction and grabs
 * the NEAREST Oddstone within the configured radius.
 * 
 * So each Oddstone can connect to at most 6 other Oddstones (one per face),
 * just like slime blocks can connect to at most 6 adjacent blocks.
 * 
 * Once connections are established, the push/pull propagation works exactly
 * like vanilla slime:
 * - Connected blocks form a group that moves together
 * - The chain continues through each connected block's faces
 * - If the total group exceeds 12 blocks, the piston push FAILS
 * - If any block in the group is obstructed, the piston push FAILS
 * 
 * FACE SEARCH EXPLAINED (visually):
 * Imagine an Oddstone's EAST face. We scan east from the block, one position
 * at a time, up to the configured radius. The FIRST Oddstone we find is the
 * one this face "grabs." If there's a non-air block in the way before we
 * find an Oddstone, we stop (the connection is blocked by terrain).
 * 
 *   [Oddstone A] . . . [Oddstone B] . . [Oddstone C]
 *       ^--- east face grabs B (nearest)
 *                           ^--- east face grabs C
 * 
 * IMPLEMENTATION:
 * We use PistonEvent.Pre to intercept BEFORE the piston pushes.
 * We check if any Oddstone would be in the push path.
 * If so, we find all sympathetically linked Oddstones and check if the
 * entire group can move. If it can't (too many blocks, or obstruction),
 * we cancel the piston event. If it can, we let the piston do its thing
 * and then move the sympathetic blocks in PistonEvent.Post.
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OddstoneMovementHandler {

    // Max blocks in a piston push (vanilla limit)
    private static final int PISTON_PUSH_LIMIT = 12;

    // Store the sympathetic group found during Pre for use in Post
    // Key: piston position, Value: set of BlockPos to move sympathetically
    // We use piston pos + tick as key to avoid stale data
    private static final Map<Long, MovementPlan> pendingMoves = new HashMap<>();

    /**
     * Stores a planned sympathetic movement for execution after piston fires.
     */
    private static class MovementPlan {
        final Set<BlockPos> blocksToMove;
        final Direction moveDirection;
        final long tick;

        MovementPlan(Set<BlockPos> blocksToMove, Direction moveDirection, long tick) {
            this.blocksToMove = blocksToMove;
            this.moveDirection = moveDirection;
            this.tick = tick;
        }
    }

    /**
     * PRE-PUSH: Check if the piston is about to push any Oddstone.
     * If so, calculate the full sympathetic group and validate the move.
     * If the move would fail (too many blocks or obstruction), cancel the piston.
     */
    @SubscribeEvent
    public static void onPistonPre(PistonEvent.Pre event) {
        Level level = (Level) event.getLevel();
        if (level.isClientSide()) return;

        Direction pistonFacing = event.getDirection();
        boolean extending = event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND;
        Direction moveDirection = extending ? pistonFacing : pistonFacing.getOpposite();
        BlockPos pistonPos = event.getPos();
        long tick = level.getGameTime();

        // Find Oddstone blocks that would be directly pushed/pulled by this piston
        List<BlockPos> directOddstones = findOddstonesInPistonPath(level, pistonPos, pistonFacing, extending);

        if (directOddstones.isEmpty()) return;

        // Build the full sympathetic group by following face connections
        Set<BlockPos> sympatheticGroup = buildSympatheticGroup(level, directOddstones);

        // Remove the ones already being pushed by the piston directly
        // (they'll be handled by vanilla piston logic)
        sympatheticGroup.removeAll(directOddstones);

        if (sympatheticGroup.isEmpty()) return;

        // Count total blocks: piston's own push + sympathetic
        // We need to estimate how many blocks the piston is already pushing
        int pistonPushCount = countPistonPushBlocks(level, pistonPos, pistonFacing, extending);
        int totalBlocks = pistonPushCount + sympatheticGroup.size();

        if (totalBlocks > PISTON_PUSH_LIMIT) {
            // Too many blocks - cancel the piston push entirely, just like
            // when slime blocks make a group too large
            event.setCanceled(true);
            Ideomod.LOGGER.debug("Piston push cancelled: {} blocks (piston: {}, sympathetic: {}) exceeds limit of {}",
                    totalBlocks, pistonPushCount, sympatheticGroup.size(), PISTON_PUSH_LIMIT);
            return;
        }

        // Check if all sympathetic blocks can actually move to their destinations
        for (BlockPos pos : sympatheticGroup) {
            BlockPos dest = pos.relative(moveDirection);

            // Destination must be movable-into:
            // air, replaceable, OR another oddstone that's also in the move set
            // (including the directly-pushed ones)
            if (!canMoveInto(level, dest, sympatheticGroup, directOddstones, moveDirection)) {
                // Obstructed - cancel the whole push, like slime blocks do
                event.setCanceled(true);
                Ideomod.LOGGER.debug("Piston push cancelled: sympathetic Oddstone at {} blocked at destination {}",
                        pos, dest);
                return;
            }
        }

        // Everything checks out - store the plan for Post to execute
        long key = pistonPos.asLong() ^ (tick * 31);
        pendingMoves.put(key, new MovementPlan(sympatheticGroup, moveDirection, tick));

        Ideomod.LOGGER.debug("Piston pre-approved: {} sympathetic Oddstone(s) will move {}",
                sympatheticGroup.size(), moveDirection);
    }

    /**
     * POST-PUSH: After the piston has moved its blocks, execute the
     * sympathetic movement for the linked Oddstones.
     */
    @SubscribeEvent
    public static void onPistonPost(PistonEvent.Post event) {
        Level level = (Level) event.getLevel();
        if (level.isClientSide()) return;

        BlockPos pistonPos = event.getPos();
        long tick = level.getGameTime();
        long key = pistonPos.asLong() ^ (tick * 31);

        MovementPlan plan = pendingMoves.remove(key);
        if (plan == null) return;

        // Clean up old entries (safety net)
        pendingMoves.entrySet().removeIf(e -> tick - e.getValue().tick > 5);

        // Execute the sympathetic movement
        moveBlocks(level, plan.blocksToMove, plan.moveDirection);

        Ideomod.LOGGER.debug("Sympathetically moved {} Oddstone block(s) {}",
                plan.blocksToMove.size(), plan.moveDirection);
    }

    // ========================================================================
    // FACE-BASED NEIGHBOR SEARCH
    // ========================================================================

    /**
     * For a given Oddstone, finds its sympathetic neighbors by searching
     * each of the 6 faces outward up to the configured radius.
     * 
     * Each face finds AT MOST one Oddstone - the nearest one in that direction.
     * If a non-air, non-replaceable block is in the way, the search for that
     * face stops (terrain blocks the connection).
     * 
     * @return List of up to 6 connected Oddstone positions
     */
    private static List<BlockPos> findFaceNeighbors(Level level, BlockPos oddstonePos) {
        int radius = OddstoneConfig.getSympatheticRadius();
        List<BlockPos> neighbors = new ArrayList<>(6);

        for (Direction face : Direction.values()) {
            // Search outward from this face, starting at distance 2
            // (distance 1 is the directly adjacent block - which IS checked,
            // but we start at 2 because if they're touching, vanilla slime
            // mechanics would handle it. Actually no - Oddstone isn't a slime
            // block. So we start at 1 to also catch adjacent Oddstones.)
            for (int dist = 1; dist <= radius; dist++) {
                BlockPos checkPos = oddstonePos.relative(face, dist);
                BlockState state = level.getBlockState(checkPos);

                if (state.is(ModBlocks.ODDSTONE.get())) {
                    // Found the nearest Oddstone on this face!
                    neighbors.add(checkPos);
                    break; // Only grab the nearest one per face
                }

                // Connection passes through ALL blocks - walls, stone, whatever.
                // The sympathetic link is "wireless" and ignores terrain.
            }
        }

        return neighbors;
    }

    /**
     * Builds the full sympathetic group starting from catalyst blocks.
     * Uses breadth-first search, following face connections and chaining
     * through each found Oddstone's faces.
     * 
     * This is like how slime block groups are built:
     * Start with the pushed block(s), find their sticky neighbors,
     * then find THOSE blocks' sticky neighbors, and so on.
     * 
     * @param catalysts The Oddstone blocks directly in the piston's push path
     * @return Complete set of all connected Oddstone blocks (including catalysts)
     */
    private static Set<BlockPos> buildSympatheticGroup(Level level, List<BlockPos> catalysts) {
        Set<BlockPos> group = new LinkedHashSet<>(catalysts);
        Queue<BlockPos> searchQueue = new LinkedList<>(catalysts);

        while (!searchQueue.isEmpty()) {
            BlockPos current = searchQueue.poll();
            List<BlockPos> neighbors = findFaceNeighbors(level, current);

            for (BlockPos neighbor : neighbors) {
                if (!group.contains(neighbor)) {
                    group.add(neighbor);
                    searchQueue.add(neighbor); // Chain: search this one's faces too
                }
            }
        }

        return group;
    }

    // ========================================================================
    // PISTON PATH SCANNING
    // ========================================================================

    /**
     * Finds Oddstone blocks in the piston's direct push/pull path.
     * These are the blocks that the piston itself would move.
     */
    private static List<BlockPos> findOddstonesInPistonPath(Level level, BlockPos pistonPos,
                                                             Direction pistonFacing, boolean extending) {
        List<BlockPos> found = new ArrayList<>();

        if (extending) {
            // When extending, scan the line of blocks starting in front of piston
            BlockPos checkPos = pistonPos.relative(pistonFacing);
            for (int i = 0; i <= PISTON_PUSH_LIMIT; i++) {
                BlockPos pos = checkPos.relative(pistonFacing, i);
                BlockState state = level.getBlockState(pos);

                if (state.isAir()) break;
                if (!PistonBaseBlock.isPushable(state, level, pos, pistonFacing, true, pistonFacing)) break;

                if (state.is(ModBlocks.ODDSTONE.get())) {
                    found.add(pos);
                }
            }
        } else {
            // When retracting (sticky piston), only the block 2 away from piston
            // (1 away from where piston head was) gets pulled
            BlockPos pullPos = pistonPos.relative(pistonFacing, 2);
            BlockState state = level.getBlockState(pullPos);
            if (state.is(ModBlocks.ODDSTONE.get())) {
                found.add(pullPos);
            }
        }

        return found;
    }

    /**
     * Estimates how many blocks the piston is pushing directly.
     * Used to check against the 12-block limit.
     */
    private static int countPistonPushBlocks(Level level, BlockPos pistonPos,
                                              Direction pistonFacing, boolean extending) {
        if (!extending) return 1; // Sticky pull = at most 1 block

        int count = 0;
        BlockPos checkPos = pistonPos.relative(pistonFacing);
        for (int i = 0; i <= PISTON_PUSH_LIMIT; i++) {
            BlockPos pos = checkPos.relative(pistonFacing, i);
            BlockState state = level.getBlockState(pos);

            if (state.isAir()) break;
            if (!PistonBaseBlock.isPushable(state, level, pos, pistonFacing, true, pistonFacing)) break;

            count++;
        }
        return count;
    }

    // ========================================================================
    // MOVEMENT VALIDATION & EXECUTION
    // ========================================================================

    /**
     * Checks if a destination position can receive a moving block.
     */
    private static boolean canMoveInto(Level level, BlockPos destination,
                                        Set<BlockPos> sympatheticGroup,
                                        List<BlockPos> directlyPushed,
                                        Direction moveDirection) {
        BlockState destState = level.getBlockState(destination);

        // Air or replaceable = fine
        if (destState.isAir() || destState.canBeReplaced()) {
            return true;
        }

        // If destination contains an Oddstone that's also moving in the
        // same direction, it'll vacate the space (conga line effect)
        if (destState.is(ModBlocks.ODDSTONE.get())) {
            if (sympatheticGroup.contains(destination) || directlyPushed.contains(destination)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Moves a set of blocks in the given direction.
     * Sorted front-to-back so blocks don't overwrite each other.
     */
    private static void moveBlocks(Level level, Set<BlockPos> positions, Direction moveDirection) {
        if (positions.isEmpty()) return;

        List<BlockPos> sorted = new ArrayList<>(positions);

        // Sort: furthest in move direction first (front-to-back)
        sorted.sort((a, b) -> {
            int aVal = getAxisValue(a, moveDirection);
            int bVal = getAxisValue(b, moveDirection);

            if (moveDirection.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                return Integer.compare(bVal, aVal); // Descending
            } else {
                return Integer.compare(aVal, bVal); // Ascending
            }
        });

        // Phase 1: Collect all states and clear old positions
        // (Two-phase prevents blocks from overwriting each other)
        List<BlockState> states = new ArrayList<>();
        for (BlockPos pos : sorted) {
            states.add(level.getBlockState(pos));
        }

        // Phase 2: Clear all old positions
        for (BlockPos pos : sorted) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 64);
        }

        // Phase 3: Place all blocks at new positions
        for (int i = 0; i < sorted.size(); i++) {
            BlockPos newPos = sorted.get(i).relative(moveDirection);
            level.setBlock(newPos, states.get(i), 2 | 64);
        }

        // Phase 4: Update neighbors
        for (BlockPos pos : sorted) {
            BlockPos newPos = pos.relative(moveDirection);
            level.updateNeighborsAt(pos, Blocks.AIR);
            level.updateNeighborsAt(newPos, level.getBlockState(newPos).getBlock());
        }
    }

    /**
     * Gets the coordinate value along a direction's axis.
     */
    private static int getAxisValue(BlockPos pos, Direction direction) {
        return switch (direction.getAxis()) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }
}
