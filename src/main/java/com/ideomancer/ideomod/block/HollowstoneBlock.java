package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Hollowstone — a decorative block created when lava meets Oddstone.
 *
 * Features:
 * - FILLED property: when false, displays dark opaque centers; when true,
 *   transparent centers reveal an inner decorative block rendered by BESR.
 * - Right-click with a block item to fill (cosmetic only, no behavior delegation).
 * - Mining drops 5-7 Hollowstone Shards (Fortune compatible, Silk Touch drops block).
 * - Breaking a filled Hollowstone leaves the inner block in-world.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class HollowstoneBlock extends Block implements EntityBlock {

    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    public HollowstoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }

    // ========================================================================
    // BLOCK ENTITY
    // ========================================================================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HollowstoneBlockEntity(pos, state);
    }

    // ========================================================================
    // RIGHT-CLICK TO FILL
    // ========================================================================

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(FILLED)) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || !(held.getItem() instanceof BlockItem blockItem)) {
            return InteractionResult.PASS;
        }

        Block innerBlock = blockItem.getBlock();

        // Don't allow block entity blocks (keep it simple like Framed Blocks)
        if (innerBlock.defaultBlockState().hasBlockEntity()) {
            return InteractionResult.PASS;
        }

        // Don't allow air
        if (innerBlock.defaultBlockState().isAir()) {
            return InteractionResult.PASS;
        }

        // Full cube blocks only. FIX 2026-10-22: buttons, levers, torches, slabs
        // and the like could be put inside, where they float in the middle.
        if (!innerBlock.defaultBlockState().isCollisionShapeFullBlock(level, pos)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            // Store inner block in block entity
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HollowstoneBlockEntity hollowBE) {
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(innerBlock);
                hollowBE.setInnerBlock(blockId);
            }

            // Set blockstate to FILLED=true
            level.setBlock(pos, state.setValue(FILLED, true), Block.UPDATE_ALL);

            // Consume item (not in creative)
            if (!player.isCreative()) {
                held.shrink(1);
            }

            // Play placement sound
            level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS,
                    1.0f, 1.0f);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // ========================================================================
    // MINING BEHAVIOR — FILLED BLOCK LEAVES INNER BLOCK IN-WORLD
    // ========================================================================
    //
    // Mined by a player, a filled Hollowstone leaves its inner block standing, except when it
    // drops itself (Silk Touch with a tool that harvests it), which keeps the inner block in
    // the item. The inner block goes back only after the drops: vanilla gives drops only if
    // the position is clear after the break, so placing it during the removal (as before
    // 2026-11-15) cost the player the shards. A break that drops nothing (wrong tool, creative)
    // puts the inner block back at once.
    //
    // Other removals (explosions, commands) still take the inner block with them (README:
    // Known rough edges).

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        BlockState inner = willHarvest || level.isClientSide() ? null : innerState(level.getBlockEntity(pos), state);
        boolean removed = super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        if (removed && inner != null) {
            level.setBlock(pos, inner, Block.UPDATE_ALL);
        }
        return removed;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);   // the drops
        boolean silkTouch = tool.getEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH) > 0;
        if (!level.isClientSide() && !silkTouch && level.getBlockState(pos).isAir()) {
            BlockState inner = innerState(blockEntity, state);
            if (inner != null) level.setBlock(pos, inner, Block.UPDATE_ALL);
        }
    }

    /** The inner block a filled Hollowstone would leave standing, or null. */
    @Nullable
    private static BlockState innerState(@Nullable BlockEntity be, BlockState state) {
        if (!state.getValue(FILLED) || !(be instanceof HollowstoneBlockEntity hollowBE) || !hollowBE.hasInnerBlock()) {
            return null;
        }
        Block innerBlock = hollowBE.getInnerBlock();
        return innerBlock == null ? null : innerBlock.defaultBlockState();
    }
}
