package com.ideomancer.ideomod.multiblock.definitions;

import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.multiblock.StructureDefinition;
import com.ideomancer.ideomod.multiblock.effects.FurnaceArrayEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import com.ideomancer.ideomod.multiblock.StructureManager;

/**
 * Factory class that builds the StructureDefinition for the Machine Furnace Array.
 *
 * All furnace-array-specific parameters are hardcoded here:
 * - Core blocks: furnace, blast_furnace, smoker
 * - Frame tag: #ideomod:furnace_array_frame
 * - Max cluster: 5×5
 * - Scan mode: PATTERN (rigid 3-layer frame)
 * - Effect handler: FurnaceArrayEffect (stamps NBT flags on furnaces)
 *
 * When Phase 2 moves to JSON definitions, this class gets replaced by a
 * JSON file in data/ideomod/structures/. The StructureDefinition it produces
 * would look the same to the framework.
 */
public class FurnaceArrayDefinition {

    public static final String NAME = "machine_furnace_array";

    /**
     * Block tag for valid frame materials.
     * Defined at: data/ideomod/tags/blocks/furnace_array_frame.json
     */
    public static final TagKey<Block> FRAME_TAG = BlockTags.create(
            new ResourceLocation("ideomod", "furnace_array_frame")
    );

    /**
     * Creates and returns the StructureDefinition for the furnace array.
     * Called once during mod init to register with the StructureManager.
     */
    public static StructureDefinition create() {
        return new StructureDefinition.Builder(NAME)
                .setScanMode(StructureDefinition.ScanMode.PATTERN)
                .addCoreBlock(new ResourceLocation("minecraft", "furnace"))
                .addCoreBlock(new ResourceLocation("minecraft", "blast_furnace"))
                .addCoreBlock(new ResourceLocation("minecraft", "smoker"))
                .setFrameTag(FRAME_TAG)
                .setMaxClusterSize(IdeomodConfig.getFurnaceArrayMaxWidth(), IdeomodConfig.getFurnaceArrayMaxHeight())
                .setEffectHandler(new FurnaceArrayEffect())
                .build();
    }

    // Private constructor — this is a static factory, not instantiated
    private FurnaceArrayDefinition() {}
}
