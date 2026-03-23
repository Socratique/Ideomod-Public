package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.machine.FluidType;
import com.ideomancer.ideomod.block.machine.MachineConductorBlock;
import com.ideomancer.ideomod.block.machine.MachineFluidPipeBlock;
import com.ideomancer.ideomod.block.machine.MachineRepeaterBlock;
import com.ideomancer.ideomod.block.machine.MachineTimerBlock;
import com.ideomancer.ideomod.block.machine.MachineToggleBlock;
import com.ideomancer.ideomod.block.machine.MachineTransformerBlock;
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
 * The color maps POWER 0-15 to a vanilla-style gradient: dark maroon at 0,
 * dim red at low power, bright red at 15. Matches vanilla RedStoneWireBlock's
 * color feel — unpowered components have a visible dark red rather than
 * pure black.
 *
 * Machine Repeater uses tintIndex 1 for its inner underlay cuboid.
 * Binary: dark maroon when unpowered, bright red (POWER_COLORS[15])
 * when powered.
 *
 * Machine Toggle uses tintIndex 1 for its inner underlay cuboid.
 * Binary: dark maroon when unpowered, bright red when powered.
 *
 * Machine Transformer uses tintIndex 1 for its inner underlay cuboid.
 * Level-aware: dark maroon when unpowered, POWER_COLORS[outputLevel]
 * when powered — so a transformer set to level 5 glows dimmer than one
 * set to 15.
 *
 * Machine Timer uses tintIndex 1 for its inner underlay cuboid.
 * Binary: dark maroon when unpowered, bright red when powered.
 *
 * Machine Fluid Pipe uses tintIndex 0 for the water texture inner cuboid.
 * Vanilla water_still.png is greyscale — the colour handler applies
 * biome water colour (or default blue) when the pipe holds water.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModColorHandlers {

    /**
     * Pre-computed color table for power levels 0-15.
     *
     * Vanilla-style gradient matching RedStoneWireBlock's color feel:
     *   0 = dark maroon (visible, not pure black — shading detail shows)
     *   1-15 = progressively brighter red with subtle warm tones at high power.
     *
     * Uses vanilla's approach: even power 0 has a base red component so
     * unpowered redstone components look like dark red rather than flat black.
     * Grey shading in indicator textures renders as dark red gradations when
     * off, bright red gradations when fully powered.
     */
    private static final int[] POWER_COLORS = new int[16];

    static {
        for (int power = 0; power <= 15; power++) {
            float f = (float) power / 15.0F;

            float r, g, b;
            if (power == 0) {
                r = 0.3F;
                g = 0.0F;
                b = 0.0F;
            } else {
                r = f * 0.6F + 0.4F;
                g = Math.max(0.0F, f * f * 0.7F - 0.5F);
                b = Math.max(0.0F, f * f * 0.6F - 0.7F);
            }

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
        // Binary: off = dark maroon, on = full bright red.
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 1) {
                return state.getValue(MachineRepeaterBlock.POWERED)
                        ? POWER_COLORS[15] : POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_REPEATER.get());

        // Machine Toggle: tintIndex 1 on inner underlay cuboid.
        // Binary: off = dark maroon, on = bright red.
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 1) {
                return state.getValue(MachineToggleBlock.POWERED)
                        ? POWER_COLORS[15] : POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_TOGGLE.get());

        // Machine Transformer: tintIndex 1 on inner underlay cuboid.
        // Level-aware: off = dark maroon, on = POWER_COLORS[outputLevel].
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 1) {
                if (state.getValue(MachineTransformerBlock.POWERED)) {
                    int outputLevel = state.getValue(MachineTransformerBlock.OUTPUT_LEVEL);
                    return POWER_COLORS[outputLevel];
                }
                return POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_TRANSFORMER.get());

        // Machine Timer: tintIndex 1 on inner underlay cuboid.
        // Binary: off = dark maroon, on = bright red.
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 1) {
                return state.getValue(MachineTimerBlock.POWERED)
                        ? POWER_COLORS[15] : POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_TIMER.get());

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
        // Machine Conductor item: show unpowered (dark maroon)
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                return POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_CONDUCTOR.get().asItem());

        // Machine Repeater item: show unpowered (dark maroon)
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                return POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModItems.MACHINE_REPEATER.get());

        // Machine Toggle item: show unpowered (dark maroon)
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                return POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModItems.MACHINE_TOGGLE.get());

        // Machine Transformer item: show unpowered (dark maroon)
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                return POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModItems.MACHINE_TRANSFORMER.get());

        // Machine Timer item: show unpowered (dark maroon)
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                return POWER_COLORS[0];
            }
            return 0xFFFFFF;
        }, ModItems.MACHINE_TIMER.get());

        // Machine Fluid Pipe item: no tint needed
        event.register((stack, tintIndex) -> {
            return 0xFFFFFF;
        }, ModBlocks.MACHINE_FLUID_PIPE.get().asItem());
    }
}
