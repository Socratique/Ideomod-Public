package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.frame.IFrameBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.piston.RangedPistonFlags;
import com.ideomancer.ideomod.block.movement.StickinessRules;
import com.ideomancer.ideomod.config.OddstoneConfig;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
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
 *      second-chance logic.
 *
 *   B) @Redirect on canStickTo (inside addBlockLine) -- CHAIN COHESION
 *      Checks grab immunity AND directional rules.
 *      EXCEPTION: Active pullers (OddSlimeBlock or Framed Odd Slime
 *      with FACING matching pushDirection) skip grab immunity for the
 *      behind block — they're pulling it along.
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
 * - The @Redirect on canStickTo was added to fix slime pulling Oddstone
 *   in the backward walk. Weakening grab immunity there WILL re-break this.
 * - Any exemption in the backward walk must be NARROWLY targeted.
 * - Active puller exemption only matches OddSlimeBlock or FramedBlock
 *   with INNER_STICKY=ODD_SLIME + FACING matching pushDirection.
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
    // FEATURE 1B + 2B: ONE-WAY & DIRECTIONAL STICKINESS -- BACKWARD WALK
    // ====================================================================

    /**
     * Intercepts the canStickTo check in the backward walk of addBlockLine.
     *
     * "self" = the block behind (being considered as a chain member)
     * "other" = the block in front (already in the push list)
     *
     * Active puller exemption: when an Odd Slime (or Framed Odd Slime) is
     * at the front being pushed in its FACING direction, it pulls blocks
     * behind it — grab immunity is skipped for the behind block.
     */
    @Redirect(method = "addBlockLine",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean ideomod$checkCanStickToWithRules(BlockState self, BlockState other) {
        if (!self.canStickTo(other)) {
            return false;
        }

        // --- Directional checks on both sides ---

        // Check "self" (behind block) directional rules
        if (StickinessRules.isDirectionalGrabBlocked(self, this.pushDirection)) {
            if (!ideomod$wouldFrameStickinessAllow(self, other)) {
                return false;
            }
        }
        if (ideomod$isFramedOddSlimeDirectionallyBlocked(self)) {
            if (!ideomod$wouldFrameStickinessAllow(self, other)) {
                return false;
            }
        }

        // Check "other" (front block) directional rules
        if (StickinessRules.isDirectionalGrabBlocked(other, this.pushDirection)) {
            if (!ideomod$wouldFrameStickinessAllow(other, self)) {
                return false;
            }
        }
        if (ideomod$isFramedOddSlimeDirectionallyBlocked(other)) {
            if (!ideomod$wouldFrameStickinessAllow(other, self)) {
                return false;
            }
        }

        // --- Active puller exemption ---
        boolean targetIsActivePuller = ideomod$isActivePuller(other);

        if (!targetIsActivePuller) {
            if (StickinessRules.isGrabBlocked(self, other)) {
                return false;
            }
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

    /**
     * Check if a block is an active puller — an Odd Slime or Framed Odd Slime
     * being pushed in its FACING direction. Active pullers pull blocks behind
     * them in the chain, so grab immunity is skipped for those behind blocks.
     */
    @Unique
    private boolean ideomod$isActivePuller(BlockState state) {
        if (state.getBlock() instanceof OddSlimeBlock) {
            Direction facing = state.getValue(DirectionalBlock.FACING);
            return this.pushDirection.equals(facing);
        }
        if (state.getBlock() instanceof FramedBlock
                && state.getValue(FramedBlock.INNER_STICKY) == FramedBlock.STICKY_ODD_SLIME) {
            Direction facing = state.getValue(FramedBlock.FACING);
            return this.pushDirection.equals(facing);
        }
        return false;
    }

    // ====================================================================
    // FEATURE 3: ODDSTONE SYMPATHETIC MOVEMENT
    // ====================================================================

    @Inject(method = "addBranchingBlocks", at = @At("TAIL"), cancellable = true)
    private void ideomod$sympatheticSearch(CallbackInfoReturnable<Boolean> cir) {
        int radius = OddstoneConfig.getSympatheticRadius();
        if (radius <= 1) return;

        List<BlockPos> snapshot = new ArrayList<>(this.toPush);
        boolean addedAny = false;

        for (BlockPos oddPos : snapshot) {
            if (!ideomod$isOddstoneOrFramedOddstone(oddPos)) continue;

            for (Direction face : Direction.values()) {
                for (int dist = 2; dist <= radius; dist++) {
                    BlockPos checkPos = oddPos.relative(face, dist);

                    if (ideomod$isOddstoneOrFramedOddstone(checkPos)) {
                        if (!this.toPush.contains(checkPos)) {
                            boolean result = this.addBlockLine(checkPos, face);
                            if (this.toPush.contains(checkPos)) {
                                addedAny = true;
                            }
                        }
                        break;
                    }
                }
            }
        }

        if (addedAny) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean ideomod$isOddstoneOrFramedOddstone(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);

        if (state.is(ModBlocks.ODDSTONE.get())) return true;

        if (state.getBlock() instanceof FramedBlock) {
            BlockEntity be = this.level.getBlockEntity(pos);
            if (be instanceof FramedBlockEntity fbe && fbe.hasInnerBlock()) {
                return fbe.getInnerBlock() instanceof OddstoneBlock;
            }
        }

        return false;
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
