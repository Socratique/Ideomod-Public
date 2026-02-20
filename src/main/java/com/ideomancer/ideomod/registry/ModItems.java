package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all mod items
 * BlockItems are registered here to associate blocks with their item forms
 * 
 * Note: Oddstone Portal block does NOT get an item form.
 * It's only created by portal activation, never placed by hand.
 * Same as vanilla nether portal blocks.
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
    
    // No BlockItem for ODDSTONE_PORTAL - it's not placeable by players

    public static final RegistryObject<Item> ODDSTONE_REPULSER = ITEMS.register("oddstone_repulser",
            () -> new BlockItem(ModBlocks.ODDSTONE_REPULSER.get(), new Item.Properties()));

    public static final RegistryObject<Item> ODDSTONE_ATTRACTOR = ITEMS.register("oddstone_attractor",
            () -> new BlockItem(ModBlocks.ODDSTONE_ATTRACTOR.get(), new Item.Properties()));

    // ========================================================================
    // MACHINE TRACK / TROLLEY
    // ========================================================================

    public static final RegistryObject<Item> MACHINE_TRACK = ITEMS.register("machine_track",
            () -> new BlockItem(ModBlocks.MACHINE_TRACK.get(), new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_TROLLEY = ITEMS.register("machine_trolley",
            () -> new BlockItem(ModBlocks.MACHINE_TROLLEY.get(), new Item.Properties()));

    // ================================================================
    // Tools / Debug Items
    // ================================================================

    public static final RegistryObject<Item> STRUCTURE_DEBUG_STICK = ITEMS.register(
            "structure_debug_stick", () -> new Item(new Item.Properties().stacksTo(1)));
}
