package com.ideomancer.ideomod.client;

import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Adds inner block info to the F3 debug screen when looking at a Framed Block.
 *
 * Appends a line like "Inner Block: minecraft:stone" to the left-side debug text
 * immediately after the block state line.
 *
 * Register this on the Forge event bus (MinecraftForge.EVENT_BUS) in client setup.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlockDebugOverlay {

    @SubscribeEvent
    public void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!mc.options.renderDebug) return;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (!(state.getBlock() instanceof FramedBlock)) return;

        if (mc.level.getBlockEntity(pos) instanceof FramedBlockEntity fbe && fbe.hasInnerBlock()) {
            ResourceLocation innerBlockId = fbe.getInnerBlockId();
            event.getRight().add("Inner Block: " + innerBlockId);
        } else {
            event.getRight().add("Inner Block: [empty]");
        }
    }
}
