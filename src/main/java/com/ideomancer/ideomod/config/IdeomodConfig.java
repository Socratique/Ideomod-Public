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
 * Central configuration file for all Ideomod systems.
 *
 * Config file location: config/ideomod/ideomod.json
 *
 * Contains configurable values for:
 * - Oddstone: sympathetic radius, affected mobs list
 * - Pistons: ranged piston scan range, base push limit
 * - Machine Braces: push limit per brace, max braces, max push limit
 * - Pipes: item and fluid transfer cooldowns
 * - Portals: teleport cooldown, scatter distance range, scatter damage
 * - Judgement Effect: trigger chance, search radius, stare duration
 * - Furnace Array: max cluster dimensions
 * - Timer: duration presets, default selection index
 *
 * On first launch, the file is auto-generated with sensible defaults.
 * Players can then modify values to tune gameplay.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class IdeomodConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();
    private static volatile boolean loaded = false;

    // ========================================================================
    // ODDSTONE
    // ========================================================================

    /** How far Oddstone blocks link sympathetically when pushed (in blocks). */
    private static int sympatheticRadius = 12;
    /** Maximum allowed sympathetic radius (performance clamp). */
    private static final int MAX_SYMPATHETIC_RADIUS = 16;

    /** Mobs that are repelled by Oddstone pathfinding. */
    private static Set<String> affectedMobs = new HashSet<>();

    // ========================================================================
    // PISTONS & MOVEMENT
    // ========================================================================

    /** How far Repulser/Attractor blocks scan for targets (in blocks). */
    private static int rangedPistonScanRange = 12;

    /** Base piston push limit (vanilla default is 12). */
    private static int basePushLimit = 12;

    // ========================================================================
    // MACHINE BRACES
    // ========================================================================

    /** Additional push limit each Machine Brace adds. */
    private static int pushLimitPerBrace = 6;

    /** Maximum number of Machine Braces that contribute to push limit. */
    private static int maxBraces = 8;

    /** Hard cap on total push limit with braces. */
    private static int maxPushLimit = 60;

    // ========================================================================
    // PIPES
    // ========================================================================

    /** Item pipe transfer cooldown in ticks (vanilla hopper = 8). */
    private static int itemPipeTransferCooldown = 8;

    /** Fluid pipe transfer cooldown in ticks. */
    private static int fluidPipeTransferCooldown = 8;

    // ========================================================================
    // PORTALS
    // ========================================================================

    /** Cooldown between portal uses in ticks (80 = 4 seconds). */
    private static int portalTeleportCooldown = 80;

    /** Minimum scatter teleport distance for unlinked portals (in blocks). */
    private static int scatterDistanceMin = 200;

    /** Maximum scatter teleport distance for unlinked portals (in blocks). */
    private static int scatterDistanceMax = 1000;

    /** Damage dealt by scatter teleport (in half-hearts, 10 = 5 hearts). */
    private static float scatterDamage = 10.0F;

    // ========================================================================
    // JUDGEMENT EFFECT
    // ========================================================================

    /** 1-in-N chance Judgement triggers on passive mob kill. */
    private static int judgementChance = 200;

    /** How far (in blocks) to scan for mobs when Judgement triggers. */
    private static double judgementRadius = 32.0;

    /** How long the stare lasts (in ticks, 100 = 5 seconds). */
    private static int judgementDurationTicks = 100;

    // ========================================================================
    // FURNACE ARRAY
    // ========================================================================

    /** Maximum furnace array cluster width. */
    private static int furnaceArrayMaxWidth = 5;

    /** Maximum furnace array cluster height. */
    private static int furnaceArrayMaxHeight = 5;

    // ========================================================================
    // TIMER
    // ========================================================================

    /** Available duration presets for the Machine Timer, in game ticks. */
    private static int[] timerDurations = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80};

    /** Default selected index into timerDurations array (0-based). Index 3 = 20 ticks. */
    private static int timerDefaultIndex = 3;

    // ========================================================================
    // ENGINE
    // ========================================================================

    /** Available interval presets for the Machine Engine, in game ticks. Increments of 5. */
    private static int[] engineDurations = {5, 10, 15, 20, 25, 30, 35, 40};

    /** Default selected index into engineDurations array (0-based). Index 3 = 20 ticks. */
    private static int engineDefaultIndex = 3;

    // ========================================================================
    // PULSER
    // ========================================================================

    /** Fixed pulse duration for the Machine Pulser, in game ticks. */
    private static int pulserPulseDuration = 2;

    // ========================================================================
    // GETTERS — Oddstone
    // ========================================================================

    public static int getSympatheticRadius() {
        ensureLoaded();
        return sympatheticRadius;
    }

    public static boolean isMobAffected(String mobId) {
        ensureLoaded();
        return affectedMobs.contains(mobId);
    }

    // ========================================================================
    // GETTERS — Pistons & Movement
    // ========================================================================

    public static int getRangedPistonScanRange() {
        ensureLoaded();
        return rangedPistonScanRange;
    }

    public static int getBasePushLimit() {
        ensureLoaded();
        return basePushLimit;
    }

    // ========================================================================
    // GETTERS — Machine Braces
    // ========================================================================

    public static int getPushLimitPerBrace() {
        ensureLoaded();
        return pushLimitPerBrace;
    }

    public static int getMaxBraces() {
        ensureLoaded();
        return maxBraces;
    }

    public static int getMaxPushLimit() {
        ensureLoaded();
        return maxPushLimit;
    }

    // ========================================================================
    // GETTERS — Pipes
    // ========================================================================

    public static int getItemPipeTransferCooldown() {
        ensureLoaded();
        return itemPipeTransferCooldown;
    }

    public static int getFluidPipeTransferCooldown() {
        ensureLoaded();
        return fluidPipeTransferCooldown;
    }

    // ========================================================================
    // GETTERS — Portals
    // ========================================================================

    public static int getPortalTeleportCooldown() {
        ensureLoaded();
        return portalTeleportCooldown;
    }

    public static int getScatterDistanceMin() {
        ensureLoaded();
        return scatterDistanceMin;
    }

    public static int getScatterDistanceMax() {
        ensureLoaded();
        return scatterDistanceMax;
    }

    public static float getScatterDamage() {
        ensureLoaded();
        return scatterDamage;
    }

    // ========================================================================
    // GETTERS — Judgement Effect
    // ========================================================================

    public static int getJudgementChance() {
        ensureLoaded();
        return judgementChance;
    }

    public static double getJudgementRadius() {
        ensureLoaded();
        return judgementRadius;
    }

    public static int getJudgementDurationTicks() {
        ensureLoaded();
        return judgementDurationTicks;
    }

    // ========================================================================
    // GETTERS — Furnace Array
    // ========================================================================

    public static int getFurnaceArrayMaxWidth() {
        ensureLoaded();
        return furnaceArrayMaxWidth;
    }

    public static int getFurnaceArrayMaxHeight() {
        ensureLoaded();
        return furnaceArrayMaxHeight;
    }

    // ========================================================================
    // GETTERS — Timer
    // ========================================================================

    /**
     * Get the array of available timer duration presets (in game ticks).
     * Returns a defensive copy.
     */
    public static int[] getTimerDurations() {
        ensureLoaded();
        return timerDurations.clone();
    }

    /**
     * Get the default selected index into the timer durations array.
     */
    public static int getTimerDefaultIndex() {
        ensureLoaded();
        return timerDefaultIndex;
    }

    /**
     * Get the default timer duration in ticks (resolves the default index).
     */
    public static int getTimerDefaultDuration() {
        ensureLoaded();
        if (timerDefaultIndex >= 0 && timerDefaultIndex < timerDurations.length) {
            return timerDurations[timerDefaultIndex];
        }
        return 20; // Fallback
    }

    // ========================================================================
    // GETTERS — Engine
    // ========================================================================

    /**
     * Get the array of available engine interval presets (in game ticks).
     * Returns a defensive copy.
     */
    public static int[] getEngineDurations() {
        ensureLoaded();
        return engineDurations.clone();
    }

    /**
     * Get the default selected index into the engine durations array.
     */
    public static int getEngineDefaultIndex() {
        ensureLoaded();
        return engineDefaultIndex;
    }

    /**
     * Get the default engine pulse interval in ticks (resolves the default index).
     */
    public static int getEngineDefaultDuration() {
        ensureLoaded();
        if (engineDefaultIndex >= 0 && engineDefaultIndex < engineDurations.length) {
            return engineDurations[engineDefaultIndex];
        }
        return 10; // Fallback
    }

    // ========================================================================
    // GETTERS — Pulser
    // ========================================================================

    /**
     * Get the fixed pulse duration for the Machine Pulser, in game ticks.
     * Default: 2 (= 1 redstone tick).
     */
    public static int getPulserPulseDuration() {
        ensureLoaded();
        return pulserPulseDuration;
    }

    // ========================================================================
    // LOADING & PARSING
    // ========================================================================

    private static void ensureLoaded() {
        if (!loaded) {
            synchronized (LOCK) {
                if (!loaded) loadConfig();
            }
        }
    }

    /**
     * Loads the config from disk, or creates a default one if it doesn't exist.
     * Called lazily on first access.
     */
    public static void loadConfig() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("ideomod");
        Path configFile = configDir.resolve("ideomod.json");

        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (Files.exists(configFile)) {
                String json = Files.readString(configFile);
                parseConfig(json);
                Ideomod.LOGGER.info("Loaded Ideomod config (sympathetic radius={}, push limit={}, {} affected mobs, {} timer presets)",
                        sympatheticRadius, basePushLimit, affectedMobs.size(), timerDurations.length);
            } else {
                populateDefaultMobs();
                createDefaultConfig(configFile);
                Ideomod.LOGGER.info("Created default Ideomod config ({} mobs)", affectedMobs.size());
            }
        } catch (IOException e) {
            Ideomod.LOGGER.error("Failed to load Ideomod config, using defaults", e);
            populateDefaultMobs();
        }

        loaded = true;
    }

    /**
     * Parses the JSON config into runtime values.
     * Missing keys gracefully fall back to defaults.
     */
    private static void parseConfig(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);

        // --- Oddstone ---
        JsonObject oddstone = getSection(root, "oddstone");
        if (oddstone != null) {
            sympatheticRadius = clamp(getInt(oddstone, "sympathetic_radius", sympatheticRadius), 1, MAX_SYMPATHETIC_RADIUS);

            affectedMobs.clear();
            if (oddstone.has("affected_mobs")) {
                JsonArray mobs = oddstone.getAsJsonArray("affected_mobs");
                for (int i = 0; i < mobs.size(); i++) {
                    affectedMobs.add(mobs.get(i).getAsString());
                }
            } else {
                populateDefaultMobs();
            }
        } else {
            populateDefaultMobs();
        }

        // --- Pistons & Movement ---
        JsonObject pistons = getSection(root, "pistons");
        if (pistons != null) {
            rangedPistonScanRange = clamp(getInt(pistons, "ranged_scan_range", rangedPistonScanRange), 1, 64);
            basePushLimit = clamp(getInt(pistons, "base_push_limit", basePushLimit), 1, 128);
        }

        // --- Machine Braces ---
        JsonObject braces = getSection(root, "machine_braces");
        if (braces != null) {
            pushLimitPerBrace = clamp(getInt(braces, "push_limit_per_brace", pushLimitPerBrace), 1, 64);
            maxBraces = clamp(getInt(braces, "max_braces", maxBraces), 0, 64);
            maxPushLimit = clamp(getInt(braces, "max_push_limit", maxPushLimit), 1, 1024);
        }

        // --- Pipes ---
        JsonObject pipes = getSection(root, "pipes");
        if (pipes != null) {
            itemPipeTransferCooldown = clamp(getInt(pipes, "item_transfer_cooldown", itemPipeTransferCooldown), 1, 200);
            fluidPipeTransferCooldown = clamp(getInt(pipes, "fluid_transfer_cooldown", fluidPipeTransferCooldown), 1, 200);
        }

        // --- Portals ---
        JsonObject portals = getSection(root, "portals");
        if (portals != null) {
            portalTeleportCooldown = clamp(getInt(portals, "teleport_cooldown", portalTeleportCooldown), 1, 1200);
            scatterDistanceMin = clamp(getInt(portals, "scatter_distance_min", scatterDistanceMin), 1, 10000);
            scatterDistanceMax = clamp(getInt(portals, "scatter_distance_max", scatterDistanceMax), scatterDistanceMin, 30000);
            scatterDamage = clampFloat(getFloat(portals, "scatter_damage", scatterDamage), 0.0F, 100.0F);
        }

        // --- Judgement Effect ---
        JsonObject judgement = getSection(root, "judgement");
        if (judgement != null) {
            judgementChance = clamp(getInt(judgement, "chance", judgementChance), 1, 10000);
            judgementRadius = clampDouble(getDouble(judgement, "radius", judgementRadius), 1.0, 256.0);
            judgementDurationTicks = clamp(getInt(judgement, "duration_ticks", judgementDurationTicks), 1, 6000);
        }

        // --- Furnace Array ---
        JsonObject furnaceArray = getSection(root, "furnace_array");
        if (furnaceArray != null) {
            furnaceArrayMaxWidth = clamp(getInt(furnaceArray, "max_width", furnaceArrayMaxWidth), 1, 16);
            furnaceArrayMaxHeight = clamp(getInt(furnaceArray, "max_height", furnaceArrayMaxHeight), 1, 16);
        }

        // --- Timer ---
        JsonObject timer = getSection(root, "timer");
        if (timer != null) {
            if (timer.has("durations") && timer.get("durations").isJsonArray()) {
                JsonArray durArray = timer.getAsJsonArray("durations");
                int[] parsed = new int[durArray.size()];
                for (int i = 0; i < durArray.size(); i++) {
                    parsed[i] = clamp(durArray.get(i).getAsInt(), 1, 6000);
                }
                if (parsed.length > 0) {
                    timerDurations = parsed;
                }
            }
            timerDefaultIndex = clamp(getInt(timer, "default_index", timerDefaultIndex), 0, timerDurations.length - 1);
        }

        // --- Engine ---
        JsonObject engine = getSection(root, "engine");
        if (engine != null) {
            if (engine.has("durations") && engine.get("durations").isJsonArray()) {
                JsonArray engDurArray = engine.getAsJsonArray("durations");
                int[] parsed = new int[engDurArray.size()];
                for (int i = 0; i < engDurArray.size(); i++) {
                    parsed[i] = clamp(engDurArray.get(i).getAsInt(), 1, 6000);
                }
                if (parsed.length > 0) {
                    engineDurations = parsed;
                }
            }
            engineDefaultIndex = clamp(getInt(engine, "default_index", engineDefaultIndex), 0, engineDurations.length - 1);
        }

        // --- Pulser ---
        JsonObject pulser = getSection(root, "pulser");
        if (pulser != null) {
            pulserPulseDuration = clamp(getInt(pulser, "pulse_duration", pulserPulseDuration), 1, 100);
        }
    }

    // ========================================================================
    // CONFIG FILE CREATION
    // ========================================================================

    private static void createDefaultConfig(Path configFile) throws IOException {
        JsonObject root = new JsonObject();

        root.addProperty("_comment", "Ideomod Configuration — edit values below to tune gameplay. Delete this file to regenerate defaults.");

        // --- Oddstone ---
        JsonObject oddstone = new JsonObject();
        oddstone.addProperty("_comment", "Oddstone sympathetic movement and mob pathfinding avoidance");
        oddstone.addProperty("sympathetic_radius", sympatheticRadius);
        JsonArray mobArray = new JsonArray();
        affectedMobs.stream().sorted().forEach(mobArray::add);
        oddstone.add("affected_mobs", mobArray);
        root.add("oddstone", oddstone);

        // --- Pistons ---
        JsonObject pistons = new JsonObject();
        pistons.addProperty("_comment", "Ranged piston (Repulser/Attractor) scan distance and base push limit");
        pistons.addProperty("ranged_scan_range", rangedPistonScanRange);
        pistons.addProperty("base_push_limit", basePushLimit);
        root.add("pistons", pistons);

        // --- Machine Braces ---
        JsonObject braces = new JsonObject();
        braces.addProperty("_comment", "Machine Brace push limit scaling. Total limit = base + (braces * per_brace), capped at max_push_limit");
        braces.addProperty("push_limit_per_brace", pushLimitPerBrace);
        braces.addProperty("max_braces", maxBraces);
        braces.addProperty("max_push_limit", maxPushLimit);
        root.add("machine_braces", braces);

        // --- Pipes ---
        JsonObject pipes = new JsonObject();
        pipes.addProperty("_comment", "Transfer cooldowns in ticks (20 ticks = 1 second). Vanilla hopper is 8 ticks.");
        pipes.addProperty("item_transfer_cooldown", itemPipeTransferCooldown);
        pipes.addProperty("fluid_transfer_cooldown", fluidPipeTransferCooldown);
        root.add("pipes", pipes);

        // --- Portals ---
        JsonObject portals = new JsonObject();
        portals.addProperty("_comment", "Oddstone portal teleportation settings. Scatter teleport occurs for unlinked portals.");
        portals.addProperty("teleport_cooldown", portalTeleportCooldown);
        portals.addProperty("scatter_distance_min", scatterDistanceMin);
        portals.addProperty("scatter_distance_max", scatterDistanceMax);
        portals.addProperty("scatter_damage", scatterDamage);
        root.add("portals", portals);

        // --- Judgement ---
        JsonObject judgement = new JsonObject();
        judgement.addProperty("_comment", "Judgement effect — rare event when killing passive mobs. chance = 1-in-N. duration in ticks.");
        judgement.addProperty("chance", judgementChance);
        judgement.addProperty("radius", judgementRadius);
        judgement.addProperty("duration_ticks", judgementDurationTicks);
        root.add("judgement", judgement);

        // --- Furnace Array ---
        JsonObject furnaceArray = new JsonObject();
        furnaceArray.addProperty("_comment", "Furnace Array multiblock maximum dimensions (width x height cluster of furnaces)");
        furnaceArray.addProperty("max_width", furnaceArrayMaxWidth);
        furnaceArray.addProperty("max_height", furnaceArrayMaxHeight);
        root.add("furnace_array", furnaceArray);

        // --- Timer ---
        JsonObject timer = new JsonObject();
        timer.addProperty("_comment", "Machine Timer duration presets in game ticks (20 ticks = 1 second). default_index selects which button is pre-selected.");
        JsonArray durArray = new JsonArray();
        for (int d : timerDurations) {
            durArray.add(d);
        }
        timer.add("durations", durArray);
        timer.addProperty("default_index", timerDefaultIndex);
        root.add("timer", timer);

        // --- Engine ---
        JsonObject engine = new JsonObject();
        engine.addProperty("_comment", "Machine Engine pulse interval presets in game ticks. default_index selects which button is pre-selected.");
        JsonArray engDurArray = new JsonArray();
        for (int d : engineDurations) {
            engDurArray.add(d);
        }
        engine.add("durations", engDurArray);
        engine.addProperty("default_index", engineDefaultIndex);
        root.add("engine", engine);

        // --- Pulser ---
        JsonObject pulser = new JsonObject();
        pulser.addProperty("_comment", "Machine Pulser fixed pulse duration in game ticks (2 game ticks = 1 redstone tick). Range: 1-100.");
        pulser.addProperty("pulse_duration", pulserPulseDuration);
        root.add("pulser", pulser);

        String jsonStr = GSON.toJson(root);
        Files.writeString(configFile, jsonStr);
    }

    // ========================================================================
    // RELOAD
    // ========================================================================

    /**
     * Force a reload of the config. Useful for /reload commands.
     */
    public static void reloadConfig() {
        synchronized (LOCK) {
            loaded = false;
            affectedMobs.clear();
            loadConfig();
        }
    }

    // ========================================================================
    // DEFAULT MOB LIST
    // ========================================================================

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

    // ========================================================================
    // UTILITY HELPERS
    // ========================================================================

    private static JsonObject getSection(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : null;
    }

    private static int getInt(JsonObject obj, String key, int defaultVal) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultVal;
    }

    private static float getFloat(JsonObject obj, String key, float defaultVal) {
        return obj.has(key) ? obj.get(key).getAsFloat() : defaultVal;
    }

    private static double getDouble(JsonObject obj, String key, double defaultVal) {
        return obj.has(key) ? obj.get(key).getAsDouble() : defaultVal;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
