package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.machine.MachinePistonBlock;
import com.ideomancer.ideomod.block.movement.IPistonMovable;
import com.ideomancer.ideomod.block.movement.MovingBlockEntityData;
import com.ideomancer.ideomod.block.movement.MovementScanHelper;
import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/**
 * Mixin into PistonBaseBlock for four Ideomod features:
 *
 * FEATURE 1: ALLOW MOVABLE BLOCK ENTITIES (isPushable)
 * =====================================================
 * Vanilla hardcodes "block entities can't move" in isPushable().
 * We bypass this for block entities implementing IPistonMovable.
 *
 * FEATURE 2: SAVE BLOCK ENTITY DATA BEFORE VANILLA PISTON MOVES (moveBlocks)
 * ============================================================================
 * We save IPistonMovable block entity data at HEAD of moveBlocks(),
 * before vanilla destroys the block entities during the move.
 *
 * For both EXTENSION and RETRACTION:
 *   We construct our own PistonStructureResolver to find which blocks
 *   will move, then save their data keyed by destination position.
 *
 * RETRACTION TIMING: For retraction, vanilla removes the piston head
 * BEFORE creating its resolver, but our HEAD injection fires before that.
 * We must remove the head ourselves before running our resolver, otherwise
 * the resolver's forward walk hits the non-pushable piston head and fails
 * even though vanilla's resolver (running after head removal) succeeds.
 *
 * Restore is handled by PistonMovingBlockEntityMixin.
 *
 * CRITICAL: moveBlocks runs on BOTH client and server (via triggerEvent).
 * We must only save on the server side. Client-side block entities don't
 * have synced inventory data, so saving on the client would overwrite the
 * server's good data with empty data in the shared static map.
 *
 * FEATURE 3: ODDSTONE STICKY PISTON IMMUNITY (isPushable + triggerEvent)
 * =======================================================================
 * Oddstone cannot be DIRECTLY pulled by sticky pistons. When a sticky
 * piston retracts, if the block at the pull position (pistonPos + dir*2)
 * is Oddstone or Framed Oddstone, the piston retracts without pulling.
 *
 * IMPORTANT: This only blocks oddstone from being the DIRECTLY PULLED
 * block — the one the sticky piston grabs. If oddstone is part of a
 * group (e.g., pulled along by slime via its own stickiness), the group
 * moves normally. The immunity is about the piston's adhesion, not about
 * oddstone being immovable.
 *
 * This is thematic: Oddstone is "ungrabable" -- it decides what it
 * connects to, nothing else can claim it. The piston can't grab it,
 * but another sticky block CAN drag it if oddstone sticks to that block.
 *
 * Implementation: triggerEvent stores the SPECIFIC pull position in a
 * ThreadLocal. isPushable only blocks oddstone at that exact position.
 * This prevents the old bug where ANY oddstone in a retraction group
 * would cancel the entire retraction.
 *
 * FEATURE 4: FRAMED BLOCK INNER PUSH REACTION (isPushable)
 * =========================================================
 * When a FramedBlock wraps an immovable inner block (e.g., obsidian),
 * the framed version should also be immovable. Uses the
 * ideomod:immovable_when_framed tag for data-driven extensibility
 * (Audit item #20), plus the destroySpeed == -1 fallback for
 * bedrock-type blocks.
 *
 * FEATURE 5: MACHINE PISTON IMMOVABILITY (isPushable)
 * ====================================================
 * Machine Pistons with any extended heads cannot be pushed or pulled.
 * Matches vanilla piston behavior where extended pistons are immovable.
 * Uses MachinePistonBlock.hasExtendedHead() to check all 6 neighbors
 * for head blocks pointing back at the piston.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {

    // ========================================================================
    // FEATURE 3: ThreadLocal for the specific pull position
    // ========================================================================

    /**
     * Stores the exact BlockPos that the sticky piston is trying to pull
     * during retraction. Set before triggerEvent runs, cleared after.
     * Only blocks oddstone at this EXACT position — not elsewhere in group.
     *
     * Null means "not in retraction context" (same as the old boolean flag,
     * but with position specificity).
     */
    @Unique
    private static final ThreadLocal<BlockPos> ideomod$pullTargetPos = ThreadLocal.withInitial(() -> null);

    // ========================================================================
    // FEATURE 1 + 3 + 4 + 5: isPushable overrides
    // ========================================================================

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void ideomod$allowMovableBlockEntities(
            BlockState blockState, Level level, BlockPos pos,
            Direction pushDirection, boolean allowDestroy, Direction pistonFacing,
            CallbackInfoReturnable<Boolean> cir) {

        // --- Feature 5: Machine Piston immovability when extended ---
        // Like vanilla pistons, Machine Pistons cannot be pushed while any
        // head is extended. Check all 6 neighbors for head blocks pointing
        // back at this position.
        if (blockState.getBlock() instanceof MachinePistonBlock) {
            if (MachinePistonBlock.hasExtendedHead(level, pos)) {
                Ideomod.LOGGER.debug("[PistonBaseMixin] Machine Piston immovable: " +
                        "has extended head at {}", pos);
                cir.setReturnValue(false);
                return;
            }
        }

        // --- Feature 3: Oddstone pull immunity during sticky piston retraction ---
        // Only blocks the DIRECTLY PULLED block (pullTargetPos), not every
        // oddstone in the group. If oddstone is dragged by group stickiness
        // (e.g., slime stuck to oddstone), the group moves normally.
        BlockPos pullTarget = ideomod$pullTargetPos.get();
        if (pullTarget != null && pullTarget.equals(pos)) {
            if (MovementScanHelper.isOddstoneOrFramedOddstone(level, pos)) {
                Ideomod.LOGGER.debug("[PistonBaseMixin] Oddstone pull immunity: " +
                        "blocking direct sticky piston pull at {}", pos);
                cir.setReturnValue(false);
                return;
            }
        }

        // --- Feature 4: Framed block inner push reaction ---
        // If the block is a FramedBlock, check if the inner block would be
        // immovable. Uses the ideomod:immovable_when_framed tag for
        // data-driven extensibility, plus destroySpeed == -1 for bedrock-type
        // blocks. (Audit item #20: replaced hardcoded block checks with tag.)
        //
        // This runs BEFORE the Feature 1 IPistonMovable check so that
        // framed obsidian is correctly blocked even though FramedBlockEntity
        // implements IPistonMovable.
        if (blockState.getBlock() instanceof FramedBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FramedBlockEntity fbe && fbe.hasInnerBlock()) {
                BlockState innerState = fbe.getInnerDefaultState();
                // Tag-based immovable check (replaces hardcoded obsidian/crying_obsidian/
                // reinforced_deepslate). Datapacks and other mods can extend this tag.
                if (innerState.is(ModTags.IMMOVABLE_WHEN_FRAMED)) {
                    Ideomod.LOGGER.debug("[PistonBaseMixin] Framed block immovable: " +
                            "inner block {} is in immovable_when_framed tag at {}",
                            fbe.getInnerBlockId(), pos);
                    cir.setReturnValue(false);
                    return;
                }
                // Fallback: indestructible blocks (bedrock, barriers, etc.)
                if (innerState.getDestroySpeed(level, pos) == -1.0f) {
                    Ideomod.LOGGER.debug("[PistonBaseMixin] Framed block immovable: " +
                            "inner block {} has destroy speed -1 at {}",
                            fbe.getInnerBlockId(), pos);
                    cir.setReturnValue(false);
                    return;
                }
            } else {
                Ideomod.LOGGER.debug("[PistonBaseMixin] Feature 4: FramedBlock at {} " +
                        "but BE check failed: be={}, hasInner={}",
                        pos, be != null ? be.getClass().getSimpleName() : "null",
                        be instanceof FramedBlockEntity fbe2 ? fbe2.hasInnerBlock() : "N/A");
            }
        }

        // --- Feature 1: Allow IPistonMovable block entities ---
        if (!blockState.hasBlockEntity()) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IPistonMovable) {
            cir.setReturnValue(true);
        }
    }

    // ========================================================================
    // FEATURE 3: Set pull target position during triggerEvent
    // ========================================================================

    /**
     * Intercept triggerEvent to calculate and store the specific pull position.
     *
     * In vanilla's triggerEvent, when a sticky piston retracts (type 1),
     * the block it tries to pull is at pistonPos + pushDirection * 2.
     * We store that exact position so isPushable can check it.
     *
     * We need the push direction from the blockstate, which is the FACING
     * property of the piston. The 'data' parameter of triggerEvent encodes
     * the direction as direction.get3DDataValue().
     */
    @Inject(method = "triggerEvent", at = @At("HEAD"))
    private void ideomod$setPullTargetPos(
            BlockState state, Level level, BlockPos pos, int type, int data,
            CallbackInfoReturnable<Boolean> cir) {
        // type 0 = extend, type 1 = retract, type 2 = retract (1-tick pulse)
        if (type == 1 || type == 2) {
            // Defensive reset: if non-null from a previous failed run (exception
            // thrown between HEAD and RETURN), warn and clear before setting new value.
            BlockPos stale = ideomod$pullTargetPos.get();
            if (stale != null) {
                Ideomod.LOGGER.warn("[PistonBaseMixin] pullTargetPos was non-null ({}) at HEAD — " +
                        "defensive reset from previous failed run", stale);
                ideomod$pullTargetPos.set(null);
            }
            Direction direction = Direction.from3DDataValue(data);
            BlockPos pullPos = pos.relative(direction, 2);
            ideomod$pullTargetPos.set(pullPos);
        }
    }

    /**
     * Clear the pull target after triggerEvent completes.
     */
    @Inject(method = "triggerEvent", at = @At("RETURN"))
    private void ideomod$clearPullTargetPos(
            BlockState state, Level level, BlockPos pos, int type, int data,
            CallbackInfoReturnable<Boolean> cir) {
        if (type == 1 || type == 2) {
            ideomod$pullTargetPos.set(null);
        }
    }


    // ========================================================================
    // FEATURE 2: SAVE BLOCK ENTITY DATA BEFORE VANILLA PISTON MOVES
    // ========================================================================

    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private void ideomod$saveMovableBlockEntityData(
            Level level, BlockPos pistonPos, Direction direction,
            boolean extending, CallbackInfoReturnable<Boolean> cir) {

        if (level.isClientSide()) return;

        String dimensionKey = level.dimension().location().toString();

        if (extending) {
            saveForExtension(level, pistonPos, direction, dimensionKey);
        } else {
            saveForRetraction(level, pistonPos, direction, dimensionKey);
        }
    }

    /**
     * Extension: construct a resolver, iterate toPush, save IPistonMovable data.
     * The resolver works reliably for extension because our isPushable mixin
     * handles the block entity check.
     */
    private void saveForExtension(Level level, BlockPos pistonPos,
                                   Direction direction, String dimensionKey) {
        PistonStructureResolver resolver = new PistonStructureResolver(
                level, pistonPos, direction, true
        );

        if (!resolver.resolve()) return;

        List<BlockPos> toPush = resolver.getToPush();
        if (toPush.isEmpty()) return;

        for (BlockPos sourcePos : toPush) {
            saveBlockEntityAt(level, sourcePos, sourcePos.relative(direction), dimensionKey);
        }
    }

    /**
     * Retraction: save block entity data for blocks being pulled by a sticky piston.
     *
     * Uses PistonStructureResolver (same as saveForExtension). The resolver's
     * addBlockLine + addBranchingBlocks recursion correctly discovers ALL blocks
     * vanilla will move. Our isPushable mixin makes the resolver work for
     * IPistonMovable blocks.
     *
     * CRITICAL TIMING FIX (2026-03-31c): This method runs at HEAD of moveBlocks,
     * but vanilla's moveBlocks removes the piston head BEFORE creating its own
     * resolver. Without this fix, our resolver's forward walk hits the still-present
     * piston head (which isn't pushable), making addBlockLine return false and
     * resolve() return false — even though vanilla's resolver succeeds after
     * removing the head. The result: blocks move but data isn't saved, causing
     * spec item deletion and state loss (pulsers stuck, etc.).
     *
     * Fix: We remove the piston head before creating our resolver, mirroring
     * vanilla's behavior. We don't need to restore it because vanilla will
     * remove it in the very next line of moveBlocks after our HEAD returns.
     *
     * Finishes with a sympathetic scan for remote oddstone blocks.
     *
     * Written by Claude, directed and guided by The Ideomancer.
     */
    private void saveForRetraction(Level level, BlockPos pistonPos,
                                    Direction direction, String dimensionKey) {
        Direction moveDir = direction.getOpposite(); // blocks move toward the piston

        BlockPos pullPos = pistonPos.relative(direction, 2);
        BlockState pullState = level.getBlockState(pullPos);

        if (pullState.isAir()) return;

        // FIX 2026-03-31c: Remove the piston head before running our resolver.
        // Vanilla's moveBlocks removes it before creating its resolver, but our
        // HEAD injection fires before that. Without this, the resolver's forward
        // walk from startPos hits the piston head and fails.
        //
        // We use level.removeBlock(pos, false) — EXACTLY matching vanilla's call.
        // This uses flag 3 (UPDATE_NEIGHBORS | UPDATE_CLIENTS) with isMoving=false,
        // which is safe because vanilla has already set the piston to EXTENDED=false
        // in triggerEvent before calling moveBlocks. PistonHeadBlock.onRemove only
        // tries to destroy the piston base when EXTENDED=true, so it's a no-op here.
        //
        // Using the same flags ensures our resolver sees the exact same post-removal
        // world state (including any neighbor update cascades) that vanilla's would.
        BlockPos headPos = pistonPos.relative(direction);
        BlockState headState = level.getBlockState(headPos);
        boolean removedHead = false;
        if (headState.getBlock() instanceof PistonHeadBlock) {
            level.removeBlock(headPos, false);
            removedHead = true;
            Ideomod.LOGGER.debug("[RETRACT] Removed piston head at {} before resolver (vanilla would do this next)",
                    headPos);
        }

        PistonStructureResolver resolver = new PistonStructureResolver(
                level, pistonPos, direction, false  // extending=false for retraction
        );

        if (!resolver.resolve()) {
            // If the resolver fails even after head removal, vanilla will also
            // fail — no blocks will move. Restore the head so vanilla finds it
            // in the expected state when it tries to remove it.
            if (removedHead) {
                level.setBlock(headPos, headState, 3);
            }
            Ideomod.LOGGER.debug("[RETRACT] Resolver returned false for retraction at {} — " +
                    "no blocks will move, skipping save", pistonPos);
            return;
        }
        // Don't restore the head — vanilla's very next line in moveBlocks
        // will call level.removeBlock(headPos, false). Finding AIR there is
        // harmless (no-op removal). This avoids a redundant set+remove cycle.

        List<BlockPos> toPush = resolver.getToPush();
        java.util.Set<BlockPos> savedPositions = new java.util.HashSet<>();

        for (BlockPos sourcePos : toPush) {
            BlockPos destPos = sourcePos.relative(moveDir);
            saveBlockEntityAt(level, sourcePos, destPos, dimensionKey);
            savedPositions.add(sourcePos);
        }

        // Sympathetic scan: resolver doesn't know about our sympathetic system
        scanSympathetic(level, moveDir, dimensionKey, savedPositions);
    }

    /**
     * Scan for additional IPistonMovable blocks attached to the pulled block
     * via stickiness. This handles cases where a sticky Machine Frame drags
     * other Machine Frames during retraction.
     *
     * Uses MovementScanHelper.scanStickyGroup to find the group, then
     * iterates to save block entity data for each block.
     */
    private void scanStickyGroup(Level level, BlockPos startPos,
                                  Direction moveDir, String dimensionKey,
                                  java.util.Set<BlockPos> savedPositions) {
        // Use helper to find all blocks in the sticky group
        // FIX 2026-03-29: Was hardcoded to 12. Now reads from config to match
        // the client mixin (which uses ideomod$getMaxPushBlocks) and the
        // brace-aware push limit system.
        Set<BlockPos> groupBlocks = MovementScanHelper.scanStickyGroup(
                level, startPos, MovementScanHelper.getBasePushLimit());

        // Iterate newly found positions and save their data
        for (BlockPos pos : groupBlocks) {
            if (!savedPositions.contains(pos)) {
                BlockPos destPos = pos.relative(moveDir);
                saveBlockEntityAt(level, pos, destPos, dimensionKey);
                savedPositions.add(pos);
            }
        }
    }

    /**
     * Scan for sympathetically linked Oddstone/Framed Oddstone blocks
     * that the PistonStructureResolverMixin's sympathetic search would
     * have added to the push list. These blocks are at range (not adjacent)
     * so the BFS in scanStickyGroup won't find them.
     *
     * Uses MovementScanHelper.scanSympathetic to find all sympathetic oddstone
     * positions, then iterates them to save block entity data and scan their
     * sticky groups.
     *
     * CRITICAL: After finding a sympathetic oddstone, also scans its sticky
     * group. Without this, blocks attached to the remote oddstone via stickiness
     * (e.g. machine-activated framed blocks) would not have their block entity
     * data saved, causing data loss on retraction. This was the root cause of
     * the "sympathetic retraction with machine-activated framed blocks" bug.
     */
    private void scanSympathetic(Level level, Direction moveDir,
                                  String dimensionKey,
                                  java.util.Set<BlockPos> savedPositions) {
        int radius = IdeomodConfig.getSympatheticRadius();

        // Use helper to find all sympathetic oddstone blocks
        Set<BlockPos> allFound = MovementScanHelper.scanSympathetic(level, savedPositions, radius);

        // Iterate newly found positions (those not in savedPositions)
        for (BlockPos checkPos : allFound) {
            if (!savedPositions.contains(checkPos)) {
                BlockState checkState = level.getBlockState(checkPos);

                // Save the oddstone's block entity data
                BlockPos destPos = checkPos.relative(moveDir);
                saveBlockEntityAt(level, checkPos, destPos, dimensionKey);
                savedPositions.add(checkPos);

                // Scan its sticky group too (resolver's addBlockLine behavior)
                if (checkState.isStickyBlock()) {
                    scanStickyGroup(level, checkPos, moveDir,
                            dimensionKey, savedPositions);
                }
            }
        }
    }

    /**
     * Save a single block entity's IPistonMovable data if applicable.
     */
    private void saveBlockEntityAt(Level level, BlockPos sourcePos,
                                    BlockPos destPos, String dimensionKey) {
        BlockEntity be = level.getBlockEntity(sourcePos);
        if (be instanceof IPistonMovable movable) {
            CompoundTag data = movable.saveMovementData();
            if (data != null) {
                MovingBlockEntityData.store(dimensionKey, destPos, data);
                Ideomod.LOGGER.debug("[PistonBaseMixin] SAVED IPistonMovable data from {} -> dest {} (tags: {})",
                        sourcePos, destPos, data.getAllKeys());
            }
        }
    }
}
