package com.ideomancer.ideomod;

import com.ideomancer.ideomod.block.frame.FrameActivation;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FrameMaterial;
import com.ideomancer.ideomod.block.frame.IFrameBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;
import com.ideomancer.ideomod.block.machine.MachineSwapHelper;
import com.ideomancer.ideomod.registry.ModRecipeSerializers;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.movement.StickinessRules;
import com.ideomancer.ideomod.block.movement.StickyType;
import com.ideomancer.ideomod.block.portal.EnderPearlPortalHandler;
import com.ideomancer.ideomod.client.render.FramedBlockItemModel;
import com.ideomancer.ideomod.client.render.FramedBlockRenderer;
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

        // Register specialization mappings (item -> block) for machine swap system.
        // Uses deferred suppliers internally so safe to call before registry resolution.
        MachineSwapHelper.registerMappings();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::onModifyBakingResult);

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Ideomod initializing!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Register framed block items for each material before stickiness rules
        FrameMaterial.registerFramedBlockItem(FrameMaterial.IRON, ModItems.IRON_FRAMED_BLOCK::get);
        FrameMaterial.registerFramedBlockItem(FrameMaterial.COPPER, ModItems.COPPER_FRAMED_BLOCK::get);

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
     *
     * Machine Family = MachineFrameBlock, MachineTrackBlock, MachineTrolleyBlock
     *   (and future specializations). All implement IMachineFamily.
     * Frame Family = IronFrame, CopperFrame, IronFramedBlock, CopperFramedBlock.
     *
     * MACHINE FAMILY STICKINESS MODEL (MachineType-aware):
     * =====================================================
     * Machine Frame (MachineType.FRAME) sticks to ALL machine types.
     * Specializations stick to SAME type + FRAME only (via canStickTo).
     * All types stick to Frame Family (when active) and are permissive
     * to slime/honey/odd_slime (so grab immunity can block the reverse).
     *
     * Inter-specialization isolation (Track doesn't stick to Trolley) is
     * handled by canStickTo returning false, NOT by grab immunity rules.
     * Machine Frame bridges different specialization types.
     *
     * CONSOLIDATED RULES (4 grab immunity + 1 directional):
     * ======================================================
     * Rule 1: Non-Oddstone can't grab Oddstone (real or framed).
     *         Protects both OddstoneBlock and FramedBlock with INNER_STICKY=ODDSTONE.
     *         CARVE-OUTS:
     *           A) Framed Oddstone (INNER_STICKY=ODDSTONE) IS allowed to grab
     *              real Oddstone and other framed oddstone -- inner stickiness.
     *           B) For FRAMED oddstone targets only (not bare): Machine Family
     *              and ACTIVE same-material IFrameBlock can reach framed oddstone.
     *              "Framing is the only way other blocks can grab oddstone."
     *         Machine Family / Frame Family have their own rules (2, 4).
     *
     * Rule 2: Machine Family can't grab sticky blocks (slime/honey/odd_slime).
     *
     * Rule 3: Slime/honey can't grab Odd Slime (mutual exclusion enforcement).
     *
     * Rule 4: Frame Family can't grab sticky blocks (slime/honey/odd_slime/oddstone).
     *         CARVE-OUTS:
     *           A) FramedBlock whose INNER_STICKY matches the target type IS
     *              allowed (Framed Slime grabs like real slime, etc.)
     *           B) ACTIVE same-material frame connection (same as Rule 1B).
     *
     * FRAMED ODD SLIME DIRECTIONAL BEHAVIOR:
     * =======================================
     * Framed Odd Slime does NOT use a blanket directional rule here. A blanket
     * rule would disable ALL stickiness (including frame stickiness) when pushed
     * in non-FACING directions, which is wrong -- an ACTIVE Framed Odd Slime
     * should still stick to same-material frames regardless of push direction.
     *
     * Instead, the directional gating for Framed Odd Slime's INNER stickiness
     * is handled directly in PistonStructureResolverMixin, which has the context
     * to distinguish "inner sticky blocked by direction" from "frame sticky
     * independently allowed." See the mixin's ideomod$isFramedOddSlimeDirectionallyBlocked
     * and ideomod$wouldFrameStickinessAllow helpers.
     */
    private void registerStickinessRules() {
        // ================================================================
        // GRAB IMMUNITY RULES (type-based)
        // ================================================================

        // --- Rule 1: Non-Oddstone can't grab Oddstone (real or framed) ---
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    boolean targetIsOddstone = target.getBlock() instanceof OddstoneBlock;
                    boolean targetIsFramedOddstone = StickinessRules.isFramedOddstone(target);
                    if (!targetIsOddstone && !targetIsFramedOddstone) return false;

                    // Carve-out A: oddstone-type grabber can grab oddstone target
                    if (StickinessRules.isOddstoneType(grabber)) return false;

                    // Carve-out B: frame network can reach FRAMED oddstone
                    if (targetIsFramedOddstone && target.getValue(FramedBlock.ACTIVE)) {
                        if (MachineFrameBlock.isMachineFamily(grabber.getBlock())) return false;
                        if (StickinessRules.isActiveFrameConnection(grabber, target)) return false;
                    }

                    return true;
                }
        );

        // --- Rule 2: Machine Family can't grab sticky blocks ---
        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    if (!MachineFrameBlock.isMachineFamily(grabber.getBlock())) return false;
                    Block targetBlock = target.getBlock();
                    return targetBlock instanceof SlimeBlock
                        || targetBlock instanceof HoneyBlock
                        || targetBlock instanceof OddSlimeBlock;
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

    /**
     * Registers the frame activation tick handler for end-of-tick batching.
     * This ensures all frame activation changes in a tick are batched into
     * a single recalculation per network, instead of one per block change.
     * (Audit item #14)
     */
    private void registerFrameActivationTickHandler() {
        MinecraftForge.EVENT_BUS.register(new FrameActivation.TickHandler());
        LOGGER.info("Registered frame activation tick handler");
    }

    /**
     * Registers the ender pearl portal activation handler.
     * Listens for ProjectileImpactEvent to catch ender pearls landing
     * in valid Oddstone portal frames.
     */
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

            BlockEntityRenderers.register(ModBlockEntities.FRAMED_BLOCK.get(), FramedBlockRenderer::new);

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
