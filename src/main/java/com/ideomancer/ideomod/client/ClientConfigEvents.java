package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.config.IdeomodConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * After leaving a server whose config this client was using (ConfigSyncPacket), go
 * back to this game's own config file.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientConfigEvents {

    private ClientConfigEvents() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        IdeomodConfig.restoreLocalConfig();
    }
}
