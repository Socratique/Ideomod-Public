package com.ideomancer.ideomod.compat;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Living alongside Ideomancer's Machine Mod (mod id "machinemod").
 *
 * The Machine Mod is a cut of Ideomod: every block and item in it is in Ideomod too, under the same
 * name in Ideomod's namespace (machinemod:oddstone is ideomod:oddstone). When both are installed the
 * Machine Mod steps aside and registers nothing, so Ideomod provides it all. Either way, a world built
 * with the Machine Mod opens here with its blocks, items and machine contents intact: its
 * machinemod: names translate to ours.
 */
public final class MachineModCompat {
    private static final String MACHINE_MOD_ID = "machinemod";

    private MachineModCompat() {}

    /** Called from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(EventPriority.LOWEST, MachineModCompat::addBlockEntityAliases);
        MinecraftForge.EVENT_BUS.addListener(MachineModCompat::onMissingMappings);
    }

    /**
     * Block entity types are not saved with the world, so Forge raises no missing-mappings event for
     * them: an alias is the only way a chunk's "machinemod:machine_block" entity finds our type (and
     * keeps the machine's contents).
     */
    private static void addBlockEntityAliases(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES)) {
            addAliases(ForgeRegistries.BLOCK_ENTITY_TYPES);
        }
    }

    private static <T> void addAliases(IForgeRegistry<T> registry) {
        ForgeRegistry<T> forgeRegistry = (ForgeRegistry<T>) registry;
        for (ResourceLocation key : registry.getKeys()) {
            if (key.getNamespace().equals(Ideomod.MOD_ID)) {
                forgeRegistry.addAlias(new ResourceLocation(MACHINE_MOD_ID, key.getPath()), key);
            }
        }
    }

    /** Blocks and items are saved with the world; Forge asks about the ones it cannot find. */
    private static void onMissingMappings(MissingMappingsEvent event) {
        remap(event, ForgeRegistries.Keys.BLOCKS, ForgeRegistries.BLOCKS);
        remap(event, ForgeRegistries.Keys.ITEMS, ForgeRegistries.ITEMS);
    }

    private static <T> void remap(MissingMappingsEvent event, ResourceKey<? extends Registry<T>> key,
                                  IForgeRegistry<T> registry) {
        for (MissingMappingsEvent.Mapping<T> mapping : event.getMappings(key, MACHINE_MOD_ID)) {
            ResourceLocation ours = new ResourceLocation(Ideomod.MOD_ID, mapping.getKey().getPath());
            if (registry.containsKey(ours)) {
                mapping.remap(registry.getValue(ours));
            }
        }
    }
}
