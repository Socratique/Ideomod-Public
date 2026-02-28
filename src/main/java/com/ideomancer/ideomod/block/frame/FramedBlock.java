package com.ideomancer.ideomod.block.frame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;

/**
 * Framed Block — a block that wraps any valid "inner block" with a metal frame.
 *
 * Two instances exist: iron_framed_block and copper_framed_block.
 * Each behaves as a transparent proxy for its inner block (stored in the
 * block entity) while adding frame-family stickiness on top.
 *
 * BEHAVIORAL DELEGATION:
 * ======================
 * ~22 methods are overridden to delegate to the inner block's default state.
 * This means a Framed Redstone Block emits redstone signal, a Framed Slime
 * Block has slime friction/bounce, a Framed Oddstone repels mobs, etc.
 * The delegation is GENERIC — we ask the inner block instance directly,
 * no hardcoded per-block-type logic.
 *
 * Methods that receive a BlockPos can look up the block entity and delegate.
 * Methods that only receive a BlockState (no position) cannot delegate
 * per-position and use conservative defaults.
 *
 * ENTITY INTERACTION DELEGATION:
 * ==============================
 * stepOn, fallOn, updateEntityAfterFallOn, entityInside all delegate to
 * the inner block. These give framed blocks the physical feel of their
 * inner block — framed magma burns, framed slime bounces on fall, etc.
 *
 * Note: slime bounce requires BOTH fallOn (cancels damage) AND
 * updateEntityAfterFallOn (reverses Y velocity). Missing either one
 * gives incomplete behavior.
 *
 * getSpeedFactor and getJumpFactor are position-less Block methods that can't
 * delegate per-position. These are handled by EntityMovementMixin which
 * intercepts the Entity-side call where position IS available.
 *
 * getPistonPushReaction is also position-less. Framed obsidian immovability
 * is handled by PistonBaseBlockMixin's isPushable intercept where position
 * IS available. Note: vanilla uses hardcoded block identity checks (not
 * PushReaction.BLOCK) for obsidian immovability, so the mixin mirrors
 * those exact checks on the inner block.
 *
 * STICKINESS MODEL:
 * =================
 * isStickyBlock() returns true when ACTIVE (frame stickiness) OR when
 * INNER_STICKY > 0 (inner block is natively sticky).
 *
 * canStickTo() combines:
 *   - Frame family rules (same-material frames, machine blocks) — gated by ACTIVE
 *   - Inner block stickiness rules — gated by INNER_STICKY value
 *   - Permissive entries for vanilla stickies
 *
 * StickinessRules grab immunity prevents frames from actively grabbing
 * slime/honey/odd slime, BUT a carve-out in Ideomod.java exempts framed
 * blocks whose inner IS the same sticky type. E.g. a Framed Slime Block
 * IS allowed to grab normal blocks like real slime would.
 *
 * INNER_STICKY values:
 *   0 = not sticky (default)
 *   1 = slime-type (grabs everything except honey, odd slime, oddstone)
 *   2 = honey-type (grabs everything except slime, odd slime, oddstone)
 *   3 = odd-slime-type (grabs everything except slime, honey, oddstone;
 *       directional — only when pushed in FACING direction)
 *   4 = oddstone-type (grabs other oddstone/framed-oddstone + all slime
 *       types; mirrors real Oddstone's canStickTo behavior)
 *
 * FACING:
 * =======
 * All framed blocks have a FACING property set from player look direction
 * at placement. For most inner blocks this is cosmetic/unused. For Framed
 * Odd Slime (INNER_STICKY=3) it controls directional stickiness — the
 * block is only sticky when pushed in its FACING direction, matching
 * real Odd Slime behavior.
 *
 * FRAME ACTIVATION:
 * =================
 * Implements IFrameBlock. Has ACTIVE BooleanProperty. Participates in
 * FrameActivation wipe-and-rebuild from Machine Block sources. Chains
 * through same-material frames (iron->iron, copper->copper).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlock extends Block implements IFrameBlock, EntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final DirectionProperty FACING = DirectionalBlock.FACING;

    /**
     * Inner block sticky type. Encoded on the blockstate so isStickyBlock()
     * and canStickTo() can read it without position context.
     *
     * 0 = not sticky (default)
     * 1 = slime-type inner
     * 2 = honey-type inner
     * 3 = odd-slime-type inner
     * 4 = oddstone-type inner
     *
     * Set by FramedBlockEntity when the inner block is loaded or changed,
     * via updateBlockstateFromInner().
     */
    public static final IntegerProperty INNER_STICKY = IntegerProperty.create("inner_sticky", 0, 4);

    /** Constants for INNER_STICKY values */
    public static final int STICKY_NONE = 0;
    public static final int STICKY_SLIME = 1;
    public static final int STICKY_HONEY = 2;
    public static final int STICKY_ODD_SLIME = 3;
    public static final int STICKY_ODDSTONE = 4;

    private final FrameMaterial frameMaterial;

    public FramedBlock(FrameMaterial material, Properties properties) {
        super(properties);
        this.frameMaterial = material;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(INNER_STICKY, STICKY_NONE));
    }

    @Override
    public FrameMaterial getFrameMaterial() {
        return frameMaterial;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, FACING, INNER_STICKY);
    }

    // ========================================================================
    // PLACEMENT — set FACING from player look direction
    // ========================================================================

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    // ========================================================================
    // ROTATION / MIRROR SUPPORT
    // ========================================================================

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ========================================================================
    // BLOCK ENTITY
    // ========================================================================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FramedBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // MODEL for the frame overlay; inner block texture drawn by BESR
        return RenderShape.MODEL;
    }

    // ========================================================================
    // INNER BLOCK HELPER — safe delegation lookup
    // ========================================================================

    /**
     * Get the inner block at a world position. Returns null if the block
     * entity is missing or has no inner block set. All delegation methods
     * must null-check the result and fall back to default behavior.
     */
    @Nullable
    private FramedBlockEntity getFramedBE(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FramedBlockEntity fbe && fbe.hasInnerBlock()) {
            return fbe;
        }
        return null;
    }

    // ========================================================================
    // DELEGATED BEHAVIORS — Physical Properties
    // ========================================================================

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerDefaultState().getDestroyProgress(player, level, pos);
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public SoundType getSoundType(BlockState state, net.minecraft.world.level.LevelReader level,
                                   BlockPos pos, @Nullable Entity entity) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerDefaultState().getSoundType();
        }
        return super.getSoundType(state, level, pos, entity);
    }

    @Override
    public float getExplosionResistance() {
        // Can't delegate without position — use our own default.
        return super.getExplosionResistance();
    }

    @Override
    public float getFriction(BlockState state, net.minecraft.world.level.LevelReader level,
                              BlockPos pos, @Nullable Entity entity) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerBlock().getFriction(fbe.getInnerDefaultState(), level, pos, entity);
        }
        return super.getFriction(state, level, pos, entity);
    }

    /**
     * Speed factor (soul sand = 0.4, honey = 0.4, default = 1.0).
     *
     * This Block method takes no position parameter, so we can't delegate
     * per-position here. Delegation is handled by EntityMovementMixin which
     * intercepts Entity.getBlockSpeedFactor() where position IS available.
     *
     * We return the default (1.0) here so the mixin's value takes precedence.
     */
    @Override
    public float getSpeedFactor() {
        return super.getSpeedFactor();
    }

    /**
     * Jump factor (honey = 0.5, default = 1.0).
     *
     * Same situation as getSpeedFactor() — no position, can't delegate.
     * Handled by EntityMovementMixin at the Entity level.
     */
    @Override
    public float getJumpFactor() {
        return super.getJumpFactor();
    }

    // ========================================================================
    // DELEGATED BEHAVIORS — Entity Interaction
    // ========================================================================

    /**
     * Called when an entity steps on this block. Delegates to the inner block
     * so framed magma burns, framed slime applies surface effects, etc.
     */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            // Call the inner block's stepOn with the inner block's default state
            fbe.getInnerBlock().stepOn(level, pos, fbe.getInnerDefaultState(), entity);
            return;
        }
        super.stepOn(level, pos, state, entity);
    }

    /**
     * Called when an entity falls onto this block. Delegates to the inner block
     * so framed slime bounces, framed hay bale reduces fall damage, etc.
     */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            fbe.getInnerBlock().fallOn(level, fbe.getInnerDefaultState(), pos, entity, fallDistance);
            return;
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    /**
     * Called after an entity lands on this block to modify its vertical velocity.
     * This is where slime's BOUNCE actually happens (not in fallOn).
     *
     * Vanilla call chain for slime:
     *   Entity.checkFallDamage() → Block.fallOn() → Block.updateEntityAfterFallOn()
     *   - SlimeBlock.fallOn() cancels fall damage (calls super with 0 distance)
     *   - SlimeBlock.updateEntityAfterFallOn() reverses Y velocity (the bounce)
     *
     * Without delegating this method, framed slime would cancel fall damage
     * (via fallOn delegation) but NOT bounce (this method defaults to just
     * zeroing Y velocity).
     */
    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        FramedBlockEntity fbe = getFramedBE(level, entity.blockPosition().below());
        if (fbe != null) {
            fbe.getInnerBlock().updateEntityAfterFallOn(level, entity);
            return;
        }
        super.updateEntityAfterFallOn(level, entity);
    }

    /**
     * Called every tick an entity is inside this block's collision box.
     * Delegates to the inner block so framed cobweb slows, framed sweet
     * berry damages, framed powder snow freezes, etc.
     *
     * Note: most blocks that use entityInside have non-full collision boxes
     * (cobweb, sweet berry, powder snow) and may not be in the frameable
     * whitelist. But this delegation is here for future-proofing and for
     * any full-cube blocks that DO use entityInside (like bubble columns
     * once waterlogging is implemented).
     */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            fbe.getInnerBlock().entityInside(fbe.getInnerDefaultState(), level, pos, entity);
            return;
        }
        super.entityInside(state, level, pos, entity);
    }

    // ========================================================================
    // DELEGATED BEHAVIORS — Visual Properties
    // ========================================================================

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerDefaultState().getLightEmission();
        }
        return super.getLightEmission(state, level, pos);
    }

    @Override
    public net.minecraft.world.level.material.MapColor getMapColor(BlockState state,
            BlockGetter level, BlockPos pos, net.minecraft.world.level.material.MapColor defaultColor) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerDefaultState().getMapColor(level, pos);
        }
        return super.getMapColor(state, level, pos, defaultColor);
    }

    // ========================================================================
    // DELEGATED BEHAVIORS — Redstone
    // ========================================================================

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerBlock().getSignal(fbe.getInnerDefaultState(), level, pos, direction);
        }
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerBlock().getDirectSignal(fbe.getInnerDefaultState(), level, pos, direction);
        }
        return 0;
    }

    // ========================================================================
    // DELEGATED BEHAVIORS — Mob Pathfinding
    // ========================================================================

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos,
                                   PathComputationType type) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerBlock().isPathfindable(fbe.getInnerDefaultState(), level, pos, type);
        }
        return super.isPathfindable(state, level, pos, type);
    }

    @Override
    public @Nullable BlockPathTypes getBlockPathType(BlockState state, BlockGetter level,
                                                      BlockPos pos, @Nullable Mob mob) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerBlock().getBlockPathType(fbe.getInnerDefaultState(), level, pos, mob);
        }
        return super.getBlockPathType(state, level, pos, mob);
    }

    @Override
    public @Nullable BlockPathTypes getAdjacentBlockPathType(BlockState state, BlockGetter level,
                                                              BlockPos pos, @Nullable Mob mob,
                                                              BlockPathTypes originalType) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerBlock().getAdjacentBlockPathType(
                    fbe.getInnerDefaultState(), level, pos, mob, originalType);
        }
        return super.getAdjacentBlockPathType(state, level, pos, mob, originalType);
    }

    // ========================================================================
    // STICKINESS — Active-gated frame stickiness + inner block stickiness
    // ========================================================================

    /**
     * Framed blocks are sticky when:
     *   - ACTIVE (powered by Machine Block) — frame stickiness
     *   - INNER_STICKY > 0 — inner block is natively sticky
     */
    @Override
    public boolean isStickyBlock(BlockState state) {
        return state.getValue(ACTIVE) || state.getValue(INNER_STICKY) > 0;
    }

    /**
     * Combined stickiness: frame rules (gated by ACTIVE) + inner block
     * rules (gated by INNER_STICKY) + permissive entries.
     *
     * FRAME RULES (ONLY when ACTIVE):
     *   - Same-material IFrameBlock: YES
     *   - Machine Family: YES
     *
     * INNER BLOCK RULES (when INNER_STICKY > 0):
     *   Mirrors the canStickTo behavior of the actual inner block type.
     *   - Slime (1): sticks to everything EXCEPT honey and odd slime
     *   - Honey (2): sticks to everything EXCEPT slime and odd slime
     *   - Odd Slime (3): sticks to everything EXCEPT slime and honey
     *   - Oddstone (4): sticks to oddstone (real+framed) + slime types
     *   Note: oddstone grab immunity is handled by StickinessRules.
     *   Directional gating for odd slime is handled by a StickinessRules
     *   directional rule registered in Ideomod.java.
     *
     * PERMISSIVE ENTRIES (always):
     *   Allow vanilla stickies and oddstone to grab us if THEY initiate.
     */
    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        Block otherBlock = other.getBlock();
        int innerSticky = state.getValue(INNER_STICKY);

        // --- Frame family rules — ONLY when activated by Machine Block ---
        if (state.getValue(ACTIVE)) {
            if (otherBlock instanceof IFrameBlock otherFrame) {
                return otherFrame.getFrameMaterial() == this.frameMaterial;
            }
            if (MachineFrameBlock.isMachineFamily(otherBlock)) return true;
        }

        // --- Inner block stickiness rules ---
        if (innerSticky > 0) {
            switch (innerSticky) {
                case STICKY_SLIME:
                    // Slime excludes honey and odd slime (mutual exclusion)
                    if (otherBlock instanceof HoneyBlock) return false;
                    if (otherBlock instanceof OddSlimeBlock) return false;
                    if (otherBlock instanceof FramedBlock) {
                        int otherSticky = other.getValue(INNER_STICKY);
                        if (otherSticky == STICKY_HONEY || otherSticky == STICKY_ODD_SLIME) return false;
                    }
                    return true;

                case STICKY_HONEY:
                    // Honey excludes slime and odd slime
                    if (otherBlock instanceof SlimeBlock) return false;
                    if (otherBlock instanceof OddSlimeBlock) return false;
                    if (otherBlock instanceof FramedBlock) {
                        int otherSticky = other.getValue(INNER_STICKY);
                        if (otherSticky == STICKY_SLIME || otherSticky == STICKY_ODD_SLIME) return false;
                    }
                    return true;

                case STICKY_ODD_SLIME:
                    // Odd slime excludes slime and honey
                    if (otherBlock instanceof SlimeBlock) return false;
                    if (otherBlock instanceof HoneyBlock) return false;
                    if (otherBlock instanceof FramedBlock) {
                        int otherSticky = other.getValue(INNER_STICKY);
                        if (otherSticky == STICKY_SLIME || otherSticky == STICKY_HONEY) return false;
                    }
                    return true;

                case STICKY_ODDSTONE:
                    // Oddstone sticks to: other oddstone (real + framed),
                    // and all slime types (real + framed).
                    // Mirrors OddstoneBlock.canStickTo() behavior.
                    // Does NOT stick to machine family or normal blocks via inner.
                    if (otherBlock instanceof OddstoneBlock) return true;
                    if (otherBlock instanceof SlimeBlock) return true;
                    if (otherBlock instanceof HoneyBlock) return true;
                    if (otherBlock instanceof OddSlimeBlock) return true;
                    if (otherBlock instanceof FramedBlock) {
                        int otherSticky = other.getValue(INNER_STICKY);
                        if (otherSticky == STICKY_ODDSTONE) return true;
                        if (otherSticky == STICKY_SLIME) return true;
                        if (otherSticky == STICKY_HONEY) return true;
                        if (otherSticky == STICKY_ODD_SLIME) return true;
                    }
                    return false;
            }
        }

        // --- Permissive entries (always) ---
        // Allow vanilla stickies and oddstone to grab us if THEY initiate.
        if (otherBlock instanceof SlimeBlock) return true;
        if (otherBlock instanceof HoneyBlock) return true;
        if (otherBlock instanceof OddSlimeBlock) return true;
        if (otherBlock instanceof OddstoneBlock) return true;

        // Also be permissive to other framed blocks that are inner-sticky,
        // so their inner stickiness can reach us
        if (otherBlock instanceof FramedBlock && other.getValue(INNER_STICKY) > 0) return true;

        return false;
    }

    // ========================================================================
    // LIFECYCLE — Frame activation triggers + inner sticky sync
    // ========================================================================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            // Sync INNER_STICKY from block entity data (item NBT loaded by now)
            FramedBlockEntity fbe = getFramedBE(level, pos);
            if (fbe != null) {
                fbe.updateBlockstateFromInner();
            }

            if (!state.is(oldState.getBlock())) {
                FrameActivation.recalculate(level, pos);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !movedByPiston) {
            FrameActivation.recalculate(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide()) {
            FrameActivation.recalculate(level, pos);
        }
    }

    // ========================================================================
    // RENDERING HELPERS
    // ========================================================================

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                CollisionContext context) {
        return super.getShape(state, level, pos, context);
    }

    // ========================================================================
    // MIDDLE-CLICK (Pick Block) — return item with inner block NBT
    // ========================================================================

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null && fbe.getInnerBlockId() != null) {
            net.minecraft.world.item.Item item = (this.frameMaterial == FrameMaterial.IRON)
                    ? com.ideomancer.ideomod.registry.ModItems.IRON_FRAMED_BLOCK.get()
                    : com.ideomancer.ideomod.registry.ModItems.COPPER_FRAMED_BLOCK.get();
            return FramedBlockItem.createStack(item, fbe.getInnerBlockId());
        }
        return super.getCloneItemStack(level, pos, state);
    }
}
