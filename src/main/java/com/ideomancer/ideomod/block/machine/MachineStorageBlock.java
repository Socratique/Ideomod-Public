package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.ticks.TickPriority;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Machine Storage -- a Machine Base holding a chest or barrel: a 3x3 store that
 * moves with its contents, unlike a vanilla chest.
 *
 * A signal turning on, on any face, pushes out ONE item (The Ideomancer, 2026-11-08).
 * It takes turns between the faces that can accept items, and never pushes to a face
 * that has given it items, so a pipe feeding it is never fed back. Pipes stay push-only.
 *
 * Every new placement (piston moves included) settles for one tick before it listens:
 * a moving group lands block by block, and a signal block landing after this one would
 * otherwise look like a rising edge.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineStorageBlock extends AbstractMachineBlock {

    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;

    public MachineStorageBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TRIGGERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TRIGGERED);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.STORAGE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineStorageBlockEntity(pos, state);
    }

    // ========================================================================
    // SIGNAL
    // ========================================================================

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof MachineStorageBlockEntity storage)) return;
        if (storage.isPistonSettling()) return;

        storage.forgetGoneFeeders(level, pos);

        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(TRIGGERED)) return;
        level.setBlock(pos, state.setValue(TRIGGERED, powered), Block.UPDATE_INVISIBLE);
        if (powered) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof MachineStorageBlockEntity storage)) return;
        if (storage.isPistonSettling()) {
            // Settled: take the signal as it is now, without pushing.
            storage.setPistonSettling(false);
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(TRIGGERED)) {
                level.setBlock(pos, state.setValue(TRIGGERED, powered), Block.UPDATE_INVISIBLE);
            }
            return;
        }
        pushOneItem(level, pos, storage);
    }

    /** Pushes one item into the next face (in turn) that accepts one. Hopper order: first slot first. */
    private static void pushOneItem(Level level, BlockPos pos, MachineStorageBlockEntity storage) {
        storage.forgetGoneFeeders(level, pos);
        Direction[] faces = Direction.values();
        int start = storage.getRoundRobinIndex();
        for (int i = 0; i < faces.length; i++) {
            int index = (start + i) % faces.length;
            Direction face = faces[index];
            if (storage.wasFedFrom(face)) continue;

            BlockEntity target = level.getBlockEntity(pos.relative(face));
            if (target == null) continue;
            IItemHandler handler = target.getCapability(ForgeCapabilities.ITEM_HANDLER, face.getOpposite()).orElse(null);
            if (handler == null) continue;

            for (int slot = MachineStorageBlockEntity.FIRST_SLOT; slot < storage.getContainerSize(); slot++) {
                ItemStack stack = storage.getItem(slot);
                if (stack.isEmpty()) continue;
                ItemStack one = stack.copy();
                one.setCount(1);
                if (!ItemHandlerHelper.insertItemStacked(handler, one, false).isEmpty()) continue;

                stack.shrink(1);
                if (stack.isEmpty()) storage.setItem(slot, ItemStack.EMPTY);
                storage.setRoundRobinIndex((index + 1) % faces.length);
                storage.setChanged();
                // A pipe must know where its item came from, so it never sends it back.
                if (target instanceof MachinePipeBlockEntity pipe) pipe.onItemReceived(face.getOpposite());
                return;
            }
        }
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())
                && level.getBlockEntity(pos) instanceof MachineStorageBlockEntity storage) {
            storage.setPistonSettling(true);
            level.scheduleTick(pos, this, 1, TickPriority.HIGH);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Spill the contents only when really broken: a swap ejects them through
        // MachineSwapHelper, and a piston move carries them via IPistonMovable.
        if (!state.is(newState.getBlock()) && !MachineSwapHelper.isSwapping() && !movedByPiston
                && level.getBlockEntity(pos) instanceof MachineStorageBlockEntity storage) {
            for (int slot = MachineStorageBlockEntity.FIRST_SLOT; slot < storage.getContainerSize(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), storage.getItem(slot));
            }
            if (!level.isClientSide()) FrameActivation.markDirty(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ========================================================================
    // COMPARATOR (like a chest, over the nine storage slots)
    // ========================================================================

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MachineStorageBlockEntity storage)) return 0;
        float fullness = 0;
        boolean any = false;
        for (int slot = MachineStorageBlockEntity.FIRST_SLOT; slot < storage.getContainerSize(); slot++) {
            ItemStack stack = storage.getItem(slot);
            if (stack.isEmpty()) continue;
            any = true;
            fullness += (float) stack.getCount() / Math.min(storage.getMaxStackSize(), stack.getMaxStackSize());
        }
        return (int) Math.floor(fullness / MachineStorageBlockEntity.STORAGE_SIZE * 14.0F) + (any ? 1 : 0);
    }
}
