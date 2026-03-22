package com.ideomancer.ideomod.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared signal detection utility for Machine Family redstone specializations.
 *
 * SINGLE SOURCE OF TRUTH for "what signal is my neighbor providing to me?"
 *
 * Every Machine Family redstone specialization (Repeater, Toggle, Transformer,
 * and future Clock/Pulser) calls these methods instead of maintaining its own
 * copy of getIncomingSignalFromFace(). Adding a new redstone specialization
 * only requires updating THIS file — all existing specializations automatically
 * recognize it.
 *
 * The Conductor is the exception: it has its own simpler version that treats
 * all non-wire blocks as sources (no decay). This is correct because the
 * Conductor is a wire that decays signal, while the other machines are
 * "devices" that output a fixed level. The Conductor reads machine device
 * output via vanilla level.getSignal() which works correctly.
 *
 * NEIGHBOR TYPE HANDLING:
 * =======================
 *   MachineConductorBlock → read getSignal with -1 decay (wire)
 *   Any powered machine device → read output level, input-face check (device)
 *   RedStoneWireBlock → POWER with -1 decay (wire)
 *   Everything else → vanilla level.getSignal (source)
 *
 * "Machine device" = any AbstractMachineBlock that has a POWERED boolean
 * blockstate and uses the input/output face model. Currently: Repeater,
 * Toggle, Transformer. Detected generically via getRedstoneOutput().
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public final class MachineRedstoneHelper {

    private MachineRedstoneHelper() {} // Utility class

    /**
     * Get the incoming signal strength from a specific face for a machine device.
     *
     * @param level The world
     * @param pos Our block position
     * @param face The face to check (direction from us to the neighbor)
     * @param shouldSignal The calling block's shouldSignal ThreadLocal — will be
     *                     set to false during vanilla signal reads to prevent
     *                     self-reads through solid blocks
     * @return Signal strength (0-15)
     */
    public static int getIncomingSignalFromFace(Level level, BlockPos pos, Direction face,
                                                 ThreadLocal<Boolean> shouldSignal) {
        BlockPos neighborPos = pos.relative(face);
        BlockState neighborState = level.getBlockState(neighborPos);
        Block neighborBlock = neighborState.getBlock();

        // --- Wire type: Conductor (solid-block redstone wire) ---
        // Read via getSignal which respects their input face suppression.
        // Apply -1 decay (same as dust-to-dust).
        if (neighborBlock instanceof MachineConductorBlock) {
            int signal = neighborState.getSignal(level, neighborPos, face);
            return Math.max(0, signal - 1);
        }

        // --- Device type: any Machine Family redstone device ---
        // These are "source-like" blocks that output a fixed level on non-input faces.
        // Read their output level directly, respecting input face suppression.
        // No decay — they are sources, not wires.
        if (neighborBlock instanceof AbstractMachineBlock machineBlock) {
            int output = machineBlock.getRedstoneOutput(neighborState);
            if (output > 0) {
                // Check input face suppression: if we're on their input face, they
                // output 0 toward us.
                Direction neighborInputFace = getNeighborInputFace(level, neighborPos, neighborState);
                if (neighborInputFace != null && neighborInputFace == face.getOpposite()) {
                    return 0; // We're on their input face — they don't output toward us
                }
                return output;
            }
            return 0;
        }

        // --- Wire type: vanilla redstone dust ---
        // Read POWER directly with -1 decay. We can't use dust's getSignal
        // because it's directional and may return 0 if the dust isn't
        // "pointing" at us.
        if (neighborBlock instanceof RedStoneWireBlock) {
            int power = neighborState.getValue(RedStoneWireBlock.POWER);
            return Math.max(0, power - 1);
        }

        // --- Everything else: vanilla source ---
        // Levers, buttons, redstone blocks, comparators, etc.
        // Read via vanilla level.getSignal with shouldSignal=false to prevent
        // reading our own output through adjacent solid blocks.
        shouldSignal.set(false);
        try {
            return level.getSignal(neighborPos, face);
        } finally {
            shouldSignal.set(true);
        }
    }

    /**
     * Scan all 6 faces for the best incoming signal.
     *
     * @return int[2]: [bestSignal, bestFaceOrdinal]. bestFaceOrdinal is -1 if no signal.
     */
    public static int[] scanAllFaces(Level level, BlockPos pos, ThreadLocal<Boolean> shouldSignal) {
        int bestSignal = 0;
        int bestFaceOrd = -1;
        for (Direction dir : Direction.values()) {
            int signal = getIncomingSignalFromFace(level, pos, dir, shouldSignal);
            if (signal > bestSignal) {
                bestSignal = signal;
                bestFaceOrd = dir.ordinal();
            }
        }
        return new int[]{bestSignal, bestFaceOrd};
    }

    /**
     * Determine which face a source position is on relative to us.
     * Returns null if sourcePos is not an immediate neighbor.
     */
    public static Direction getFaceFromSourcePos(BlockPos myPos, BlockPos sourcePos) {
        int dx = sourcePos.getX() - myPos.getX();
        int dy = sourcePos.getY() - myPos.getY();
        int dz = sourcePos.getZ() - myPos.getZ();

        if (dx == 1 && dy == 0 && dz == 0) return Direction.EAST;
        if (dx == -1 && dy == 0 && dz == 0) return Direction.WEST;
        if (dx == 0 && dy == 1 && dz == 0) return Direction.UP;
        if (dx == 0 && dy == -1 && dz == 0) return Direction.DOWN;
        if (dx == 0 && dy == 0 && dz == 1) return Direction.SOUTH;
        if (dx == 0 && dy == 0 && dz == -1) return Direction.NORTH;
        return null;
    }

    // ========================================================================
    // INTERNAL HELPERS
    // ========================================================================

    /**
     * Get the input face of a neighbor machine block.
     *
     * For Toggle, the input face is stored in the blockstate (INPUT_FACE property)
     * for BlockGetter compatibility. For all others, it's in the block entity.
     * This method checks both sources.
     */
    private static Direction getNeighborInputFace(Level level, BlockPos neighborPos,
                                                   BlockState neighborState) {
        // Toggle stores input face in blockstate
        if (neighborState.getBlock() instanceof MachineToggleBlock) {
            return neighborState.getValue(MachineToggleBlock.INPUT_FACE).getDirection();
        }

        // All others store input face in BE
        BlockEntity nbe = level.getBlockEntity(neighborPos);
        if (nbe instanceof MachineFrameBlockEntity nMachine) {
            return nMachine.getInputFace();
        }
        return null;
    }
}
