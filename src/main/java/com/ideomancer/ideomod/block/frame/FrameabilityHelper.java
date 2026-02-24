package com.ideomancer.ideomod.block.frame;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Static utility for determining whether a block can be wrapped in a frame.
 *
 * A block is frameable if it meets ALL of these criteria:
 *   1. Has a full cube collision shape (no stairs, slabs, fences, etc.)
 *   2. Is NOT a block entity holder (no chests, furnaces, signs, etc.)
 *   3. Is NOT already a FramedBlock (no double-framing)
 *   4. Is NOT a Frame Block (can't frame an empty frame)
 *   5. Is NOT bedrock or similar indestructible blocks
 *   6. Is NOT air or a liquid
 *
 * Checked at craft time by FramedBlockRecipe and at creative tab population
 * time by the BuildCreativeModeTabContentsEvent handler.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FrameabilityHelper {

    /**
     * Check if a block can be wrapped in a frame.
     *
     * @param block The block to check
     * @return true if the block is frameable
     */
    public static boolean isFrameable(Block block) {
        BlockState defaultState = block.defaultBlockState();

        // Rule 6: No air or liquids
        if (defaultState.isAir()) return false;
        if (!defaultState.getFluidState().isEmpty()) return false;

        // Rule 1: Must be a full cube collision shape
        // We check the collision shape against Shapes.block() (the full 1x1x1 cube)
        // Some blocks (e.g. MovingPistonBlock) require a non-null level/pos for
        // getCollisionShape and will NPE if we pass null. Catch and reject those.
        VoxelShape collisionShape;
        try {
            collisionShape = defaultState.getCollisionShape(null, null);
        } catch (NullPointerException e) {
            return false; // Block requires world context — not a simple full cube
        }
        if (collisionShape != Shapes.block()) return false;

        // Rule 2: No block entities (except our own whitelisted ones)
        if (block instanceof EntityBlock && !isWhitelistedEntityBlock(block)) return false;

        // Rule 3: No double-framing
        if (block instanceof FramedBlock) return false;

        // Rule 4: No framing empty frames
        if (block instanceof IFrameBlock) return false;

        // Rule 5: No indestructible blocks
        if (defaultState.getDestroySpeed(null, null) < 0) return false;

        // Rule 5b: Explicit bedrock exclusion (redundant with destroy speed check but safe)
        if (block == Blocks.BEDROCK) return false;
        if (block == Blocks.END_PORTAL_FRAME) return false;
        if (block == Blocks.COMMAND_BLOCK) return false;
        if (block == Blocks.CHAIN_COMMAND_BLOCK) return false;
        if (block == Blocks.REPEATING_COMMAND_BLOCK) return false;
        if (block == Blocks.STRUCTURE_BLOCK) return false;
        if (block == Blocks.JIGSAW) return false;
        if (block == Blocks.BARRIER) return false;
        if (block == Blocks.LIGHT) return false;

        return true;
    }

    /**
     * Whitelisted block entity blocks that CAN be framed.
     * Currently none — reserved for future use (e.g. Machine Blocks).
     */
    private static boolean isWhitelistedEntityBlock(Block block) {
        // Future: add Machine Block or other controlled block entities here
        return false;
    }
}
