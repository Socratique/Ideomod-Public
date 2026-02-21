package com.ideomancer.ideomod;

import com.ideomancer.ideomod.block.MachineFrameBlock;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.StickinessRules;
import com.ideomancer.ideomod.client.MachineFrameScreen;
import com.ideomancer.ideomod.registry.ModBlockEntities;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModCreativeTabs;
import com.ideomancer.ideomod.registry.ModItems;
import com.ideomancer.ideomod.registry.ModMenuTypes;
import com.ideomancer.ideomod.structure.StructureManager;
import com.ideomancer.ideomod.structure.definitions.FurnaceArrayDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.MenuScreens;
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
 *
 * Updated by Claude, directed and guided by The Ideomancer.
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
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Ideomod initializing!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        registerStickinessRules();
        registerStructureDefinitions();
        registerStructureEventHandlers();
        LOGGER.info("Ideomod common setup complete!");
    }

    /**
     * Registers all stickiness rules for the mod.
     * See STICKINESS_SYSTEM.md for full documentation.
     *
     * Machine Family = MachineFrameBlock, MachineTrackBlock, MachineTrolleyBlock.
     * All three share the same grab immunity rules via MachineFrameBlock.isMachineFamily().
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

        // MACHINE FAMILY: Machine Family should NOT grab slime.
        // (Covers MachineFrameBlock, MachineTrackBlock, MachineTrolleyBlock)
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> MachineFrameBlock.isMachineFamily(grabber.getBlock())
                                  && target.getBlock() instanceof SlimeBlock
        );

        // MACHINE FAMILY: Machine Family should NOT grab honey.
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> MachineFrameBlock.isMachineFamily(grabber.getBlock())
                                  && target.getBlock() instanceof HoneyBlock
        );

        // MACHINE FAMILY: Machine Family should NOT grab Odd Slime.
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> MachineFrameBlock.isMachineFamily(grabber.getBlock())
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

    private void registerStructureDefinitions() {
        StructureManager.registerDefinition(FurnaceArrayDefinition.create());
        LOGGER.info("Registered multiblock structure definitions");
    }

    private void registerStructureEventHandlers() {
        MinecraftForge.EVENT_BUS.register(new StructureManager.EventHandler());
        LOGGER.info("Registered structure event handlers");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.MACHINE_FRAME.get(), MachineFrameScreen::new);
        });
        LOGGER.info("Ideomod client setup complete!");
    }
}
