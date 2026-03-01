package com.ideomancer.ideomod.mixin.client;

import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.movement.MovementScanHelper;
import com.ideomancer.ideomod.client.render.MovingFrameData;
import com.ideomancer.ideomod.config.OddstoneConfig;
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
 * Captures FramedBlock inner block IDs, facing, and inner sticky type
 * BEFORE vanilla destroys the block entities during piston movement.
 * Stores them in MovingFrameData so the transit renderer can render
 * the inner block with correct orientation during the 2-tick animation.
 *
 * EXTENSION: Uses PistonStructureResolver (works reliably).
 *
 * RETRACTION: Resolver fails for our IPistonMovable blocks. Uses
 * sticky-group-aware BFS mirroring the server-side approach.
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

        if (!level.isClientSide()) return;

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

    @Unique
    private static int ideomod$captureRetraction(Level level, BlockPos pistonPos, Direction direction) {
        Direction moveDir = direction.getOpposite();

        BlockPos pullPos = pistonPos.relative(direction, 2);
        BlockState pullState = level.getBlockState(pullPos);

        if (pullState.isAir() || pullState.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.DESTROY) {
            return 0;
        }

        List<BlockPos> lineBlocks = new ArrayList<>();
        BlockPos current = pullPos;
        for (int i = 0; i < MAX_PUSH_BLOCKS; i++) {
            BlockState state = level.getBlockState(current);
            if (state.isAir()) break;
            lineBlocks.add(current);
            current = current.relative(direction);
        }

        Set<BlockPos> allBlocks = new HashSet<>(lineBlocks);
        Queue<BlockPos> queue = new LinkedList<>(lineBlocks);

        while (!queue.isEmpty() && allBlocks.size() < MAX_PUSH_BLOCKS) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);

            if (!state.isStickyBlock()) continue;

            for (Direction face : Direction.values()) {
                BlockPos neighbor = pos.relative(face);
                if (allBlocks.contains(neighbor)) continue;

                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.isAir()) continue;

                if (state.canStickTo(neighborState) && neighborState.canStickTo(state)) {
                    allBlocks.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        // Sympathetic scan: find oddstone/framed oddstone at range and
        // scan their sticky groups too. Mirrors the server-side logic in
        // PistonBaseBlockMixin.scanSympathetic().
        ideomod$captureSympathetic(level, allBlocks);

        int captured = 0;
        for (BlockPos sourcePos : allBlocks) {
            if (ideomod$captureIfFramed(level, sourcePos, sourcePos.relative(moveDir))) {
                captured++;
            }
        }
        return captured;
    }

    /**
     * Scan for sympathetically linked Oddstone/Framed Oddstone blocks
     * and add them (plus their sticky groups) to allBlocks.
     * Client-side mirror of PistonBaseBlockMixin.scanSympathetic().
     *
     * Uses MovementScanHelper to find all sympathetic oddstone blocks,
     * then iterates to scan their sticky groups for capture.
     *
     * This ensures framed blocks attached to remote sympathetic oddstone
     * via machine activation are captured for transit rendering.
     */
    @Unique
    private static void ideomod$captureSympathetic(Level level, Set<BlockPos> allBlocks) {
        int radius = OddstoneConfig.getSympatheticRadius();

        // Use helper to find all sympathetic oddstone blocks
        Set<BlockPos> allFound = MovementScanHelper.scanSympathetic(level, allBlocks, radius);

        // Add newly found oddstone blocks
        for (BlockPos checkPos : allFound) {
            if (!allBlocks.contains(checkPos)) {
                BlockState checkState = level.getBlockState(checkPos);
                allBlocks.add(checkPos);

                // Scan sticky group attached to this sympathetic oddstone
                // (machine frames, activated framed blocks, etc.)
                if (checkState.isStickyBlock()) {
                    ideomod$scanStickyGroupInto(level, checkPos, allBlocks);
                }
            }
        }
    }

    /**
     * BFS scan of a sticky group starting from startPos, adding all
     * connected blocks to allBlocks. Used by sympathetic scanning to
     * find blocks attached to remote oddstone.
     *
     * Uses MovementScanHelper.scanStickyGroup to find the group.
     */
    @Unique
    private static void ideomod$scanStickyGroupInto(Level level, BlockPos startPos,
                                                     Set<BlockPos> allBlocks) {
        Set<BlockPos> groupBlocks = MovementScanHelper.scanStickyGroup(level, startPos, MAX_PUSH_BLOCKS);
        allBlocks.addAll(groupBlocks);
    }


    /**
     * If the block at sourcePos is a FramedBlock with an inner block,
     * store its inner block ID, facing, and inner sticky type in
     * MovingFrameData keyed by destPos.
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

        Direction facing = state.getValue(FramedBlock.FACING);
        int innerSticky = state.getValue(FramedBlock.INNER_STICKY);

        MovingFrameData.store(destPos, innerBlockId, facing, innerSticky);
        return true;
    }
}
