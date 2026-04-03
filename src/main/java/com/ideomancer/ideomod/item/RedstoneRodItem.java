package com.ideomancer.ideomod.item;

import com.ideomancer.ideomod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Redstone Rod — a handheld redstone pulse tool.
 *
 * Right-clicking any block delivers a 1-redstone-tick (2 game ticks) pulse
 * of signal strength 15 to the clicked face. Uses Forge's onItemUseFirst()
 * so that block GUIs (machine blocks, chests, etc.) are bypassed entirely.
 *
 * The pulse is injected via RedstoneRodSignalManager and made visible to
 * vanilla signal checks through LevelSignalMixin on Level.getSignal().
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class RedstoneRodItem extends Item {

    public RedstoneRodItem(Properties properties) {
        super(properties);
    }

    /**
     * Fires BEFORE Block.use(), preventing machine GUIs from opening.
     * This is Forge's priority-1 interaction hook.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();

        // Client side: consume the interaction silently (server handles logic)
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos targetPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        // Inject the pulse into the signal manager
        RedstoneRodSignalManager.injectPulse(level, targetPos, clickedFace);

        // --- Feedback ---

        // Click sound (like a lever)
        level.playSound(null, targetPos,
                SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                0.3F, 0.6F);

        // Redstone dust particles on the clicked face
        spawnFaceParticles((ServerLevel) level, targetPos, clickedFace);

        // Swing the player's arm
        if (context.getPlayer() != null) {
            context.getPlayer().swing(context.getHand());
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Spawns a small burst of redstone particles on the clicked face
     * to give visual feedback that the pulse was delivered.
     */
    private void spawnFaceParticles(ServerLevel level, BlockPos pos, Direction face) {
        // Center of the clicked face
        double cx = pos.getX() + 0.5 + face.getStepX() * 0.55;
        double cy = pos.getY() + 0.5 + face.getStepY() * 0.55;
        double cz = pos.getZ() + 0.5 + face.getStepZ() * 0.55;

        // Bright redstone-red particles
        DustParticleOptions redDust = new DustParticleOptions(
                new Vector3f(1.0F, 0.0F, 0.0F), 1.0F);

        for (int i = 0; i < 6; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 0.4;
            double oy = (level.random.nextDouble() - 0.5) * 0.4;
            double oz = (level.random.nextDouble() - 0.5) * 0.4;

            // Flatten the offset onto the face plane
            if (face.getAxis() == Direction.Axis.X) ox = 0;
            if (face.getAxis() == Direction.Axis.Y) oy = 0;
            if (face.getAxis() == Direction.Axis.Z) oz = 0;

            level.sendParticles(redDust,
                    cx + ox, cy + oy, cz + oz,
                    1, 0, 0, 0, 0);
        }
    }
}
