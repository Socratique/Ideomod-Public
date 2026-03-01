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
 * DEBUG: Temporary logging in canStickTo() for bug #10 investigation.
 * Remove debug logging once bug is resolved.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlock extends Block implements IFrameBlock, EntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    public static final IntegerProperty INNER_STICKY = IntegerProperty.create("inner_sticky", 0, 4);

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
    // PLACEMENT
    // ========================================================================

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
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
     * rules (gated by INNER_STICKY) + permissive entries.
     *
     * DEBUG LOGGING: Temporary logging for bug #10 investigation.
     * Search for "BUG10" to find all debug lines. Remove once resolved.
     */
    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        Block otherBlock = other.getBlock();
        int innerSticky = state.getValue(INNER_STICKY);

        // --- Frame family rules -- ONLY when activated by Machine Block ---
        if (state.getValue(ACTIVE)) {
            if (otherBlock instanceof IFrameBlock otherFrame) {
                boolean materialMatch = otherFrame.getFrameMaterial() == this.frameMaterial;
                if (materialMatch) return true;
                // BUG10: Cross-material frame — don't return false, fall through
                // to inner sticky / permissive checks
                com.ideomancer.ideomod.Ideomod.LOGGER.info(
                    "[BUG10 canStickTo] Cross-material frame fall-through. self={} ({}), other={} ({})",
                    this.frameMaterial, state, otherFrame.getFrameMaterial(), other);
            }
            if (MachineFrameBlock.isMachineFamily(otherBlock)) return true;
        }

        // --- Inner block stickiness rules ---
        if (innerSticky > 0) {
            StickyType myType = StickyType.fromInnerStickyValue(innerSticky);
            StickyType otherType = StickyType.of(other);

            if (myType == StickyType.ODDSTONE) {
                boolean result = myType.canGrab(otherType) && otherType != StickyType.NONE;
                com.ideomancer.ideomod.Ideomod.LOGGER.info(
                    "[BUG10 canStickTo] ODDSTONE inner: otherType={}, result={}", otherType, result);
                return result;
            }

            // Slime-family inner: sticks to everything EXCEPT excluded types
            if (myType.isExcludedFrom(otherType)) {
                com.ideomancer.ideomod.Ideomod.LOGGER.info(
                    "[BUG10 canStickTo] Inner sticky EXCLUDED: myType={}, otherType={}", myType, otherType);
                return false;
            }
            com.ideomancer.ideomod.Ideomod.LOGGER.info(
                "[BUG10 canStickTo] Inner sticky ALLOWS: myType={}, otherType={}, other={}",
                myType, otherType, other);
            return true;
        }

        // --- Permissive entries (always) ---
        if (otherBlock instanceof SlimeBlock) return true;
        if (otherBlock instanceof HoneyBlock) return true;
        if (otherBlock instanceof OddSlimeBlock) return true;
        if (otherBlock instanceof OddstoneBlock) return true;

        if (otherBlock instanceof FramedBlock && other.getValue(INNER_STICKY) > 0) return true;

        // BUG10: Final failure
        com.ideomancer.ideomod.Ideomod.LOGGER.info(
            "[BUG10 canStickTo] FAILED — no rule matched. self={} [active={}, inner_sticky={}, material={}], other={} [{}]",
            this.getClass().getSimpleName(),
            state.getValue(ACTIVE), innerSticky, this.frameMaterial,
            otherBlock.getClass().getSimpleName(), other);

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
