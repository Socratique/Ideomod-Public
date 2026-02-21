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
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class ModCreativeTabs {
    
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = 
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Ideomod.MOD_ID);
    
    public static final RegistryObject<CreativeModeTab> IDEOMOD_TAB = CREATIVE_TABS.register("ideomod_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.IDEOBLOCK.get()))
                    .title(Component.translatable("itemGroup." + Ideomod.MOD_ID + ".ideomod_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.IDEOBLOCK.get());
                        output.accept(ModItems.DRILL.get());
                        output.accept(ModItems.ODDSTONE.get());
                        output.accept(ModItems.MACHINE_BLOCK.get());
                        output.accept(ModItems.ODD_SLIME.get());
                        output.accept(ModItems.ODDSTONE_REPULSER.get());
                        output.accept(ModItems.ODDSTONE_ATTRACTOR.get());
                        output.accept(ModItems.MACHINE_TRACK.get());
                        output.accept(ModItems.MACHINE_TROLLEY.get());

                        // Frame Blocks
                        output.accept(ModItems.IRON_FRAME.get());
                        output.accept(ModItems.COPPER_FRAME.get());

                        // Tools / Debug
                        output.accept(ModItems.STRUCTURE_DEBUG_STICK.get());
                    })
                    .build()
    );
}
