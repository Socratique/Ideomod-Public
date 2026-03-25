package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.frame.FrameMaterial;
import com.ideomancer.ideomod.block.frame.FramedBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all mod items.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class ModItems {
    
    public static final DeferredRegister<Item> ITEMS = 
            DeferredRegister.create(ForgeRegistries.ITEMS, Ideomod.MOD_ID);
    
    public static final RegistryObject<Item> IDEOBLOCK = ITEMS.register("ideoblock",
            () -> new BlockItem(ModBlocks.IDEOBLOCK.get(), new Item.Properties()));
    
    public static final RegistryObject<Item> DRILL = ITEMS.register("drill",
            () -> new BlockItem(ModBlocks.DRILL.get(), new Item.Properties()));
    
    public static final RegistryObject<Item> ODDSTONE = ITEMS.register("oddstone",
            () -> new BlockItem(ModBlocks.ODDSTONE.get(), new Item.Properties()));
    
    public static final RegistryObject<Item> MACHINE_BLOCK = ITEMS.register("machine_block",
            () -> new BlockItem(ModBlocks.MACHINE_BLOCK.get(), new Item.Properties()));
    
    public static final RegistryObject<Item> ODD_SLIME = ITEMS.register("odd_slime",
            () -> new BlockItem(ModBlocks.ODD_SLIME.get(), new Item.Properties()));

    public static final RegistryObject<Item> ODDSTONE_REPULSER = ITEMS.register("oddstone_repulser",
            () -> new BlockItem(ModBlocks.ODDSTONE_REPULSER.get(), new Item.Properties()));

    public static final RegistryObject<Item> ODDSTONE_ATTRACTOR = ITEMS.register("oddstone_attractor",
            () -> new BlockItem(ModBlocks.ODDSTONE_ATTRACTOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_TRACK = ITEMS.register("machine_track",
            () -> new BlockItem(ModBlocks.MACHINE_TRACK.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_TROLLEY = ITEMS.register("machine_trolley",
            () -> new BlockItem(ModBlocks.MACHINE_TROLLEY.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_CONDUCTOR = ITEMS.register("machine_conductor",
            () -> new BlockItem(ModBlocks.MACHINE_CONDUCTOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_PIPE = ITEMS.register("machine_pipe",
            () -> new BlockItem(ModBlocks.MACHINE_PIPE.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_FLUID_PIPE = ITEMS.register("machine_fluid_pipe",
            () -> new BlockItem(ModBlocks.MACHINE_FLUID_PIPE.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_PISTON = ITEMS.register("machine_piston",
            () -> new BlockItem(ModBlocks.MACHINE_PISTON.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_STICKY_PISTON = ITEMS.register("machine_sticky_piston",
            () -> new BlockItem(ModBlocks.MACHINE_STICKY_PISTON.get(), new Item.Properties()));

    // No item for MACHINE_PISTON_HEAD -- it is not player-placeable.

    public static final RegistryObject<Item> MACHINE_BRACE = ITEMS.register("machine_brace",
            () -> new BlockItem(ModBlocks.MACHINE_BRACE.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_REPEATER = ITEMS.register("machine_repeater",
            () -> new BlockItem(ModBlocks.MACHINE_REPEATER.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_TOGGLE = ITEMS.register("machine_toggle",
            () -> new BlockItem(ModBlocks.MACHINE_TOGGLE.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_TRANSFORMER = ITEMS.register("machine_transformer",
            () -> new BlockItem(ModBlocks.MACHINE_TRANSFORMER.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_TIMER = ITEMS.register("machine_timer",
            () -> new BlockItem(ModBlocks.MACHINE_TIMER.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_PULSER = ITEMS.register("machine_pulser",
            () -> new BlockItem(ModBlocks.MACHINE_PULSER.get(), new Item.Properties()));

    public static final RegistryObject<Item> STRUCTURE_DEBUG_STICK = ITEMS.register(
            "structure_debug_stick", () -> new Item(new Item.Properties().stacksTo(1)));

    // ========================================================================
    // FRAME BLOCKS
    // ========================================================================

    public static final RegistryObject<Item> IRON_FRAME = ITEMS.register("iron_frame",
            () -> new BlockItem(ModBlocks.IRON_FRAME.get(), new Item.Properties()));

    public static final RegistryObject<Item> COPPER_FRAME = ITEMS.register("copper_frame",
            () -> new BlockItem(ModBlocks.COPPER_FRAME.get(), new Item.Properties()));

    // ========================================================================
    // FRAMED BLOCKS (custom BlockItem with NBT inner block data)
    // ========================================================================

    public static final RegistryObject<Item> IRON_FRAMED_BLOCK = ITEMS.register("iron_framed_block",
            () -> new FramedBlockItem(ModBlocks.IRON_FRAMED_BLOCK.get(),
                    FrameMaterial.IRON, new Item.Properties()));

    public static final RegistryObject<Item> COPPER_FRAMED_BLOCK = ITEMS.register("copper_framed_block",
            () -> new FramedBlockItem(ModBlocks.COPPER_FRAMED_BLOCK.get(),
                    FrameMaterial.COPPER, new Item.Properties()));
}
