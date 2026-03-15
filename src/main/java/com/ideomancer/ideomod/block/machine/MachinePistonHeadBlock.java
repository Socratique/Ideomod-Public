package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Machine Piston Head -- the 8x8x16 arm that extends from a Machine Piston.
 *
 * PROPERTIES:
 * ===========
 * - FACING: direction the arm points (away from the piston base)
 * - STICKY: true for sticky piston heads (different front face texture)
 *
 * MODEL:
 * ======
 * A single 8x8x16 cuboid (centered on the block face), extending the full
 * block length in the FACING direction. No T-shape like vanilla -- just a
 * simple rectangular bar.
 *
 * BEHAVIOUR:
 * ==========
 * - Cannot be pushed by pistons (PushReaction.BLOCK)
 * - Breaks instantly (hardness 0, no drops)
 * - Breaking the head notifies the parent piston to recognize retraction
 * - Requires a valid parent piston block behind it to survive
 * - Not sticky itself (does NOT participate in Machine Family stickiness)
 * - Not an EntityBlock (pure blockstate, no block entity)
 * - Custom collision shape: 8x8 cross-section centered on the face
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachinePistonHeadBlock extends DirectionalBlock {

    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    public static final BooleanProperty STICKY = BooleanProperty.create("sticky");

    // ========================================================================
    // COLLISION SHAPES -- 8x8x16 bar centered on the block, oriented by FACING
    // ========================================================================
    // Pixel coordinates: 8x8 centered = from 4 to 12 in the two perpendicular axes
    // Full 16 in the extension axis (0 to 16)

    private static final VoxelShape SHAPE_NORTH = Block.box(4, 4, 0, 12, 12, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(4, 4, 0, 12, 12, 16);
    private static final VoxelShape SHAPE_EAST  = Block.box(0, 4, 4, 16, 12, 12);
    private static final VoxelShape SHAPE_WEST  = Block.box(0, 4, 4, 16, 12, 12);
    private static final VoxelShape SHAPE_UP    = Block.box(4, 0, 4, 12, 16, 12);
    private static final VoxelShape SHAPE_DOWN  = Block.box(4, 0, 4, 12, 16, 12);

    public MachinePistonHeadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(STICKY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STICKY);
    }

    // ========================================================================
    // SHAPE
    // ========================================================================

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForDirection(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForDirection(state.getValue(FACING));
    }

    private static VoxelShape getShapeForDirection(Direction dir) {
        return switch (dir) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
        };
    }

    // ========================================================================
    // PUSH REACTION -- heads cannot be pushed by pistons
    // ========================================================================

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    // ========================================================================
    // SURVIVAL CHECK -- head requires parent piston behind it
    // ========================================================================

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction behindDir = state.getValue(FACING).getOpposite();
        if (direction == behindDir) {
            if (!isValidParent(neighborState, state.getValue(FACING))) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction behindDir = state.getValue(FACING).getOpposite();
        BlockPos parentPos = pos.relative(behindDir);
        BlockState parentState = level.getBlockState(parentPos);
        return isValidParent(parentState, state.getValue(FACING));
    }

    /**
     * A valid parent is a MachinePistonBlock or MachineStickyPistonBlock.
     * We don't check direction on the parent because it's omnidirectional.
     */
    private boolean isValidParent(BlockState parentState, Direction headFacing) {
        return parentState.getBlock() instanceof MachinePistonBlock;
    }

    // ========================================================================
    // BREAKING -- notify parent piston
    // ========================================================================

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !movedByPiston) {
            Direction facing = state.getValue(FACING);
            Direction behindDir = facing.getOpposite();
            BlockPos parentPos = pos.relative(behindDir);
            BlockState parentState = level.getBlockState(parentPos);
            if (parentState.getBlock() instanceof MachinePistonBlock pistonBlock) {
                pistonBlock.onHeadBroken(level, parentPos, facing);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // In creative mode, also notify the parent. For survival this is handled by onRemove.
        super.playerWillDestroy(level, pos, state, player);
    }

    // ========================================================================
    // NO DROPS -- head is not a collectable item
    // ========================================================================

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target,
                                        BlockGetter level, BlockPos pos, Player player) {
        // Middle-click returns the parent piston item, not the head itself
        boolean sticky = state.getValue(STICKY);
        if (sticky) {
            return MachineSwapHelper.createCloneStack(
                    com.ideomancer.ideomod.registry.ModBlocks.MACHINE_STICKY_PISTON.get());
        } else {
            return MachineSwapHelper.createCloneStack(
                    com.ideomancer.ideomod.registry.ModBlocks.MACHINE_PISTON.get());
        }
    }

    // ========================================================================
    // RENDERING -- not a full block
    // ========================================================================

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
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
}
