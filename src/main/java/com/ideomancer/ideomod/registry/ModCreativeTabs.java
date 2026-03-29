package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.frame.FrameabilityHelper;
import com.ideomancer.ideomod.block.frame.FramedBlockItem;
import com.ideomancer.ideomod.block.machine.MachineSwapHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for creative mode tabs.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class ModCreativeTabs {

    private static List<Block> cachedFrameableBlocks = null;

    private static List<Block> getFrameableBlocks() {
        if (cachedFrameableBlocks == null) {
            cachedFrameableBlocks = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (FrameabilityHelper.isFrameable(block)) {
                    cachedFrameableBlocks.add(block);
                }
            }
            Ideomod.LOGGER.debug("[ModCreativeTabs] Cached {} frameable blocks", cachedFrameableBlocks.size());
        }
        return cachedFrameableBlocks;
    }

    public static void invalidateCache() {
        cachedFrameableBlocks = null;
        Ideomod.LOGGER.debug("[ModCreativeTabs] Frameable blocks cache invalidated");
    }

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
                        output.accept(ModItems.COBBLED_ODDSTONE.get());
                        output.accept(ModItems.ODDSTONE_BRICKS.get());
                        output.accept(ModItems.ODDSTONE_WALL.get());
                        output.accept(ModItems.ODDSTONE_STAIRS.get());
                        output.accept(ModItems.ODDSTONE_SLAB.get());
                        output.accept(ModItems.COBBLED_ODDSTONE_WALL.get());
                        output.accept(ModItems.COBBLED_ODDSTONE_STAIRS.get());
                        output.accept(ModItems.COBBLED_ODDSTONE_SLAB.get());
                        output.accept(ModItems.ODDSTONE_BRICK_WALL.get());
                        output.accept(ModItems.ODDSTONE_BRICK_STAIRS.get());
                        output.accept(ModItems.ODDSTONE_BRICK_SLAB.get());
                        output.accept(ModItems.HOLLOW_STONE.get());
                        output.accept(ModItems.HOLLOW_STONE_SHARD.get());
                        output.accept(ModItems.MACHINE_BLOCK.get());
                        output.accept(ModItems.ODD_SLIME.get());
                        output.accept(ModItems.ODDSTONE_REPULSER.get());
                        output.accept(ModItems.ODDSTONE_ATTRACTOR.get());

                        // Machine specializations: pre-loaded with their specialization item
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_TRACK.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_TROLLEY.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_CONDUCTOR.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_PIPE.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_FLUID_PIPE.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_PISTON.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_STICKY_PISTON.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_BRACE.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_REPEATER.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_TOGGLE.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_TRANSFORMER.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_TIMER.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_ENGINE.get()));
                        output.accept(MachineSwapHelper.createPreloadedStack(ModBlocks.MACHINE_PULSER.get()));

                        // Frame Blocks
                        output.accept(ModItems.IRON_FRAME.get());
                        output.accept(ModItems.COPPER_FRAME.get());

                        // Tools / Debug
                        output.accept(ModItems.STRUCTURE_DEBUG_STICK.get());
                    })
                    .build()
    );

    public static final RegistryObject<CreativeModeTab> FRAMED_BLOCKS_TAB = CREATIVE_TABS.register("framed_blocks_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> {
                        return FramedBlockItem.createStack(
                                ModItems.IRON_FRAMED_BLOCK.get(),
                                net.minecraft.world.level.block.Blocks.STONE);
                    })
                    .title(Component.translatable("itemGroup." + Ideomod.MOD_ID + ".framed_blocks_tab"))
                    .displayItems((parameters, output) -> {
                        for (Block block : getFrameableBlocks()) {
                            output.accept(FramedBlockItem.createStack(
                                    ModItems.IRON_FRAMED_BLOCK.get(), block));
                            output.accept(FramedBlockItem.createStack(
                                    ModItems.COPPER_FRAMED_BLOCK.get(), block));
                        }
                    })
                    .build()
    );
}
