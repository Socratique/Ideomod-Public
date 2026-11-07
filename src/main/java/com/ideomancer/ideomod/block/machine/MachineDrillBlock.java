package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Machine Drill -- a Machine Base holding a pickaxe (Ideomancer, 2026-11-06).
 *
 * Like the Machine Piston it has no front: while a face has a redstone signal, the Drill
 * cuts the 3x3 of blocks on the opposite face, one block deep. Signals on several faces cut
 * several sides.
 *
 * It needs a constant signal (Ideomancer, 2026-11-07): when a face loses its signal, the cut
 * on that side stops and its cracks clear, as when a player lets go of the mouse. While the
 * signal lasts it keeps cutting whatever comes into the 3x3 (falling gravel, blocks put back,
 * a new wall after the Drill was moved).
 *
 * Each block is mined as a player would mine it with that pickaxe -- the same speed (tier,
 * Efficiency), showing the same crack overlay -- and breaks when its own time is up, so stone
 * goes before obsidian beside it. While any cut is running the Drill cannot be moved by
 * pistons (blockstate DRILLING; PistonBaseBlockMixin Feature 6), so a tunnel bore waits for
 * its drill.
 *
 * The pickaxe in the slot is the tool, used as a player would use it:
 *   - its tier decides what the Drill can cut: a block that needs a better pickaxe to drop
 *     anything is left in place (an iron pickaxe does not cut obsidian);
 *   - its enchantments apply to the drops (Fortune, Silk Touch), the speed (Efficiency)
 *     and the wear (Unbreaking);
 *   - every block cut costs it one point of durability. When it breaks (or is taken out),
 *     the cuts stop, the cracks clear, and the Drill turns back into a plain Machine Base.
 * Items drop at the Drill. Air, fluids and unbreakable blocks are skipped.
 *
 * All of this runs in the Drill's server tick, which reads the signal on each face itself
 * (so a Drill that arrives somewhere already powered starts at once). The cuts in progress
 * live only in memory: after a restart they start again from nothing.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineDrillBlock extends AbstractMachineBlock {

    /** True while a cut is running; the Drill cannot be pushed then. */
    public static final BooleanProperty DRILLING = BooleanProperty.create("drilling");

    /** One block being cut. */
    private static final class Cut {
        final Direction side;
        final BlockPos pos;
        final int crackId;
        BlockState state;
        int total;
        int elapsed;
        int stage = -1;

        Cut(Direction side, BlockPos pos, BlockState state, int total) {
            this.side = side;
            this.pos = pos;
            this.state = state;
            this.total = total;
            // a breaker id of our own per block, so all nine cracks show at once (entity ids are positive)
            this.crackId = -1 - (int) ((pos.asLong() * 0x9E3779B97F4A7C15L) >>> 33);
        }
    }

    /** Cuts in progress, per level, per Drill position. Server side only. */
    private static final Map<Level, Map<BlockPos, List<Cut>>> CUTS = new WeakHashMap<>();

    public MachineDrillBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(DRILLING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DRILLING);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.DRILL;
    }

    /** True for an item the Drill can work with: any pickaxe. */
    public static boolean isDrillTool(ItemStack stack) {
        return stack.getItem() instanceof PickaxeItem;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return state.getValue(DRILLING) ? PushReaction.BLOCK : PushReaction.NORMAL;
    }

    /** Two directions across the face, for the 3x3. */
    private static Direction[] across(Direction side) {
        return switch (side.getAxis()) {
            case X -> new Direction[]{Direction.UP, Direction.SOUTH};
            case Y -> new Direction[]{Direction.NORTH, Direction.EAST};
            case Z -> new Direction[]{Direction.UP, Direction.EAST};
        };
    }

    /** What this pickaxe can cut: not air, fluids or unbreakable blocks, nor blocks needing a better tier. */
    private static boolean cuttable(Level level, BlockPos pos, BlockState state, ItemStack pickaxe) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return !state.requiresCorrectToolForDrops() || pickaxe.isCorrectToolForDrops(state);
    }

    /**
     * Ticks to mine this block by hand with this pickaxe, as vanilla works it out for a player
     * standing on the ground with no potion effects: tool speed (plus Efficiency), divided by
     * hardness, divided by 30 when it drops something (100 when it does not).
     */
    static int mineTicks(Level level, BlockPos pos, BlockState state, ItemStack pickaxe) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0) return 1;
        float speed = pickaxe.getDestroySpeed(state);
        if (speed > 1.0f) {
            int efficiency = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, pickaxe);
            if (efficiency > 0) speed += efficiency * efficiency + 1;
        }
        boolean drops = !state.requiresCorrectToolForDrops() || pickaxe.isCorrectToolForDrops(state);
        float perTick = speed / hardness / (drops ? 30.0f : 100.0f);
        return Math.max(1, Mth.ceil(1.0f / perTick));
    }

    // ========================================================================
    // TICK: read the signal, take up new blocks, advance the cuts, break what is done
    // ========================================================================

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.MACHINE_FRAME.get()) return null;
        return (lvl, pos, st, be) -> serverTick(lvl, pos, st, (MachineFrameBlockEntity) be);
    }

    private static void serverTick(Level level, BlockPos pos, BlockState state, MachineFrameBlockEntity be) {
        ItemStack pickaxe = be.getSpecializationItem();
        boolean hasTool = isDrillTool(pickaxe);

        // which sides are being worked: the side opposite each face with a signal
        int sides = 0;
        if (hasTool) {
            for (Direction face : Direction.values()) {
                if (level.getSignal(pos.relative(face), face) > 0) sides |= 1 << face.getOpposite().get3DDataValue();
            }
        }

        Map<BlockPos, List<Cut>> here = CUTS.get(level);
        List<Cut> cuts = here == null ? null : here.get(pos);

        // a side that lost its signal (or a Drill with no pickaxe) stops, and its cracks clear
        if (cuts != null) {
            for (Cut c : new ArrayList<>(cuts)) {
                if ((sides & (1 << c.side.get3DDataValue())) == 0) {
                    clearCrack(level, c);
                    cuts.remove(c);
                }
            }
        }

        // a side with a signal takes up anything new in its 3x3
        if (sides != 0) {
            for (Direction side : Direction.values()) {
                if ((sides & (1 << side.get3DDataValue())) != 0) {
                    if (cuts == null) {
                        cuts = CUTS.computeIfAbsent(level, l -> new HashMap<>())
                                .computeIfAbsent(pos.immutable(), p -> new ArrayList<>());
                        here = CUTS.get(level);
                    }
                    takeUp(level, pos, side, pickaxe, cuts);
                }
            }
        }

        // advance the cuts (on a copy: breaking blocks can change things around us)
        if (cuts != null) {
            for (Cut c : new ArrayList<>(cuts)) {
                BlockState now = level.getBlockState(c.pos);
                if (now != c.state) { // something else is there now: start again on it, or give up on it
                    clearCrack(level, c);
                    if (!cuttable(level, c.pos, now, pickaxe)) {
                        cuts.remove(c);
                        continue;
                    }
                    c.state = now;
                    c.total = mineTicks(level, c.pos, now, pickaxe);
                    c.elapsed = 0;
                }
                c.elapsed++;
                if (c.elapsed < c.total) {
                    int stage = Math.min(9, 10 * c.elapsed / c.total);
                    if (stage != c.stage) {
                        level.destroyBlockProgress(c.crackId, c.pos, stage);
                        c.stage = stage;
                    }
                    continue;
                }
                clearCrack(level, c);
                cuts.remove(c);
                if (breakBlock((ServerLevel) level, pos, c.pos, pickaxe)) {
                    // the pickaxe broke: stop everything, empty the slot (the Drill becomes a Machine Base)
                    cancel(level, pos);
                    be.setItem(0, ItemStack.EMPTY);
                    return;
                }
                be.setChanged();
            }
            if (cuts.isEmpty() && here != null) here.remove(pos);
        }

        boolean drilling = cuts != null && !cuts.isEmpty();
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof MachineDrillBlock && st.getValue(DRILLING) != drilling) {
            level.setBlock(pos, st.setValue(DRILLING, drilling), Block.UPDATE_CLIENTS);
        }
    }

    /** Adds every cuttable block of the 3x3 on that side that is not being cut already. */
    private static void takeUp(Level level, BlockPos pos, Direction side, ItemStack pickaxe, List<Cut> cuts) {
        BlockPos centre = pos.relative(side);
        Direction[] across = across(side);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                BlockPos target = centre.relative(across[0], i).relative(across[1], j);
                boolean queued = false;
                for (Cut c : cuts) {
                    if (c.pos.equals(target)) { queued = true; break; }
                }
                if (queued) continue;
                BlockState state = level.getBlockState(target);
                if (cuttable(level, target, state, pickaxe)) {
                    cuts.add(new Cut(side, target.immutable(), state, mineTicks(level, target, state, pickaxe)));
                }
            }
        }
    }

    /** Breaks one block with the pickaxe and drops its items at the Drill. True if the pickaxe broke. */
    private static boolean breakBlock(ServerLevel level, BlockPos drillPos, BlockPos target, ItemStack pickaxe) {
        BlockState state = level.getBlockState(target);
        BlockEntity targetEntity = level.getBlockEntity(target);
        LootParams.Builder loot = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(target))
                .withParameter(LootContextParams.TOOL, pickaxe)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, targetEntity);
        List<ItemStack> drops = state.getDrops(loot);

        state.spawnAfterBreak(level, target, pickaxe, true); // ore experience, as mining by hand
        level.destroyBlock(target, false);

        Vec3 at = Vec3.atCenterOf(drillPos);
        for (ItemStack drop : drops) {
            ItemEntity item = new ItemEntity(level, at.x, at.y + 0.5, at.z, drop);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }

        // one point of wear per block (Unbreaking applies inside hurt)
        if (pickaxe.hurt(1, level.getRandom(), null)) {
            level.playSound(null, drillPos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8f,
                    0.8f + level.getRandom().nextFloat() * 0.4f);
            return true;
        }
        return false;
    }

    private static void clearCrack(Level level, Cut c) {
        if (c.stage >= 0) level.destroyBlockProgress(c.crackId, c.pos, -1);
        c.stage = -1;
    }

    /** Stops every cut of the Drill at pos and clears their cracks. */
    private static void cancel(Level level, BlockPos pos) {
        Map<BlockPos, List<Cut>> here = CUTS.get(level);
        if (here == null) return;
        List<Cut> cuts = here.remove(pos);
        if (cuts != null) {
            for (Cut c : cuts) clearCrack(level, c);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !newState.is(state.getBlock())) {
            cancel(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
