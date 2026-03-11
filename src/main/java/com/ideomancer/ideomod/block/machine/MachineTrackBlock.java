package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.ideomancer.ideomod.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import com.ideomancer.ideomod.block.movement.BlockMoveHelper;

import java.util.List;

/**
 * Machine Track -- pushes adjacent Machine Trolleys on redstone signal.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class MachineTrackBlock extends Block implements EntityBlock, IMachineFamily {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final int EVENT_PUSH = 0;
    private static final ThreadLocal<Boolean> isUpdating = ThreadLocal.withInitial(() -> false);

    public MachineTrackBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    public MachineType getMachineType() { return MachineType.TRACK; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineFrameBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineFrameBlockEntity machineFrame) {
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, machineFrame, pos);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isStickyBlock(BlockState state) { return true; }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return IMachineFamily.canMachineFamilyStickTo(MachineType.TRACK, other);
    }

    @Override
    public boolean isSignalSource(BlockState state) { return false; }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 0; }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return 0; }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;
        if (isUpdating.get()) return;
        int oldPower = state.getValue(POWER);
        int newPower = calculatePower(level, pos);
        if (newPower != oldPower) {
            isUpdating.set(true);
            try {
                level.setBlock(pos, state.setValue(POWER, newPower), 2);
                level.updateNeighborsAt(pos, this);
            } finally {
                isUpdating.set(false);
            }
            if (oldPower == 0 && newPower > 0) scheduleAllPushes(level, pos, newPower);
        }
        FrameActivation.recalculate(level, pos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this)) {
            updatePowerState(level, pos, state);
            BlockMoveHelper.updateAllNeighbors(level, pos, this);
            FrameActivation.recalculate(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            BlockMoveHelper.updateAllNeighbors(level, pos, this);
            FrameActivation.recalculate(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                             @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity machineEntity) {
                ItemStack stored = machineEntity.getSpecializationItem();
                if (!stored.isEmpty()) MachineSwapHelper.checkAndSwap(level, pos, stored);
            }
        }
    }

    // ========================================================================
    // DROPS + CLONE
    // ========================================================================

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (MachineSwapHelper.isSwapping()) return drops;
        BlockEntity be = builder.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (be instanceof MachineFrameBlockEntity me && !me.getSpecializationItem().isEmpty()) {
            drops.clear();
            drops.add(MachineSwapHelper.createDrop(this, me));
        }
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target,
                                        BlockGetter level, BlockPos pos, Player player) {
        return MachineSwapHelper.createCloneStack(this);
    }

    // ========================================================================
    // REDSTONE INTERNALS
    // ========================================================================

    private void updatePowerState(Level level, BlockPos pos, BlockState state) {
        int newPower = calculatePower(level, pos);
        if (newPower != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, newPower), 2);
            level.updateNeighborsAt(pos, this);
        }
    }

    private int calculatePower(Level level, BlockPos pos) {
        int bestPower = 0;
        for (Direction dir : Direction.values()) {
            BlockPos nPos = pos.relative(dir);
            BlockState nState = level.getBlockState(nPos);
            Block nBlock = nState.getBlock();
            if (MachineFrameBlock.isRedstoneConduit(nBlock)) {
                int p = nState.getValue(POWER) - 1;
                if (p > bestPower) bestPower = p;
            } else if (isRedstoneComponent(nBlock)) {
                int s = level.getSignal(nPos, dir);
                if (s > bestPower) bestPower = s;
            }
        }
        return Math.min(15, Math.max(0, bestPower));
    }

    private static boolean isRedstoneComponent(Block block) {
        return block.defaultBlockState().is(ModTags.REDSTONE_COMPONENTS);
    }

    // ========================================================================
    // PUSH SCHEDULING
    // ========================================================================

    private void scheduleAllPushes(Level level, BlockPos pos, int myPower) {
        for (Direction signalDir : Direction.values()) {
            BlockPos nPos = pos.relative(signalDir);
            BlockState nState = level.getBlockState(nPos);
            Block nBlock = nState.getBlock();
            boolean isSource = false;
            if (MachineFrameBlock.isRedstoneConduit(nBlock)) {
                isSource = nState.getValue(POWER) > myPower;
            } else if (isRedstoneComponent(nBlock)) {
                isSource = level.getSignal(nPos, signalDir) > 0;
            }
            if (!isSource) continue;
            Direction pushDir = signalDir.getOpposite();
            for (Direction tFace : Direction.values()) {
                BlockPos tPos = pos.relative(tFace);
                if (level.getBlockState(tPos).getBlock() instanceof MachineTrolleyBlock) {
                    level.blockEvent(pos, this, EVENT_PUSH, packDirs(pushDir, tFace));
                }
            }
        }
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (id == EVENT_PUSH) {
            Direction pushDir = Direction.from3DDataValue(param & 0x7);
            Direction tFace = Direction.from3DDataValue((param >> 3) & 0x7);
            BlockPos tPos = pos.relative(tFace);
            if (!(level.getBlockState(tPos).getBlock() instanceof MachineTrolleyBlock)) return true;
            BlockMoveHelper.moveBlockAt(level, pos, tPos, pushDir);
            return true;
        }
        return false;
    }

    private static int packDirs(Direction a, Direction b) {
        return a.get3DDataValue() | (b.get3DDataValue() << 3);
    }
}
