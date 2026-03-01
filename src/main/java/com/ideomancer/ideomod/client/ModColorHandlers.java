package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.Ideomod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers block and item color handlers for tintIndex-based coloring.
 *
 * TODO(machine-conductor): Restore block/item color handlers when Machine Conductor
 * specialization is implemented. This file is intentionally kept as a registration
 * skeleton. See ROADMAP_STATUS.md for Machine Conductor tracking.
 *
 * Previously handled Machine Block power color gradient (steel grey to red).
 * Machine Frame rework removed conduction from the base block, so that
 * color handler is removed. It will return when the Machine Conductor
 * specialization is implemented.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModColorHandlers {

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        // Machine Block power color REMOVED -- conduction stripped from base block.
        // Will return as Machine Conductor specialization color handler.
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // Machine Block item color REMOVED -- matches block change above.
    }
}
