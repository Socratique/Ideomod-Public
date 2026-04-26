package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Defines custom block tags for Ideomod.
 * 
 * Tags are named lists of blocks stored as JSON data files.
 * They can be extended by other mods or datapacks without
 * touching Java code.
 * 
 * Java code uses these TagKey references to check membership:
 *   state.is(ModTags.PORTAL_CORNER_BLOCKS)
 * 
 * The actual block lists live in:
 *   data/ideomod/tags/blocks/<tag_name>.json
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class ModTags {

    /**
     * Crafted mineral/metal blocks that can serve as Oddstone portal corners.
     * The combination of 4 corner blocks defines a portal's address.
     * 
     * Tag file: data/ideomod/tags/blocks/portal_corner_blocks.json
     */
    public static final TagKey<Block> PORTAL_CORNER_BLOCKS =
            BlockTags.create(new ResourceLocation(Ideomod.MOD_ID, "portal_corner_blocks"));

    /**
     * Blocks that can be wrapped in iron/copper frames via the framing recipe.
     * This is a WHITELIST -- only blocks in this tag are frameable.
     * Other mods or datapacks can add their blocks to this tag.
     *
     * Safety checks in FrameabilityHelper still apply on top of the tag:
     * blocks must not be air, liquid, already framed, or a frame block.
     * The tag replaces the old heuristic-based system (full-cube check,
     * no-block-entity check, etc.) with an explicit opt-in list.
     *
     * Tag file: data/ideomod/tags/blocks/frameable_blocks.json
     */
    public static final TagKey<Block> FRAMEABLE_BLOCKS =
            BlockTags.create(new ResourceLocation(Ideomod.MOD_ID, "frameable_blocks"));

    /**
     * Stone-family blocks that form the frame around furnace array multiblocks.
     * Data-driven via tag so datapacks can add/remove valid frame materials.
     *
     * Tag file: data/ideomod/tags/blocks/furnace_array_frame.json
     */
    public static final TagKey<Block> FURNACE_ARRAY_FRAME =
            BlockTags.create(new ResourceLocation(Ideomod.MOD_ID, "furnace_array_frame"));

    /**
     * Blocks that should be immovable when wrapped inside a FramedBlock.
     * PistonBaseBlockMixin checks this tag instead of hardcoding specific blocks.
     *
     * Default contents: obsidian, crying_obsidian, reinforced_deepslate.
     * Other mods or datapacks can add their own immovable blocks.
     *
     * Note: The destroySpeed == -1 check (bedrock, barriers, etc.) still runs
     * as a fallback for unlisted indestructible blocks. This tag is for blocks
     * like obsidian that ARE destructible but should still be immovable.
     *
     * Tag file: data/ideomod/tags/blocks/immovable_when_framed.json
     * (Audit item #20, added 2026-03-13)
     */
    public static final TagKey<Block> IMMOVABLE_WHEN_FRAMED =
            BlockTags.create(new ResourceLocation(Ideomod.MOD_ID, "immovable_when_framed"));

    /**
     * Item tags for Ideomod.
     */
    public static class Items {
        /**
         * Items that trigger the Machine Pulser specialization when inserted
         * into a Machine Frame. Contains all vanilla button items.
         *
         * Tag file: data/ideomod/tags/items/pulser_items.json
         */
        public static final TagKey<Item> PULSER_ITEMS =
                ItemTags.create(new ResourceLocation(Ideomod.MOD_ID, "pulser_items"));

        /**
         * Items that trigger the Machine Transmitter specialization when inserted
         * into a Machine Frame. Contains all Oddstone block items (oddstone,
         * cobbled oddstone, oddstone bricks, and all wall/stair/slab variants).
         *
         * Tag file: data/ideomod/tags/items/transmitter_items.json
         */
        public static final TagKey<Item> TRANSMITTER_ITEMS =
                ItemTags.create(new ResourceLocation(Ideomod.MOD_ID, "transmitter_items"));
    }
}
