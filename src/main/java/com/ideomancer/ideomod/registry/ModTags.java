package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
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
     * Blocks that are "real" redstone signal sources.
     * Machine Block only accepts redstone signal from blocks in this tag,
     * preventing feedback loops with generic powered solid blocks.
     * 
     * Tag file: data/ideomod/tags/blocks/redstone_components.json
     */
    public static final TagKey<Block> REDSTONE_COMPONENTS =
            BlockTags.create(new ResourceLocation(Ideomod.MOD_ID, "redstone_components"));
}
