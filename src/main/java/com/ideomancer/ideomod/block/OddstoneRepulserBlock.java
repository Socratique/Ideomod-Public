package com.ideomancer.ideomod.block;

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

/**
 * Oddstone Repulser -- a directional ranged piston that pushes Oddstone at a distance.
 * 
 * Uses the block event system (like vanilla pistons) so that movement code
 * runs on BOTH client and server. This is critical because vanilla's
 * MovingPistonBlock.newBlockEntity() returns null -- the client-side
 * PistonMovingBlockEntity is only created when moveBlocks-style code runs
 * on the client via triggerEvent(). Without this, the 2-tick slide animation
 * would be invisible.
 * 
 * Flow:
 *   1. neighborChanged() detects rising edge, calls level.blockEvent()
 *   2. Minecraft queues the event for the block event phase
 *   3. triggerEvent() runs on BOTH client and server during that phase
 *   4. Server: resolves + moves blocks (creates PistonMovingBlockEntity)
 *   5. Client: receives the block event packet, runs triggerEvent() too,
 *      creating client-side PistonMovingBlockEntity for rendering
 */
public class OddstoneRepulserBlock extends DirectionalBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** Block event ID for "fire the repulser". */
    private static final int EVENT_FIRE = 0;

    public OddstoneRepulserBlock(Properties properties) {
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
     * The actual movement happens in triggerEvent() which runs on both sides.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            boolean isPowered = level.hasNeighborSignal(pos);
            boolean wasPowered = state.getValue(POWERED);

            if (isPowered != wasPowered) {
                level.setBlock(pos, state.setValue(POWERED, isPowered), 3);

                // Fire on rising edge only -- schedule block event
                if (isPowered && !wasPowered) {
                    Direction facing = state.getValue(FACING);
                    level.blockEvent(pos, this, EVENT_FIRE, facing.get3DDataValue());
                }
            }
        }
    }

    /**
     * Block event handler -- runs on BOTH client and server.
     * This is the same pattern vanilla PistonBaseBlock uses. The block event
     * packet is sent to clients automatically, causing them to call this
     * method too, which creates the PistonMovingBlockEntity on the client
     * for rendering.
     */
    /**
     * Block event handler -- runs on BOTH client and server.
     * Must execute the full movement on both sides so that the client
     * creates PistonMovingBlockEntity instances for rendering.
     * This mirrors how vanilla PistonBaseBlock.triggerEvent() calls
     * moveBlocks() without a server-side guard.
     */
    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (id == EVENT_FIRE) {
            Direction facing = Direction.from3DDataValue(param);
            RangedPistonUtil.tryRepulse(level, pos, facing, RangedPistonUtil.ODDSTONE_ONLY);
            return true;
        }
        return false;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
