package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Machine Fluid Pipe -- fluid vacuum and depositor.
 *
 * BEHAVIOUR:
 * ==========
 * - Specialization item: Bucket (empty) in Machine Frame GUI slot
 * - No transit slot (fluid state is tracked as BE data, not a slot item)
 * - Redstone powered = actively pulling fluid from adjacent source blocks/cauldrons
 * - Auto-push: when holding fluid, pushes to adjacent empty fluid pipes on cooldown
 * - Deposit: when no valid pipe target, places fluid in world or fills cauldron
 * - Water+Lava collision: bucket replaced with obsidian -> auto-revert to Machine Frame
 *
 * REDSTONE (OPPOSITE of item Pipe):
 * ==================================
 * - Powered = actively pulling (the "pump" metaphor)
 * - Unpowered = idle (still auto-pushes if holding fluid, but won't pull)
 *
 * FLUID_TYPE blockstate: empty, water, lava -- drives the inner cuboid texture.
 *
 * OUTPUT PRIORITY:
 * ================
 * Same as item Pipe: forward (opposite of input face), right, left, up, down.
 * All relative to looking along the input direction.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineFluidPipeBlock extends AbstractMachineBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ideomod/FluidPipe");

    /** The fluid type currently held by this pipe. Drives inner texture swap. */
    public static final EnumProperty<FluidType> FLUID_TYPE = EnumProperty.create("fluid_type", FluidType.class);

    public MachineFluidPipeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FLUID_TYPE, FluidType.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLUID_TYPE);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.FLUID_PIPE;
    }

    // ========================================================================
    // BLOCK ENTITY
    // ========================================================================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineFluidPipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == ModBlockEntities.MACHINE_FLUID_PIPE.get()
                ? (lvl, pos, st, be) -> serverTick(lvl, pos, st, (MachineFluidPipeBlockEntity) be)
                : null;
    }

    // ========================================================================
    // SERVER TICK -- core fluid pipe logic
    // ========================================================================

    /**
     * Server-side tick. Manages cooldown, pull (when powered), and push.
     */
    private static void serverTick(Level level, BlockPos pos, BlockState state,
                                    MachineFluidPipeBlockEntity pipe) {
        // Decrement cooldown
        if (pipe.getTransferCooldown() > 0) {
            pipe.setTransferCooldown(pipe.getTransferCooldown() - 1);
        }

        boolean powered = level.hasNeighborSignal(pos);

        // PULL: when powered and empty, try to pull fluid from adjacent
        if (powered && !pipe.hasFluid() && pipe.getTransferCooldown() <= 0) {
            if (tryPullFluid(level, pos, pipe)) {
                pipe.setTransferCooldown(MachineFluidPipeBlockEntity.getConfigTransferCooldown());
                pipe.setChanged();
            }
        }

        // PUSH: when holding fluid and cooldown expired, try to push
        if (pipe.hasFluid() && pipe.getTransferCooldown() <= 0) {
            if (tryPushFluid(level, pos, pipe)) {
                pipe.setTransferCooldown(MachineFluidPipeBlockEntity.getConfigTransferCooldown());
                pipe.setChanged();
            }
        }
    }

    // ========================================================================
    // PULL LOGIC (powered pump)
    // ========================================================================

    /**
     * Attempts to pull fluid from an adjacent source block or cauldron.
     * Scans all 6 faces in priority order, pulls from the first valid source.
     * Only pulls source blocks (flowing water/lava ignored).
     *
     * @return true if fluid was successfully pulled
     */
    private static boolean tryPullFluid(Level level, BlockPos pos, MachineFluidPipeBlockEntity pipe) {
        Direction inputFace = pipe.getLastInputFace();
        if (inputFace == null) inputFace = Direction.UP;

        // Scan all 6 directions for fluid sources
        Direction[] scanOrder = getOutputOrder(inputFace);

        for (Direction dir : scanOrder) {
            BlockPos targetPos = pos.relative(dir);
            BlockState targetState = level.getBlockState(targetPos);

            // Check for water source block
            FluidState fluidState = targetState.getFluidState();
            if (fluidState.is(Fluids.WATER) && fluidState.isSource()) {
                // Remove the water source block
                level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
                pipe.setFluidType(FluidType.WATER);
                pipe.onFluidReceived(dir);
                pipe.syncToClients();
                LOGGER.debug("Fluid pipe at {} pulled water from {}", pos, targetPos);
                return true;
            }

            // Check for lava source block
            if (fluidState.is(Fluids.LAVA) && fluidState.isSource()) {
                level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
                pipe.setFluidType(FluidType.LAVA);
                pipe.onFluidReceived(dir);
                pipe.syncToClients();
                LOGGER.debug("Fluid pipe at {} pulled lava from {}", pos, targetPos);
                return true;
            }

            // Check for full water cauldron
            if (targetState.is(Blocks.WATER_CAULDRON)) {
                int cauldronLevel = targetState.getValue(LayeredCauldronBlock.LEVEL);
                if (cauldronLevel == 3) { // Full cauldron
                    // Empty the cauldron completely
                    level.setBlock(targetPos, Blocks.CAULDRON.defaultBlockState(), 3);
                    pipe.setFluidType(FluidType.WATER);
                    pipe.onFluidReceived(dir);
                    pipe.syncToClients();
                    LOGGER.debug("Fluid pipe at {} pulled water from cauldron at {}", pos, targetPos);
                    return true;
                }
            }

            // Check for full lava cauldron
            if (targetState.is(Blocks.LAVA_CAULDRON)) {
                // Lava cauldrons are always full (no level property)
                level.setBlock(targetPos, Blocks.CAULDRON.defaultBlockState(), 3);
                pipe.setFluidType(FluidType.LAVA);
                pipe.onFluidReceived(dir);
                pipe.syncToClients();
                LOGGER.debug("Fluid pipe at {} pulled lava from cauldron at {}", pos, targetPos);
                return true;
            }
        }

        // Also check the input face itself (scan all 6 faces total)
        if (inputFace != null) {
            BlockPos inputPos = pos.relative(inputFace);
            BlockState inputState = level.getBlockState(inputPos);
            FluidState inputFluid = inputState.getFluidState();

            if (inputFluid.is(Fluids.WATER) && inputFluid.isSource()) {
                level.setBlock(inputPos, Blocks.AIR.defaultBlockState(), 3);
                pipe.setFluidType(FluidType.WATER);
                pipe.onFluidReceived(inputFace);
                pipe.syncToClients();
                return true;
            }
            if (inputFluid.is(Fluids.LAVA) && inputFluid.isSource()) {
                level.setBlock(inputPos, Blocks.AIR.defaultBlockState(), 3);
                pipe.setFluidType(FluidType.LAVA);
                pipe.onFluidReceived(inputFace);
                pipe.syncToClients();
                return true;
            }
            if (inputState.is(Blocks.WATER_CAULDRON) && inputState.getValue(LayeredCauldronBlock.LEVEL) == 3) {
                level.setBlock(inputPos, Blocks.CAULDRON.defaultBlockState(), 3);
                pipe.setFluidType(FluidType.WATER);
                pipe.onFluidReceived(inputFace);
                pipe.syncToClients();
                return true;
            }
            if (inputState.is(Blocks.LAVA_CAULDRON)) {
                level.setBlock(inputPos, Blocks.CAULDRON.defaultBlockState(), 3);
                pipe.setFluidType(FluidType.LAVA);
                pipe.onFluidReceived(inputFace);
                pipe.syncToClients();
                return true;
            }
        }

        return false;
    }

    // ========================================================================
    // PUSH LOGIC (auto, like item Pipe)
    // ========================================================================

    /**
     * Attempts to push fluid to an adjacent Fluid Pipe, or deposit into the world.
     * Uses round-robin output selection relative to the last input face.
     *
     * Priority:
     * 1. Push to adjacent empty Fluid Pipe (round-robin)
     * 2. Deposit into world (air -> place source, cauldron -> fill)
     *
     * @return true if fluid was successfully transferred or deposited
     */
    private static boolean tryPushFluid(Level level, BlockPos pos, MachineFluidPipeBlockEntity pipe) {
        Direction inputFace = pipe.getLastInputFace();
        if (inputFace == null) inputFace = Direction.UP;

        Direction[] outputOrder = getOutputOrder(inputFace);
        int startIndex = pipe.getRoundRobinIndex();
        FluidType myFluid = pipe.getFluidType();

        // Pass 1: Try to push to adjacent Fluid Pipes
        for (int i = 0; i < outputOrder.length; i++) {
            int tryIndex = (startIndex + i) % outputOrder.length;
            Direction outputDir = outputOrder[tryIndex];

            // Don't push back to the input face
            if (outputDir == inputFace) continue;

            BlockPos targetPos = pos.relative(outputDir);
            BlockEntity targetBE = level.getBlockEntity(targetPos);

            if (targetBE instanceof MachineFluidPipeBlockEntity targetPipe) {
                // Check for water+lava collision
                if (targetPipe.hasFluid()) {
                    if (targetPipe.handleFluidCollision(myFluid)) {
                        // Collision! The target pipe's bucket became obsidian.
                        // Our fluid was consumed in the reaction too.
                        pipe.setFluidType(FluidType.EMPTY);
                        pipe.syncToClients();
                        pipe.setRoundRobinIndex((tryIndex + 1) % outputOrder.length);
                        LOGGER.debug("Fluid collision at {}: {} met {} -> obsidian",
                                targetPos, myFluid, targetPipe.getFluidType());
                        return true;
                    }
                    // Same fluid type or target already has fluid -- skip
                    continue;
                }

                // Target is empty -- push fluid
                targetPipe.setFluidType(myFluid);
                targetPipe.onFluidReceived(outputDir.getOpposite());
                targetPipe.syncToClients();

                pipe.setFluidType(FluidType.EMPTY);
                pipe.syncToClients();
                pipe.setRoundRobinIndex((tryIndex + 1) % outputOrder.length);

                LOGGER.debug("Fluid pipe at {} pushed {} to {}", pos, myFluid, targetPos);
                return true;
            }
        }

        // Pass 2: Try to deposit into the world (at each output face in order)
        for (int i = 0; i < outputOrder.length; i++) {
            int tryIndex = (startIndex + i) % outputOrder.length;
            Direction outputDir = outputOrder[tryIndex];

            if (outputDir == inputFace) continue;

            BlockPos targetPos = pos.relative(outputDir);
            BlockState targetState = level.getBlockState(targetPos);

            // Deposit into air
            if (targetState.isAir()) {
                BlockState fluidBlock = myFluid == FluidType.WATER
                        ? Blocks.WATER.defaultBlockState()
                        : Blocks.LAVA.defaultBlockState();
                level.setBlock(targetPos, fluidBlock, 3);
                pipe.setFluidType(FluidType.EMPTY);
                pipe.syncToClients();
                pipe.setRoundRobinIndex((tryIndex + 1) % outputOrder.length);
                LOGGER.debug("Fluid pipe at {} deposited {} into air at {}", pos, myFluid, targetPos);
                return true;
            }

            // Deposit into empty cauldron
            if (targetState.is(Blocks.CAULDRON)) {
                BlockState filledCauldron = myFluid == FluidType.WATER
                        ? Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)
                        : Blocks.LAVA_CAULDRON.defaultBlockState();
                level.setBlock(targetPos, filledCauldron, 3);
                pipe.setFluidType(FluidType.EMPTY);
                pipe.syncToClients();
                pipe.setRoundRobinIndex((tryIndex + 1) % outputOrder.length);
                LOGGER.debug("Fluid pipe at {} filled cauldron at {}", pos, targetPos);
                return true;
            }

            // Deposit water into partially-filled water cauldron
            if (myFluid == FluidType.WATER && targetState.is(Blocks.WATER_CAULDRON)) {
                int cauldronLevel = targetState.getValue(LayeredCauldronBlock.LEVEL);
                if (cauldronLevel < 3) {
                    // Fill to max (vanilla bucket behavior)
                    level.setBlock(targetPos,
                            targetState.setValue(LayeredCauldronBlock.LEVEL, 3), 3);
                    pipe.setFluidType(FluidType.EMPTY);
                    pipe.syncToClients();
                    pipe.setRoundRobinIndex((tryIndex + 1) % outputOrder.length);
                    LOGGER.debug("Fluid pipe at {} topped up cauldron at {}", pos, targetPos);
                    return true;
                }
            }
        }

        return false; // No valid output found
    }

    // ========================================================================
    // OUTPUT DIRECTION CALCULATION (same as item Pipe)
    // ========================================================================

    /**
     * Returns the 5 possible output directions in priority order,
     * relative to the input face. Same logic as MachinePipeBlock.
     */
    private static Direction[] getOutputOrder(Direction inputFace) {
        return switch (inputFace) {
            case NORTH -> new Direction[]{Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
            case SOUTH -> new Direction[]{Direction.NORTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};
            case EAST -> new Direction[]{Direction.WEST, Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN};
            case WEST -> new Direction[]{Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN};
            case UP -> new Direction[]{Direction.DOWN, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
            case DOWN -> new Direction[]{Direction.UP, Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH};
        };
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        // No transit items to drop (fluid state is in the BE, not a slot item).
        // The bucket in slot 0 is handled by AbstractMachineBlock's drop logic.
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                FrameActivation.markDirty(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ========================================================================
    // COMPARATOR OUTPUT (0 = empty, 15 = has fluid)
    // ========================================================================

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineFluidPipeBlockEntity fluidPipe) {
            return fluidPipe.hasFluid() ? 15 : 0;
        }
        return 0;
    }
}
