package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.frame.FrameabilityHelper;
import com.ideomancer.ideomod.block.frame.FramedBlockItem;
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
 * The framed blocks tab caches the list of frameable blocks on first open
 * rather than iterating the entire block registry every time. The cache
 * is built once (after all mods have registered their blocks) and reused
 * for all subsequent tab opens.
 *
 * Since frameability now depends on the ideomod:frameable_blocks tag,
 * the cache is invalidated on datapack reload so changes take effect
 * without restarting.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class ModCreativeTabs {

    /** Cached list of blocks that pass FrameabilityHelper.isFrameable(). Built on first tab open. */
    private static List<Block> cachedFrameableBlocks = null;

    /**
     * Returns the cached list of frameable blocks, building it on first call.
     * Thread-safe: displayItems runs on the main thread, so no synchronization needed.
     */
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

    /**
     * Invalidate the frameable blocks cache. Call this on datapack reload
     * so tag changes take effect without restarting.
     */
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

    /**
     * Dynamic creative tab for Framed Blocks.
     *
     * Uses a cached list of frameable blocks (built on first open) instead
     * of iterating the entire block registry every time the tab is opened.
     * Since frameability is now tag-based (ideomod:frameable_blocks),
     * the cache is invalidated on datapack reload via invalidateCache().
     */
    public static final RegistryObject<CreativeModeTab> FRAMED_BLOCKS_TAB = CREATIVE_TABS.register("framed_blocks_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> {
                        // Icon: iron framed stone
                        return FramedBlockItem.createStack(
                                ModItems.IRON_FRAMED_BLOCK.get(),
                                net.minecraft.world.level.block.Blocks.STONE);
                    })
                    .title(Component.translatable("itemGroup." + Ideomod.MOD_ID + ".framed_blocks_tab"))
                    .displayItems((parameters, output) -> {
                        for (Block block : getFrameableBlocks()) {
                            // Iron variant
                            output.accept(FramedBlockItem.createStack(
                                    ModItems.IRON_FRAMED_BLOCK.get(), block));
                            // Copper variant
                            output.accept(FramedBlockItem.createStack(
                                    ModItems.COPPER_FRAMED_BLOCK.get(), block));
                        }
                    })
                    .build()
    );
}
