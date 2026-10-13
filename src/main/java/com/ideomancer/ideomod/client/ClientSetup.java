package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.client.gui.MachineFrameScreen;
import com.ideomancer.ideomod.client.render.FramedBlockItemModel;
import com.ideomancer.ideomod.client.render.FramedBlockRenderer;
import com.ideomancer.ideomod.client.render.HollowstoneRenderer;
import com.ideomancer.ideomod.client.render.MachinePistonArmRenderer;
import com.ideomancer.ideomod.registry.ModBlockEntities;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;

/**
 * Client-only setup: screens, render layers, block entity renderers, item model wrapping.
 *
 * Kept out of the main mod class so a dedicated server never loads these client
 * classes (doing so crashes the server at startup).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
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
        Ideomod.LOGGER.info("Ideomod client setup complete!");
    }

    @SubscribeEvent
    public static void onModifyBakingResult(final ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();

        ModelResourceLocation ironKey = new ModelResourceLocation(
                Ideomod.MOD_ID, "iron_framed_block", "inventory");
        ModelResourceLocation copperKey = new ModelResourceLocation(
                Ideomod.MOD_ID, "copper_framed_block", "inventory");

        BakedModel ironOriginal = models.get(ironKey);
        if (ironOriginal != null) {
            models.put(ironKey, new FramedBlockItemModel(ironOriginal));
            Ideomod.LOGGER.info("Wrapped iron_framed_block item model with FramedBlockItemModel");
        }

        BakedModel copperOriginal = models.get(copperKey);
        if (copperOriginal != null) {
            models.put(copperKey, new FramedBlockItemModel(copperOriginal));
            Ideomod.LOGGER.info("Wrapped copper_framed_block item model with FramedBlockItemModel");
        }
    }
}
