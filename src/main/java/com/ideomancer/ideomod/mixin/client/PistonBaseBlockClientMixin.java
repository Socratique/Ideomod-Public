package com.ideomancer.ideomod.mixin.client;

import com.ideomancer.ideomod.block.FramedBlock;
import com.ideomancer.ideomod.block.FramedBlockEntity;
import com.ideomancer.ideomod.client.MovingFrameData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * CLIENT-SIDE mixin into PistonBaseBlock.moveBlocks().
 *
 * Captures FramedBlock inner block IDs BEFORE vanilla destroys the block
 * entities during piston movement. Stores them in MovingFrameData so the
 * PistonMovingBlockEntityRendererMixin can render the inner block texture
 * during the 2-tick sliding animation.
 *
 * EXTENSION: Uses PistonStructureResolver (works reliably).
 *
 * RETRACTION: Resolver fails for our IPistonMovable blocks (documented in
 * changelog and IDEOMOD_DOCUMENTATION.md). Instead, mirrors the server-side
 * PistonBaseBlockMixin approach: direct scan at the pull position, then
 * sticky-group-aware BFS that only follows blocks connected via
 * isStickyBlock()/canStickTo() chains.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockClientMixin {

    @Unique
    private static final Logger IDEOMOD_LOGGER = LoggerFactory.getLogger("Ideomod");

    @Unique
    private static final int MAX_PUSH_BLOCKS = 12;

    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private void ideomod$captureFramedBlockDataForRenderer(
            Level level, BlockPos pistonPos, Direction direction,
            boolean extending, CallbackInfoReturnable<Boolean> cir) {

        // ONLY run on the client
        if (!level.isClientSide()) return;

        // Clear stale data from previous piston operations
        MovingFrameData.clearAll();

        int captured;
        if (extending) {
            captured = ideomod$captureExtension(level, pistonPos, direction);
        } else {
            captured = ideomod$captureRetraction(level, pistonPos, direction);
        }

        if (captured > 0) {
            IDEOMOD_LOGGER.info("[ClientCapture] Captured {} framed block(s) for {} at {}",
                    captured, extending ? "extension" : "retraction", pistonPos);
        }
    }

    /**
     * Extension: use PistonStructureResolver to find all blocks being pushed.
     * This handles branching groups properly.
     */
    @Unique
    private static int ideomod$captureExtension(Level level, BlockPos pistonPos, Direction direction) {
        PistonStructureResolver resolver = new PistonStructureResolver(
                level, pistonPos, direction, true
        );

        if (!resolver.resolve()) return 0;

        int captured = 0;
        List<BlockPos> toPush = resolver.getToPush();

        for (BlockPos sourcePos : toPush) {
            if (ideomod$captureIfFramed(level, sourcePos, sourcePos.relative(direction))) {
                captured++;
            }
        }
        return captured;
    }

    /**
     * Retraction: resolver fails for our blocks. Mirror the server-side
     * PistonBaseBlockMixin approach:
     *
     * 1. Find the "pull position" at pistonPos + direction * 2
     *    (the block directly behind the retracting piston head)
     * 2. Walk the line backward from there (in the push direction)
     *    collecting all pushable blocks in a chain
     * 3. BFS from ALL collected blocks, following sticky connections
     *    to find branching group members
     *
     * All blocks move in direction.getOpposite() (toward the piston).
     */
    @Unique
    private static int ideomod$captureRetraction(Level level, BlockPos pistonPos, Direction direction) {
        Direction moveDir = direction.getOpposite(); // blocks move toward piston

        // The block being pulled is 2 blocks from piston in the facing direction
        BlockPos pullPos = pistonPos.relative(direction, 2);
        BlockState pullState = level.getBlockState(pullPos);

        // If the pull position is air or unpushable, nothing to capture
        if (pullState.isAir() || pullState.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.DESTROY) {
            return 0;
        }

        // Step 1: Walk the line from pull position outward (in push direction)
        // collecting all connected blocks in a line
        List<BlockPos> lineBlocks = new ArrayList<>();
        BlockPos current = pullPos;
        for (int i = 0; i < MAX_PUSH_BLOCKS; i++) {
            BlockState state = level.getBlockState(current);
            if (state.isAir()) break;
            lineBlocks.add(current);
            current = current.relative(direction); // walk further from piston
        }

        // Step 2: Sticky-group BFS from all line blocks
        // Only follow connections where the existing block isStickyBlock()
        // AND both sides agree on canStickTo()
        Set<BlockPos> allBlocks = new HashSet<>(lineBlocks);
        Queue<BlockPos> queue = new LinkedList<>(lineBlocks);

        while (!queue.isEmpty() && allBlocks.size() < MAX_PUSH_BLOCKS) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);

            // Only expand from sticky blocks
            if (!state.isStickyBlock()) continue;

            for (Direction face : Direction.values()) {
                BlockPos neighbor = pos.relative(face);
                if (allBlocks.contains(neighbor)) continue;

                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.isAir()) continue;

                // Check bidirectional stickiness (same as Forge's check)
                if (state.canStickTo(neighborState) && neighborState.canStickTo(state)) {
                    allBlocks.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        // Step 3: Capture all FramedBlocks found
        int captured = 0;
        for (BlockPos sourcePos : allBlocks) {
            if (ideomod$captureIfFramed(level, sourcePos, sourcePos.relative(moveDir))) {
                captured++;
            }
        }
        return captured;
    }

    /**
     * If the block at sourcePos is a FramedBlock with an inner block,
     * store its inner block ID in MovingFrameData keyed by destPos.
     * Returns true if data was stored.
     */
    @Unique
    private static boolean ideomod$captureIfFramed(Level level, BlockPos sourcePos, BlockPos destPos) {
        BlockState state = level.getBlockState(sourcePos);
        if (!(state.getBlock() instanceof FramedBlock)) return false;

        BlockEntity be = level.getBlockEntity(sourcePos);
        if (!(be instanceof FramedBlockEntity framedBE)) return false;
        if (!framedBE.hasInnerBlock()) return false;

        ResourceLocation innerBlockId = framedBE.getInnerBlockId();
        if (innerBlockId == null) return false;

        MovingFrameData.store(destPos, innerBlockId);
        return true;
    }
}
