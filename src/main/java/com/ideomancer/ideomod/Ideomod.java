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
    public static final String MOD_ID = "ideomod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ideomod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        // NOTE: ModEntityTypes registration removed -- ranged pistons now use
        // vanilla's MovingPistonBlock/PistonMovingBlockEntity for animation.
        // No custom entities needed. Re-add if other custom entities are added later.

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Ideomod initializing!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        registerStickinessRules();
        LOGGER.info("Ideomod common setup complete!");
    }

    /**
     * Registers all stickiness rules for the mod.
     * See STICKINESS_SYSTEM.md for full documentation.
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
        StickinessRules.registerDirectionalRule(
                (state, pushDirection) -> {
                    if (!(state.getBlock() instanceof OddSlimeBlock)) return false;
                    Direction facing = state.getValue(DirectionalBlock.FACING);
                    return !pushDirection.equals(facing);
                }
        );

        LOGGER.info("Registered stickiness rules (grab immunity + directional)");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Ideomod client setup complete!");
    }
}
