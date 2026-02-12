package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.MachineBlock;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers all block and item color handlers for the mod.
 * 
 * Color handlers apply runtime tints to block/item textures.
 * The texture itself is a grayscale or neutral image, and the
 * color handler multiplies it by a color value to produce the
 * final appearance. Same system vanilla uses for grass, leaves,
 * and redstone dust.
 * 
 * Currently handles:
 * - Machine Block: power-level gradient (dark steel grey → bright red)
 * - Oddstone Portal: light blue tint (recolors nether portal texture)
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModColorHandlers {

    // ========================================================================
    // BLOCK COLORS
    // ========================================================================

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        // Machine Block: power level gradient
        event.register(
                (state, level, pos, tintIndex) -> {
                    if (tintIndex == 0) {
                        int power = state.getValue(MachineBlock.POWER);
                        return MachineBlock.getPowerColor(power);
                    }
                    return 0xFFFFFF;
                },
                ModBlocks.MACHINE_BLOCK.get()
        );

        // Oddstone Portal: light blue tint
        // 0x44DDFF = a bright cyan/light blue
        // This gets multiplied onto the nether portal texture,
        // turning its purple hue into a cool blue
        event.register(
                (state, level, pos, tintIndex) -> {
                    if (tintIndex == 0) {
                        return 0x44DDFF; // Light blue / cyan
                    }
                    return 0xFFFFFF;
                },
                ModBlocks.ODDSTONE_PORTAL.get()
        );
    }

    // ========================================================================
    // ITEM COLORS
    // ========================================================================

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // Machine Block: unpowered color in inventory
        event.register(
                (stack, tintIndex) -> {
                    if (tintIndex == 0) {
                        return MachineBlock.getPowerColor(0);
                    }
                    return 0xFFFFFF;
                },
                ModBlocks.MACHINE_BLOCK.get().asItem()
        );

        // No item color for portal block - it has no item form
    }
}
