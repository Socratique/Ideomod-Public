package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.MachineBlock;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers block and item color handlers for Machine Block's
 * power-level gradient.
 * 
 * HOW MINECRAFT BLOCK TINTING WORKS:
 * ===================================
 * Normally a block's texture is rendered as-is. But you can register
 * a "color handler" that multiplies a color onto the texture at runtime.
 * 
 * This is the same system that makes grass/leaves change color based on
 * biome temperature. For us, it changes Machine Block's color based on
 * its redstone power level.
 * 
 * The texture itself should be a GRAYSCALE design. The tint color gets
 * multiplied onto it, so:
 * - White pixels → show the full tint color
 * - Grey pixels → show a darker version of the tint
 * - Black pixels → stay black regardless of tint
 * 
 * This means we design ONE texture and the color system handles the
 * gradient from steel grey (unpowered) to bright red (full power).
 * 
 * IMPORTANT: This class only loads on the CLIENT side (Dist.CLIENT).
 * Servers don't render blocks so they don't need color handlers.
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MachineBlockColor {

    /**
     * Register block color handler.
     * The lambda receives (blockstate, world, position, tintIndex) and
     * returns an RGB color int. tintIndex refers to which layer of the
     * model to tint (some models have multiple tintable layers).
     * We use tintIndex 0 for our tinted layer.
     */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> {
                    if (tintIndex == 0) {
                        int power = state.getValue(MachineBlock.POWER);
                        return MachineBlock.getPowerColor(power);
                    }
                    return 0xFFFFFF; // No tint for other layers
                },
                ModBlocks.MACHINE_BLOCK.get()
        );
    }

    /**
     * Register item color handler.
     * When the block is in your inventory/hand, there's no blockstate to
     * read power from, so we always show the unpowered color (power 0).
     */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> {
                    if (tintIndex == 0) {
                        return MachineBlock.getPowerColor(0); // Unpowered in inventory
                    }
                    return 0xFFFFFF;
                },
                ModBlocks.MACHINE_BLOCK.get().asItem()
        );
    }
}
