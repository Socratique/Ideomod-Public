package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.RangedPistonFlags;
import com.ideomancer.ideomod.block.StickinessRules;
import com.ideomancer.ideomod.config.OddstoneConfig;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
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
 * Extended range sticky search for Oddstone blocks.
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

    /**
     * At HEAD of resolve(): if a ranged piston operation is active,
     * override startPos to point at the real target block instead of
     * the calculated pistonPos + pushDirection.
     * 
     * This is the core fix for the sympathetic boundary issue. The
     * virtual piston sits at the device position (far away), so its
     * pistonPos can't collide with any blocks that should be in the
     * move group. The startPos redirect ensures the resolver still
     * starts walking from the correct target block.
     */
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

    /**
     * At HEAD of addBlockLine: check if this grab should be blocked.
     * Only active during branching (sideways neighbor expansion phase).
     * 
     * This is the "active grabbing" phase -- a sticky block reaches out
     * to its neighbors. Both grab immunity AND directional rules apply
     * with full force here.
     */
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
            // Check 1: Directional stickiness -- is the grabber disabled for this push?
            if (StickinessRules.isDirectionalGrabBlocked(grabberState, this.pushDirection)) {
                cir.setReturnValue(true);
                return;
            }

            // Check 2: Grab immunity -- does the target resist the grabber?
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
     * @Redirect on canStickTo inside addBlockLine.
     * 
     * Context: addBlockLine walks backward from the given position,
     * checking if each block sticks to the one in front of it. This is
     * the "chain cohesion" phase -- not active grabbing, but maintaining
     * connections within an existing line.
     * 
     * We intercept canStickTo here to apply BOTH grab immunity AND
     * directional stickiness rules to the backward walk.
     * 
     * SPECIAL CASE -- ODD SLIME AS TARGET:
     * When the target (block in front) is an OddSlimeBlock whose FACING
     * matches pushDirection, we SKIP grab immunity. Why? Because in this
     * case, the Odd Slime is the active puller -- it's carrying blocks
     * behind it in its chain. The block behind is being pulled along BY
     * the Odd Slime, not grabbing it. Without this exception, Odd Slime
     * couldn't pull anything because grab immunity would block the
     * backward-walk connection.
     * 
     * WARNING: This exception MUST remain narrowly scoped to OddSlimeBlock.
     * Generalizing it will re-break one-way stickiness (Stage 5 bug).
     * 
     * @param self     The BlockState calling canStickTo (the "behind" block)
     * @param other    The BlockState being stuck to (the "front" block / target)
     * @return true if they can stick, false if blocked by our rules
     */
    @Redirect(method = "addBlockLine",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean ideomod$checkCanStickToWithRules(BlockState self, BlockState other) {
        // Run the original canStickTo first
        if (!self.canStickTo(other)) {
            return false;
        }

        // Apply directional rules to the "behind" block (self).
        // If it shouldn't be sticky for this push direction, block the stick.
        if (StickinessRules.isDirectionalGrabBlocked(self, this.pushDirection)) {
            return false;
        }

        // Apply directional rules to the "front" block (other).
        if (StickinessRules.isDirectionalGrabBlocked(other, this.pushDirection)) {
            return false;
        }

        // Check grab immunity -- with narrow exception for active Odd Slime targets.
        //
        // If the target (front block) is an OddSlimeBlock that is active for
        // this push direction, it's the one doing the pulling. The grabber
        // (behind block) should come along regardless of grab immunity.
        //
        // IMPORTANT: This check is intentionally specific to OddSlimeBlock.
        // Do NOT generalize to "any sticky block" -- that re-opens the Stage 5
        // bug (slime pulling Oddstone in backward walk).
        boolean targetIsActivePuller = false;
        if (other.getBlock() instanceof OddSlimeBlock) {
            Direction facing = other.getValue(DirectionalBlock.FACING);
            targetIsActivePuller = this.pushDirection.equals(facing);
        }

        if (!targetIsActivePuller) {
            // Normal case: apply grab immunity
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
     * for any Oddstone blocks in toPush. For each one, we scan along
     * each face for distant Oddstones and call addBlockLine to add them.
     * 
     * If we added any new blocks, we override the return value to true,
     * which tells the resolver's loop in resolve() to re-run branching.
     */
    @Inject(method = "addBranchingBlocks", at = @At("TAIL"), cancellable = true)
    private void ideomod$sympatheticSearch(CallbackInfoReturnable<Boolean> cir) {
        int radius = OddstoneConfig.getSympatheticRadius();
        if (radius <= 1) return;

        List<BlockPos> snapshot = new ArrayList<>(this.toPush);
        boolean addedAny = false;

        for (BlockPos oddPos : snapshot) {
            BlockState state = this.level.getBlockState(oddPos);
            if (!state.is(ModBlocks.ODDSTONE.get())) continue;

            for (Direction face : Direction.values()) {
                for (int dist = 2; dist <= radius; dist++) {
                    BlockPos checkPos = oddPos.relative(face, dist);
                    BlockState checkState = this.level.getBlockState(checkPos);

                    if (checkState.is(ModBlocks.ODDSTONE.get())) {
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
}
