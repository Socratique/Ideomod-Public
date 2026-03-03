package com.ideomancer.ideomod.block.portal;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handles ender pearl portal activation.
 *
 * Portals are activated like nether portals with flint and steel:
 * build the frame, throw an ender pearl into the interior, and the
 * portal lights. The pearl is consumed and its teleport is cancelled.
 *
 * DETECTION LOGIC:
 * ================
 * When an ender pearl hits a block:
 * 1. Check if the hit block is Oddstone (frame edge hit) or if the pearl
 *    is inside the frame interior (air blocks within a valid frame).
 * 2. For frame edge hits: the pearl hit an Oddstone block in the frame.
 *    Try to detect a valid frame from that Oddstone.
 * 3. For interior hits: the pearl passed through air and hit the far side.
 *    Check adjacent blocks for Oddstone to find a frame.
 * 4. If a valid frame is found and successfully activated, cancel the
 *    pearl's teleport effect and discard the entity.
 *
 * This class is registered on MinecraftForge.EVENT_BUS in Ideomod.java.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class EnderPearlPortalHandler {

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        // Only care about ender pearls
        if (!(event.getProjectile() instanceof ThrownEnderpearl pearl)) return;

        // Server only
        Level level = pearl.level();
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Only care about block hits
        if (event.getRayTraceResult().getType() != HitResult.Type.BLOCK) return;
        BlockHitResult blockHit = (BlockHitResult) event.getRayTraceResult();

        BlockPos hitPos = blockHit.getBlockPos();
        BlockState hitState = level.getBlockState(hitPos);

        // Strategy 1: Pearl hit an Oddstone block directly (frame edge)
        if (hitState.is(ModBlocks.ODDSTONE.get())) {
            if (tryActivateFromOddstone(serverLevel, hitPos)) {
                cancelPearl(event, pearl);
                return;
            }
        }

        // Strategy 2: Pearl hit a block adjacent to air inside a frame.
        // Check the block position the pearl was heading INTO (the air block
        // adjacent to the hit face). This catches pearls thrown into the
        // frame interior that hit the opposite wall.
        BlockPos interiorPos = hitPos.relative(blockHit.getDirection());
        BlockState interiorState = level.getBlockState(interiorPos);

        if (interiorState.isAir()) {
            if (tryActivateFromInterior(serverLevel, interiorPos)) {
                cancelPearl(event, pearl);
                return;
            }
        }

        // Strategy 3: Pearl hit a corner block. Check adjacent Oddstone.
        if (PortalUtils.isCornerBlock(level, hitPos)) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = hitPos.relative(dir);
                if (level.getBlockState(neighbor).is(ModBlocks.ODDSTONE.get())) {
                    if (tryActivateFromOddstone(serverLevel, neighbor)) {
                        cancelPearl(event, pearl);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Attempts to activate a portal starting from an Oddstone frame block.
     * Delegates to OddstonePortalHandler.tryActivate().
     *
     * @return true if a portal was successfully activated
     */
    private boolean tryActivateFromOddstone(ServerLevel level, BlockPos oddstonePos) {
        return OddstonePortalHandler.tryActivate(level, oddstonePos);
    }

    /**
     * Attempts to activate a portal from an interior air position.
     * Checks all 6 neighbors for Oddstone blocks and tries activation
     * from each one found.
     *
     * @return true if a portal was successfully activated
     */
    private boolean tryActivateFromInterior(ServerLevel level, BlockPos interiorPos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = interiorPos.relative(dir);
            if (level.getBlockState(neighbor).is(ModBlocks.ODDSTONE.get())) {
                if (OddstonePortalHandler.tryActivate(level, neighbor)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Cancels the ender pearl's normal teleport behavior and discards it.
     * The pearl is consumed (not dropped as an item).
     */
    private void cancelPearl(ProjectileImpactEvent event, ThrownEnderpearl pearl) {
        event.setCanceled(true);
        pearl.discard();
        Ideomod.LOGGER.debug("Ender pearl consumed for portal activation at {}",
                pearl.blockPosition());
    }
}
