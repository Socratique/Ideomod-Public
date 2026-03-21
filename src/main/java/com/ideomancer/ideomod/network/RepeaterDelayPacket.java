package com.ideomancer.ideomod.network;

import com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity;
import com.ideomancer.ideomod.block.machine.MachineRepeaterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server packet for changing the Machine Repeater's delay setting.
 *
 * Sent when the player clicks a delay button (1-4) in the Machine GUI.
 * Server validates the request: checks range, checks that the block at the
 * position is actually a MachineRepeaterBlock, and checks that the player
 * is close enough to interact.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class RepeaterDelayPacket {

    private final BlockPos pos;
    private final int delay;

    public RepeaterDelayPacket(BlockPos pos, int delay) {
        this.pos = pos;
        this.delay = delay;
    }

    // ========================================================================
    // ENCODE / DECODE
    // ========================================================================

    public static void encode(RepeaterDelayPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.delay);
    }

    public static RepeaterDelayPacket decode(FriendlyByteBuf buf) {
        return new RepeaterDelayPacket(buf.readBlockPos(), buf.readInt());
    }

    // ========================================================================
    // HANDLER
    // ========================================================================

    public static void handle(RepeaterDelayPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Validate delay range
            if (msg.delay < 1 || msg.delay > 4) return;

            // Validate distance (same check as stillValid)
            if (player.distanceToSqr(
                    msg.pos.getX() + 0.5,
                    msg.pos.getY() + 0.5,
                    msg.pos.getZ() + 0.5) > 64.0) return;

            // Validate block type
            BlockState state = player.level().getBlockState(msg.pos);
            if (!(state.getBlock() instanceof MachineRepeaterBlock)) return;

            // Validate block entity
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

            // Apply the delay change
            machineBE.setRepeaterDelay(msg.delay);
            machineBE.syncToClients();
        });
        ctx.setPacketHandled(true);
    }
}
