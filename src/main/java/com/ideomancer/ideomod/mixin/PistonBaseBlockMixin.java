package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.machine.MachinePistonBlock;
import com.ideomancer.ideomod.block.machine.MachineDrillBlock;
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
 * FEATURE 6: MACHINE DRILL IMMOVABILITY (isPushable)
 * ===================================================
 * A Machine Drill cannot be pushed or pulled while it is cutting (DRILLING).
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

        // --- Feature 6: Machine Drill immovability while cutting ---
        // A Drill in the middle of a cut (blockstate DRILLING) cannot be pushed or pulled,
        // so a moving machine waits for its drill.
        if (blockState.getBlock() instanceof MachineDrillBlock
                && blockState.getValue(MachineDrillBlock.DRILLING)) {
            cir.setReturnValue(false);
            return;
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
            // Vanilla's own first checks still apply (they are skipped by answering here):
            // nothing moves outside the world border, nor up out of the top of the world or
            // down out of the bottom. Without them a machine block pushed off the edge was
            // deleted with its contents.
            cir.setReturnValue(ideomod$insideWorld(level, pos, pushDirection));
        }
    }

    /** Vanilla PistonBaseBlock.isPushable's bounds checks, for a block that is not air. */
    @Unique
    private static boolean ideomod$insideWorld(Level level, BlockPos pos, Direction pushDirection) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() > level.getMaxBuildHeight() - 1) return false;
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        if (pushDirection == Direction.DOWN && pos.getY() == level.getMinBuildHeight()) return false;
        return !(pushDirection == Direction.UP && pos.getY() == level.getMaxBuildHeight() - 1);
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

    /**
     * Saves the machine data of every block this move carries, from vanilla's own resolver, at
     * the moment moveBlocks asks it for the blocks to move: after it succeeded (a failed resolve
     * never gets here), and before any block is replaced, so the block entities are still there.
     *
     * (Until 2026-11-16 this ran at HEAD of moveBlocks with a second resolver of its own, which
     * for a pull meant taking the piston head away early to match vanilla. Same blocks, now
     * worked out once.)
     */
    @Inject(method = "moveBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;getToPush()Ljava/util/List;",
            ordinal = 0))
    private void ideomod$saveMovableBlockEntityData(
            Level level, BlockPos pistonPos, Direction direction,
            boolean extending, CallbackInfoReturnable<Boolean> cir) {

        if (level.isClientSide()) return;   // the client side is PistonBaseBlockClientMixin's
        PistonStructureResolver resolver = com.ideomancer.ideomod.block.movement.ResolvedMoves.take();
        if (resolver == null) return;

        String dimensionKey = level.dimension().location().toString();
        Direction moveDir = extending ? direction : direction.getOpposite();
        for (BlockPos sourcePos : resolver.getToPush()) {
            saveBlockEntityAt(level, sourcePos, sourcePos.relative(moveDir), dimensionKey);
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
