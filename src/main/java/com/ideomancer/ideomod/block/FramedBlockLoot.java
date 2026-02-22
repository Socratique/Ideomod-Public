package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Helper for Framed Block drops.
 *
 * Framed blocks drop themselves with their inner block NBT intact.
 * This is "permanent framing" — no decrafting. The drop is the same
 * framed block item you'd get from creative or crafting.
 *
 * Called from the loot table via the copy_nbt loot function, OR
 * we can use Block.getDrops override. Since loot tables for framed
 * blocks use the "copy_nbt" approach, this class provides a static
 * helper for manual drop creation if needed.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlockLoot {

    /**
     * Create the correct drop ItemStack for a framed block entity.
     * Used when we need to manually create drops (e.g. explosion handling).
     *
     * @param be The FramedBlockEntity being broken
     * @return ItemStack with proper inner block NBT
     */
    public static ItemStack createDrop(FramedBlockEntity be) {
        if (be == null) return ItemStack.EMPTY;

        // Determine if iron or copper
        boolean isIron = false;
        if (be.getBlockState().getBlock() == ModBlocks.IRON_FRAMED_BLOCK.get()) {
            isIron = true;
        }

        net.minecraft.world.item.Item item = isIron
                ? ModItems.IRON_FRAMED_BLOCK.get()
                : ModItems.COPPER_FRAMED_BLOCK.get();

        ResourceLocation innerBlockId = be.getInnerBlockId();
        if (innerBlockId == null) {
            return new ItemStack(item);
        }

        return FramedBlockItem.createStack(item, innerBlockId);
    }
}
