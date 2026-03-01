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
 * Mixin into PistonStructureResolver for Ideomod features.
 *
 * DEBUG: Temporary logging added for bug #10 investigation.
 * Search for "BUG10" to find all debug lines. Remove once resolved.
 *
 * See original file for full feature documentation.
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
        {
            BlockState targetState = this.level.getBlockState(pos);
            if (!targetState.isAir() && ideomod$isFramedBlockWithImmovableInner(targetState, pos)) {
                cir.setReturnValue(false);
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
            // BUG10: Log every branching grab attempt
            com.ideomancer.ideomod.Ideomod.LOGGER.info(
                "[BUG10 branching] Grab attempt: grabber={} at {}, target={} at {}, pushDir={}",
                grabberState, grabberPos, neighborState, pos, this.pushDirection);

            // Standard directional check (covers real Odd Slime via registered rule)
            if (StickinessRules.isDirectionalGrabBlocked(grabberState, this.pushDirection)) {
                if (ideomod$wouldFrameStickinessAllow(grabberState, neighborState)) {
                    com.ideomancer.ideomod.Ideomod.LOGGER.info(
                        "[BUG10 branching] Directional blocked but frame stickiness allows");
                } else {
                    com.ideomancer.ideomod.Ideomod.LOGGER.info(
                        "[BUG10 branching] BLOCKED by directional rule");
                    cir.setReturnValue(true);
                    return;
                }
            }

            // Framed Odd Slime directional check (not registered as a rule)
            if (ideomod$isFramedOddSlimeDirectionallyBlocked(grabberState)) {
                if (ideomod$wouldFrameStickinessAllow(grabberState, neighborState)) {
                    com.ideomancer.ideomod.Ideomod.LOGGER.info(
                        "[BUG10 branching] Framed odd slime directional blocked but frame stickiness allows");
                } else {
                    com.ideomancer.ideomod.Ideomod.LOGGER.info(
                        "[BUG10 branching] BLOCKED by framed odd slime directional rule");
                    cir.setReturnValue(true);
                    return;
                }
            }

            // Standard grab immunity check
            if (StickinessRules.isGrabBlocked(grabberState, neighborState)) {
                com.ideomancer.ideomod.Ideomod.LOGGER.info(
                    "[BUG10 branching] BLOCKED by grab immunity: grabber={}, target={}",
                    grabberState, neighborState);
                cir.setReturnValue(true);
                return;
            }

            com.ideomancer.ideomod.Ideomod.LOGGER.info(
                "[BUG10 branching] All checks PASSED — grab allowed");
        }
    }

    // ====================================================================
    // FEATURE 1B + 2B: STICKINESS RULES -- CHAIN COHESION (BACKWARD WALK)
    // ====================================================================

    @Redirect(method = "addBlockLine",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean ideomod$checkCanStickToWithRules(BlockState self, BlockState other) {
        boolean isFirstCall = ideomod$firstCanStickToCall.get();
        ideomod$firstCanStickToCall.set(!isFirstCall);

        // BUG10: Log every canStickTo redirect call
        com.ideomancer.ideomod.Ideomod.LOGGER.info(
            "[BUG10 backward] canStickTo redirect: self={}, other={}, isFirstCall={}",
            self, other, isFirstCall);

        // First: respect the original canStickTo result
        if (!self.canStickTo(other)) {
            com.ideomancer.ideomod.Ideomod.LOGGER.info(
                "[BUG10 backward] canStickTo returned FALSE: self={}, other={}", self, other);
            if (isFirstCall) {
                ideomod$firstCanStickToCall.set(true);
            }
            return false;
        }

        // --- Directional checks (apply on BOTH calls) ---
        if (StickinessRules.isDirectionalGrabBlocked(self, this.pushDirection)) {
            if (!ideomod$wouldFrameStickinessAllow(self, other)) {
                com.ideomancer.ideomod.Ideomod.LOGGER.info(
                    "[BUG10 backward] BLOCKED by directional (self): self={}", self);
                if (isFirstCall) ideomod$firstCanStickToCall.set(true);
                return false;
            }
        }
        if (ideomod$isFramedOddSlimeDirectionallyBlocked(self)) {
            if (!ideomod$wouldFrameStickinessAllow(self, other)) {
                com.ideomancer.ideomod.Ideomod.LOGGER.info(
                    "[BUG10 backward] BLOCKED by framed odd slime directional (self): self={}", self);
                if (isFirstCall) ideomod$firstCanStickToCall.set(true);
                return false;
            }
        }
        if (StickinessRules.isDirectionalGrabBlocked(other, this.pushDirection)) {
            if (!ideomod$wouldFrameStickinessAllow(other, self)) {
                com.ideomancer.ideomod.Ideomod.LOGGER.info(
                    "[BUG10 backward] BLOCKED by directional (other): other={}", other);
                if (isFirstCall) ideomod$firstCanStickToCall.set(true);
                return false;
            }
        }
        if (ideomod$isFramedOddSlimeDirectionallyBlocked(other)) {
            if (!ideomod$wouldFrameStickinessAllow(other, self)) {
                com.ideomancer.ideomod.Ideomod.LOGGER.info(
                    "[BUG10 backward] BLOCKED by framed odd slime directional (other): other={}", other);
                if (isFirstCall) ideomod$firstCanStickToCall.set(true);
                return false;
            }
        }

        // --- Grab immunity (FIRST CALL ONLY) ---
        if (isFirstCall) {
            if (StickinessRules.isGrabBlocked(self, other)) {
                com.ideomancer.ideomod.Ideomod.LOGGER.info(
                    "[BUG10 backward] BLOCKED by grab immunity (Call 1): self={}, other={}", self, other);
                ideomod$firstCanStickToCall.set(true);
                return false;
            }
        }

        // Reset toggle
        if (!isFirstCall) {
            ideomod$firstCanStickToCall.set(true);
        }

        com.ideomancer.ideomod.Ideomod.LOGGER.info(
            "[BUG10 backward] canStickTo redirect PASSED: self={}, other={}, isFirstCall={}", self, other, isFirstCall);
        return true;
    }

    // ====================================================================
    // FRAMED ODD SLIME HELPERS
    // ====================================================================

    @Unique
    private boolean ideomod$isFramedOddSlimeDirectionallyBlocked(BlockState state) {
        if (!(state.getBlock() instanceof FramedBlock)) return false;
        if (state.getValue(FramedBlock.INNER_STICKY) != FramedBlock.STICKY_ODD_SLIME) return false;
        Direction facing = state.getValue(FramedBlock.FACING);
        return !this.pushDirection.equals(facing);
    }

    @Unique
    private boolean ideomod$wouldFrameStickinessAllow(BlockState grabberState, BlockState neighborState) {
        if (!(grabberState.getBlock() instanceof FramedBlock framedBlock)) return false;
        if (!grabberState.getValue(FramedBlock.ACTIVE)) return false;

        if (neighborState.getBlock() instanceof IFrameBlock otherFrame) {
            return otherFrame.getFrameMaterial() == framedBlock.getFrameMaterial();
        }

        if (MachineFrameBlock.isMachineFamily(neighborState.getBlock())) return true;

        return false;
    }

    // ====================================================================
    // FEATURE 3: ODDSTONE SYMPATHETIC MOVEMENT
    // ====================================================================

    @Inject(method = "addBranchingBlocks", at = @At("TAIL"), cancellable = true)
    private void ideomod$sympatheticSearch(CallbackInfoReturnable<Boolean> cir) {
        int radius = OddstoneConfig.getSympatheticRadius();

        java.util.Set<BlockPos> allFound = MovementScanHelper.scanSympathetic(level, new java.util.HashSet<>(this.toPush), radius);

        boolean addedAny = false;

        for (BlockPos checkPos : allFound) {
            if (!this.toPush.contains(checkPos)) {
                List<BlockPos> snapshot = new ArrayList<>(this.toPush);
                for (BlockPos oddPos : snapshot) {
                    if (!MovementScanHelper.isOddstoneOrFramedOddstone(level, oddPos)) continue;

                    for (Direction face : Direction.values()) {
                        if (checkPos.equals(oddPos.relative(face, radius)) ||
                            checkPos.equals(oddPos.relative(face, 2))) {
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

    @Unique
    private boolean ideomod$isFramedBlockWithImmovableInner(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof FramedBlock)) return false;

        BlockEntity be = this.level.getBlockEntity(pos);
        if (!(be instanceof FramedBlockEntity fbe) || !fbe.hasInnerBlock()) return false;

        BlockState innerState = fbe.getInnerDefaultState();
        if (innerState.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)) return true;
        if (innerState.is(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN)) return true;
        if (innerState.is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE)) return true;
        if (innerState.getDestroySpeed(this.level, pos) == -1.0f) return true;

        return false;
    }
}
