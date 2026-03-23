package com.ideomancer.ideomod.network;

import com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity;
import com.ideomancer.ideomod.block.machine.MachineTimerBlock;
import com.ideomancer.ideomod.config.IdeomodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server packet for changing the Machine Timer's duration.
 *
 * Sent when the player clicks a duration button in the Machine GUI.
 * Server validates the request: checks that the duration index is valid
 * within the config's durations array, that the block at the position is
 * actually a MachineTimerBlock, and that the player is close enough.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class TimerDurationPacket {

    private final BlockPos pos;
    private final int durationIndex;

    public TimerDurationPacket(BlockPos pos, int durationIndex) {
        this.pos = pos;
        this.durationIndex = durationIndex;
    }

    // ========================================================================
    // ENCODE / DECODE
    // ========================================================================

    public static void encode(TimerDurationPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.durationIndex);
    }

    public static TimerDurationPacket decode(FriendlyByteBuf buf) {
        return new TimerDurationPacket(buf.readBlockPos(), buf.readInt());
    }

    // ========================================================================
    // HANDLER
    // ========================================================================

    public static void handle(TimerDurationPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Validate duration index against config
            int[] durations = IdeomodConfig.getTimerDurations();
            if (msg.durationIndex < 0 || msg.durationIndex >= durations.length) return;

            // Validate distance (same check as stillValid)
            if (player.distanceToSqr(
                    msg.pos.getX() + 0.5,
                    msg.pos.getY() + 0.5,
                    msg.pos.getZ() + 0.5) > 64.0) return;

            // Validate block type
            BlockState state = player.level().getBlockState(msg.pos);
            if (!(state.getBlock() instanceof MachineTimerBlock)) return;

            // Validate block entity
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

            // Apply the duration change
            int newDuration = durations[msg.durationIndex];
            machineBE.setTimerDuration(newDuration);
            machineBE.syncToClients();
        });
        ctx.setPacketHandled(true);
    }
}
