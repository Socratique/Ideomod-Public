package com.ideomancer.ideomod;

import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;
import com.ideomancer.ideomod.registry.ModRecipeSerializers;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.movement.StickinessRules;
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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.DirectionalBlock;
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

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::onModifyBakingResult);

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
     *
     * INNER STICKY CARVE-OUT (rules 9-11):
     * =====================================
     * Frame Family blocks normally can't grab slime/honey/odd slime (rules 9-11).
     * BUT when a FramedBlock's inner block IS that same sticky type, the grab
     * immunity is lifted — a Framed Slime Block should grab normal blocks just
     * like real slime does. The carve-out checks INNER_STICKY on the grabber:
     *   - Rule 9 (frame -> slime): exempted when grabber INNER_STICKY == SLIME
     *   - Rule 10 (frame -> honey): exempted when grabber INNER_STICKY == HONEY
     *   - Rule 11 (frame -> odd slime): exempted when grabber INNER_STICKY == ODD_SLIME
     *
     * FRAMED ODD SLIME DIRECTIONAL BEHAVIOR:
     * =======================================
     * Framed Odd Slime does NOT use a blanket directional rule here. A blanket
     * rule would disable ALL stickiness (including frame stickiness) when pushed
     * in non-FACING directions, which is wrong — an ACTIVE Framed Odd Slime
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

        // --- ODDSTONE rules (1-3) ---
        // Slime/honey/odd slime can't grab Oddstone.
        // Also applies to framed blocks with those inner types.

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    if (target.getBlock() instanceof OddstoneBlock) {
                        if (grabber.getBlock() instanceof SlimeBlock) return true;
                        if (grabber.getBlock() instanceof FramedBlock
                                && grabber.getValue(FramedBlock.INNER_STICKY) == FramedBlock.STICKY_SLIME) return true;
                    }
                    return false;
                }
        );

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    if (target.getBlock() instanceof OddstoneBlock) {
                        if (grabber.getBlock() instanceof HoneyBlock) return true;
                        if (grabber.getBlock() instanceof FramedBlock
                                && grabber.getValue(FramedBlock.INNER_STICKY) == FramedBlock.STICKY_HONEY) return true;
                    }
                    return false;
                }
        );

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    if (target.getBlock() instanceof OddstoneBlock) {
                        if (grabber.getBlock() instanceof OddSlimeBlock) return true;
                        if (grabber.getBlock() instanceof FramedBlock
                                && grabber.getValue(FramedBlock.INNER_STICKY) == FramedBlock.STICKY_ODD_SLIME) return true;
                    }
                    return false;
                }
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
        // CARVE-OUT: FramedBlock whose inner IS the target type is exempted.

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    if (!(target.getBlock() instanceof SlimeBlock)) return false;
                    if (!StickinessRules.isFrameFamily(grabber.getBlock())) return false;
                    if (grabber.getBlock() instanceof FramedBlock
                            && grabber.getValue(FramedBlock.INNER_STICKY) == FramedBlock.STICKY_SLIME) return false;
                    return true;
                }
        );

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    if (!(target.getBlock() instanceof HoneyBlock)) return false;
                    if (!StickinessRules.isFrameFamily(grabber.getBlock())) return false;
                    if (grabber.getBlock() instanceof FramedBlock
                            && grabber.getValue(FramedBlock.INNER_STICKY) == FramedBlock.STICKY_HONEY) return false;
                    return true;
                }
        );

        StickinessRules.registerGrabImmunity(
                (grabber, target) -> {
                    if (!(target.getBlock() instanceof OddSlimeBlock)) return false;
                    if (!StickinessRules.isFrameFamily(grabber.getBlock())) return false;
                    if (grabber.getBlock() instanceof FramedBlock
                            && grabber.getValue(FramedBlock.INNER_STICKY) == FramedBlock.STICKY_ODD_SLIME) return false;
                    return true;
                }
        );

        // ================================================================
        // DIRECTIONAL RULES
        // ================================================================

        // Real Odd Slime: only sticky when pushed in FACING direction.
        // Blanket rule is fine — real Odd Slime has no other stickiness source.
        StickinessRules.registerDirectionalRule(
                (state, pushDirection) -> {
                    if (!(state.getBlock() instanceof OddSlimeBlock)) return false;
                    Direction facing = state.getValue(DirectionalBlock.FACING);
                    return !pushDirection.equals(facing);
                }
        );

        // NOTE: Framed Odd Slime does NOT register a directional rule here.
        // See class javadoc for explanation. Handled in mixin instead.

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
