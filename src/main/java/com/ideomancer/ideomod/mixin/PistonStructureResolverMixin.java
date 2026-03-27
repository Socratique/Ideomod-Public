package com.ideomancer.ideomod.mixin;

import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.frame.FrameMaterial;
import com.ideomancer.ideomod.block.frame.IFrameBlock;
import com.ideomancer.ideomod.block.machine.MachineBraceBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;
import com.ideomancer.ideomod.block.piston.RangedPistonFlags;
import com.ideomancer.ideomod.block.movement.MovementScanHelper;
import com.ideomancer.ideomod.block.movement.StickinessRules;
import com.ideomancer.ideomod.config.IdeomodConfig;
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
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin into PistonStructureResolver for Ideomod features.
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
        // Flush any pending frame activation changes BEFORE the resolver
        // reads any blockstate. Without this, frames activated by markDirty()
        // during the current tick would still show ACTIVE=false during the
        // resolver's addBlockLine and addBranchingBlocks phases, causing
        // vanilla to skip them for stickiness checks. The second push would
        // work because end-of-tick processing catches up. (Bug fix)
        com.ideomancer.ideomod.block.frame.FrameActivation.flushIfDirty(this.level);

        BlockPos target = RangedPistonFlags.getTarget();
        if (target != null) {
            this.startPos = target;
        }
    }

    // ====================================================================
    // FEATURE 8: MACHINE BRACE DYNAMIC PUSH LIMIT
    // ====================================================================

    /**
     * Replaces the hardcoded push limit constant (12) in addBlockLine() with
     * a dynamic value based on how many Machine Brace blocks are already in
     * the toPush list.
     *
     * The cascading behavior is automatic: as the resolver gathers blocks and
     * encounters Braces, the limit increases, allowing it to gather more blocks
     * which may include more Braces. Each Brace adds +6 to the limit, up to
     * a hard cap of 60 (8 Braces). A Brace only counts if it was discovered
     * within the limit that existed when it was found -- this is inherent in
     * how the resolver works, since blocks beyond the current limit are never
     * reached.
     *
     * The constant 12 appears in addBlockLine in two comparison points:
     * - Forward walk: if (this.toPush.size() >= 12) return false;
     * - Backward walk: the same size check
     * @ModifyConstant targets ALL occurrences of the int constant 12 in the
     * method. The replacement reads the current toPush list to count Braces.
     */
    @ModifyConstant(method = "addBlockLine", constant = @Constant(intValue = 12))
    private int ideomod$dynamicPushLimit(int originalLimit) {
        return ideomod$calculatePushLimit();
    }

    /**
     * Calculates the current push limit based on Machine Braces in toPush.
     * Base limit is 12. Each Brace adds 6. Cap at 60 (8 Braces max).
     *
     * This method is called every time vanilla checks the push limit during
     * addBlockLine. It's lightweight: just iterates toPush (which is small,
     * max 60 entries) and counts MachineBraceBlock instances.
     */
    @Unique
    private int ideomod$calculatePushLimit() {
        int braceCount = 0;
        for (BlockPos pos : this.toPush) {
            if (this.level.getBlockState(pos).getBlock() instanceof MachineBraceBlock) {
                braceCount++;
                if (braceCount >= MachineBraceBlock.getMaxBraces()) break;
            }
        }

        int limit = IdeomodConfig.getBasePushLimit() + (braceCount * MachineBraceBlock.getPushLimitPerBrace());
        return Math.min(limit, MachineBraceBlock.getMaxPushLimit());
    }

    // ====================================================================
    // PHASE DETECTION
    // ====================================================================

    @Inject(method = "addBranchingBlocks", at = @At("HEAD"))
    private void ideomod$enterBranching(CallbackInfoReturnable<Boolean> cir) {
        // Defensive reset: if flag is already true from a previous failed run
        // (exception thrown between HEAD and TAIL), reset it first.
        if (ideomod$inBranching.get()) {
            com.ideomancer.ideomod.Ideomod.LOGGER.warn(
                "[Ideomod] inBranching was already true at HEAD — defensive reset from previous failed run");
        }
        ideomod$inBranching.set(true);
        // Defensive reset for call-pair toggle too
        ideomod$firstCanStickToCall.set(true);
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
            boolean isFramedImmovable = !targetState.isAir() && ideomod$isFramedBlockWithImmovableInner(targetState, pos);
            if (isFramedImmovable) {
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
            // Standard directional check (covers real Odd Slime via registered rule)
            if (StickinessRules.isDirectionalGrabBlocked(grabberState, this.pushDirection)) {
                if (!ideomod$wouldFrameStickinessAllow(grabberState, neighborState)) {
                    cir.setReturnValue(true);
                    return;
                }
            }

            // Framed Odd Slime directional check (not registered as a rule)
            if (ideomod$isFramedOddSlimeDirectionallyBlocked(grabberState)) {
                if (!ideomod$wouldFrameStickinessAllow(grabberState, neighborState)) {
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

    @Redirect(method = "addBlockLine",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean ideomod$checkCanStickToWithRules(BlockState self, BlockState other) {
        boolean isFirstCall = ideomod$firstCanStickToCall.get();
        ideomod$firstCanStickToCall.set(!isFirstCall);

        // First: respect the original canStickTo result
        if (!self.canStickTo(other)) {
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
        if (isFirstCall) {
            if (StickinessRules.isGrabBlocked(self, other)) {
                ideomod$firstCanStickToCall.set(true);
                return false;
            }
        }

        // Reset toggle
        if (!isFirstCall) {
            ideomod$firstCanStickToCall.set(true);
        }

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

        // Get this framed block's inner conductor material (if any)
        FrameMaterial innerMat = FrameMaterial.fromConductorValue(
            grabberState.getValue(FramedBlock.INNER_CONDUCTOR));

        if (neighborState.getBlock() instanceof IFrameBlock otherFrame) {
            // Same frame material — always
            if (otherFrame.getFrameMaterial() == framedBlock.getFrameMaterial()) return true;
            // Cross-frame mediation: inner conductor matches other's frame material
            if (innerMat != null && innerMat == otherFrame.getFrameMaterial()) return true;
            // Reverse mediation: other framed block's inner conductor matches our frame material
            if (neighborState.getBlock() instanceof FramedBlock) {
                FrameMaterial otherInnerMat = FrameMaterial.fromConductorValue(
                    neighborState.getValue(FramedBlock.INNER_CONDUCTOR));
                if (otherInnerMat != null && otherInnerMat == framedBlock.getFrameMaterial()) return true;
            }
            return false;
        }

        if (MachineFrameBlock.isMachineFamily(neighborState.getBlock())) return true;

        // Vanilla metal block: same-material conductor OR inner conductor match
        FrameMaterial otherVanillaMat = FrameMaterial.fromVanillaBlock(neighborState.getBlock());
        if (otherVanillaMat != null) {
            if (otherVanillaMat == framedBlock.getFrameMaterial()) return true;
            if (innerMat != null && innerMat == otherVanillaMat) return true;
        }

        return false;
    }

    // ====================================================================
    // FEATURE 3: ODDSTONE SYMPATHETIC MOVEMENT
    // ====================================================================

    @Inject(method = "addBranchingBlocks", at = @At("TAIL"), cancellable = true)
    private void ideomod$sympatheticSearch(CallbackInfoReturnable<Boolean> cir) {
        // NOTE: flushIfDirty is called at resolve() HEAD, not here.
        // It must run BEFORE vanilla's addBranchingBlocks checks isStickyBlock()
        // and canStickTo(), not after. See ideomod$redirectStartPos.

        // --- Feature 6: Vanilla metal block frame family stickiness ---
        // Vanilla iron/copper blocks have isStickyBlock()=false, so the resolver
        // never checks their neighbors for sticking. We manually scan them here:
        // for each block already in toPush, check if any neighbor is a vanilla
        // metal conductor that should stick. If so, add it via addBlockLine.
        boolean addedMetals = ideomod$addVanillaMetalConductors();

        // --- Feature 3: Sympathetic movement (channel-aware) ---
        // Each sympathetic channel (oddstone, cobbled_oddstone, oddstone_bricks,
        // oddstone_wall, etc.) is scanned independently. Blocks only link with
        // others on the same channel.
        int radius = IdeomodConfig.getSympatheticRadius();

        java.util.Map<String, java.util.Set<BlockPos>> channelResults =
                MovementScanHelper.scanAllChannels(level, new java.util.HashSet<>(this.toPush), radius);

        boolean addedAny = false;

        for (java.util.Map.Entry<String, java.util.Set<BlockPos>> entry : channelResults.entrySet()) {
            String channel = entry.getKey();
            java.util.Set<BlockPos> channelFound = entry.getValue();

            for (BlockPos checkPos : channelFound) {
                if (!this.toPush.contains(checkPos)) {
                    // Find a source block on the same channel in toPush to derive direction
                    List<BlockPos> snapshot = new ArrayList<>(this.toPush);
                    boolean foundSource = false;
                    for (BlockPos sourcePos : snapshot) {
                        String sourceChannel = MovementScanHelper.getSympatheticChannel(level, sourcePos);
                        if (!channel.equals(sourceChannel)) continue;

                        // Derive the axis-aligned direction from sourcePos to checkPos.
                        // Sympathetic links are always axis-aligned (the scan walks
                        // along cardinal directions), so exactly one axis will differ.
                        Direction linkDir = ideomod$getAxisDirection(sourcePos, checkPos);
                        if (linkDir != null) {
                            boolean result = this.addBlockLine(checkPos, linkDir);
                            if (this.toPush.contains(checkPos)) {
                                addedAny = true;
                                foundSource = true;
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (addedAny || addedMetals) {
            cir.setReturnValue(true);
        }

        // --- Feature 7: Immovable framed block anchor check ---
        // After all blocks have been gathered (branching + metals + sympathetic),
        // check if any block in toPush is adjacent to a framed block with an
        // immovable inner (obsidian, crying obsidian, reinforced deepslate, etc.)
        // that would be frame-sticky with it. If so, that immovable block anchors
        // the entire group — the move must fail.
        //
        // This catches the case that vanilla's backward walk misses: when an
        // immovable block is adjacent to a group member, vanilla's isPushable
        // check silently stops the walk (returns true = "fine, stop here").
        // But for frame-sticky immovable blocks, we need to treat them as
        // anchors that prevent the entire group from moving.
        if (ideomod$hasImmovableFrameAnchor()) {
            cir.setReturnValue(false);
        }
    }

    // ====================================================================
    // SYMPATHETIC LINK DIRECTION HELPER
    // ====================================================================

    /**
     * Derives the cardinal Direction from 'from' to 'to', IF the two positions
     * are axis-aligned (differ on exactly one axis). Returns null if not
     * axis-aligned. Used by sympathetic movement to determine which direction
     * to pass to addBlockLine when adding a found oddstone.
     */
    @Unique
    private static Direction ideomod$getAxisDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();

        // Exactly one axis must be nonzero for axis-aligned link
        int nonZero = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
        if (nonZero != 1) return null;

        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        if (dy > 0) return Direction.UP;
        if (dy < 0) return Direction.DOWN;
        if (dz > 0) return Direction.SOUTH;
        return Direction.NORTH;
    }

    // ====================================================================
    // FEATURE 6: VANILLA METAL CONDUCTOR STICKINESS
    // ====================================================================

    /**
     * Scans blocks in toPush for vanilla metal conductor connections.
     * Works BIDIRECTIONALLY using a BFS frontier queue:
     *
     * 1. Finds vanilla iron/copper blocks adjacent to frame family members
     *    already in toPush, and adds them.
     * 2. Finds frame family members (active frames, machine blocks) adjacent
     *    to vanilla metals already in toPush, and adds them.
     *
     * This handles chains like: frame → iron_block → iron_block → frame
     * where the vanilla blocks bridge between frame family members.
     *
     * Uses a frontier queue instead of snapshot-and-rescan: when a new block
     * is added, only its neighbors are checked next, giving O(n) instead of
     * O(n²) for chains of n vanilla metals. (Audit item #15)
     *
     * @return true if any blocks were added to toPush
     */
    @Unique
    private boolean ideomod$addVanillaMetalConductors() {
        boolean addedAny = false;

        // Seed the frontier with all current toPush positions
        java.util.Queue<BlockPos> frontier = new java.util.ArrayDeque<>(this.toPush);
        java.util.Set<Long> processed = new java.util.HashSet<>();
        // Safety cap to prevent runaway iteration
        int maxIterations = 256;
        int iterations = 0;

        while (!frontier.isEmpty() && iterations < maxIterations) {
            iterations++;
            BlockPos pushedPos = frontier.poll();
            long packedPos = pushedPos.asLong();

            // Skip if already processed from the frontier
            if (!processed.add(packedPos)) continue;

            BlockState pushedState = this.level.getBlockState(pushedPos);

            // --- Direction A: pushed block is a frame family member,
            //     find adjacent vanilla metals to pull in ---
            if (ideomod$isVanillaMetalStickyNeighbor(pushedState)) {
                for (Direction dir : Direction.values()) {
                    BlockPos neighborPos = pushedPos.relative(dir);
                    if (this.toPush.contains(neighborPos)) continue;

                    BlockState neighborState = this.level.getBlockState(neighborPos);
                    FrameMaterial neighborMat = FrameMaterial.fromVanillaBlock(neighborState.getBlock());
                    if (neighborMat == null) continue;

                    if (ideomod$shouldVanillaMetalStick(neighborMat, pushedState, neighborPos)) {
                        this.addBlockLine(neighborPos, dir);
                        if (this.toPush.contains(neighborPos)) {
                            addedAny = true;
                            // Newly added — check ITS neighbors next
                            frontier.add(neighborPos);
                        }
                    }
                }
            }

            // --- Direction B: pushed block is a vanilla metal,
            //     find adjacent frame family members to pull in ---
            FrameMaterial pushedMetal = FrameMaterial.fromVanillaBlock(pushedState.getBlock());
            if (pushedMetal != null) {
                for (Direction dir : Direction.values()) {
                    BlockPos neighborPos = pushedPos.relative(dir);
                    if (this.toPush.contains(neighborPos)) continue;

                    BlockState neighborState = this.level.getBlockState(neighborPos);

                    if (ideomod$shouldVanillaMetalGrabNeighbor(pushedMetal, neighborState, pushedPos)) {
                        this.addBlockLine(neighborPos, dir);
                        if (this.toPush.contains(neighborPos)) {
                            addedAny = true;
                            // Newly added — check ITS neighbors next
                            frontier.add(neighborPos);
                        }
                    }
                }
            }
        }

        return addedAny;
    }

    /**
     * Checks if a block in toPush is the type that vanilla metal blocks
     * would want to stick to (active frame family, machine family, or
     * another vanilla metal of the same material, or a framed block with
     * matching inner conductor).
     */
    @Unique
    private boolean ideomod$isVanillaMetalStickyNeighbor(BlockState state) {
        if (state.getBlock() instanceof IFrameBlock && IFrameBlock.isActive(state)) return true;
        if (MachineFrameBlock.isMachineFamily(state.getBlock())) return true;
        if (FrameMaterial.fromVanillaBlock(state.getBlock()) != null) return true;
        return false;
    }

    /**
     * Checks if a vanilla metal block of the given material should stick
     * to the given neighbor block (used when scanning for vanilla metals
     * to ADD to toPush). Handles cross-frame mediation via INNER_CONDUCTOR.
     * Only sticks when the vanilla metal is activated.
     */
    @Unique
    private boolean ideomod$shouldVanillaMetalStick(FrameMaterial metalMat, BlockState neighborState, BlockPos metalPos) {
        // Vanilla metal must be activated to form sticky connections
        if (!com.ideomancer.ideomod.block.frame.FrameActivation.isVanillaMetalActivated(metalPos)) return false;

        if (neighborState.getBlock() instanceof IFrameBlock otherFrame) {
            if (!IFrameBlock.isActive(neighborState)) return false;
            if (otherFrame.getFrameMaterial() == metalMat) return true;
            if (neighborState.getBlock() instanceof FramedBlock) {
                FrameMaterial innerMat = FrameMaterial.fromConductorValue(
                    neighborState.getValue(FramedBlock.INNER_CONDUCTOR));
                if (innerMat == metalMat) return true;
            }
            return false;
        }
        if (MachineFrameBlock.isMachineFamily(neighborState.getBlock())) return true;
        FrameMaterial neighborMat = FrameMaterial.fromVanillaBlock(neighborState.getBlock());
        if (neighborMat == metalMat) {
            // Other vanilla metal must also be activated
            // (can't check position here — we'd need neighborPos)
            // But if it's same material and WE're activated, it should be too
            // since activation propagates through same-material metals
            return true;
        }
        return false;
    }

    /**
     * Checks if a vanilla metal block should grab a neighboring block.
     * Only grabs when the vanilla metal is activated.
     *
     * A vanilla iron block grabs:
     * - Active iron frames and iron framed blocks (same material)
     * - Active framed blocks with INNER_CONDUCTOR=IRON (cross-frame mediation)
     * - Machine Family blocks
     * - Other activated vanilla iron blocks
     */
    @Unique
    private boolean ideomod$shouldVanillaMetalGrabNeighbor(FrameMaterial metalMat, BlockState neighborState, BlockPos metalPos) {
        // Vanilla metal must be activated to grab
        if (!com.ideomancer.ideomod.block.frame.FrameActivation.isVanillaMetalActivated(metalPos)) return false;

        // Same-material active IFrameBlock
        if (neighborState.getBlock() instanceof IFrameBlock otherFrame) {
            if (!IFrameBlock.isActive(neighborState)) return false;
            if (otherFrame.getFrameMaterial() == metalMat) return true;
            // Cross-frame mediation: framed block whose inner conductor matches
            if (neighborState.getBlock() instanceof FramedBlock) {
                FrameMaterial innerMat = FrameMaterial.fromConductorValue(
                    neighborState.getValue(FramedBlock.INNER_CONDUCTOR));
                if (innerMat == metalMat) return true;
            }
            return false;
        }
        // Machine Family
        if (MachineFrameBlock.isMachineFamily(neighborState.getBlock())) return true;
        // Same-material vanilla metal
        FrameMaterial neighborMat = FrameMaterial.fromVanillaBlock(neighborState.getBlock());
        return neighborMat == metalMat;
    }

    // ====================================================================
    // FEATURE 7: IMMOVABLE FRAME ANCHOR DETECTION
    // ====================================================================

    /**
     * Checks if any block in toPush is adjacent to a framed block with an
     * immovable inner that is frame-sticky with it. If so, the immovable
     * block acts as an anchor and the entire move must fail.
     *
     * Only checks blocks NOT already in toPush (blocks in toPush were already
     * handled by Feature 5's addBlockLine HEAD check).
     */
    @Unique
    private boolean ideomod$hasImmovableFrameAnchor() {
        for (BlockPos pushedPos : this.toPush) {
            BlockState pushedState = this.level.getBlockState(pushedPos);

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pushedPos.relative(dir);
                if (this.toPush.contains(neighborPos)) continue;

                BlockState neighborState = this.level.getBlockState(neighborPos);
                if (!ideomod$isFramedBlockWithImmovableInner(neighborState, neighborPos)) continue;

                // Immovable framed block must be active to form a frame stickiness
                // connection. Inactive framed obsidian is just inert — not connected
                // to the group, so it doesn't anchor anything.
                if (neighborState.getBlock() instanceof FramedBlock
                    && !neighborState.getValue(FramedBlock.ACTIVE)) continue;

                // Check frame stickiness in both directions
                if (ideomod$wouldFrameStickinessAllow(pushedState, neighborState)
                    || ideomod$wouldFrameStickinessAllow(neighborState, pushedState)) {
                    return true;
                }

                // Also check if the pushed block is a Machine Family member
                // adjacent to an active immovable framed block (machine blocks
                // stick to all active frame family members)
                if (MachineFrameBlock.isMachineFamily(pushedState.getBlock())
                    && neighborState.getBlock() instanceof IFrameBlock
                    && IFrameBlock.isActive(neighborState)) {
                    return true;
                }
            }
        }
        return false;
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
