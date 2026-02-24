package com.ideomancer.ideomod.block.piston;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.function.Predicate;

/**
 * Shared base class for all ranged piston blocks (Repulser and Attractor variants).
 *
 * Handles the common pattern:
 *   - Directional placement (piston-style, faces player look direction)
 *   - POWERED state with rising-edge detection
 *   - Block event scheduling (neighborChanged) and dispatch (triggerEvent)
 *   - Rotation/mirror support
 *
 * Subclasses only need to implement {@link #fire(Level, BlockPos, Direction)}
 * which defines what happens when the device activates (scan + move).
 *
 * Uses the block event system so movement runs on BOTH client and server,
 * which is required for PistonMovingBlockEntity rendering. See BLOCK_EVENT_LESSON.md.
 */
public abstract class AbstractRangedPistonBlock extends DirectionalBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** Block event ID for "fire the device". */
    private static final int EVENT_FIRE = 0;

    protected AbstractRangedPistonBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /**
     * Redstone detection -- on rising edge, schedule a block event.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            boolean isPowered = level.hasNeighborSignal(pos);
            boolean wasPowered = state.getValue(POWERED);

            if (isPowered != wasPowered) {
                level.setBlock(pos, state.setValue(POWERED, isPowered), 3);

                if (isPowered && !wasPowered) {
                    Direction facing = state.getValue(FACING);
                    level.blockEvent(pos, this, EVENT_FIRE, facing.get3DDataValue());
                }
            }
        }
    }

    /**
     * Block event handler -- runs on BOTH client and server.
     * Decodes direction from param and delegates to {@link #fire}.
     */
    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (id == EVENT_FIRE) {
            Direction facing = Direction.from3DDataValue(param);
            fire(level, pos, facing);
            return true;
        }
        return false;
    }

    /**
     * Subclass hook: perform the scan and movement for this device type.
     * Called from triggerEvent on both client and server.
     *
     * @param level  The world (runs on both sides)
     * @param pos    Device position
     * @param facing Direction the device is pointing
     */
    protected abstract void fire(Level level, BlockPos pos, Direction facing);

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
