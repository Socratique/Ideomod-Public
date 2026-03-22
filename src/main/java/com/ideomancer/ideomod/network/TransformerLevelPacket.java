package com.ideomancer.ideomod.network;

import com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity;
import com.ideomancer.ideomod.block.machine.MachineTransformerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server packet for changing the Machine Transformer's output level.
 *
 * Sent when the player clicks a level button (1-15) in the Machine GUI.
 * Server validates the request: checks range, checks that the block at the
 * position is actually a MachineTransformerBlock, and checks that the player
 * is close enough to interact.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class TransformerLevelPacket {

    private final BlockPos pos;
    private final int level;

    public TransformerLevelPacket(BlockPos pos, int level) {
        this.pos = pos;
        this.level = level;
    }

    // ========================================================================
    // ENCODE / DECODE
    // ========================================================================

    public static void encode(TransformerLevelPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.level);
    }

    public static TransformerLevelPacket decode(FriendlyByteBuf buf) {
        return new TransformerLevelPacket(buf.readBlockPos(), buf.readInt());
    }

    // ========================================================================
    // HANDLER
    // ========================================================================

    public static void handle(TransformerLevelPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Validate level range
            if (msg.level < 1 || msg.level > 15) return;

            // Validate distance (same check as stillValid)
            if (player.distanceToSqr(
                    msg.pos.getX() + 0.5,
                    msg.pos.getY() + 0.5,
                    msg.pos.getZ() + 0.5) > 64.0) return;

            // Validate block type
            BlockState state = player.level().getBlockState(msg.pos);
            if (!(state.getBlock() instanceof MachineTransformerBlock)) return;

            // Validate block entity
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (!(be instanceof MachineFrameBlockEntity machineBE)) return;

            // Apply the level change
            machineBE.setTransformerLevel(msg.level);
            machineBE.syncToClients();
        });
        ctx.setPacketHandled(true);
    }
}
