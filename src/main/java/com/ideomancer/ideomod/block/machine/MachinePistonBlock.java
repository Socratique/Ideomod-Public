package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.ideomancer.ideomod.block.movement.BlockMoveHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

import com.ideomancer.ideomod.registry.ModBlockEntities;

/**
 * Machine Piston -- omnidirectional piston as a Machine Family specialization.
 *
 * ANIMATION SYSTEM (BESR-based):
 * ==============================
 * Uses per-face animation state in MachineFrameBlockEntity rather than
 * vanilla's MovingPistonBlock system. A ticker advances progress, and
 * MachinePistonArmRenderer draws the arm at the interpolated offset.
 * When extension completes, the real MachinePistonHeadBlock is placed.
 * When retraction starts, the real head is removed immediately and
 * the animation is purely visual (arm sliding back in).
 *
 * Block pushing/pulling still uses BlockMoveHelper which handles
 * vanilla MovingPistonBlock for the PUSHED blocks (that works fine
 * for regular blocks).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachinePistonBlock extends AbstractMachineBlock {

    protected static final int EVENT_EXTEND = 0;
    protected static final int EVENT_RETRACT = 1;

    public MachinePistonBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.PISTON;
    }

    public boolean isSticky() {
        return false;
    }

    // ========================================================================
    // TICKER -- advances arm animation on both client and server
    // ========================================================================

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        return type == ModBlockEntities.MACHINE_FRAME.get()
                ? (lvl, pos, st, be) -> tickAnimation(lvl, pos, st, (MachineFrameBlockEntity) be)
                : null;
    }

    private static void tickAnimation(Level level, BlockPos pos, BlockState state,
                                       MachineFrameBlockEntity be) {
        if (!(state.getBlock() instanceof MachinePistonBlock pistonBlock)) return;

        boolean changed = false;
        for (Direction dir : Direction.values()) {
            int animDir = be.getArmAnimDirection(dir);
            if (animDir == 0) continue;

            float progress = be.getArmProgress(dir);
            be.setArmProgressO(dir, progress);

            progress += 0.5f; // 2 ticks to complete
            changed = true;

            if (progress >= 1.0f) {
                progress = 1.0f;
                be.setArmProgress(dir, progress);
                be.setArmAnimDirection(dir, 0);

                if (animDir > 0) {
                    // Extension complete -- place real head
                    BlockPos headPos = pos.relative(dir);
                    BlockState current = level.getBlockState(headPos);
                    if (current.isAir() || current.is(Blocks.MOVING_PISTON)) {
                        BlockState headState = pistonBlock.getHeadState(dir);
                        level.setBlock(headPos, headState, 3);
                    }
                }
                // Retraction: nothing to do, head already removed

                be.setArmProgress(dir, 0);
                be.setArmProgressO(dir, 0);
            } else {
                be.setArmProgress(dir, progress);
            }
        }
        if (changed) {
            be.setChanged();
        }
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    private static final ThreadLocal<Boolean> isProcessing = ThreadLocal.withInitial(() -> false);

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (isProcessing.get()) return;

        isProcessing.set(true);
        try {
            for (Direction signalFace : Direction.values()) {
                Direction extensionDir = signalFace.getOpposite();
                BlockPos headPos = pos.relative(extensionDir);
                boolean hasSignal = level.getSignal(pos.relative(signalFace), signalFace) > 0;
                boolean hasHead = isOurHead(level, headPos, extensionDir);

                // Skip if animation is in progress
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MachineFrameBlockEntity mbe && mbe.getArmAnimDirection(extensionDir) != 0) {
                    continue;
                }

                if (hasSignal && !hasHead) {
                    BlockState targetState = level.getBlockState(headPos);
                    if (canExtendInto(targetState)) {
                        level.blockEvent(pos, this, EVENT_EXTEND, extensionDir.get3DDataValue());
                    }
                } else if (!hasSignal && hasHead) {
                    level.blockEvent(pos, this, EVENT_RETRACT, extensionDir.get3DDataValue());
                }
            }
        } finally {
            isProcessing.set(false);
        }

        FrameActivation.markDirty(level, pos);
    }

    // ========================================================================
    // BLOCK EVENT
    // ========================================================================

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        Direction extensionDir = Direction.from3DDataValue(param);

        if (id == EVENT_EXTEND) {
            return doExtend(level, pos, extensionDir);
        } else if (id == EVENT_RETRACT) {
            return doRetract(level, pos, extensionDir);
        }
        return false;
    }

    // ========================================================================
    // EXTENSION
    // ========================================================================

    protected boolean doExtend(Level level, BlockPos pos, Direction extensionDir) {
        BlockPos headPos = pos.relative(extensionDir);
        boolean pushedBlocks = false;

        BlockState targetState = level.getBlockState(headPos);
        if (!targetState.isAir() && !targetState.is(Blocks.MOVING_PISTON)) {
            if (targetState.getPistonPushReaction() == PushReaction.DESTROY) {
                level.destroyBlock(headPos, true);
            } else if (targetState.getPistonPushReaction() != PushReaction.BLOCK) {
                boolean pushed = BlockMoveHelper.moveBlockAt(level, pos, headPos, extensionDir);
                if (!pushed) return false;
                pushedBlocks = true;
            } else {
                return false;
            }
        }

        // Start extension animation
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineFrameBlockEntity mbe) {
            mbe.setArmProgress(extensionDir, 0);
            mbe.setArmProgressO(extensionDir, 0);
            mbe.setArmAnimDirection(extensionDir, 1);
            mbe.setChanged();
        }

        if (!pushedBlocks) {
            level.playSound(null, pos, SoundEvents.PISTON_EXTEND,
                    SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.25F + 0.6F);
        }

        return true;
    }

    // ========================================================================
    // RETRACTION
    // ========================================================================

    protected boolean doRetract(Level level, BlockPos pos, Direction extensionDir) {
        BlockPos headPos = pos.relative(extensionDir);

        if (!isOurHead(level, headPos, extensionDir)) return false;

        boolean pulledBlocks = false;

        if (isSticky()) {
            BlockPos pullPos = headPos.relative(extensionDir);
            BlockState pullState = level.getBlockState(pullPos);
            if (!pullState.isAir() && pullState.getPistonPushReaction() != PushReaction.DESTROY
                    && pullState.getPistonPushReaction() != PushReaction.BLOCK
                    && !pullState.is(Blocks.MOVING_PISTON)) {
                level.setBlock(headPos, Blocks.AIR.defaultBlockState(), 18);
                BlockMoveHelper.moveBlockAt(level, pos, pullPos, extensionDir.getOpposite());
                pulledBlocks = true;
            } else {
                level.setBlock(headPos, Blocks.AIR.defaultBlockState(), 3);
            }
        } else {
            level.setBlock(headPos, Blocks.AIR.defaultBlockState(), 3);
        }

        // Start retraction animation (purely visual -- head already removed)
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineFrameBlockEntity mbe) {
            mbe.setArmProgress(extensionDir, 0);
            mbe.setArmProgressO(extensionDir, 0);
            mbe.setArmAnimDirection(extensionDir, -1);
            mbe.setChanged();
        }

        if (!pulledBlocks) {
            level.playSound(null, pos, SoundEvents.PISTON_CONTRACT,
                    SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.15F + 0.6F);
        }

        return true;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    protected BlockState getHeadState(Direction extensionDir) {
        return com.ideomancer.ideomod.registry.ModBlocks.MACHINE_PISTON_HEAD.get()
                .defaultBlockState()
                .setValue(MachinePistonHeadBlock.FACING, extensionDir)
                .setValue(MachinePistonHeadBlock.STICKY, isSticky());
    }

    protected boolean isOurHead(Level level, BlockPos headPos, Direction extensionDir) {
        BlockState headState = level.getBlockState(headPos);
        if (headState.getBlock() instanceof MachinePistonHeadBlock) {
            return headState.getValue(MachinePistonHeadBlock.FACING) == extensionDir;
        }
        return false;
    }

    private boolean canExtendInto(BlockState state) {
        if (state.isAir()) return true;
        if (state.getPistonPushReaction() == PushReaction.DESTROY) return true;
        if (state.getPistonPushReaction() == PushReaction.BLOCK) return false;
        return true;
    }

    public static boolean hasExtendedHead(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof MachinePistonHeadBlock) {
                if (neighborState.getValue(MachinePistonHeadBlock.FACING) == dir) {
                    return true;
                }
            }
        }
        return false;
    }

    public void onHeadBroken(Level level, BlockPos pistonPos, Direction extensionDir) {
        Ideomod.LOGGER.debug("MachinePiston at {} -- head broken on {} face", pistonPos, extensionDir);
    }
}
