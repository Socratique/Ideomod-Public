package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.block.OddSlimeBlock;
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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin into PistonStructureResolver for three Ideomod features:
 * 
 * FEATURE 1: ONE-WAY STICKINESS
 * ==============================
 * Selectively blocks sticky grabs based on rules in StickinessRules.
 * Example: slime tries to grab Oddstone -> BLOCKED.
 *          Oddstone tries to grab slime -> ALLOWED.
 * 
 * Enforced at two injection points:
 * 
 *   A) HEAD of addBlockLine (during branching only) — ACTIVE GRABBING
 *      Checks both grab immunity AND directional rules.
 * 
 *   B) @Redirect on canStickTo (inside addBlockLine) — CHAIN COHESION
 *      Checks grab immunity AND directional rules.
 *      EXCEPTION: If the target (front block) is an active OddSlimeBlock,
 *      grab immunity is skipped — the Odd Slime is pulling the behind
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
 * HISTORY / KNOWN PITFALLS:
 * =========================
 * - The @Redirect on canStickTo was added specifically to fix slime
 *   pulling Oddstone in the backward walk (Stage 5 bug fix). Removing
 *   or weakening grab immunity in the @Redirect WILL re-break this.
 * - Any exemption to grab immunity in the backward walk must be
 *   NARROWLY targeted. Do NOT exempt based on generic "is sticky" or
 *   "has no directional rules" checks — that matches Oddstone, slime,
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
    // FEATURE 1A + 2A: ONE-WAY & DIRECTIONAL STICKINESS — SIDEWAYS GRABS
    // ====================================================================

    /**
     * At HEAD of addBlockLine: check if this grab should be blocked.
     * Only active during branching (sideways neighbor expansion phase).
     * 
     * This is the "active grabbing" phase — a sticky block reaches out
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
            // Check 1: Directional stickiness — is the grabber disabled for this push?
            if (StickinessRules.isDirectionalGrabBlocked(grabberState, this.pushDirection)) {
                cir.setReturnValue(true);
                return;
            }

            // Check 2: One-way grab immunity — is this specific grab blocked?
            if (StickinessRules.isGrabBlocked(grabberState, neighborState)) {
                cir.setReturnValue(true);
            }
        }
    }

    // ====================================================================
    // FEATURE 1B + 2B: STICKINESS — BACKWARD WALK (chain cohesion)
    // ====================================================================

    /**
     * Redirects the canStickTo() call inside addBlockLine's backward walk.
     * 
     * The backward walk finds sticky blocks behind the current push line
     * and checks if they should join the group. The call is:
     *   behindBlockState.canStickTo(frontBlockState)
     * 
     * "grabberState" = block BEHIND (being considered for the chain)
     * "targetState"  = block IN FRONT (already in the line)
     * 
     * RULES APPLIED:
     * ==============
     * 1. Directional rules: ALWAYS. If a block isn't active for this push
     *    direction, it's not sticky — period.
     * 
     * 2. Grab immunity: ALWAYS, with ONE narrow exception:
     *    If the TARGET is an OddSlimeBlock whose FACING matches pushDirection
     *    (i.e. it's actively pulling), grab immunity is skipped.
     * 
     * WHY THE EXCEPTION:
     * ==================
     * When active Odd Slime is in front and Machine Block is behind it,
     * the backward walk calls MachineBlock.canStickTo(OddSlime). Our grab
     * immunity says Machine Block can't grab Odd Slime. But Machine Block
     * isn't grabbing — Odd Slime is pulling it along. Without the exception,
     * Machine Block gets incorrectly left behind.
     * 
     * WHY IT'S NARROWLY TARGETED:
     * ===========================
     * We specifically check for OddSlimeBlock + FACING match. We do NOT
     * use generic "is sticky and has no directional block" — that would
     * match Oddstone, slime, honey, and re-open the Stage 5 bug where
     * slime pulled Oddstone in the backward walk.
     */
    @Redirect(
        method = "addBlockLine",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z"
        )
    )
    private boolean ideomod$checkCanStickToWithRules(BlockState grabberState, BlockState targetState) {
        // First: run vanilla/Forge's original canStickTo check
        boolean vanillaResult = grabberState.canStickTo(targetState);
        if (!vanillaResult) {
            return false;
        }

        // Check directional rules for both blocks
        if (StickinessRules.isDirectionalGrabBlocked(grabberState, this.pushDirection)) {
            return false;
        }
        if (StickinessRules.isDirectionalGrabBlocked(targetState, this.pushDirection)) {
            return false;
        }

        // Check grab immunity — with narrow exception for active Odd Slime targets.
        //
        // If the target (front block) is an OddSlimeBlock that is active for
        // this push direction, it's the one doing the pulling. The grabber
        // (behind block) should come along regardless of grab immunity.
        //
        // IMPORTANT: This check is intentionally specific to OddSlimeBlock.
        // Do NOT generalize to "any sticky block" — that re-opens the Stage 5
        // bug (slime pulling Oddstone in backward walk).
        boolean targetIsActivePuller = false;
        if (targetState.getBlock() instanceof OddSlimeBlock) {
            Direction facing = targetState.getValue(DirectionalBlock.FACING);
            targetIsActivePuller = this.pushDirection.equals(facing);
        }

        if (!targetIsActivePuller) {
            // Normal case: apply grab immunity
            if (StickinessRules.isGrabBlocked(grabberState, targetState)) {
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
