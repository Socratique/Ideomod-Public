package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.frame.IFrameBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;
import com.ideomancer.ideomod.block.piston.RangedPistonFlags;
import com.ideomancer.ideomod.block.movement.MovementScanHelper;
import com.ideomancer.ideomod.block.movement.StickinessRules;
import com.ideomancer.ideomod.config.OddstoneConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin into PistonStructureResolver for four Ideomod features:
 *
 * FEATURE 1: ONE-WAY STICKINESS
 * ==============================
 * Selectively blocks sticky grabs based on rules in StickinessRules.
 * Example: slime tries to grab Oddstone -> BLOCKED.
 *          Oddstone tries to grab slime -> ALLOWED.
 *
 * Enforced at two injection points:
 *
 *   A) HEAD of addBlockLine (during branching only) -- ACTIVE GRABBING
 *      Checks directional rules, grab immunity, and framed odd slime
 *      second-chance logic. This is the primary enforcement point.
 *
 *   B) @Redirect on canStickTo (inside addBlockLine) -- CHAIN COHESION
 *      Checks directional rules on both calls. Checks grab immunity on
 *      the FIRST call only (front block reaching backward).
 *
 *      Vanilla's backward walk calls canStickTo twice per iteration:
 *        Call 1: oldState.canStickTo(blockstate) — front reaching back
 *        Call 2: blockstate.canStickTo(oldState) — behind responding
 *      Vanilla ANDs them together. A toggle tracks which call we're in.
 *
 *      Grab immunity only applies on Call 1 because the front block is
 *      the active grabber. Call 2 is the behind block's passive response.
 *      Without this distinction, chains like oddstone-slime would break:
 *      oddstone reaching back to slime passes, but slime responding to
 *      oddstone would hit Rule 1 (non-oddstone can't grab oddstone).
 *
 * FEATURE 2: DIRECTIONAL STICKINESS (Odd Slime + Framed Odd Slime)
 * =================================================================
 * Real Odd Slime uses a blanket directional rule (StickinessRules).
 *
 * Framed Odd Slime does NOT use a blanket directional rule because that
 * would also disable frame stickiness in non-FACING directions. Instead,
 * the mixin handles it inline:
 *   1. Check if it's a Framed Odd Slime pushed in non-FACING direction
 *   2. If so, the inner stickiness is blocked
 *   3. SECOND CHANCE: check if ACTIVE frame stickiness independently
 *      allows the grab (same-material frame or machine family)
 *   4. If yes, allow the grab despite inner directional block
 *
 * FEATURE 3: ODDSTONE SYMPATHETIC MOVEMENT
 * =========================================
 * Extended range sticky search for Oddstone blocks AND Framed Blocks
 * whose inner block is Oddstone. "See through" frames for sympathetic.
 *
 * FEATURE 4: RANGED PISTON START POSITION REDIRECT
 * =================================================
 * When a ranged piston creates the resolver, it sets pistonPos far away.
 * This mixin overrides startPos to point at the real target block.
 *
 * FEATURE 5: FRAMED BLOCK IMMOVABLE INNER (BRANCHING)
 * ====================================================
 * When a sticky group tries to include a framed block whose inner is
 * immovable (obsidian, crying obsidian, reinforced deepslate, or any
 * block with destroySpeed -1), the entire push fails. This mirrors
 * vanilla behavior where obsidian in a slime group blocks the push.
 *
 * This check is needed in the resolver because vanilla's addBlockLine
 * returns TRUE (skip) when isPushable returns false, rather than FALSE
 * (fail). For blocks that ARE part of a sticky group, skipping is wrong —
 * the immovable block should anchor the group and prevent movement.
 *
 * HISTORY / KNOWN PITFALLS:
 * =========================
 * - Grab immunity was originally checked on BOTH canStickTo calls in the
 *   backward walk. This broke oddstone-slime chains in the push axis:
 *   Call 1 (oddstone->slime) passed, Call 2 (slime->oddstone) hit Rule 1.
 *   Vanilla ANDs both results, so the chain broke.
 * - Removing grab immunity entirely overcorrected: slime could then chain
 *   backward to oddstone (Call 1 slime->oddstone went unchecked).
 * - The fix uses a call-pair toggle: grab immunity applies only on Call 1
 *   (front block reaching back = active grabber). Call 2 (behind block
 *   responding) skips grab immunity.
 * - Directional rules remain on both calls because they represent genuine
 *   physical constraints (odd slime direction), not one-way grab rules.
 * - The isActivePuller exemption was removed — it was a workaround for
 *   the original bug where grab immunity fired on both calls.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {

    // ====================================================================
    // SHADOWED FIELDS AND METHODS
    // ====================================================================

    @Shadow @Final private Level level;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private Direction pushDirection;
    @Shadow @Final @Mutable private BlockPos startPos;
    @Shadow protected abstract boolean addBlockLine(BlockPos pos, Direction dir);

    @Unique
    private static final ThreadLocal<Boolean> ideomod$inBranching = ThreadLocal.withInitial(() -> false);

    /**
     * Tracks which canStickTo call we're in within the backward walk.
     * 
     * Vanilla's backward walk evaluates:
     *   oldState.canStickTo(blockstate) && blockstate.canStickTo(oldState)
     * 
     * Call 1 (firstCanStickToCall=true):  self=front(oldState), other=behind(blockstate)
     *   -> This is the FRONT block reaching back. Grab immunity applies here
     *      because the front block is the one initiating the chain extension.
     * 
     * Call 2 (firstCanStickToCall=false): self=behind(blockstate), other=front(oldState)
     *   -> This is the BEHIND block's compatibility check. Grab immunity is
     *      NOT checked here -- if the front block is allowed to grab, the
     *      behind block's canStickTo is sufficient.
     * 
     * The toggle flips on each call and resets to true when the pair completes.
     */
    @Unique
    private static final ThreadLocal<Boolean> ideomod$firstCanStickToCall = ThreadLocal.withInitial(() -> true);

    // ====================================================================
    // FEATURE 4: RANGED PISTON START POSITION REDIRECT
    // ====================================================================

    @Inject(method = "resolve", at = @At("HEAD"))
    private void ideomod$redirectStartPos(CallbackInfoReturnable<Boolean> cir) {
        BlockPos target = RangedPistonFlags.getTarget();
        if (target != null) {
            this.startPos = target;
        }
    }

    // ====================================================================
    // PHASE DETECTION
    // ====================================================================

    @Inject(method = "addBranchingBlocks", at = @At("HEAD"))
    private void ideomod$enterBranching(CallbackInfoReturnable<Boolean> cir) {
        ideomod$inBranching.set(true);
    }

    @Inject(method = "addBranchingBlocks", at = @At("TAIL"))
    private void ideomod$exitBranching(CallbackInfoReturnable<Boolean> cir) {
        ideomod$inBranching.set(false);
    }

    // ====================================================================
    // FEATURE 1A + 2A: ONE-WAY & DIRECTIONAL STICKINESS -- SIDEWAYS GRABS
    // ====================================================================

    @Inject(method = "addBlockLine", at = @At("HEAD"), cancellable = true)
    private void ideomod$blockOneWayGrabs(BlockPos pos, Direction dir,
                                           CallbackInfoReturnable<Boolean> cir) {

        // --- Feature 5: Framed block immovable inner check (ALL phases) ---
        // When any code path tries to add a framed block with an immovable
        // inner (e.g. framed obsidian), the ENTIRE push must fail.
        //
        // This fires before the branching guard because it applies to BOTH
        // line-building and branching. Vanilla's addBlockLine returns TRUE
        // when isPushable returns false (silently skipping the block), but
        // for framed blocks that are part of the sticky group, the immovable
        // inner should anchor the group and prevent all movement.
        {
            BlockState targetState = this.level.getBlockState(pos);
            if (!targetState.isAir() && ideomod$isFramedBlockWithImmovableInner(targetState, pos)) {
                cir.setReturnValue(false); // false = fail the entire resolve
                return;
            }
        }

        // --- One-way stickiness checks only apply during branching ---
        if (!ideomod$inBranching.get()) return;

        BlockPos grabberPos = pos.relative(dir.getOpposite());
        if (!this.toPush.contains(grabberPos)) return;

        BlockState grabberState = this.level.getBlockState(grabberPos);
        BlockState neighborState = this.level.getBlockState(pos);

        if (neighborState.isAir()) return;

        if (grabberState.isStickyBlock()) {
            // Standard directional check (covers real Odd Slime via registered rule)
            if (StickinessRules.isDirectionalGrabBlocked(grabberState, this.pushDirection)) {
                // Second chance for Framed Odd Slime: frame stickiness still works
                // (Real Odd Slime has no frame stickiness, so this only fires for FramedBlock)
                if (ideomod$wouldFrameStickinessAllow(grabberState, neighborState)) {
                    // Frame stickiness allows this grab — don't block
                } else {
                    cir.setReturnValue(true);
                    return;
                }
            }

            // Framed Odd Slime directional check (not registered as a rule)
            if (ideomod$isFramedOddSlimeDirectionallyBlocked(grabberState)) {
                // Inner stickiness blocked by direction. Check frame stickiness.
                if (ideomod$wouldFrameStickinessAllow(grabberState, neighborState)) {
                    // Frame stickiness allows — don't block
                } else {
                    cir.setReturnValue(true);
                    return;
                }
            }

            // Standard grab immunity check
            if (StickinessRules.isGrabBlocked(grabberState, neighborState)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    // ====================================================================
    // FEATURE 1B + 2B: STICKINESS RULES -- CHAIN COHESION (BACKWARD WALK)
    // ====================================================================

    /**
     * Intercepts ALL canStickTo calls inside addBlockLine.
     *
     * IMPORTANT CONTEXT:
     * ==================
     * Vanilla's backward walk checks:
     *   oldState.canStickTo(blockstate) && blockstate.canStickTo(oldState)
     *
     * This redirect fires for BOTH calls. We use a toggle to distinguish them:
     *
     *   Call 1 (first=true):  self=oldState (FRONT), other=blockstate (BEHIND)
     *     The front block is reaching backward to grab the candidate.
     *     Grab immunity IS checked — the front block is the active grabber.
     *     Example: slime reaching back to oddstone -> blocked by Rule 1.
     *
     *   Call 2 (first=false): self=blockstate (BEHIND), other=oldState (FRONT)
     *     The behind block's compatibility response to the front block.
     *     Grab immunity is NOT checked — this is passive agreement, not active grab.
     *     Example: oddstone responding to slime's reach -> just canStickTo.
     *
     * WHY THIS MATTERS:
     * =================
     * Without the toggle, grab immunity fired on BOTH calls. This broke
     * chains like [slime]--[oddstone]-->push where oddstone should hold
     * slime behind it: Call 1 (oddstone->slime) passed, but Call 2
     * (slime->oddstone) hit Rule 1 (non-oddstone can't grab oddstone).
     * Since vanilla ANDs both results, the chain broke even though oddstone
     * legitimately wanted to hold slime.
     *
     * Removing grab immunity entirely overcorrected — slime could then
     * chain backward to oddstone (Call 1 slime->oddstone unchecked).
     *
     * The fix: only Call 1 (the front block reaching back) is subject to
     * grab immunity. Call 2 (behind block responding) only needs canStickTo
     * + directional checks.
     *
     * Directional rules apply on BOTH calls because they represent genuine
     * physical constraints (odd slime direction), not one-way social rules.
     */
    @Redirect(method = "addBlockLine",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean ideomod$checkCanStickToWithRules(BlockState self, BlockState other) {
        // Track which call in the pair we're on.
        // Call 1: oldState.canStickTo(blockstate) — front reaching back
        // Call 2: blockstate.canStickTo(oldState) — behind responding
        boolean isFirstCall = ideomod$firstCanStickToCall.get();
        ideomod$firstCanStickToCall.set(!isFirstCall);

        // First: respect the original canStickTo result
        if (!self.canStickTo(other)) {
            // If first call fails, vanilla short-circuits the &&, so the
            // second call never fires. Reset toggle for next iteration.
            if (isFirstCall) {
                ideomod$firstCanStickToCall.set(true);
            }
            return false;
        }

        // --- Directional checks (apply on BOTH calls) ---
        if (StickinessRules.isDirectionalGrabBlocked(self, this.pushDirection)) {
            if (!ideomod$wouldFrameStickinessAllow(self, other)) {
                if (isFirstCall) ideomod$firstCanStickToCall.set(true);
                return false;
            }
        }
        if (ideomod$isFramedOddSlimeDirectionallyBlocked(self)) {
            if (!ideomod$wouldFrameStickinessAllow(self, other)) {
                if (isFirstCall) ideomod$firstCanStickToCall.set(true);
                return false;
            }
        }
        if (StickinessRules.isDirectionalGrabBlocked(other, this.pushDirection)) {
            if (!ideomod$wouldFrameStickinessAllow(other, self)) {
                if (isFirstCall) ideomod$firstCanStickToCall.set(true);
                return false;
            }
        }
        if (ideomod$isFramedOddSlimeDirectionallyBlocked(other)) {
            if (!ideomod$wouldFrameStickinessAllow(other, self)) {
                if (isFirstCall) ideomod$firstCanStickToCall.set(true);
                return false;
            }
        }

        // --- Grab immunity (FIRST CALL ONLY) ---
        // Call 1: self=front block (oldState), other=behind block (blockstate).
        // The front block is the one whose isStickyBlock() started the backward
        // walk. It's actively reaching back. Grab immunity applies here.
        if (isFirstCall) {
            if (StickinessRules.isGrabBlocked(self, other)) {
                // Reset toggle — vanilla will short-circuit, skip Call 2
                ideomod$firstCanStickToCall.set(true);
                return false;
            }
        }

        // Call 2 (or Call 1 that passed): reset toggle for next loop iteration
        if (!isFirstCall) {
            ideomod$firstCanStickToCall.set(true);
        }

        return true;
    }

    // ====================================================================
    // FRAMED ODD SLIME HELPERS
    // ====================================================================

    /**
     * Check if a block is a Framed Odd Slime being pushed in a non-FACING
     * direction. In this case, the INNER stickiness should be disabled,
     * but frame stickiness may still work.
     *
     * This is NOT registered as a StickinessRules directional rule because
     * blanket directional rules disable ALL stickiness — they don't have
     * the context to allow frame stickiness as a fallback.
     */
    @Unique
    private boolean ideomod$isFramedOddSlimeDirectionallyBlocked(BlockState state) {
        if (!(state.getBlock() instanceof FramedBlock)) return false;
        if (state.getValue(FramedBlock.INNER_STICKY) != FramedBlock.STICKY_ODD_SLIME) return false;
        Direction facing = state.getValue(FramedBlock.FACING);
        return !this.pushDirection.equals(facing);
    }

    /**
     * Check if a block's ACTIVE frame stickiness would independently allow
     * a grab on the given neighbor. This is the "second chance" after inner
     * stickiness or directional rules have blocked the grab.
     *
     * Returns true if the block is an ACTIVE FramedBlock and the neighbor
     * is a same-material IFrameBlock or Machine Family member.
     */
    @Unique
    private boolean ideomod$wouldFrameStickinessAllow(BlockState grabberState, BlockState neighborState) {
        if (!(grabberState.getBlock() instanceof FramedBlock framedBlock)) return false;
        if (!grabberState.getValue(FramedBlock.ACTIVE)) return false;

        // Same-material frame check
        if (neighborState.getBlock() instanceof IFrameBlock otherFrame) {
            return otherFrame.getFrameMaterial() == framedBlock.getFrameMaterial();
        }

        // Machine Family check
        if (MachineFrameBlock.isMachineFamily(neighborState.getBlock())) return true;

        return false;
    }

    // ====================================================================
    // FEATURE 3: ODDSTONE SYMPATHETIC MOVEMENT
    // ====================================================================

    @Inject(method = "addBranchingBlocks", at = @At("TAIL"), cancellable = true)
    private void ideomod$sympatheticSearch(CallbackInfoReturnable<Boolean> cir) {
        int radius = OddstoneConfig.getSympatheticRadius();

        // Use helper to find all sympathetic oddstone blocks
        java.util.Set<BlockPos> allFound = MovementScanHelper.scanSympathetic(level, new java.util.HashSet<>(this.toPush), radius);

        boolean addedAny = false;

        // Try to add newly found blocks via addBlockLine (resolver's normal flow)
        for (BlockPos checkPos : allFound) {
            if (!this.toPush.contains(checkPos)) {
                List<BlockPos> snapshot = new ArrayList<>(this.toPush);
                for (BlockPos oddPos : snapshot) {
                    if (!MovementScanHelper.isOddstoneOrFramedOddstone(level, oddPos)) continue;

                    for (Direction face : Direction.values()) {
                        if (checkPos.equals(oddPos.relative(face, radius)) ||
                            checkPos.equals(oddPos.relative(face, 2))) { // Match both near and far
                            boolean result = this.addBlockLine(checkPos, face);
                            if (this.toPush.contains(checkPos)) {
                                addedAny = true;
                                break;
                            }
                        }
                    }
                    if (this.toPush.contains(checkPos)) break;
                }
            }
        }

        if (addedAny) {
            cir.setReturnValue(true);
        }
    }


    // ====================================================================
    // FEATURE 5: FRAMED BLOCK IMMOVABLE INNER CHECK
    // ====================================================================

    /**
     * Check if a block is a FramedBlock whose inner block would be immovable.
     * Mirrors vanilla's isPushable hardcoded checks (obsidian, crying obsidian,
     * reinforced deepslate, destroy speed -1).
     *
     * Used during branching to fail the entire resolve when an active frame
     * group contains an immovable framed block. Without this, vanilla's
     * addBlockLine silently skips the block (returns true) instead of failing
     * the push, because isPushable returning false for non-air blocks causes
     * addBlockLine to return true (skip) rather than false (fail).
     */
    @Unique
    private boolean ideomod$isFramedBlockWithImmovableInner(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof FramedBlock)) return false;

        BlockEntity be = this.level.getBlockEntity(pos);
        if (!(be instanceof FramedBlockEntity fbe) || !fbe.hasInnerBlock()) return false;

        BlockState innerState = fbe.getInnerDefaultState();

        // Hardcoded immovable blocks (mirrors vanilla isPushable)
        if (innerState.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)) return true;
        if (innerState.is(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN)) return true;
        if (innerState.is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE)) return true;

        // Indestructible blocks (bedrock, etc.)
        if (innerState.getDestroySpeed(this.level, pos) == -1.0f) return true;

        return false;
    }
}
