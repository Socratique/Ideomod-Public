package com.ideomancer.ideomod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A drill block that mines a 3x3 grid when given a redstone signal.
 * Placement behaves like pistons - faces toward the player's look direction.
 * Can be pushed/pulled by pistons.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class DrillBlock extends DirectionalBlock {
    
    // Property to track if the drill is currently powered
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    
    public DrillBlock(Properties properties) {
        super(properties);
        // Default state: facing north, not powered
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }
    
    /**
     * Placement behavior like pistons - faces the direction the player is looking
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }
    
    /**
     * Called when a neighbor block changes - used to detect redstone signals
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            boolean isPowered = level.hasNeighborSignal(pos);
            boolean wasPowered = state.getValue(POWERED);
            
            if (isPowered != wasPowered) {
                // Update the powered state
                level.setBlock(pos, state.setValue(POWERED, isPowered), 3);
                
                // If we just got powered, do the mining
                if (isPowered && !wasPowered) {
                    mineBlocks((ServerLevel) level, pos, state.getValue(FACING));
                }
            }
        }
    }
    
    /**
     * Mines a 3x3 grid of blocks in front of the drill.
     * Gets perpendicular directions directly from the facing axis —
     * no intermediate axis variable needed.
     */
    private void mineBlocks(ServerLevel level, BlockPos drillPos, Direction facing) {
        BlockPos frontPos = drillPos.relative(facing);
        
        // Get perpendicular directions for the 3x3 grid
        Direction[] perpDirs = getPerpendicularDirections(facing);
        Direction perpDir1 = perpDirs[0];
        Direction perpDir2 = perpDirs[1];
        
        // Mine the 3x3 grid
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                BlockPos targetPos = frontPos
                        .relative(perpDir1, i)
                        .relative(perpDir2, j);
                
                mineBlock(level, targetPos, drillPos);
            }
        }
    }
    
    /**
     * Gets two perpendicular directions for a given facing direction.
     */
    private Direction[] getPerpendicularDirections(Direction facing) {
        return switch (facing.getAxis()) {
            case X -> new Direction[]{Direction.UP, Direction.SOUTH};
            case Y -> new Direction[]{Direction.NORTH, Direction.EAST};
            case Z -> new Direction[]{Direction.UP, Direction.EAST};
        };
    }
    
    /**
     * Mines a single block and drops its items
     */
    private void mineBlock(ServerLevel level, BlockPos targetPos, BlockPos drillPos) {
        BlockState targetState = level.getBlockState(targetPos);
        
        // Skip air blocks
        if (targetState.isAir()) {
            return;
        }
        
        // Skip blocks with hardness -1 (bedrock, etc.) or very hard blocks
        float hardness = targetState.getDestroySpeed(level, targetPos);
        if (hardness < 0) {
            return;
        }
        
        // Get the drops for this block
        LootParams.Builder lootParamsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(targetPos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(targetPos));
        
        List<ItemStack> drops = targetState.getDrops(lootParamsBuilder);
        
        // Remove the block
        level.destroyBlock(targetPos, false);
        
        // Spawn the drops near the drill
        Vec3 dropPos = Vec3.atCenterOf(drillPos);
        for (ItemStack drop : drops) {
            ItemEntity itemEntity = new ItemEntity(level, dropPos.x, dropPos.y + 0.5, dropPos.z, drop);
            itemEntity.setDefaultPickUpDelay();
            level.addFreshEntity(itemEntity);
        }
    }
    
    // Rotation and mirror support for structure blocks
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
