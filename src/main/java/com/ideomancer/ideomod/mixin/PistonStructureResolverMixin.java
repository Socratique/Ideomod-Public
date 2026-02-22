package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.block.FramedBlock;
import com.ideomancer.ideomod.block.FramedBlockEntity;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.RangedPistonFlags;
import com.ideomancer.ideomod.block.StickinessRules;
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
 *      Checks both grab immunity AND directional rules.
 * 
 *   B) @Redirect on canStickTo (inside addBlockLine) -- CHAIN COHESION
 *      Checks grab immunity AND directional rules.
 *      EXCEPTION: If the target (front block) is an active OddSlimeBlock,
 *      grab immunity is skipped -- the Odd Slime is pulling the behind
 *      block along in its chain, not the other way around.
 *      See ideomod$checkCanStickToWithRules javadoc for details.
 * 
 * FEATURE 2: DIRECTIONAL STICKINESS (Odd Slime)
 * ==============================================
 * Blocks whose stickiness depends on push direction.
 * Checked at both injection points.
 * 
 * FEATURE 3: ODDSTONE SYMPATHETIC MOVEMENT
 * =========================================
 * Extended range sticky search for Oddstone blocks AND Framed Blocks
 * whose inner block is Oddstone. "See through" frames for sympathetic.
 * 
 * FEATURE 4: RANGED PISTON START POSITION REDIRECT
 * =================================================
 * When a ranged piston (Repulser/Attractor) creates the resolver, it
 * sets pistonPos to the device's own position (safe, far away). But the
 * resolver calculates startPos = pistonPos + pushDirection, which would
 * point to the block next to the device -- not the actual target.
 * 
 * This mixin checks RangedPistonFlags at the HEAD of resolve(). If a
 * ranged target is set, it overrides startPos to point at the real
 * target block. The resolver then walks the line from the correct
 * position, and pistonPos being far away means it can't collide with
 * any blocks in the move group (fixing the sympathetic boundary issue).
 * 
 * HISTORY / KNOWN PITFALLS:
 * =========================
 * - The @Redirect on canStickTo was added specifically to fix slime
 *   pulling Oddstone in the backward walk (Stage 5 bug fix). Removing
 *   or weakening grab immunity in the @Redirect WILL re-break this.
 * - Any exemption to grab immunity in the backward walk must be
 *   NARROWLY targeted. Do NOT exempt based on generic "is sticky" or
 *   "has no directional rules" checks -- that matches Oddstone, slime,
 *   honey, etc. and re-opens the Stage 5 bug.
 * - The current exemption specifically checks for OddSlimeBlock with
 *   FACING matching pushDirection. Only this block type gets the pass.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {

    // ====================================================================
    // SHADOWED FIELDS AND METHODS FROM PistonStructureResolver
    // ====================================================================

    @Shadow @Final private Level level;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private Direction pushDirection;

    /**
     * startPos is the resolver's first block to check, normally set in the
     * constructor as pistonPos.relative(pushDirection). We shadow it with
     * @Mutable so we can override it for ranged piston operations.
     */
    @Shadow @Final @Mutable private BlockPos startPos;

    /** The resolver's own method for adding a block and its push line. */
    @Shadow protected abstract boolean addBlockLine(BlockPos pos, Direction dir);

    /**
     * ThreadLocal flag: true when we're inside addBranchingBlocks.
     * Used to distinguish sticky branching (active grabbing) from
     * line-building (passive chain cohesion).
     */
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
        if (!ideomod$inBranching.get()) return;

        BlockPos grabberPos = pos.relative(dir.getOpposite());
        if (!this.toPush.contains(grabberPos)) return;

        BlockState grabberState = this.level.getBlockState(grabberPos);
        BlockState neighborState = this.level.getBlockState(pos);

        if (neighborState.isAir()) return;

        if (grabberState.isStickyBlock()) {
            if (StickinessRules.isDirectionalGrabBlocked(grabberState, this.pushDirection)) {
                cir.setReturnValue(true);
                return;
            }

            if (StickinessRules.isGrabBlocked(grabberState, neighborState)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    // ====================================================================
    // FEATURE 1B + 2B: ONE-WAY & DIRECTIONAL STICKINESS -- BACKWARD WALK
    // ====================================================================

    @Redirect(method = "addBlockLine",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean ideomod$checkCanStickToWithRules(BlockState self, BlockState other) {
        if (!self.canStickTo(other)) {
            return false;
        }

        if (StickinessRules.isDirectionalGrabBlocked(self, this.pushDirection)) {
            return false;
        }

        if (StickinessRules.isDirectionalGrabBlocked(other, this.pushDirection)) {
            return false;
        }

        boolean targetIsActivePuller = false;
        if (other.getBlock() instanceof OddSlimeBlock) {
            Direction facing = other.getValue(DirectionalBlock.FACING);
            targetIsActivePuller = this.pushDirection.equals(facing);
        }

        if (!targetIsActivePuller) {
            if (StickinessRules.isGrabBlocked(self, other)) {
                return false;
            }
        }

        return true;
    }

    // ====================================================================
    // FEATURE 3: ODDSTONE SYMPATHETIC MOVEMENT
    // ====================================================================

    /**
     * At TAIL of addBranchingBlocks: after vanilla/Forge has finished
     * checking all sticky neighbors at distance 1, we extend the search
     * for Oddstone blocks and Framed Blocks wrapping Oddstone. For each
     * one found in toPush, we scan along each face for distant Oddstones
     * (real or framed) and call addBlockLine to add them.
     *
     * "See through" logic: a FramedBlock whose inner block is OddstoneBlock
     * participates in sympathetic movement just like real Oddstone.
     */
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
                        break; // Found nearest oddstone along this axis
                    }
                }
            }
        }

        if (addedAny) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Check if a position contains Oddstone or a Framed Block wrapping Oddstone.
     * This is the "see through frames" logic for sympathetic movement.
     */
    @Unique
    private boolean ideomod$isOddstoneOrFramedOddstone(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);

        // Direct Oddstone check
        if (state.is(ModBlocks.ODDSTONE.get())) return true;

        // Framed Oddstone check: is it a FramedBlock whose inner block is Oddstone?
        if (state.getBlock() instanceof FramedBlock) {
            BlockEntity be = this.level.getBlockEntity(pos);
            if (be instanceof FramedBlockEntity fbe && fbe.hasInnerBlock()) {
                return fbe.getInnerBlock() instanceof OddstoneBlock;
            }
        }

        return false;
    }
}
