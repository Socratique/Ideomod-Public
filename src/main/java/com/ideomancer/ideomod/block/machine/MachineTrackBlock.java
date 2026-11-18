package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.ideomancer.ideomod.block.movement.BlockMoveHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Machine Track -- carries a pulse along a line of Tracks and pushes Trolleys as it passes.
 *
 * A Track never stays on. When a redstone signal reaching it rises (a face that had no signal now
 * has one), it starts a pulse. A pulse travels one Track per tick to every neighbouring Track except
 * the one it came from, with no limit on length. Each Track it reaches pushes every Trolley touching
 * it one block, away from the side the pulse came from, and flashes its grey indicator (POWER 15,
 * fading by 5 a tick), so a pulse shows as a bright spot with a short tail running down the line.
 *
 * Every pulse has an id, and a Track ignores a pulse it has already passed on: a loop of Tracks, or
 * two branches that meet again, cannot send a pulse round forever. At one Track per tick the pulse
 * outruns the Trolley it pushed (which is still moving when the pulse reaches the next Track), so a
 * Trolley moves one block per pulse.
 *
 * Signal output: NONE. isSignalSource=false, getSignal=0, getDirectSignal=0. POWER is only the
 * indicator, invisible to vanilla redstone.
 *
 * Until 2026-11-18 a Track held a signal that dropped by 1 per Track, like redstone dust, which
 * limited a line to 15 Tracks; the Ideomancer's original idea was a Track that goes by the direction
 * the signal comes from, not its strength.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class MachineTrackBlock extends AbstractMachineBlock {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final int EVENT_PUSH = 0;
    /** How much the indicator dims each tick after a pulse passes (15, 10, 5, off). */
    private static final int FADE_STEP = 5;

    /** Per world: the last pulse id each Track passed on, and the pulse each Track will pass on next tick. */
    private static final Map<Level, Map<Long, Long>> SEEN = new WeakHashMap<>();
    private static final Map<Level, Map<Long, Pending>> PENDING = new WeakHashMap<>();

    private record Pending(long id, Direction from) {}

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
        int now = redstoneInputs(level, pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineFrameBlockEntity machine) {
            int before = machine.getTrackInputs();
            machine.setTrackInputs(now);
            int rising = before < 0 ? 0 : now & ~before;   // -1: first look after an upgrade, record only
            if (rising != 0) {
                Direction from = Direction.from3DDataValue(Integer.numberOfTrailingZeros(rising));
                receivePulse(level, pos, from, level.getGameTime() * 31 + pos.asLong());
            }
        }
        FrameActivation.markDirty(level, pos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.isClientSide() || oldState.is(this)) return;
        // A Track set down next to a signal that is already on does not pulse: only a rising edge does.
        if (level.getBlockEntity(pos) instanceof MachineFrameBlockEntity machine) {
            machine.setTrackInputs(redstoneInputs(level, pos));
        }
        // During piston movement blocks are removed and put back; skip the neighbour updates then.
        if (!movedByPiston) {
            BlockMoveHelper.updateAllNeighbors(level, pos, this);
            FrameActivation.markDirty(level, pos);
        }
        if (state.getValue(POWER) > 0) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            forget(level, pos);
            if (!movedByPiston) {
                BlockMoveHelper.updateAllNeighbors(level, pos, this);
                FrameActivation.markDirty(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ========================================================================
    // PULSES
    // ========================================================================

    /** The faces (one bit per Direction) where a non-Track neighbour gives this Track a redstone signal. */
    private static int redstoneInputs(Level level, BlockPos pos) {
        int mask = 0;
        for (Direction dir : Direction.values()) {
            BlockPos nPos = pos.relative(dir);
            if (level.getBlockState(nPos).getBlock() instanceof MachineTrackBlock) continue;
            if (level.getSignal(nPos, dir) > 0) mask |= 1 << dir.get3DDataValue();
        }
        return mask;
    }

    /**
     * A pulse reaches this Track from the side {@code from}: push the Trolleys touching it away from
     * that side, light the indicator, and pass the pulse on next tick. A pulse it has already passed
     * on is ignored.
     */
    private void receivePulse(Level level, BlockPos pos, Direction from, long id) {
        Map<Long, Long> seen = SEEN.computeIfAbsent(level, l -> new HashMap<>());
        Long last = seen.put(pos.asLong(), id);
        if (last != null && last == id) return;
        Direction pushDir = from.getOpposite();
        for (Direction tFace : Direction.values()) {
            if (level.getBlockState(pos.relative(tFace)).getBlock() instanceof MachineTrolleyBlock) {
                level.blockEvent(pos, this, EVENT_PUSH, packDirs(pushDir, tFace));
            }
        }
        BlockState state = level.getBlockState(pos);
        if (state.getValue(POWER) != 15) level.setBlock(pos, state.setValue(POWER, 15), 2);
        PENDING.computeIfAbsent(level, l -> new HashMap<>()).put(pos.asLong(), new Pending(id, from));
        level.scheduleTick(pos, this, 1);
    }

    /** Next tick: pass a waiting pulse on to the neighbouring Tracks, and dim the indicator a step. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Map<Long, Pending> pending = PENDING.get(level);
        Pending p = pending == null ? null : pending.remove(pos.asLong());
        if (p != null) {
            for (Direction dir : Direction.values()) {
                if (dir == p.from()) continue;
                BlockPos nPos = pos.relative(dir);
                if (level.getBlockState(nPos).getBlock() instanceof MachineTrackBlock track) {
                    track.receivePulse(level, nPos, dir.getOpposite(), p.id());
                }
            }
        }
        BlockState now = level.getBlockState(pos);
        if (!now.is(this)) return;
        int power = now.getValue(POWER);
        if (pending != null && pending.containsKey(pos.asLong())) return;  // a new pulse just lit it again
        if (power > 0) {
            int dimmer = Math.max(0, power - FADE_STEP);
            level.setBlock(pos, now.setValue(POWER, dimmer), 2);
            if (dimmer > 0) level.scheduleTick(pos, this, 1);
        }
    }

    private static void forget(Level level, BlockPos pos) {
        Map<Long, Long> seen = SEEN.get(level);
        if (seen != null) seen.remove(pos.asLong());
        Map<Long, Pending> pending = PENDING.get(level);
        if (pending != null) pending.remove(pos.asLong());
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
