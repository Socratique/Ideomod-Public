package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.machine.FluidType;
import com.ideomancer.ideomod.block.machine.MachineConductorBlock;
import com.ideomancer.ideomod.block.machine.MachineFluidPipeBlock;
import com.ideomancer.ideomod.block.machine.MachineRepeaterBlock;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModItems;
import net.minecraft.client.renderer.BiomeColors;
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
 * Machine Repeater uses tintIndex 1 for its inner underlay cuboid.
 * Binary: black when unpowered, bright red (same as conductor at 15)
 * when powered. The outer shell texture has transparent windows where
 * the tinted underlay shines through.
 *
 * Machine Fluid Pipe uses tintIndex 0 for the water texture inner cuboid.
 * Vanilla water_still.png is greyscale -- the colour handler applies
 * biome water colour (or default blue) when the pipe holds water.
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
     */
    private static final int[] POWER_COLORS = new int[16];

    static {
        POWER_COLORS[0] = 0x000000;

        for (int power = 1; power <= 15; power++) {
            float f = (float) power / 15.0F;
            float curve = f * f;

            float r = curve * 0.6F + 0.15F;
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
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_CONDUCTOR.get());

        // Machine Repeater: tintIndex 1 on inner underlay cuboid.
        // Binary: off = black (invisible behind outer shell),
        //         on  = full bright red (POWER_COLORS[15]).
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 1) {
                return state.getValue(MachineRepeaterBlock.POWERED)
                        ? POWER_COLORS[15] : POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_REPEATER.get());

        // Machine Fluid Pipe: tintIndex 0 on inner cuboid faces.
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 0 && state.getValue(MachineFluidPipeBlock.FLUID_TYPE) == FluidType.WATER) {
                if (level != null && pos != null) {
                    return BiomeColors.getAverageWaterColor(level, pos);
                }
                return 0x3F76E4;
            }
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_FLUID_PIPE.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // Machine Conductor item: show unpowered (black)
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                return POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_CONDUCTOR.get().asItem());

        // Machine Repeater item: show unpowered (black)
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                return POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModItems.MACHINE_REPEATER.get());

        // Machine Fluid Pipe item: no tint needed
        event.register((stack, tintIndex) -> {
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_FLUID_PIPE.get().asItem());
    }
}
