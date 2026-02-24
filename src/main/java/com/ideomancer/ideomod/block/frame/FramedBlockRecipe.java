package com.ideomancer.ideomod.block.frame;

import com.google.gson.JsonObject;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import com.ideomancer.ideomod.registry.ModRecipeSerializers;

/**
 * Special shapeless recipe: Frame Block + any frameable block → Framed Block with NBT.
 *
 * Matches when the crafting grid contains exactly:
 *   - 1 Frame Block item (iron_frame or copper_frame)
 *   - 1 BlockItem whose block passes FrameabilityHelper.isFrameable()
 *   - Nothing else
 *
 * The output is the corresponding Framed Block item (iron or copper) with
 * BlockEntityTag.InnerBlock set to the frameable block's registry name.
 *
 * This is a CustomRecipe (special recipe) — registered as a recipe type with
 * a simple JSON trigger file. No per-combination recipe JSONs needed.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlockRecipe extends CustomRecipe {

    public FramedBlockRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        ItemStack frameStack = ItemStack.EMPTY;
        ItemStack blockStack = ItemStack.EMPTY;
        int nonEmptyCount = 0;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            nonEmptyCount++;

            if (isFrameItem(stack)) {
                if (!frameStack.isEmpty()) return false; // Two frames
                frameStack = stack;
            } else if (isFrameableBlockItem(stack)) {
                if (!blockStack.isEmpty()) return false; // Two blocks
                blockStack = stack;
            } else {
                return false; // Unknown item
            }
        }

        return nonEmptyCount == 2 && !frameStack.isEmpty() && !blockStack.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack frameStack = ItemStack.EMPTY;
        ItemStack blockStack = ItemStack.EMPTY;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (isFrameItem(stack)) {
                frameStack = stack;
            } else if (isFrameableBlockItem(stack)) {
                blockStack = stack;
            }
        }

        if (frameStack.isEmpty() || blockStack.isEmpty()) return ItemStack.EMPTY;

        // Determine which frame material
        Block frameBlock = ((BlockItem) frameStack.getItem()).getBlock();
        Item framedBlockItem;
        if (frameBlock == ModBlocks.IRON_FRAME.get()) {
            framedBlockItem = ModItems.IRON_FRAMED_BLOCK.get();
        } else {
            framedBlockItem = ModItems.COPPER_FRAMED_BLOCK.get();
        }

        // Get the inner block
        Block innerBlock = ((BlockItem) blockStack.getItem()).getBlock();

        return FramedBlockItem.createStack(framedBlockItem, innerBlock);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.FRAMED_BLOCK.get();
    }

    // ========================================================================
    // ITEM TYPE CHECKS
    // ========================================================================

    private static boolean isFrameItem(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        return block == ModBlocks.IRON_FRAME.get() || block == ModBlocks.COPPER_FRAME.get();
    }

    private static boolean isFrameableBlockItem(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        return FrameabilityHelper.isFrameable(blockItem.getBlock());
    }

    // ========================================================================
    // SERIALIZER — simple, no JSON fields needed
    // ========================================================================

    public static class Serializer implements RecipeSerializer<FramedBlockRecipe> {

        @Override
        public FramedBlockRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new FramedBlockRecipe(id, CraftingBookCategory.MISC);
        }

        @Override
        public @Nullable FramedBlockRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new FramedBlockRecipe(id, CraftingBookCategory.MISC);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, FramedBlockRecipe recipe) {
            // Nothing to write — the recipe is fully dynamic
        }
    }
}
