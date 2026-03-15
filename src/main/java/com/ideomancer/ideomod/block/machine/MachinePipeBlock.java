package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import com.ideomancer.ideomod.registry.ModBlockEntities;

/**
 * Machine Pipe -- push-only item transport block.
 *
 * BEHAVIOUR:
 * ==========
 * - Specialization item: Hopper in Machine Frame GUI slot
 * - Transit slot: holds 1 stack, visible in GUI
 * - Push only: items are pushed to adjacent containers at hopper speed (8 ticks)
 * - No pull: items enter via player hand, hopper, or another pipe pushing in
 * - Redstone locking: powered = frozen (like vanilla hoppers)
 * - Output priority: relative to input face -- forward, right, left, up, down
 * - Round-robin: cycles through valid outputs, resets when input face changes
 * - FILLED blockstate: true when transit slot has items, drives texture swap
 *
 * OUTPUT PRIORITY EXPLAINED:
 * ==========================
 * "Forward" = opposite of input face. "Right/left" are relative to looking
 * along the input direction (as if you're the item entering the pipe).
 * For horizontal input faces, up/down/right/left are intuitive.
 * For vertical input (UP/DOWN), we anchor to NORTH as "forward equivalent"
 * to give a consistent rotation basis.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachinePipeBlock extends AbstractMachineBlock {

    /** True when the transit slot contains items. Drives texture swap. */
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    public MachinePipeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.PIPE;
    }

    // ========================================================================
    // BLOCK ENTITY
    // ========================================================================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachinePipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide()) return null; // No client-side ticking needed
        return type == ModBlockEntities.MACHINE_PIPE.get()
                ? (lvl, pos, st, be) -> serverTick(lvl, pos, st, (MachinePipeBlockEntity) be)
                : null;
    }

    // ========================================================================
    // GUI (override to use pipe-specific menu)
    // GUI interaction inherited from AbstractMachineBlock.use()
    // The pipe BE's createMenu() returns the unified MachineFrameMenu,
    // which dynamically enables the transit slot when it detects a pipe BE.

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        // Redstone state is checked each tick, no blockstate update needed
    }

    // ========================================================================
    // SERVER TICK -- core pipe logic
    // ========================================================================

    /**
     * Server-side tick. Runs every game tick for active pipes.
     * Manages cooldown and attempts to push items when ready.
     */
    private static void serverTick(Level level, BlockPos pos, BlockState state,
                                    MachinePipeBlockEntity pipe) {
        // Decrement cooldown
        if (pipe.getTransferCooldown() > 0) {
            pipe.setTransferCooldown(pipe.getTransferCooldown() - 1);
        }

        // Don't transfer if locked by redstone
        if (level.hasNeighborSignal(pos)) {
            return;
        }

        // Attempt push when cooldown expires and we have items
        if (pipe.getTransferCooldown() <= 0 && pipe.hasTransitItem()) {
            if (tryPushItem(level, pos, pipe)) {
                pipe.setTransferCooldown(MachinePipeBlockEntity.TRANSFER_COOLDOWN);
                pipe.setChanged();
            }
        }
    }

    // ========================================================================
    // PUSH LOGIC
    // ========================================================================

    /**
     * Attempts to push one item from the transit slot to an adjacent container.
     * Uses round-robin output selection relative to the last input face.
     *
     * @return true if an item was successfully pushed
     */
    private static boolean tryPushItem(Level level, BlockPos pos, MachinePipeBlockEntity pipe) {
        Direction inputFace = pipe.getLastInputFace();
        if (inputFace == null) inputFace = Direction.UP; // Default: player hand

        Direction[] outputOrder = getOutputOrder(inputFace);
        int startIndex = pipe.getRoundRobinIndex();

        // Try each output direction in round-robin order
        for (int i = 0; i < outputOrder.length; i++) {
            int tryIndex = (startIndex + i) % outputOrder.length;
            Direction outputDir = outputOrder[tryIndex];

            // Don't push back to the input face
            if (outputDir == inputFace) continue;

            BlockPos targetPos = pos.relative(outputDir);
            BlockEntity targetBE = level.getBlockEntity(targetPos);
            if (targetBE == null) continue;

            // Get the item handler on the target's face that faces us
            Direction targetFace = outputDir.getOpposite();
            IItemHandler targetHandler = targetBE.getCapability(
                    ForgeCapabilities.ITEM_HANDLER, targetFace).orElse(null);
            if (targetHandler == null) continue;

            // Try to insert one item
            ItemStack toInsert = pipe.getTransitItem().copy();
            toInsert.setCount(1);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(targetHandler, toInsert, false);

            if (remainder.isEmpty()) {
                // Successfully pushed one item
                pipe.getTransitItem().shrink(1);
                if (pipe.getTransitItem().isEmpty()) {
                    pipe.setTransitItem(ItemStack.EMPTY);
                }
                // Advance round-robin to next position after the one we just used
                pipe.setRoundRobinIndex((tryIndex + 1) % outputOrder.length);
                pipe.setChanged();
                pipe.syncToClients(); // Update FILLED state on clients

                // Notify receiving pipe which face the item came from
                // so it knows not to push back to us
                if (targetBE instanceof MachinePipeBlockEntity targetPipe) {
                    targetPipe.onItemReceived(outputDir.getOpposite());
                }
                return true;
            }
        }

        return false; // No valid output found
    }

    // ========================================================================
    // OUTPUT DIRECTION CALCULATION
    // ========================================================================

    /**
     * Returns the 5 possible output directions in priority order,
     * relative to the input face.
     *
     * Order: forward (opposite of input), right, left, up, down
     * -- all relative to looking along the input direction.
     *
     * For horizontal input (NORTH/SOUTH/EAST/WEST):
     *   Forward = opposite of input
     *   Right/Left = clockwise/counterclockwise when viewed from above
     *   Up/Down = literal up/down
     *
     * For vertical input (UP = item entered from top, DOWN = from bottom):
     *   We need an arbitrary anchor. We use NORTH as the "forward" reference.
     *   UP input:   forward=DOWN, right=EAST, left=WEST, up=SOUTH, down=NORTH
     *   DOWN input: forward=UP,   right=EAST, left=WEST, up=NORTH, down=SOUTH
     */
    private static Direction[] getOutputOrder(Direction inputFace) {
        Direction forward = inputFace.getOpposite();

        switch (inputFace) {
            case NORTH:
                return new Direction[]{Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
            case SOUTH:
                return new Direction[]{Direction.NORTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};
            case EAST:
                return new Direction[]{Direction.WEST, Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN};
            case WEST:
                return new Direction[]{Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN};
            case UP:
                // Item entered from top. Forward = down, right = east, left = west
                return new Direction[]{Direction.DOWN, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
            case DOWN:
                // Item entered from bottom. Forward = up, right = east, left = west
                return new Direction[]{Direction.UP, Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH};
            default:
                return new Direction[]{forward, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
        }
    }

    // ========================================================================
    // DROPS (include transit item)
    // ========================================================================

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            // Drop transit items ONLY when block is actually broken/destroyed.
            // Skip during: swap (isSwapping), piston movement (movedByPiston).
            // Piston movement preserves items via IPistonMovable.saveMovementData().
            if (!MachineSwapHelper.isSwapping() && !movedByPiston) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MachinePipeBlockEntity pipeEntity && pipeEntity.hasTransitItem()) {
                    Block.popResource(level, pos, pipeEntity.getTransitItem().copy());
                    // Clear directly via removeItemNoUpdate to avoid updateFilledState
                    // calling level.setBlock() during removal (which would re-place the block)
                    pipeEntity.removeItemNoUpdate(MachinePipeBlockEntity.TRANSIT_SLOT);
                }
            }
            if (!level.isClientSide()) {
                FrameActivation.markDirty(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ========================================================================
    // COMPARATOR OUTPUT (signal strength based on transit slot fullness)
    // ========================================================================

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachinePipeBlockEntity pipeEntity) {
            ItemStack transit = pipeEntity.getTransitItem();
            if (transit.isEmpty()) return 0;
            // Scale: 1 item = 1, full stack = 15
            return Math.max(1, (int) Math.ceil(transit.getCount() * 15.0 / transit.getMaxStackSize()));
        }
        return 0;
    }
}
