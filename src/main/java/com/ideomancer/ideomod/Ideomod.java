package com.ideomancer.ideomod;

import com.ideomancer.ideomod.block.MachineBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.StickinessRules;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModCreativeTabs;
import com.ideomancer.ideomod.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Main mod class for Ideomod
 * A vanilla+ style overhaul mod by Ideomancer
 */
@Mod(Ideomod.MOD_ID)
public class Ideomod {
    // The mod ID - must match what's in mods.toml and gradle.properties
    public static final String MOD_ID = "ideomod";
    
    // Logger for outputting information to the console/log files
    // Made public so other classes can use it
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ideomod() {
        // Get the mod event bus for registering mod lifecycle events
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register all our deferred registers to the mod event bus
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);

        // Register the setup methods for mod loading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        // Register ourselves for server and other game events
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Ideomod initializing!");
    }

    /**
     * Common setup - runs on both client and server
     * Use this for things like registering capabilities, network packets, etc.
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // Register one-way stickiness rules
        // These tell our PistonStructureResolver mixin which grabs to block
        registerStickinessRules();

        LOGGER.info("Ideomod common setup complete!");
    }

    /**
     * Registers all one-way stickiness rules for the mod.
     * 
     * Each rule says: "When block A is expanding (being pushed by a piston
     * and checking its sticky neighbors), and it finds block B next to it,
     * BLOCK the grab. B does not come along."
     * 
     * ODDSTONE behavior:
     *   Oddstone pushed → grabs slime (allowed by canStickTo, no rule needed)
     *   Slime pushed → does NOT grab Oddstone (BLOCKED by rule below)
     * 
     * MACHINE BLOCK behavior:
     *   Slime pushed → grabs Machine Block (allowed by canStickTo, no rule needed)
     *   Machine Block pushed → does NOT grab slime (BLOCKED by rule below)
     */
    private void registerStickinessRules() {
        // ODDSTONE: Slime should NOT grab Oddstone.
        // (But Oddstone CAN grab slime - handled by OddstoneBlock.canStickTo)
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof SlimeBlock
                                  && target.getBlock() instanceof OddstoneBlock
        );

        // ODDSTONE: Honey should NOT grab Oddstone either.
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof HoneyBlock
                                  && target.getBlock() instanceof OddstoneBlock
        );

        // MACHINE BLOCK: Machine Block should NOT grab slime.
        // (But slime CAN grab Machine Block - allowed by MachineBlock.canStickTo)
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof MachineBlock
                                  && target.getBlock() instanceof SlimeBlock
        );

        // MACHINE BLOCK: Machine Block should NOT grab honey.
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof MachineBlock
                                  && target.getBlock() instanceof HoneyBlock
        );

        LOGGER.info("Registered one-way stickiness rules");
    }

    /**
     * Client-only setup - runs only on the client
     * Use this for things like registering renderers, keybindings, etc.
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Ideomod client setup complete!");
    }
}
