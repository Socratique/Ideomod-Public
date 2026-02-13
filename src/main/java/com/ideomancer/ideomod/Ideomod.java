package com.ideomancer.ideomod;

import com.ideomancer.ideomod.block.MachineBlock;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.StickinessRules;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModCreativeTabs;
import com.ideomancer.ideomod.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DirectionalBlock;
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
     * Registers all stickiness rules for the mod.
     * See STICKINESS_SYSTEM.md for full documentation.
     * 
     * TWO TYPES OF RULES:
     * 
     * 1. GRAB IMMUNITY: "When block A is expanding and finds block B as a
     *    neighbor, BLOCK the grab. B does not come along."
     * 
     * 2. DIRECTIONAL: "When block A is being pushed in direction D, disable
     *    its stickiness entirely." Used by Odd Slime.
     */
    private void registerStickinessRules() {
        // ================================================================
        // GRAB IMMUNITY RULES (type-based)
        // ================================================================

        // ODDSTONE: Slime should NOT grab Oddstone.
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof SlimeBlock
                                  && target.getBlock() instanceof OddstoneBlock
        );

        // ODDSTONE: Honey should NOT grab Oddstone.
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof HoneyBlock
                                  && target.getBlock() instanceof OddstoneBlock
        );

        // ODDSTONE: Odd Slime should NOT grab Oddstone.
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof OddSlimeBlock
                                  && target.getBlock() instanceof OddstoneBlock
        );

        // MACHINE BLOCK: Machine Block should NOT grab slime.
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof MachineBlock
                                  && target.getBlock() instanceof SlimeBlock
        );

        // MACHINE BLOCK: Machine Block should NOT grab honey.
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof MachineBlock
                                  && target.getBlock() instanceof HoneyBlock
        );

        // MACHINE BLOCK: Machine Block should NOT grab Odd Slime.
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof MachineBlock
                                  && target.getBlock() instanceof OddSlimeBlock
        );

        // SLIME: Slime should NOT grab Odd Slime (mutual exclusion).
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof SlimeBlock
                                  && target.getBlock() instanceof OddSlimeBlock
        );

        // HONEY: Honey should NOT grab Odd Slime (mutual exclusion).
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof HoneyBlock
                                  && target.getBlock() instanceof OddSlimeBlock
        );

        // ================================================================
        // DIRECTIONAL RULES
        // ================================================================

        // ODD SLIME: Only sticky when pushed in its FACING direction.
        // If push direction != FACING, disable all stickiness.
        StickinessRules.registerDirectionalRule(
                (state, pushDirection) -> {
                    if (!(state.getBlock() instanceof OddSlimeBlock)) return false;
                    Direction facing = state.getValue(DirectionalBlock.FACING);
                    // Block stickiness if push direction does NOT match facing
                    return !pushDirection.equals(facing);
                }
        );

        LOGGER.info("Registered stickiness rules (grab immunity + directional)");
    }

    /**
     * Client-only setup - runs only on the client
     * Use this for things like registering renderers, keybindings, etc.
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Ideomod client setup complete!");
    }
}
