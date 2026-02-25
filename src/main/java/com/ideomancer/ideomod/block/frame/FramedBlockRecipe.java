package com.ideomancer.ideomod.block.frame;

import com.google.gson.JsonObject;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModItems;
import net.minecraft.core.RegistryAccess;
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
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlockRecipe extends CustomRecipe {

    public FramedBlockRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        MatchResult result = scanContainer(container);
        return result != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        MatchResult result = scanContainer(container);
        if (result == null) return ItemStack.EMPTY;

        // Determine which frame material
        Block frameBlock = ((BlockItem) result.frameStack.getItem()).getBlock();
        Item framedBlockItem;
        if (frameBlock == ModBlocks.IRON_FRAME.get()) {
            framedBlockItem = ModItems.IRON_FRAMED_BLOCK.get();
        } else {
            framedBlockItem = ModItems.COPPER_FRAMED_BLOCK.get();
        }

        // Get the inner block
        Block innerBlock = ((BlockItem) result.blockStack.getItem()).getBlock();

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
    // SHARED CONTAINER SCAN
    // ========================================================================

    /**
     * Scans the crafting container for exactly one frame item and one
     * frameable block item. Returns null if the grid doesn't match.
     *
     * Shared by matches() and assemble() to avoid duplicating the
     * same iteration logic.
     */
    @Nullable
    private static MatchResult scanContainer(CraftingContainer container) {
        ItemStack frameStack = ItemStack.EMPTY;
        ItemStack blockStack = ItemStack.EMPTY;
        int nonEmptyCount = 0;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            nonEmptyCount++;

            if (isFrameItem(stack)) {
                if (!frameStack.isEmpty()) return null; // Two frames
                frameStack = stack;
            } else if (isFrameableBlockItem(stack)) {
                if (!blockStack.isEmpty()) return null; // Two blocks
                blockStack = stack;
            } else {
                return null; // Unknown item
            }
        }

        if (nonEmptyCount == 2 && !frameStack.isEmpty() && !blockStack.isEmpty()) {
            return new MatchResult(frameStack, blockStack);
        }
        return null;
    }

    /**
     * Holds the two matched stacks from a successful container scan.
     */
    private record MatchResult(ItemStack frameStack, ItemStack blockStack) {}

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
