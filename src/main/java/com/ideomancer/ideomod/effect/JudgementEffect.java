package com.ideomancer.ideomod.effect;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * The "Judgement" effect.
 * 
 * When a player kills a non-hostile mob, there's a rare chance (1/200)
 * that all nearby passive mobs freeze, turn to stare at the player,
 * and go completely silent for 5 seconds. Then they resume as normal.
 * 
 * HOW THE STARING WORKS:
 * ======================
 * Mobs have an AI system made of "goals" (wander, eat grass, panic, etc.).
 * When Judgement triggers, we call setNoAi(true) on each mob, which
 * freezes all their goals and movement. Then every game tick (1/20th
 * of a second), we manually set each mob's look rotation to face the
 * player. After the duration expires, we set setNoAi(false) and they
 * go back to normal.
 * 
 * setNoAi(true) is the same thing that happens when you summon a mob
 * with {NoAI:1} in a command - it just stands there, frozen. We toggle
 * it on and off to create the freeze effect.
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class JudgementEffect {

    // 1 in 200 chance on each passive mob kill
    private static final int CHANCE = 200;

    // How far to scan for mobs (in blocks)
    private static final double RADIUS = 32.0;

    // How long the stare lasts (in ticks, 20 ticks = 1 second)
    private static final int DURATION_TICKS = 100; // 5 seconds

    // Active judgement sessions. Each player can only have one at a time.
    // Maps player UUID -> active session data
    private static final Map<UUID, JudgementSession> activeSessions = new HashMap<>();

    /**
     * Stores everything about an active Judgement event.
     */
    private static class JudgementSession {
        final UUID playerUUID;
        final List<Mob> staringMobs;
        int ticksRemaining;

        JudgementSession(UUID playerUUID, List<Mob> staringMobs, int duration) {
            this.playerUUID = playerUUID;
            this.staringMobs = staringMobs;
            this.ticksRemaining = duration;
        }
    }

    // ====================================================================
    // TRIGGER: Player kills a passive mob
    // ====================================================================

    @SubscribeEvent
    public static void onMobKilled(LivingDeathEvent event) {
        // Only care about server side
        if (event.getEntity().level().isClientSide()) return;

        // Was it killed by a player?
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer player)) return;

        // Was the victim non-hostile? (animals, villagers, etc.)
        Entity victim = event.getEntity();
        if (!isNonHostile(victim)) return;

        // Don't trigger if player already has an active judgement
        if (activeSessions.containsKey(player.getUUID())) return;

        // Roll the dice
        if (player.getRandom().nextInt(CHANCE) == 0) {
            trigger(player);
        }
    }

    /**
     * Checks if an entity is a non-hostile mob (passive or neutral-passive).
     * Animals (cows, sheep, pigs, etc.) and villagers count.
     */
    private static boolean isNonHostile(Entity entity) {
        if (entity instanceof Animal) return true;
        if (entity instanceof AbstractVillager) return true;
        return false;
    }

    // ====================================================================
    // ACTIVATION: Can be called by event OR by command
    // ====================================================================

    /**
     * Triggers the Judgement effect on a player.
     * Called either from the kill event (rare chance) or the /judgement command.
     * 
     * @param player The player who all mobs will stare at
     */
    public static void trigger(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 playerPos = player.position();

        // Build a box around the player to scan for mobs
        AABB scanBox = new AABB(
                playerPos.x - RADIUS, playerPos.y - RADIUS, playerPos.z - RADIUS,
                playerPos.x + RADIUS, playerPos.y + RADIUS, playerPos.z + RADIUS
        );

        // Find all non-hostile mobs in range
        List<Mob> targets = new ArrayList<>();
        for (Entity entity : level.getEntities(player, scanBox)) {
            if (entity instanceof Mob mob && isNonHostile(entity)) {
                // Skip any that already have NoAI (don't mess with armor stands, etc.)
                if (mob.isNoAi()) continue;
                targets.add(mob);
            }
        }

        if (targets.isEmpty()) return;

        // Freeze all of them
        for (Mob mob : targets) {
            mob.setNoAi(true);
        }

        // Store the session
        activeSessions.put(player.getUUID(), new JudgementSession(
                player.getUUID(), targets, DURATION_TICKS
        ));

        Ideomod.LOGGER.info("Judgement triggered on {} - {} mobs staring",
                player.getName().getString(), targets.size());
    }

    // ====================================================================
    // TICK: Update staring direction every tick, end when timer expires
    // ====================================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // Only tick at the END of each server tick (avoid double-processing)
        if (event.phase != TickEvent.Phase.END) return;

        if (activeSessions.isEmpty()) return;

        // Process all active sessions (use iterator so we can remove expired ones)
        Iterator<Map.Entry<UUID, JudgementSession>> it = activeSessions.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, JudgementSession> entry = it.next();
            JudgementSession session = entry.getValue();

            // Find the player (they might have logged off)
            ServerPlayer player = null;
            for (Mob mob : session.staringMobs) {
                if (!mob.isRemoved()) {
                    // Get the server from any living mob's level
                    player = ((ServerLevel) mob.level()).getServer()
                            .getPlayerList().getPlayer(session.playerUUID);
                    break;
                }
            }

            // If player is gone or all mobs are dead, end the session
            if (player == null) {
                endSession(session);
                it.remove();
                continue;
            }

            // Count down
            session.ticksRemaining--;

            if (session.ticksRemaining <= 0) {
                // Time's up - release the mobs
                endSession(session);
                it.remove();
                continue;
            }

            // Force all mobs to look at the player
            for (Mob mob : session.staringMobs) {
                if (mob.isRemoved()) continue;

                // Calculate the look direction from mob to player
                Vec3 mobPos = mob.getEyePosition();
                Vec3 plrPos = player.getEyePosition();
                Vec3 delta = plrPos.subtract(mobPos);

                // Convert to yaw (left-right rotation) and pitch (up-down)
                // Yaw: angle on the horizontal plane. 0 = south, 90 = west, etc.
                // Math.atan2 gives us the angle from the delta x/z components
                double horizontalDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                float yaw = (float) (Math.atan2(-delta.x, delta.z) * (180.0 / Math.PI));
                float pitch = (float) (Math.atan2(-delta.y, horizontalDist) * (180.0 / Math.PI));

                // Set both head and body rotation so they fully face the player
                mob.setYRot(yaw);
                mob.setXRot(pitch);
                mob.setYHeadRot(yaw);
                mob.setYBodyRot(yaw);
            }
        }
    }

    /**
     * Ends a judgement session - unfreezes all mobs.
     */
    private static void endSession(JudgementSession session) {
        for (Mob mob : session.staringMobs) {
            if (!mob.isRemoved()) {
                mob.setNoAi(false);
            }
        }
        Ideomod.LOGGER.debug("Judgement ended for player {}", session.playerUUID);
    }
}
