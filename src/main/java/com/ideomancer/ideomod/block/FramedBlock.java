package com.ideomancer.ideomod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Framed Block — a block that wraps any valid "inner block" with a metal frame.
 *
 * Two instances exist: iron_framed_block and copper_framed_block.
 * Each behaves as a transparent proxy for its inner block (stored in the
 * block entity) while adding frame-family stickiness on top.
 *
 * BEHAVIORAL DELEGATION:
 * ======================
 * ~15 methods are overridden to delegate to the inner block's default state.
 * This means a Framed Redstone Block emits redstone signal, a Framed Slime
 * Block has slime friction/bounce, a Framed Oddstone repels mobs, etc.
 * The delegation is GENERIC — we ask the inner block instance directly,
 * no hardcoded per-block-type logic.
 *
 * Methods that receive a BlockPos can look up the block entity and delegate.
 * Methods that only receive a BlockState (no position) cannot delegate
 * per-position and use conservative defaults.
 *
 * STICKINESS MODEL:
 * =================
 * isStickyBlock() always returns true. The actual stickiness behavior is
 * controlled by canStickTo() which combines:
 *   - Frame family rules (same-material frames, machine blocks)
 *   - Inner block's own stickiness (if the inner block is slime/honey/etc.)
 * Plus StickinessRules grab immunity prevents frames from grabbing
 * slime/honey/odd slime (rules 9-11).
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

    private final FrameMaterial frameMaterial;

    public FramedBlock(FrameMaterial material, Properties properties) {
        super(properties);
        this.frameMaterial = material;
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    public FrameMaterial getFrameMaterial() {
        return frameMaterial;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
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
                                   BlockPos pos, @Nullable net.minecraft.world.entity.Entity entity) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerDefaultState().getSoundType();
        }
        return super.getSoundType(state, level, pos, entity);
    }

    @Override
    public float getExplosionResistance() {
        // Can't delegate without position — use our own default.
        // Per-position delegation would require the method signature to include BlockPos.
        return super.getExplosionResistance();
    }

    @Override
    public float getFriction(BlockState state, net.minecraft.world.level.LevelReader level,
                              BlockPos pos, @Nullable net.minecraft.world.entity.Entity entity) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return fbe.getInnerBlock().getFriction(fbe.getInnerDefaultState(), level, pos, entity);
        }
        return super.getFriction(state, level, pos, entity);
    }

    @Override
    public float getSpeedFactor() {
        // No position available — conservative default
        return super.getSpeedFactor();
    }

    @Override
    public float getJumpFactor() {
        // No position available — conservative default
        return super.getJumpFactor();
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
        // Can't delegate without position. Return true conservatively —
        // getSignal will return 0 for non-source inner blocks, which
        // effectively makes it a non-source at the signal level.
        // However, returning true always would cause unnecessary updates.
        // Compromise: return true. The cost is minor (extra neighbor updates
        // for non-redstone-source framed blocks) but ensures framed redstone
        // blocks actually work as sources.
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

    // Note: isRedstoneConductor is set via Properties lambda in ModBlocks.
    // We delegate it there with a lambda that checks the inner block.

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
    // STICKINESS — Always sticky, rules handled by canStickTo + StickinessRules
    // ========================================================================

    @Override
    public boolean isStickyBlock(BlockState state) {
        // Always true. Actual stickiness is controlled by canStickTo()
        // and StickinessRules grab immunity. This ensures:
        // - When ACTIVE: sticks to same-material frames + machine blocks
        // - When inner is sticky: preserves inner stickiness behavior
        // - When neither: canStickTo returns false for everything, so
        //   being "sticky" has no net effect
        return true;
    }

    /**
     * Combined stickiness: frame rules + inner block rules.
     *
     * Frame rules (when ACTIVE):
     *   - Same-material IFrameBlock: YES
     *   - Machine Family: YES
     *
     * Inner block rules (always):
     *   - Delegates to inner block's canStickTo for its own relationships
     *
     * Grab immunity rules 9-11 in StickinessRules prevent this block
     * from ACTIVELY grabbing slime/honey/odd slime (frame blocks don't
     * grab vanilla stickies). But inner block delegation means a framed
     * slime block CAN stick to things that slime sticks to.
     */
    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        Block otherBlock = other.getBlock();

        // Frame family rules (active when machine-powered)
        if (otherBlock instanceof IFrameBlock otherFrame) {
            return otherFrame.getFrameMaterial() == this.frameMaterial;
        }
        if (MachineFrameBlock.isMachineFamily(otherBlock)) return true;

        // Inner block delegation: check what the inner block would stick to.
        // This is position-independent — we can't look up the BE from just a state.
        // For the common case (non-sticky inner blocks), this returns false.
        // For sticky inner blocks (slime, honey, odd slime), we check their rules.
        //
        // IMPORTANT: We use the inner block's CLASS to delegate, not an instance
        // lookup. This means we need the inner block's type encoded in some way.
        // Since canStickTo only receives states (no position), we check if OUR
        // block's base stickiness rules should apply based on what types of
        // inner blocks exist.
        //
        // The practical approach: slime sticks to everything except honey.
        // Honey sticks to everything except slime. Odd slime has its own rules.
        // Since we can't know which inner block THIS position has from just a
        // state, we rely on StickinessRules grab immunity to handle the
        // asymmetric cases. The canStickTo here is permissive — the mixin
        // handles blocking.

        // Permissive: allow sticking to vanilla stickies (slime, honey, odd slime)
        // StickinessRules grab immunity (rules 9-11) will prevent us from
        // GRABBING them, but if THEY grab US, the connection is allowed.
        if (otherBlock instanceof SlimeBlock) return true;
        if (otherBlock instanceof HoneyBlock) return true;
        if (otherBlock instanceof OddSlimeBlock) return true;

        // Also allow sticking to Oddstone (inner oddstone delegation)
        if (otherBlock instanceof OddstoneBlock) return true;

        return false;
    }

    // ========================================================================
    // LIFECYCLE — Frame activation triggers
    // ========================================================================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            FrameActivation.recalculate(level, pos);
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
        // Frame overlay is partial — allow some light through
        return true;
    }

    // Full cube shape so face culling with adjacent solid blocks works correctly.
    // The frame overlay is rendered by the BESR on top of the inner block texture.
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
            // Determine which item (iron or copper)
            net.minecraft.world.item.Item item = (this.frameMaterial == FrameMaterial.IRON)
                    ? com.ideomancer.ideomod.registry.ModItems.IRON_FRAMED_BLOCK.get()
                    : com.ideomancer.ideomod.registry.ModItems.COPPER_FRAMED_BLOCK.get();
            return FramedBlockItem.createStack(item, fbe.getInnerBlockId());
        }
        return super.getCloneItemStack(level, pos, state);
    }
}
