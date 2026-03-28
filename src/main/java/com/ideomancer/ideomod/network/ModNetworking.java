package com.ideomancer.ideomod.network;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Central network channel for all Ideomod client-server packets.
 *
 * Register all packets in registerPackets(). Call from Ideomod.commonSetup().
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class ModNetworking {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ideomod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void registerPackets() {
        CHANNEL.messageBuilder(RepeaterDelayPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RepeaterDelayPacket::encode)
                .decoder(RepeaterDelayPacket::decode)
                .consumerMainThread(RepeaterDelayPacket::handle)
                .add();

        CHANNEL.messageBuilder(TransformerLevelPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TransformerLevelPacket::encode)
                .decoder(TransformerLevelPacket::decode)
                .consumerMainThread(TransformerLevelPacket::handle)
                .add();

        CHANNEL.messageBuilder(TimerDurationPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TimerDurationPacket::encode)
                .decoder(TimerDurationPacket::decode)
                .consumerMainThread(TimerDurationPacket::handle)
                .add();

        CHANNEL.messageBuilder(EnginePulseIntervalPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EnginePulseIntervalPacket::encode)
                .decoder(EnginePulseIntervalPacket::decode)
                .consumerMainThread(EnginePulseIntervalPacket::handle)
                .add();

        Ideomod.LOGGER.info("Registered {} network packet(s)", packetId);
    }
}
