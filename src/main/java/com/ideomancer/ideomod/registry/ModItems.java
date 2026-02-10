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
 */
public class ModItems {
    
    // Create the DeferredRegister for items
    public static final DeferredRegister<Item> ITEMS = 
            DeferredRegister.create(ForgeRegistries.ITEMS, Ideomod.MOD_ID);
    
    // Register the BlockItem for Ideoblock
    // This allows the block to exist as an item in inventory
    public static final RegistryObject<Item> IDEOBLOCK = ITEMS.register("ideoblock",
            () -> new BlockItem(ModBlocks.IDEOBLOCK.get(), new Item.Properties()));
    
    // Register the BlockItem for Drill
    public static final RegistryObject<Item> DRILL = ITEMS.register("drill",
            () -> new BlockItem(ModBlocks.DRILL.get(), new Item.Properties()));
    
    // Register the BlockItem for Oddstone
    public static final RegistryObject<Item> ODDSTONE = ITEMS.register("oddstone",
            () -> new BlockItem(ModBlocks.ODDSTONE.get(), new Item.Properties()));
}
