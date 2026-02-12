package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.MachineBlock;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers block and item color handlers for:
 * 
 * 1. Machine Block's power-level gradient (dark grey -> bright red)
 * 2. Oddstone Portal's light blue tint (recolors nether portal texture)
 * 
 * HOW THE PORTAL TINT WORKS:
 * ==========================
 * We reuse the vanilla nether portal texture (which is purple/magenta).
 * By applying a light blue tint via the color handler, the texture gets
 * multiplied by our color, shifting it from purple to a cyan/light blue.
 * 
 * The nether portal texture is mostly white/light purple, so multiplying
 * by light blue (0x44DDFF) gives us a distinct blue-cyan portal effect
 * that's clearly different from nether portals.
 * 
 * This avoids needing a custom animated texture (which would require
 * more complex setup with .mcmeta animation files).
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MachineBlockColor {

    // ========================================================================
    // MACHINE BLOCK COLORS
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
