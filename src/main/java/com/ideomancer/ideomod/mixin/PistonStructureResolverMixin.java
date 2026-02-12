package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.block.StickinessRules;
import com.ideomancer.ideomod.config.OddstoneConfig;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin into PistonStructureResolver for two Ideomod features:
 * 
 * FEATURE 1: ONE-WAY STICKINESS
 * ==============================
 * Selectively blocks sticky grabs based on rules in StickinessRules.
 * Example: slime tries to grab Oddstone -> BLOCKED.
 *          Oddstone tries to grab slime -> ALLOWED.
 * 
 * FEATURE 2: ODDSTONE SYMPATHETIC MOVEMENT
 * =========================================
 * Extends the resolver's sticky neighbor search for Oddstone blocks.
 * Normally the resolver only checks the 6 directly adjacent positions.
 * For Oddstone, we also search along each face up to a configurable
 * radius, finding distant Oddstones and feeding them into addBlockLine
 * as if they were touching.
 * 
 * This means distant Oddstones go through the FULL resolver pipeline:
 * - They get added to toPush like any other block
 * - Their own sticky neighbors get checked (including slime!)
 * - They participate in push chains
 * - They count toward the 12-block limit
 * - Movement ordering is handled correctly
 * 
 * This replaces OddstoneMovementHandler entirely. One system, not two.
 * 
 * HOW IT WORKS:
 * =============
 * We use a ThreadLocal flag to track when we're inside addBranchingBlocks
 * (the sticky neighbor expansion phase). During this phase:
 * 
 * 1. ONE-WAY STICKINESS: When addBlockLine is called, we check if the
 *    grab should be blocked by our rules. If so, return true (handled).
 * 
 * 2. SYMPATHETIC SEARCH: At the TAIL of addBranchingBlocks, we scan
 *    the toPush list for Oddstones and do face-search for distant ones.
 *    Any found get fed into addBlockLine. If new blocks were added,
 *    we return true to tell the resolver to re-run branching (so the
 *    newly added Oddstones get their own sticky neighbor check).
 */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {

    // ====================================================================
    // SHADOWED FIELDS AND METHODS FROM PistonStructureResolver
    // ====================================================================

    @Shadow @Final private Level level;
    @Shadow @Final private List<BlockPos> toPush;

    /** The resolver's own method for adding a block and its push line. */
    @Shadow protected abstract boolean addBlockLine(BlockPos pos, Direction dir);

    /**
     * ThreadLocal flag: true when we're inside addBranchingBlocks.
     * Used to distinguish sticky branching from line-building in
     * the addBlockLine injection, so we only block grabs during
     * the sticky expansion phase.
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
    // FEATURE 1: ONE-WAY STICKINESS
    // ====================================================================

    /**
     * At HEAD of addBlockLine: check if this grab should be blocked
     * by our one-way stickiness rules. Only active during branching.
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

        if (grabberState.isStickyBlock() && StickinessRules.isGrabBlocked(grabberState, neighborState)) {
            // Block this grab. Return TRUE = "resolved OK, nothing to add"
            cir.setReturnValue(true);
        }
    }

    // ====================================================================
    // FEATURE 2: ODDSTONE SYMPATHETIC MOVEMENT
    // ====================================================================

    /**
     * At TAIL of addBranchingBlocks: after vanilla/Forge has finished
     * checking all sticky neighbors at distance 1, we extend the search
     * for any Oddstone blocks in toPush. For each one, we scan along
     * each face for distant Oddstones and call addBlockLine to add them.
     * 
     * If we added any new blocks, we override the return value to true,
     * which tells the resolver's loop in resolve() to re-run branching.
     * This ensures newly added Oddstones get THEIR sticky neighbors
     * checked too (including more distant Oddstones — the chain propagates).
     */
    @Inject(method = "addBranchingBlocks", at = @At("TAIL"), cancellable = true)
    private void ideomod$sympatheticSearch(CallbackInfoReturnable<Boolean> cir) {
        int radius = OddstoneConfig.getSympatheticRadius();
        if (radius <= 1) return; // No extended search needed

        // Snapshot the current push list — we'll be adding to it via
        // addBlockLine, so we iterate a copy to avoid concurrent modification
        List<BlockPos> snapshot = new ArrayList<>(this.toPush);
        boolean addedAny = false;

        for (BlockPos oddPos : snapshot) {
            BlockState state = this.level.getBlockState(oddPos);
            if (!state.is(ModBlocks.ODDSTONE.get())) continue;

            // Search each face for distant Oddstones (start at distance 2;
            // distance 1 is already handled by vanilla stickiness)
            for (Direction face : Direction.values()) {
                for (int dist = 2; dist <= radius; dist++) {
                    BlockPos checkPos = oddPos.relative(face, dist);
                    BlockState checkState = this.level.getBlockState(checkPos);

                    if (checkState.is(ModBlocks.ODDSTONE.get())) {
                        // Found a distant Oddstone on this face!
                        if (!this.toPush.contains(checkPos)) {
                            // Feed it into the resolver as if it were adjacent.
                            // addBlockLine handles pushability checks, the
                            // 12-block limit, and adding the whole push line.
                            boolean result = this.addBlockLine(checkPos, face);
                            if (this.toPush.contains(checkPos)) {
                                addedAny = true;
                            }
                            // If addBlockLine returned false, the push is
                            // impossible (e.g. hit 12-block limit). The resolver
                            // will handle this — we don't need to do anything.
                        }
                        break; // Only grab the nearest Oddstone per face
                    }

                    // Sympathetic link passes through ALL blocks (wireless).
                    // Don't break on non-air — keep scanning.
                }
            }
        }

        // If we added new blocks, tell the resolver to re-run branching.
        // This ensures the newly added Oddstones get their own sticky
        // neighbor expansion (which may find more distant Oddstones,
        // more slime blocks, etc.)
        if (addedAny) {
            cir.setReturnValue(true);
        }
    }
}
