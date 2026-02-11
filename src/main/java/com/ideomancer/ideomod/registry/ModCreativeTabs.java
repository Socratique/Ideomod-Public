package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for creative mode tabs
 * Creates a custom tab for all Ideomod items
 */
public class ModCreativeTabs {
    
    // Create the DeferredRegister for creative tabs
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = 
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Ideomod.MOD_ID);
    
    // Register our main creative tab
    public static final RegistryObject<CreativeModeTab> IDEOMOD_TAB = CREATIVE_TABS.register("ideomod_tab",
            () -> CreativeModeTab.builder()
                    // The tab icon - using our Ideoblock
                    .icon(() -> new ItemStack(ModItems.IDEOBLOCK.get()))
                    // The tab title - uses translation key
                    .title(Component.translatable("itemGroup." + Ideomod.MOD_ID + ".ideomod_tab"))
                    // Add all our items to this tab
                    .displayItems((parameters, output) -> {
                        // Blocks
                        output.accept(ModItems.IDEOBLOCK.get());
                        output.accept(ModItems.DRILL.get());
                        output.accept(ModItems.ODDSTONE.get());
                        output.accept(ModItems.MACHINE_BLOCK.get());
                        
                        // Add more items here as we create them
                    })
                    .build()
    );
}
