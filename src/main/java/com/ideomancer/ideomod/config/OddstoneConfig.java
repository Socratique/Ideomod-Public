package com.ideomancer.ideomod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ideomancer.ideomod.Ideomod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles loading/saving the Oddstone JSON config file.
 * 
 * Config file location: config/ideomod/oddstone.json
 * 
 * Contains:
 * - "sympathetic_radius": int (default 5) - how far Oddstone blocks
 *   can "feel" each other for sympathetic movement
 * - "affected_mobs": string[] - list of mob registry IDs that are
 *   repelled by Oddstone pathfinding. If a mob is NOT in this list,
 *   it walks over Oddstone normally.
 * 
 * On first launch, the file is auto-generated with ALL land mobs.
 * Players can then remove mobs from the list to create exceptions.
 */
public class OddstoneConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();
    
    // Runtime values (loaded from JSON)
    private static int sympatheticRadius = 5;
    private static Set<String> affectedMobs = new HashSet<>();
    private static volatile boolean loaded = false;

    /**
     * Gets the sympathetic movement radius (in blocks).
     * Oddstone blocks within this distance of a pushed Oddstone
     * will also be moved in the same direction.
     */
    public static int getSympatheticRadius() {
        if (!loaded) {
            synchronized (LOCK) {
                if (!loaded) loadConfig();
            }
        }
        return sympatheticRadius;
    }

    /**
     * Checks if a given mob should be repelled by Oddstone.
     * @param mobId The full registry ID, e.g. "minecraft:zombie"
     * @return true if the mob avoids Oddstone, false if it can walk on it
     */
    public static boolean isMobAffected(String mobId) {
        if (!loaded) {
            synchronized (LOCK) {
                if (!loaded) loadConfig();
            }
        }
        return affectedMobs.contains(mobId);
    }

    /**
     * Loads the config from disk, or creates a default one if it doesn't exist.
     * Called lazily on first access.
     */
    public static void loadConfig() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("ideomod");
        Path configFile = configDir.resolve("oddstone.json");

        try {
            // Create config directory if it doesn't exist
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (Files.exists(configFile)) {
                // Load existing config
                String json = Files.readString(configFile);
                parseConfig(json);
                Ideomod.LOGGER.info("Loaded Oddstone config ({} affected mobs, radius {})",
                        affectedMobs.size(), sympatheticRadius);
            } else {
                // Create default config with all land mobs
                createDefaultConfig(configFile);
                Ideomod.LOGGER.info("Created default Oddstone config ({} mobs)",
                        affectedMobs.size());
            }
        } catch (IOException e) {
            Ideomod.LOGGER.error("Failed to load Oddstone config, using defaults", e);
            populateDefaultMobs();
        }

        loaded = true;
    }

    /**
     * Parses JSON string into runtime config values.
     */
    private static void parseConfig(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        
        // Read radius (default to 5 if missing)
        if (root.has("sympathetic_radius")) {
            sympatheticRadius = root.get("sympathetic_radius").getAsInt();
            // Clamp to reasonable range
            sympatheticRadius = Math.max(1, Math.min(32, sympatheticRadius));
        }

        // Read mob list
        affectedMobs.clear();
        if (root.has("affected_mobs")) {
            JsonArray mobs = root.getAsJsonArray("affected_mobs");
            for (int i = 0; i < mobs.size(); i++) {
                affectedMobs.add(mobs.get(i).getAsString());
            }
        }
    }

    /**
     * Creates the default config file with all land mobs from 1.20.1.
     * Aquatic-only mobs (cod, salmon, etc.) are excluded since they
     * don't use ground pathfinding. Flying-only mobs (ghast, ender dragon)
     * are also excluded.
     */
    private static void createDefaultConfig(Path configFile) throws IOException {
        populateDefaultMobs();

        // Build JSON
        JsonObject root = new JsonObject();
        
        // Comment fields for player reference (JSON doesn't have comments,
        // so we use descriptive key names)
        root.addProperty("_comment1", "Oddstone Configuration");
        root.addProperty("_comment2", "sympathetic_radius: How far (in blocks) Oddstone blocks link to each other when pushed by pistons");
        root.addProperty("_comment3", "affected_mobs: Mobs that refuse to walk on Oddstone. Remove a mob to let it walk normally.");
        
        root.addProperty("sympathetic_radius", sympatheticRadius);

        // Sort mobs alphabetically for readability
        JsonArray mobArray = new JsonArray();
        affectedMobs.stream().sorted().forEach(mobArray::add);
        root.add("affected_mobs", mobArray);

        // Write to file
        String json = GSON.toJson(root);
        Files.writeString(configFile, json);
    }

    /**
     * Populates the default set of affected mobs.
     * This is every mob in vanilla 1.20.1 that uses ground-based pathfinding.
     * Excluded: purely aquatic (cod, salmon, tropical_fish, pufferfish, squid,
     * glow_squid, tadpole), purely flying (ghast, ender_dragon, phantom, vex,
     * bat, allay), and the wither (boss mob, flies).
     */
    private static void populateDefaultMobs() {
        affectedMobs.clear();
        
        // === PASSIVE MOBS ===
        affectedMobs.add("minecraft:axolotl");
        affectedMobs.add("minecraft:camel");
        affectedMobs.add("minecraft:cat");
        affectedMobs.add("minecraft:chicken");
        affectedMobs.add("minecraft:cow");
        affectedMobs.add("minecraft:donkey");
        affectedMobs.add("minecraft:fox");
        affectedMobs.add("minecraft:frog");
        affectedMobs.add("minecraft:horse");
        affectedMobs.add("minecraft:mooshroom");
        affectedMobs.add("minecraft:mule");
        affectedMobs.add("minecraft:ocelot");
        affectedMobs.add("minecraft:parrot");
        affectedMobs.add("minecraft:pig");
        affectedMobs.add("minecraft:rabbit");
        affectedMobs.add("minecraft:sheep");
        affectedMobs.add("minecraft:skeleton_horse");
        affectedMobs.add("minecraft:sniffer");
        affectedMobs.add("minecraft:snow_golem");
        affectedMobs.add("minecraft:strider");
        affectedMobs.add("minecraft:turtle");
        affectedMobs.add("minecraft:villager");
        affectedMobs.add("minecraft:wandering_trader");

        // === NEUTRAL MOBS ===
        affectedMobs.add("minecraft:bee");
        affectedMobs.add("minecraft:cave_spider");
        affectedMobs.add("minecraft:dolphin");
        affectedMobs.add("minecraft:enderman");
        affectedMobs.add("minecraft:goat");
        affectedMobs.add("minecraft:iron_golem");
        affectedMobs.add("minecraft:llama");
        affectedMobs.add("minecraft:panda");
        affectedMobs.add("minecraft:piglin");
        affectedMobs.add("minecraft:polar_bear");
        affectedMobs.add("minecraft:spider");
        affectedMobs.add("minecraft:trader_llama");
        affectedMobs.add("minecraft:wolf");
        affectedMobs.add("minecraft:zombified_piglin");

        // === HOSTILE MOBS ===
        affectedMobs.add("minecraft:blaze");
        affectedMobs.add("minecraft:creeper");
        affectedMobs.add("minecraft:drowned");
        affectedMobs.add("minecraft:elder_guardian");
        affectedMobs.add("minecraft:endermite");
        affectedMobs.add("minecraft:evoker");
        affectedMobs.add("minecraft:guardian");
        affectedMobs.add("minecraft:hoglin");
        affectedMobs.add("minecraft:husk");
        affectedMobs.add("minecraft:magma_cube");
        affectedMobs.add("minecraft:piglin_brute");
        affectedMobs.add("minecraft:pillager");
        affectedMobs.add("minecraft:ravager");
        affectedMobs.add("minecraft:shulker");
        affectedMobs.add("minecraft:silverfish");
        affectedMobs.add("minecraft:skeleton");
        affectedMobs.add("minecraft:slime");
        affectedMobs.add("minecraft:stray");
        affectedMobs.add("minecraft:vindicator");
        affectedMobs.add("minecraft:warden");
        affectedMobs.add("minecraft:witch");
        affectedMobs.add("minecraft:wither_skeleton");
        affectedMobs.add("minecraft:zoglin");
        affectedMobs.add("minecraft:zombie");
        affectedMobs.add("minecraft:zombie_villager");
    }

    /**
     * Force a reload of the config (useful for a future /reload command).
     */
    public static void reloadConfig() {
        synchronized (LOCK) {
            loaded = false;
            affectedMobs.clear();
            loadConfig();
        }
    }
}
