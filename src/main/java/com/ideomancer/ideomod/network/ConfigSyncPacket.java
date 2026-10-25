package com.ideomancer.ideomod.network;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.config.IdeomodConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Server-to-client packet carrying the server's whole Ideomod config.
 *
 * Sent to each player who joins, and to everyone after /ideomod reload. The client
 * uses these values while connected (IdeomodConfig.applyServerConfig) and goes back
 * to its own file when it leaves (ClientConfigEvents). See IdeomodConfig, SERVER ->
 * CLIENT SYNC, for why.
 *
 * Not sent to the host of a singleplayer or LAN game: that client is the same game
 * as the server and already reads the same config.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ConfigSyncPacket {

    private static final int MAX_LENGTH = 262144;

    private final String json;

    public ConfigSyncPacket(String json) {
        this.json = json;
    }

    // ========================================================================
    // ENCODE / DECODE
    // ========================================================================

    public static void encode(ConfigSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.json, MAX_LENGTH);
    }

    public static ConfigSyncPacket decode(FriendlyByteBuf buf) {
        return new ConfigSyncPacket(buf.readUtf(MAX_LENGTH));
    }

    // ========================================================================
    // HANDLER (client)
    // ========================================================================

    public static void handle(ConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> IdeomodConfig.applyServerConfig(msg.json));
        ctx.setPacketHandled(true);
    }

    // ========================================================================
    // SENDING (server)
    // ========================================================================

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendTo(player);
    }

    /** Send the config to everyone on the server (after /ideomod reload). */
    public static void sendToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendTo(player);
    }

    private static void sendTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || server.isSingleplayerOwner(player.getGameProfile())) return;
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ConfigSyncPacket(IdeomodConfig.toNetworkString()));
    }
}
