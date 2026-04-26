package com.ideomancer.ideomod;

import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.item.RedstoneRodSignalManager;
import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FrameMaterial;
import com.ideomancer.ideomod.block.frame.IFrameBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;
import com.ideomancer.ideomod.block.machine.MachineGrabberBlock;
import com.ideomancer.ideomod.block.machine.MachineSwapHelper;
import com.ideomancer.ideomod.network.ModNetworking;
import com.ideomancer.ideomod.registry.ModRecipeSerializers;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.movement.StickinessRules;
import com.ideomancer.ideomod.block.movement.StickyType;
import com.ideomancer.ideomod.block.portal.EnderPearlPortalHandler;
import com.ideomancer.ideomod.client.render.FramedBlockItemModel;
import com.ideomancer.ideomod.client.render.FramedBlockRenderer;
import com.ideomancer.ideomod.client.render.HollowstoneRenderer;
import com.ideomancer.ideomod.client.render.MachinePistonArmRenderer;
import com.ideomancer.ideomod.worldgen.ModFeatures;
import com.ideomancer.ideomod.client.gui.MachineFrameScreen;
import com.ideomancer.ideomod.registry.ModBlockEntities;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModCreativeTabs;
import com.ideomancer.ideomod.registry.ModItems;
import com.ideomancer.ideomod.registry.ModMenuTypes;
import com.ideomancer.ideomod.multiblock.StructureManager;
import com.ideomancer.ideomod.multiblock.definitions.FurnaceArrayDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.Map;

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
        ModFeatures.FEATURES.register(modEventBus);
        ModFeatures.CHUNK_GENERATORS.register(modEventBus);

        // Register specialization mappings (item -> block) for machine swap system.
        MachineSwapHelper.registerMappings();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::onModifyBakingResult);

        MinecraftForge.EVENT_BUS.register(this);

        // Clear Redstone Rod phantom signals on server shutdown
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) ->
                RedstoneRodSignalManager.clearAll());

        LOGGER.info("Ideomod initializing!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Load config early so all systems have values ready
        IdeomodConfig.loadConfig();

        // Register framed block items for each material before stickiness rules
        FrameMaterial.registerFramedBlockItem(FrameMaterial.IRON, ModItems.IRON_FRAMED_BLOCK::get);
        FrameMaterial.registerFramedBlockItem(FrameMaterial.COPPER, ModItems.COPPER_FRAMED_BLOCK::get);

        // Register network packets (must be done in commonSetup, not constructor)
        ModNetworking.registerPackets();

        registerStickinessRules();
        registerStructureDefinitions();
        registerStructureEventHandlers();
        registerPortalEventHandlers();
        registerFrameActivationTickHandler();
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

        // --- Rule 1: Non-Oddstone can't grab Oddstone (real, variants, or framed) ---
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    boolean targetIsOddstone = target.getBlock() instanceof OddstoneBlock
                            || target.getBlock() instanceof com.ideomancer.ideomod.block.CobbledOddstoneBlock
                            || target.getBlock() instanceof com.ideomancer.ideomod.block.OddstoneBricksBlock;
                    boolean targetIsFramedOddstone = StickinessRules.isFramedOddstone(target);
                    if (!targetIsOddstone && !targetIsFramedOddstone) return false;

                    if (StickinessRules.isOddstoneType(grabber)) return false;

                    if (targetIsFramedOddstone && target.getValue(FramedBlock.ACTIVE)) {
                        if (MachineFrameBlock.isMachineFamily(grabber.getBlock())) return false;
                        if (StickinessRules.isActiveFrameConnection(grabber, target)) return false;
                    }

                    return true;
                }
        );

        // --- Rule 2: Machine Family can't grab sticky blocks ---
        // Exception: Powered Machine Grabbers can grab their matching sticky type.
        // Slime Grabber (powered) can grab SlimeBlock, Honey Grabber can grab HoneyBlock.
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    if (!MachineFrameBlock.isMachineFamily(grabber.getBlock())) return false;
                    Block targetBlock = target.getBlock();
                    boolean targetIsSticky = targetBlock instanceof SlimeBlock
                        || targetBlock instanceof HoneyBlock
                        || targetBlock instanceof OddSlimeBlock;
                    if (!targetIsSticky) return false;

                    // Carve-out: powered grabber matching its sticky type
                    if (grabber.getBlock() instanceof MachineGrabberBlock grabberBlock
                            && grabber.getValue(MachineGrabberBlock.POWERED)) {
                        if (grabberBlock.getGrabberType() == MachineGrabberBlock.GrabberType.SLIME
                                && targetBlock instanceof SlimeBlock) {
                            return false; // Allow slime grabber to grab slime
                        }
                        if (grabberBlock.getGrabberType() == MachineGrabberBlock.GrabberType.HONEY
                                && targetBlock instanceof HoneyBlock) {
                            return false; // Allow honey grabber to grab honey
                        }
                    }

                    return true; // Block all other Machine Family from grabbing sticky blocks
                }
        );

        // --- Rule 3: Slime/honey can't grab Odd Slime (mutual exclusion) ---
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    if (!(target.getBlock() instanceof OddSlimeBlock)) return false;
                    Block grabberBlock = grabber.getBlock();
                    return grabberBlock instanceof SlimeBlock
                        || grabberBlock instanceof HoneyBlock;
                }
        );

        // --- Rule 4: Frame Family can't grab sticky blocks ---
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    if (!StickinessRules.isFrameFamily(grabber.getBlock())) return false;
                    Block targetBlock = target.getBlock();
                    StickyType targetType = StickyType.of(target);

                    if (targetBlock instanceof SlimeBlock || targetBlock instanceof HoneyBlock
                            || targetBlock instanceof OddSlimeBlock) {
                        if (StickinessRules.hasMatchingInnerSticky(grabber, targetType)) return false;
                        return true;
                    }

                    if (StickinessRules.isOddstoneType(target)) {
                        if (StickinessRules.hasMatchingInnerSticky(grabber, StickyType.ODDSTONE)) return false;
                        if (StickinessRules.isFramedOddstone(target) && target.getValue(FramedBlock.ACTIVE)) {
                            if (StickinessRules.isActiveFrameConnection(grabber, target)) return false;
                        }
                        return true;
                    }

                    return false;
                }
        );

        // ================================================================
        // DIRECTIONAL RULES
        // ================================================================

        StickinessRules.registerDirectionalRule(
                (state, pushDirection) -> {
                    if (!(state.getBlock() instanceof OddSlimeBlock)) return false;
                    return pushDirection != state.getValue(OddSlimeBlock.FACING);
                }
        );

        LOGGER.info("Registered stickiness rules: 4 grab immunity + 1 directional");
    }

    private void registerStructureDefinitions() {
        StructureManager.registerDefinition(FurnaceArrayDefinition.create());
        LOGGER.info("Registered multiblock structure definitions");
    }

    private void registerStructureEventHandlers() {
        MinecraftForge.EVENT_BUS.register(new StructureManager.EventHandler());
        LOGGER.info("Registered structure event handlers");
    }

    private void registerFrameActivationTickHandler() {
        MinecraftForge.EVENT_BUS.register(new FrameActivation.TickHandler());
        LOGGER.info("Registered frame activation tick handler");
    }

    private void registerPortalEventHandlers() {
        MinecraftForge.EVENT_BUS.register(new EnderPearlPortalHandler());
        LOGGER.info("Registered ender pearl portal activation handler");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.MACHINE_FRAME.get(), MachineFrameScreen::new);

            ItemBlockRenderTypes.setRenderLayer(ModBlocks.IRON_FRAME.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.COPPER_FRAME.get(), RenderType.cutout());

            ItemBlockRenderTypes.setRenderLayer(ModBlocks.IRON_FRAMED_BLOCK.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.COPPER_FRAMED_BLOCK.get(), RenderType.cutoutMipped());

            // Hollowstone: cutoutMipped for filled variant (transparent center pixels)
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.HOLLOW_STONE.get(), RenderType.cutoutMipped());

            // Machine Grabbers: cutoutMipped for transparent center (inner block via BESR)
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MACHINE_SLIME_GRABBER.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MACHINE_HONEY_GRABBER.get(), RenderType.cutoutMipped());

            // Machine Transmitter: cutoutMipped for transparent center (inner oddstone via BESR)
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MACHINE_TRANSMITTER.get(), RenderType.cutoutMipped());

            BlockEntityRenderers.register(ModBlockEntities.HOLLOW_STONE.get(), HollowstoneRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.FRAMED_BLOCK.get(), FramedBlockRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.MACHINE_FRAME.get(), MachinePistonArmRenderer::new);

            MinecraftForge.EVENT_BUS.register(new com.ideomancer.ideomod.client.FramedBlockDebugOverlay());
        });
        LOGGER.info("Ideomod client setup complete!");
    }

    private void onModifyBakingResult(final ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();

        ModelResourceLocation ironKey = new ModelResourceLocation(
                MOD_ID, "iron_framed_block", "inventory");
        ModelResourceLocation copperKey = new ModelResourceLocation(
                MOD_ID, "copper_framed_block", "inventory");

        BakedModel ironOriginal = models.get(ironKey);
        if (ironOriginal != null) {
            models.put(ironKey, new FramedBlockItemModel(ironOriginal));
            LOGGER.info("Wrapped iron_framed_block item model with FramedBlockItemModel");
        }

        BakedModel copperOriginal = models.get(copperKey);
        if (copperOriginal != null) {
            models.put(copperKey, new FramedBlockItemModel(copperOriginal));
            LOGGER.info("Wrapped copper_framed_block item model with FramedBlockItemModel");
        }
    }
}
