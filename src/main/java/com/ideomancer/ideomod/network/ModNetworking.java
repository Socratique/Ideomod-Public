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

    // 2: ConfigSyncPacket added (2026-10-25). A client and server with different
    // versions refuse to connect instead of mixing settings.
    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ideomod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    /**
     * Whether this player may change the settings of the machine at pos: not a spectator, and
     * looking at that machine's menu (the only place the buttons are). The packets used to check
     * only the distance, so anyone within 8 blocks could change a machine without opening it.
     */
    public static boolean hasMenuOpen(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos pos) {
        return !player.isSpectator()
                && player.containerMenu instanceof com.ideomancer.ideomod.block.machine.MachineFrameMenu menu
                && menu.getBlockPos().equals(pos);
    }

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

        CHANNEL.messageBuilder(ConfigSyncPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConfigSyncPacket::encode)
                .decoder(ConfigSyncPacket::decode)
                .consumerMainThread(ConfigSyncPacket::handle)
                .add();

        Ideomod.LOGGER.info("Registered {} network packet(s)", packetId);
    }
}
