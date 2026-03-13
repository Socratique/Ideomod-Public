package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.movement.IPistonMovable;
import com.ideomancer.ideomod.block.movement.MovingBlockEntityData;
import com.ideomancer.ideomod.block.movement.MovementScanHelper;
import com.ideomancer.ideomod.config.OddstoneConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
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
 * For EXTENSION (extending=true):
 *   We construct our own PistonStructureResolver to find which blocks
 *   will move, then save their data keyed by destination position.
 *
 * For RETRACTION (extending=false):
 *   Our resolver returns false (likely due to block entity checks deep
 *   in the resolver's internal logic), but vanilla still moves the blocks.
 *   Instead of fighting the resolver, we directly check the pull position
 *   (pistonPos + direction * 2) and scan the sticky group attached to it.
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
 * the framed version should also be immovable. Vanilla doesn't use
 * PushReaction for this — it hardcodes specific blocks (obsidian,
 * crying obsidian, reinforced deepslate) and checks getDestroySpeed
 * == -1 for bedrock-type blocks. We mirror those exact checks on the
 * inner block.
 *
 * This makes framed obsidian behave like real obsidian for piston purposes,
 * and automatically handles any future whitelisted block with -1 destroy speed.
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
    // FEATURE 1 + 3 + 4: isPushable overrides
    // ========================================================================

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void ideomod$allowMovableBlockEntities(
            BlockState blockState, Level level, BlockPos pos,
            Direction pushDirection, boolean allowDestroy, Direction pistonFacing,
            CallbackInfoReturnable<Boolean> cir) {

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
        // immovable. Vanilla doesn't use PushReaction.BLOCK for this — it
        // hardcodes specific blocks (obsidian, crying_obsidian, reinforced
        // deepslate) and checks getDestroySpeed == -1 (bedrock etc.).
        // We mirror that logic: check if the inner block's default state
        // would fail vanilla's own isPushable checks.
        //
        // This runs BEFORE the Feature 1 IPistonMovable check so that
        // framed obsidian is correctly blocked even though FramedBlockEntity
        // implements IPistonMovable.
        if (blockState.getBlock() instanceof FramedBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FramedBlockEntity fbe && fbe.hasInnerBlock()) {
                BlockState innerState = fbe.getInnerDefaultState();
                // Mirror vanilla isPushable: hardcoded immovable blocks
                // Using is() for registry-safe comparison
                if (innerState.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)
                        || innerState.is(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN)
                        || innerState.is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE)) {
                    Ideomod.LOGGER.debug("[PistonBaseMixin] Framed block immovable: " +
                            "inner block {} is hardcoded immovable at {}",
                            fbe.getInnerBlockId(), pos);
                    cir.setReturnValue(false);
                    return;
                }
                // Mirror vanilla isPushable: indestructible blocks (bedrock, etc.)
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
     * We cannot rely on PistonStructureResolver for retraction -- it returns false
     * for our IPistonMovable blocks (likely due to internal isPushable calls with
     * different parameters than what our HEAD mixin intercepts). But vanilla's own
     * moveBlocks DOES move the blocks successfully.
     *
     * Instead, we directly check the pull position and scan the sticky group.
     *
     * The pulled block is at pistonPos + direction * 2 (one past the piston head).
     * Movement direction for retraction is direction.getOpposite() (toward piston).
     * So destination = sourcePos.relative(direction.getOpposite()).
     */
    private void saveForRetraction(Level level, BlockPos pistonPos,
                                    Direction direction, String dimensionKey) {
        Direction moveDir = direction.getOpposite(); // blocks move toward the piston

        // The block being pulled is 2 spaces out from the piston base
        // (1 for the head + 1 for the pulled block)
        BlockPos pullPos = pistonPos.relative(direction, 2);
        BlockState pullState = level.getBlockState(pullPos);

        if (pullState.isAir()) return;

        // Save the pulled block itself
        BlockPos destPos = pullPos.relative(moveDir);
        saveBlockEntityAt(level, pullPos, destPos, dimensionKey);

        // Track all saved positions for sympathetic scan
        java.util.Set<BlockPos> savedPositions = new java.util.HashSet<>();
        savedPositions.add(pullPos);

        // If the pulled block is sticky or a vanilla metal conductor,
        // scan its group for more blocks to save
        if (pullState.isStickyBlock()
                || com.ideomancer.ideomod.block.frame.FrameMaterial.fromVanillaBlock(pullState.getBlock()) != null) {
            scanStickyGroup(level, pullPos, moveDir, dimensionKey, savedPositions);
        }

        // Sympathetic scan: find oddstone/framed oddstone at range
        // that the resolver's sympathetic search would have added.
        // Without this, sympathetically linked blocks lose their BE data.
        scanSympathetic(level, moveDir, dimensionKey, savedPositions);

        Ideomod.LOGGER.debug("[PistonBaseMixin] Retraction save complete for pull pos {}", pullPos);
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
        Set<BlockPos> groupBlocks = MovementScanHelper.scanStickyGroup(level, startPos, 12);

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
        int radius = OddstoneConfig.getSympatheticRadius();

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
                if (checkState.isStickyBlock()
                        || com.ideomancer.ideomod.block.frame.FrameMaterial.fromVanillaBlock(checkState.getBlock()) != null) {
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
