package com.ideomancer.ideomod.network;

import com.ideomancer.ideomod.block.machine.MachineEngineBlock;
import com.ideomancer.ideomod.block.machine.MachineEngineBlockEntity;
import com.ideomancer.ideomod.config.IdeomodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server packet for changing the Machine Engine's pulse interval.
 *
 * Sent when the player clicks an interval button in the Machine GUI.
 * Server validates the request: checks that the interval index is valid
 * within the config's durations array, that the block at the position is
 * actually a MachineEngineBlock, and that the player is close enough.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class EnginePulseIntervalPacket {

    private final BlockPos pos;
    private final int intervalIndex;

    public EnginePulseIntervalPacket(BlockPos pos, int intervalIndex) {
        this.pos = pos;
        this.intervalIndex = intervalIndex;
    }

    // ========================================================================
    // ENCODE / DECODE
    // ========================================================================

    public static void encode(EnginePulseIntervalPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.intervalIndex);
    }

    public static EnginePulseIntervalPacket decode(FriendlyByteBuf buf) {
        return new EnginePulseIntervalPacket(buf.readBlockPos(), buf.readInt());
    }

    // ========================================================================
    // HANDLER
    // ========================================================================

    public static void handle(EnginePulseIntervalPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Validate interval index against config
            int[] durations = IdeomodConfig.getEngineDurations();
            if (msg.intervalIndex < 0 || msg.intervalIndex >= durations.length) return;

            // Validate distance (same check as stillValid)
            if (player.distanceToSqr(
                    msg.pos.getX() + 0.5,
                    msg.pos.getY() + 0.5,
                    msg.pos.getZ() + 0.5) > 64.0) return;

            // Validate block type
            BlockState state = player.level().getBlockState(msg.pos);
            if (!(state.getBlock() instanceof MachineEngineBlock)) return;

            // Validate block entity
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (!(be instanceof MachineEngineBlockEntity engineBE)) return;

            // Apply the interval change
            int newInterval = durations[msg.intervalIndex];
            engineBE.setPulseInterval(newInterval);
            engineBE.syncToClients();
        });
        ctx.setPacketHandled(true);
    }
}
