package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.machine.MachineConductorBlock;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers block and item color handlers for tintIndex-based coloring.
 *
 * Machine Conductor uses tintIndex 1 for its power indicator overlay.
 * The color maps POWER 0-15 to a gradient: black at 0, dim red at 1,
 * bright red at 15. Smoother transition at the low end so power 1-3
 * are noticeably dimmer rather than jumping straight from black to red.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModColorHandlers {

    /**
     * Pre-computed color table for power levels 0-15.
     *
     * 0 = pure black (indicator invisible)
     * 1-15 = red gradient using a squared curve for smoother low-end.
     * The squared curve (f*f instead of f) keeps levels 1-3 very dim,
     * giving a gradual ramp up rather than an abrupt jump from off to on.
     */
    private static final int[] POWER_COLORS = new int[16];

    static {
        // POWER 0 = pure black (indicator off)
        POWER_COLORS[0] = 0x000000;

        // POWER 1-15 = red gradient with squared curve for smoother low end
        for (int power = 1; power <= 15; power++) {
            float f = (float) power / 15.0F;

            // Squared curve: f*f makes low values much dimmer
            // At power 1:  f=0.067, f*f=0.004 -> very dim
            // At power 8:  f=0.533, f*f=0.284 -> moderate
            // At power 15: f=1.000, f*f=1.000 -> full bright
            float curve = f * f;

            // Red channel: scale from dim to bright
            float r = curve * 0.6F + 0.15F;
            // Green/blue: tiny amounts at high power for slight orange warmth
            float g = Math.max(0.0F, curve * 0.7F - 0.5F);
            float b = Math.max(0.0F, curve * 0.6F - 0.7F);

            int ri = Math.min(255, (int) (r * 255.0F));
            int gi = Math.min(255, (int) (g * 255.0F));
            int bi = Math.min(255, (int) (b * 255.0F));
            POWER_COLORS[power] = (ri << 16) | (gi << 8) | bi;
        }
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        // Machine Conductor: tintIndex 0 = no tint (base texture as-is)
        //                    tintIndex 1 = power indicator overlay
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 1) {
                int power = state.getValue(MachineConductorBlock.POWER);
                return POWER_COLORS[power];
            }
            return 0xFFFFFF; // tintIndex 0: white = no tint
        }, ModBlocks.MACHINE_CONDUCTOR.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // Machine Conductor item: show unpowered appearance (POWER=0 = black)
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                return POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_CONDUCTOR.get().asItem());
    }
}
