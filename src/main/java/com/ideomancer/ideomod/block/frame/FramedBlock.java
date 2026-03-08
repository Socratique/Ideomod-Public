package com.ideomancer.ideomod.block.frame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;
import com.ideomancer.ideomod.block.movement.StickyType;

import java.util.Collections;
import java.util.List;

/**
 * Framed Block -- a block that wraps any valid "inner block" with a metal frame.
 *
 * Two instances exist: iron_framed_block and copper_framed_block.
 * Each behaves as a transparent proxy for its inner block (stored in the
 * block entity) while adding frame-family stickiness on top.
 *
 * LIGHT EMISSION FIX (Issue #11):
 * getStateForPlacement() reads the inner block from the item's NBT and
 * pre-populates INNER_LIGHT, INNER_STICKY, and INNER_CONDUCTOR on the
 * blockstate BEFORE the block enters the world. This ensures the very
 * first setBlock() call has the correct light value, so the chunk's
 * light-change detection (old=AIR emission 0 -> new=FRAMED emission 15)
 * triggers the lighting engine immediately — same as vanilla glowstone.
 *
 * Without this, the block is placed with INNER_LIGHT=0 (default state),
 * the lighting engine sees no change from air, and never queues a light
 * update. Later blockstate updates via updateBlockstateFromInner() set
 * the correct value but the lighting engine has already cached "0" and
 * doesn't re-evaluate.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlock extends Block implements IFrameBlock, EntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    public static final IntegerProperty INNER_STICKY = IntegerProperty.create("inner_sticky", 0, 4);

    /**
     * Tracks whether the inner block is a vanilla metal conductor.
     * 0 = not a conductor, 1 = IRON (iron_block inner), 2 = COPPER (copper_block inner).
     *
     * This enables cross-frame mediation in canStickTo() without needing
     * block entity access. Set by FramedBlockEntity.updateBlockstateFromInner().
     */
    public static final IntegerProperty INNER_CONDUCTOR = IntegerProperty.create("inner_conductor", 0, 2);

    /**
     * Tracks the inner block's light emission level (0-15).
     * Set at placement time via getStateForPlacement() from item NBT,
     * and kept in sync by FramedBlockEntity.updateBlockstateFromInner()
     * for piston restores and chunk loads.
     *
     * The lightLevel predicate in ModBlocks.java reads this property
     * so the lighting engine knows the block's emission without needing
     * block entity access.
     */
    public static final IntegerProperty INNER_LIGHT = IntegerProperty.create("inner_light", 0, 15);

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
                .setValue(INNER_STICKY, STICKY_NONE)
                .setValue(INNER_CONDUCTOR, 0)
                .setValue(INNER_LIGHT, 0));
    }

    @Override
    public FrameMaterial getFrameMaterial() {
        return frameMaterial;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, FACING, INNER_STICKY, INNER_CONDUCTOR, INNER_LIGHT);
    }

    // ========================================================================
    // PLACEMENT
    // ========================================================================

    /**
     * Build the initial blockstate for placement. Reads the item stack's
     * BlockEntityTag to determine the inner block, then pre-populates
     * INNER_LIGHT, INNER_STICKY, and INNER_CONDUCTOR so the blockstate
     * is fully correct from the very first setBlock() call.
     *
     * This is critical for light emission: the chunk's light-change detection
     * compares old emission (air=0) vs new emission during setBlock(). If the
     * new state already has INNER_LIGHT=15, the lighting engine queues a proper
     * propagation update immediately — same as placing vanilla glowstone.
     *
     * The block entity doesn't exist yet at this point (it's created after
     * setBlock), so we read directly from the item's NBT rather than
     * delegating to FramedBlockEntity.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());

        // Read inner block from the item's NBT to pre-populate derived properties
        ItemStack stack = context.getItemInHand();
        ResourceLocation innerBlockId = FramedBlockEntity.getInnerBlockIdFromStack(stack);
        if (innerBlockId != null) {
            Block innerBlock = BuiltInRegistries.BLOCK.get(innerBlockId);
            // Guard against unknown registry keys (returns AIR)
            if (innerBlock != Blocks.AIR || innerBlockId.equals(BuiltInRegistries.BLOCK.getKey(Blocks.AIR))) {
                // INNER_LIGHT: from inner block's default light emission
                int light = innerBlock.defaultBlockState().getLightEmission();
                state = state.setValue(INNER_LIGHT, light);

                // INNER_STICKY: from StickyType classification
                int sticky = StickyType.fromBlock(innerBlock).getInnerStickyValue();
                state = state.setValue(INNER_STICKY, sticky);

                // INNER_CONDUCTOR: from FrameMaterial vanilla block detection
                FrameMaterial conductorMat = FrameMaterial.fromVanillaBlock(innerBlock);
                int conductor = conductorMat != null ? conductorMat.toConductorValue() : 0;
                state = state.setValue(INNER_CONDUCTOR, conductor);
            }
        }

        return state;
    }

    // ========================================================================
    // ROTATION / MIRROR
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
        return RenderShape.MODEL;
    }

    // ========================================================================
    // INNER BLOCK HELPER
    // ========================================================================

    @Nullable
    private FramedBlockEntity getFramedBE(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FramedBlockEntity fbe && fbe.hasInnerBlock()) {
            return fbe;
        }
        return null;
    }

    public static boolean hasImmovableInner(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FramedBlockEntity fbe) || !fbe.hasInnerBlock()) return false;
        BlockState innerState = fbe.getInnerDefaultState();
        if (innerState.is(Blocks.OBSIDIAN)) return true;
        if (innerState.is(Blocks.CRYING_OBSIDIAN)) return true;
        if (innerState.is(Blocks.REINFORCED_DEEPSLATE)) return true;
        if (innerState.getDestroySpeed(level, pos) == -1.0f) return true;
        return false;
    }

    // ========================================================================
    // MINING DYNAMICS
    // ========================================================================

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            BlockState innerState = fbe.getInnerDefaultState();
            float innerHardness = innerState.getDestroySpeed(level, pos);
            if (innerHardness < 0) return 0.0f;

            float effectiveHardness = innerHardness + frameMaterial.getMiningTimeModifier();
            boolean correctTier = hasCorrectTierForInner(player, innerState);
            float digSpeed = player.getDestroySpeed(state);
            float multiplier = correctTier ? 30.0f : 100.0f;

            return digSpeed / (effectiveHardness * multiplier);
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    private boolean hasCorrectTierForInner(Player player, BlockState innerState) {
        ItemStack tool = player.getMainHandItem();
        if (!tool.isCorrectToolForDrops(Blocks.STONE.defaultBlockState())) {
            return false;
        }
        if (innerState.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return tool.isCorrectToolForDrops(innerState);
        }
        if (innerState.is(BlockTags.NEEDS_IRON_TOOL)) {
            return tool.isCorrectToolForDrops(innerState);
        }
        if (innerState.is(BlockTags.NEEDS_STONE_TOOL)) {
            return tool.isCorrectToolForDrops(innerState);
        }
        return true;
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            return hasCorrectTierForInner(player, fbe.getInnerDefaultState());
        }
        ItemStack tool = player.getMainHandItem();
        return tool.isCorrectToolForDrops(Blocks.STONE.defaultBlockState());
    }

    // ========================================================================
    // DROPS
    // ========================================================================

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof FramedBlockEntity fbe && fbe.hasInnerBlock()) {
            ItemStack drop = FramedBlockLoot.createDrop(fbe);
            return Collections.singletonList(drop);
        }
        return Collections.singletonList(new ItemStack(frameMaterial.getFramedBlockItem()));
    }

    // ========================================================================
    // ICE MELT
    // ========================================================================

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level,
                            BlockPos pos, net.minecraft.util.RandomSource random) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe == null) return;

        Block innerBlock = fbe.getInnerBlock();
        if (innerBlock == Blocks.ICE) {
            if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) > 11 - innerBlock.defaultBlockState().getLightEmission()) {
                meltIceToWaterloggedFrame(state, level, pos);
            }
        }
    }

    private void meltIceToWaterloggedFrame(BlockState state, net.minecraft.server.level.ServerLevel level,
                                            BlockPos pos) {
        Block frameBlock;
        if (frameMaterial == FrameMaterial.IRON) {
            frameBlock = com.ideomancer.ideomod.registry.ModBlocks.IRON_FRAME.get();
        } else {
            frameBlock = com.ideomancer.ideomod.registry.ModBlocks.COPPER_FRAME.get();
        }

        boolean wasActive = state.getValue(ACTIVE);
        BlockState frameState = frameBlock.defaultBlockState()
                .setValue(FrameBlock.ACTIVE, wasActive)
                .setValue(FrameBlock.WATERLOGGED, true);

        level.setBlock(pos, frameState, Block.UPDATE_ALL);
        level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER,
                net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
    }

    // ========================================================================
    // DELEGATED BEHAVIORS -- Physical Properties
    // ========================================================================

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

    @Override
    public float getSpeedFactor() {
        return super.getSpeedFactor();
    }

    @Override
    public float getJumpFactor() {
        return super.getJumpFactor();
    }

    // ========================================================================
    // DELEGATED BEHAVIORS -- Entity Interaction
    // ========================================================================

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            fbe.getInnerBlock().stepOn(level, pos, fbe.getInnerDefaultState(), entity);
            return;
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null) {
            fbe.getInnerBlock().fallOn(level, fbe.getInnerDefaultState(), pos, entity, fallDistance);
            return;
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        FramedBlockEntity fbe = getFramedBE(level, entity.blockPosition().below());
        if (fbe != null) {
            fbe.getInnerBlock().updateEntityAfterFallOn(level, entity);
            return;
        }
        super.updateEntityAfterFallOn(level, entity);
    }

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
    // DELEGATED BEHAVIORS -- Visual Properties
    // ========================================================================

    /**
     * Runtime light emission delegation. Queries the block entity for the inner
     * block's light level. This is a fallback for code paths that call
     * getLightEmission() directly rather than reading the blockstate predicate.
     *
     * The primary light emission path is the INNER_LIGHT blockstate property,
     * read by the lightLevel predicate registered in ModBlocks.java. The
     * lighting engine uses that predicate, not this method, for light propagation.
     */
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
    // DELEGATED BEHAVIORS -- Redstone
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
    // DELEGATED BEHAVIORS -- Mob Pathfinding
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
    // STICKINESS
    // ========================================================================

    @Override
    public boolean isStickyBlock(BlockState state) {
        return state.getValue(ACTIVE) || state.getValue(INNER_STICKY) > 0;
    }

    /**
     * Combined stickiness: frame rules (gated by ACTIVE) + inner block
     * rules (gated by INNER_STICKY) + cross-frame mediation + permissive entries.
     *
     * CROSS-FRAME MEDIATION (via INNER_CONDUCTOR property):
     * When active, a framed block sticks to blocks matching EITHER its
     * frame material OR its inner conductor material. For example:
     *
     *   copper-framed iron block (material=COPPER, inner_conductor=IRON):
     *     ✓ Copper frames/framed blocks (frame material match)
     *     ✓ Iron frames/framed blocks (inner conductor match)
     *     ✓ Vanilla iron blocks (inner conductor match)
     *     ✓ Vanilla copper blocks (frame material match)
     *     ✓ Machine Family (always)
     *
     * DEBUG LOGGING: Temporary logging for bug #10 investigation.
     * Search for "BUG10" to find all debug lines. Remove once resolved.
     */
    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        Block otherBlock = other.getBlock();
        int innerSticky = state.getValue(INNER_STICKY);
        int innerConductor = state.getValue(INNER_CONDUCTOR);
        FrameMaterial innerMat = FrameMaterial.fromConductorValue(innerConductor);

        // --- Frame family rules -- ONLY when activated by Machine Block ---
        if (state.getValue(ACTIVE)) {
            if (otherBlock instanceof IFrameBlock otherFrame) {
                // Other frame must also be active for frame-family stickiness
                if (!IFrameBlock.isActive(other)) {
                    // Fall through — might have inner stickiness (framed slime etc.)
                } else {
                    // Same frame material — stick
                    if (otherFrame.getFrameMaterial() == this.frameMaterial) return true;

                    // Cross-frame mediation: MY inner conductor matches OTHER's frame material
                    if (innerMat != null && innerMat == otherFrame.getFrameMaterial()) return true;

                    // Cross-frame mediation (reverse): OTHER's inner conductor matches MY frame material
                    if (otherBlock instanceof FramedBlock) {
                        FrameMaterial otherInnerMat = FrameMaterial.fromConductorValue(
                            other.getValue(INNER_CONDUCTOR));
                        if (otherInnerMat != null && otherInnerMat == this.frameMaterial) return true;
                    }
                }

                // Cross-material: DON'T return false here. The other block might
                // have inner stickiness that should still be able to grab us.
            }
            if (MachineFrameBlock.isMachineFamily(otherBlock)) return true;

            // Vanilla metal block: same frame material OR inner conductor match
            FrameMaterial otherVanillaMat = FrameMaterial.fromVanillaBlock(otherBlock);
            if (otherVanillaMat != null) {
                if (otherVanillaMat == this.frameMaterial) return true;
                if (innerMat != null && innerMat == otherVanillaMat) return true;
            }
        }

        // --- Inner block stickiness rules ---
        if (innerSticky > 0) {
            StickyType myType = StickyType.fromInnerStickyValue(innerSticky);
            StickyType otherType = StickyType.of(other);

            if (myType == StickyType.ODDSTONE) {
                boolean result = myType.canGrab(otherType) && otherType != StickyType.NONE;
                return result;
            }

            // Slime-family inner: sticks to everything EXCEPT excluded types
            if (myType.isExcludedFrom(otherType)) return false;
            return true;
        }

        // --- Permissive entries (always) ---
        if (otherBlock instanceof SlimeBlock) return true;
        if (otherBlock instanceof HoneyBlock) return true;
        if (otherBlock instanceof OddSlimeBlock) return true;
        if (otherBlock instanceof OddstoneBlock) return true;

        if (otherBlock instanceof FramedBlock && other.getValue(INNER_STICKY) > 0) return true;

        return false;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
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
    // MIDDLE-CLICK
    // ========================================================================

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        FramedBlockEntity fbe = getFramedBE(level, pos);
        if (fbe != null && fbe.getInnerBlockId() != null) {
            net.minecraft.world.item.Item item = this.frameMaterial.getFramedBlockItem();
            return FramedBlockItem.createStack(item, fbe.getInnerBlockId());
        }
        return super.getCloneItemStack(level, pos, state);
    }
}
