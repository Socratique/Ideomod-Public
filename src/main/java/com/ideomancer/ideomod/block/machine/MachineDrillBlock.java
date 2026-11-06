package com.ideomancer.ideomod.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Machine Drill -- a Machine Base holding a pickaxe (Ideomancer, 2026-11-06).
 *
 * Like the Machine Piston it has no front: a redstone signal arriving on one face
 * mines the 3x3 of blocks on the opposite face, one block deep, once per rising edge.
 * Signals on several faces drill several sides.
 *
 * The pickaxe in the slot is the tool, used as a player would use it:
 *   - its tier decides what the Drill can cut: a block that needs a better pickaxe
 *     to drop anything is left in place (an iron pickaxe does not cut obsidian);
 *   - its enchantments apply to the drops (Fortune, Silk Touch) and to its wear (Unbreaking);
 *   - every block cut costs it one point of durability. When it breaks, the slot empties
 *     and the Drill turns back into a plain Machine Base.
 * Items drop at the Drill. Air, fluids and unbreakable blocks are skipped.
 *
 * Which faces had a signal is kept in the blockstate (SIGNAL_FACES, one bit per face),
 * so the memory travels with the Drill when a piston moves it.
 *
 * Replaces the old standalone Drill block (the reason Machine Blocks and frames exist:
 * a slime flying machine cannot carry a 3x3 bore through a tunnel).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineDrillBlock extends AbstractMachineBlock {

    /** Faces that had a redstone signal at the last check: bit = Direction.get3DDataValue(). */
    public static final IntegerProperty SIGNAL_FACES = IntegerProperty.create("signal_faces", 0, 63);

    public MachineDrillBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SIGNAL_FACES, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIGNAL_FACES);
    }

    @Override
    public MachineType getMachineType() {
        return MachineType.DRILL;
    }

    /** True for an item the Drill can work with: any pickaxe. */
    public static boolean isDrillTool(ItemStack stack) {
        return stack.getItem() instanceof PickaxeItem;
    }

    // ========================================================================
    // SIGNAL: a rising edge on one face drills the opposite side
    // ========================================================================

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide()) return;

        BlockState now = level.getBlockState(pos);
        if (now.getBlock() != this) return; // swapped away (e.g. by the super call)

        int old = now.getValue(SIGNAL_FACES);
        int mask = 0;
        for (Direction face : Direction.values()) {
            if (level.getSignal(pos.relative(face), face) > 0) mask |= 1 << face.get3DDataValue();
        }
        if (mask == old) return;
        level.setBlock(pos, now.setValue(SIGNAL_FACES, mask), Block.UPDATE_CLIENTS);

        int rising = mask & ~old;
        for (Direction face : Direction.values()) {
            if ((rising & (1 << face.get3DDataValue())) != 0) {
                if (!drill((ServerLevel) level, pos, face.getOpposite())) return; // pickaxe gone
            }
        }
    }

    /**
     * Mines the 3x3 on the given side. Returns false once the Drill has no pickaxe left
     * (it broke), so the caller stops.
     */
    private static boolean drill(ServerLevel level, BlockPos pos, Direction side) {
        BlockPos centre = pos.relative(side);
        Direction[] across = across(side);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (!(level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be)) return false;
                ItemStack pickaxe = be.getSpecializationItem();
                if (!isDrillTool(pickaxe)) return false;
                cut(level, pos, centre.relative(across[0], i).relative(across[1], j), be, pickaxe);
            }
        }
        return level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be
                && isDrillTool(be.getSpecializationItem());
    }

    /** Two directions across the face, for the 3x3. */
    private static Direction[] across(Direction side) {
        return switch (side.getAxis()) {
            case X -> new Direction[]{Direction.UP, Direction.SOUTH};
            case Y -> new Direction[]{Direction.NORTH, Direction.EAST};
            case Z -> new Direction[]{Direction.UP, Direction.EAST};
        };
    }

    private static void cut(ServerLevel level, BlockPos drillPos, BlockPos target,
                            MachineFrameBlockEntity be, ItemStack pickaxe) {
        BlockState state = level.getBlockState(target);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return;
        if (state.getDestroySpeed(level, target) < 0) return;               // bedrock, barriers, moving pistons
        if (state.requiresCorrectToolForDrops() && !pickaxe.isCorrectToolForDrops(state)) return; // needs a better tier

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
            level.playSound(null, drillPos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8f, 0.8f + level.getRandom().nextFloat() * 0.4f);
            be.setItem(0, ItemStack.EMPTY); // empty slot: the Drill becomes a Machine Base
        } else {
            be.setChanged();
        }
    }
}
