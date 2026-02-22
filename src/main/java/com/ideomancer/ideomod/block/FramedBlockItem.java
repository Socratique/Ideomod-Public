package com.ideomancer.ideomod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Custom BlockItem for Framed Blocks.
 *
 * Carries the inner block ID as NBT data ({@code BlockEntityTag.InnerBlock}).
 * Generates a dynamic display name by combining the frame type with the
 * inner block's name (e.g. "Iron Framed Stone", "Copper Framed Redstone Block").
 *
 * On placement, the standard BlockItem + EntityBlock flow automatically creates
 * the block entity. The BlockEntityTag in the item's NBT is applied to the
 * fresh block entity by vanilla's placement logic, so the inner block data
 * transfers to the world automatically.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlockItem extends BlockItem {

    private final FrameMaterial frameMaterial;

    public FramedBlockItem(Block block, FrameMaterial material, Item.Properties properties) {
        super(block, properties);
        this.frameMaterial = material;
    }

    // ========================================================================
    // DYNAMIC DISPLAY NAME
    // ========================================================================

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation innerBlockId = getInnerBlockId(stack);
        if (innerBlockId != null) {
            Block innerBlock = BuiltInRegistries.BLOCK.get(innerBlockId);
            if (innerBlock != Blocks.AIR) {
                String frameName = frameMaterial == FrameMaterial.IRON ? "Iron" : "Copper";
                Component innerName = innerBlock.getName();
                return Component.literal(frameName + " Framed " + innerName.getString());
            }
        }
        // Fallback: use the translation key (generic name)
        return super.getName(stack);
    }

    // ========================================================================
    // NBT HELPERS
    // ========================================================================

    /**
     * Get the inner block's ResourceLocation from an item stack's NBT.
     *
     * The data lives at: stack -> tag -> BlockEntityTag -> InnerBlock
     *
     * @return inner block ID, or null if not set
     */
    @Nullable
    public static ResourceLocation getInnerBlockId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("BlockEntityTag")) {
            CompoundTag beTag = tag.getCompound("BlockEntityTag");
            if (beTag.contains(FramedBlockEntity.TAG_INNER_BLOCK)) {
                return new ResourceLocation(beTag.getString(FramedBlockEntity.TAG_INNER_BLOCK));
            }
        }
        return null;
    }

    /**
     * Create an ItemStack for this framed block item with the given inner block.
     *
     * @param framedBlockItem The FramedBlockItem (iron or copper)
     * @param innerBlock      The block to wrap
     * @return Configured ItemStack with proper NBT
     */
    public static ItemStack createStack(Item framedBlockItem, Block innerBlock) {
        ItemStack stack = new ItemStack(framedBlockItem);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(innerBlock);

        CompoundTag beTag = new CompoundTag();
        beTag.putString(FramedBlockEntity.TAG_INNER_BLOCK, blockId.toString());

        CompoundTag itemTag = stack.getOrCreateTag();
        itemTag.put("BlockEntityTag", beTag);

        return stack;
    }

    /**
     * Create an ItemStack for this framed block item with the given inner block ID.
     *
     * @param framedBlockItem The FramedBlockItem (iron or copper)
     * @param innerBlockId    The inner block's registry name
     * @return Configured ItemStack with proper NBT
     */
    public static ItemStack createStack(Item framedBlockItem, ResourceLocation innerBlockId) {
        ItemStack stack = new ItemStack(framedBlockItem);

        CompoundTag beTag = new CompoundTag();
        beTag.putString(FramedBlockEntity.TAG_INNER_BLOCK, innerBlockId.toString());

        CompoundTag itemTag = stack.getOrCreateTag();
        itemTag.put("BlockEntityTag", beTag);

        return stack;
    }
}
