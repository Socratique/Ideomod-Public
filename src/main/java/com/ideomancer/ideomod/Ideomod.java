package com.ideomancer.ideomod;

import com.ideomancer.ideomod.block.FramedBlock;
import com.ideomancer.ideomod.block.MachineFrameBlock;
import com.ideomancer.ideomod.block.ModRecipeSerializers;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.StickinessRules;
import com.ideomancer.ideomod.client.FramedBlockRenderer;
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
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
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
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);

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
     * Frame Family = IronFrame, CopperFrame, IronFramedBlock, CopperFramedBlock.
     */
    private void registerStickinessRules() {
        // ================================================================
        // GRAB IMMUNITY RULES (type-based)
        // ================================================================

        // --- ODDSTONE rules (1-3) ---

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof SlimeBlock
                                  && target.getBlock() instanceof OddstoneBlock
        );

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof HoneyBlock
                                  && target.getBlock() instanceof OddstoneBlock
        );

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof OddSlimeBlock
                                  && target.getBlock() instanceof OddstoneBlock
        );

        // --- MACHINE FAMILY rules (4-6) ---

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> MachineFrameBlock.isMachineFamily(grabber.getBlock())
                                  && target.getBlock() instanceof SlimeBlock
        );

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> MachineFrameBlock.isMachineFamily(grabber.getBlock())
                                  && target.getBlock() instanceof HoneyBlock
        );

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> MachineFrameBlock.isMachineFamily(grabber.getBlock())
                                  && target.getBlock() instanceof OddSlimeBlock
        );

        // --- ODD SLIME mutual exclusion rules (7-8) ---

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof SlimeBlock
                                  && target.getBlock() instanceof OddSlimeBlock
        );

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> grabber.getBlock() instanceof HoneyBlock
                                  && target.getBlock() instanceof OddSlimeBlock
        );

        // --- FRAME FAMILY rules (9-11) ---
        // Covers both empty Frame Blocks AND Framed Blocks (both implement IFrameBlock
        // via isFrameFamily check)

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> StickinessRules.isFrameFamily(grabber.getBlock())
                                  && target.getBlock() instanceof SlimeBlock
        );

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> StickinessRules.isFrameFamily(grabber.getBlock())
                                  && target.getBlock() instanceof HoneyBlock
        );

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> StickinessRules.isFrameFamily(grabber.getBlock())
                                  && target.getBlock() instanceof OddSlimeBlock
        );

        // ================================================================
        // DIRECTIONAL RULES
        // ================================================================

        StickinessRules.registerDirectionalRule(
                (state, pushDirection) -> {
                    if (!(state.getBlock() instanceof OddSlimeBlock)) return false;
                    Direction facing = state.getValue(DirectionalBlock.FACING);
                    return !pushDirection.equals(facing);
                }
        );

        LOGGER.info("Registered stickiness rules: 11 grab immunity + 1 directional");
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

            // Frame blocks use CUTOUT render type for transparency
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.IRON_FRAME.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.COPPER_FRAME.get(), RenderType.cutout());

            // Framed blocks use CUTOUT_MIPPED for the frame overlay model
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.IRON_FRAMED_BLOCK.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.COPPER_FRAMED_BLOCK.get(), RenderType.cutoutMipped());

            // Register BESR for framed blocks
            BlockEntityRenderers.register(ModBlockEntities.FRAMED_BLOCK.get(), FramedBlockRenderer::new);

            // Register F3 debug overlay for framed blocks
            MinecraftForge.EVENT_BUS.register(new com.ideomancer.ideomod.client.FramedBlockDebugOverlay());
        });
        LOGGER.info("Ideomod client setup complete!");
    }
}
