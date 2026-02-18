package com.ideomancer.ideomod.structure;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central brain of the multiblock structure system. Manages the full lifecycle
 * of structures: detection, registration, tracking, update handling, effect
 * dispatch, persistence, and query API.
 *
 * Lives as per-level SavedData, same pattern as OddstonePortalSavedData.
 * All operations are server-side, server-thread only.
 *
 * === Data Structures ===
 *
 * structures:        UUID → StructureRegistration (all registered structures)
 * chunkIndex:        ChunkPos → Set<UUID> (which structures overlap each chunk)
 * typeIndex:         String → Set<UUID> (structures grouped by definition name)
 * coreExclusivity:   BlockPos → Map<String, UUID> (one structure per type per core)
 * trackedCores:      Set<BlockPos> (event-placed cores not yet in structures)
 *
 * Core detection uses two approaches: block entity iteration from chunk maps
 * (catches furnaces cheaply) and the event-populated trackedCores set (catches
 * future non-block-entity cores). No chunk scanning on load.
 *
 * === Event Flow ===
 *
 * Block placed/broken → check chunk index for nearby structures → rescan affected
 *                     → check if placed block is a core → try new formation
 *                     → check if placed block is frame material → search nearby for cores → try formation
 */
public class StructureManager extends SavedData {

    private static final String DATA_NAME = "ideomod_structures";
    private static final String TAG_STRUCTURES = "Structures";

    // Maximum distance to check for tracked cores near a block change.
    // Derived from max structure size: 7×7×3 frame → ~7 blocks from any core to farthest edge.
    private static final int CORE_SEARCH_RADIUS = 8;

    // ========================================================================
    // Structure Definition Registry (loaded at mod init, not persisted)
    // ========================================================================

    /** All registered structure definitions, keyed by name. */
    private static final Map<String, StructureDefinition> definitions = new HashMap<>();

    /**
     * Registers a structure definition. Called during mod init.
     * Must be called before any levels load.
     */
    public static void registerDefinition(StructureDefinition definition) {
        definitions.put(definition.getName(), definition);
        Ideomod.LOGGER.info("Registered structure definition: {}", definition.getName());
    }

    /**
     * Gets a definition by name. Returns null if not registered.
     */
    @Nullable
    public static StructureDefinition getDefinition(String name) {
        return definitions.get(name);
    }

    /**
     * Gets all definitions that have the given block as a core type.
     */
    public static List<StructureDefinition> getDefinitionsForCore(ResourceLocation blockId) {
        List<StructureDefinition> matches = new ArrayList<>();
        for (StructureDefinition def : definitions.values()) {
            if (def.isCoreBlock(blockId)) {
                matches.add(def);
            }
        }
        return matches;
    }

    // ========================================================================
    // Instance Data (per-level)
    // ========================================================================

    /** All registered structures, keyed by UUID. */
    private final Map<UUID, StructureRegistration> structures = new HashMap<>();

    /** Chunk → structure ID mapping for fast block-change lookups. */
    private final Map<ChunkPos, Set<UUID>> chunkIndex = new HashMap<>();

    /** Structure type → structure ID mapping for type-based queries. */
    private final Map<String, Set<UUID>> typeIndex = new HashMap<>();

    /**
     * Core exclusivity: position → (type → structure ID).
     * Enforces one structure per definition type per core position.
     */
    private final Map<BlockPos, Map<String, UUID>> coreExclusivity = new HashMap<>();

    // Note: Core detection uses two complementary approaches:
    // 1. Block entity iteration (covers furnaces and any block-entity cores)
    // 2. trackedCores set (covers future non-block-entity cores)
    // The set is populated cheaply from placement events — no chunk scanning.
    // On block break it's cleaned up. Both are checked in findAndScanNearbyCores().
    private final Set<BlockPos> trackedCores = new HashSet<>();

    // ========================================================================
    // SavedData Factory
    // ========================================================================

    public StructureManager() {
        super();
    }

    private StructureManager(CompoundTag tag) {
        super();
        load(tag);
    }

    /**
     * Gets the StructureManager for a ServerLevel, creating it if needed.
     * Same pattern as OddstonePortalSavedData.
     */
    public static StructureManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                StructureManager::new,    // load from NBT
                StructureManager::new,    // create fresh
                DATA_NAME
        );
    }

    // ========================================================================
    // Registration / Deregistration
    // ========================================================================

    /**
     * Attempts to register a new structure from a valid scan result.
     * Checks core exclusivity, assigns a UUID, builds indexes, and
     * dispatches the effect handler's onFormed callback.
     *
     * @return true if registration succeeded
     */
    private boolean registerStructure(StructureResult result, ServerLevel level) {
        if (!result.isValid()) return false;

        String defName = result.getDefinitionName();
        BlockPos corePos = result.getCorePos();

        // Core exclusivity check: is this core already anchoring a structure of this type?
        Map<String, UUID> coreTypes = coreExclusivity.get(corePos);
        if (coreTypes != null && coreTypes.containsKey(defName)) {
            return false;
        }

        // Create the registration
        UUID id = UUID.randomUUID();
        StructureRegistration reg = new StructureRegistration(id, defName, result);

        // Store in main map
        structures.put(id, reg);

        // Update chunk index
        for (ChunkPos chunk : reg.getOverlappingChunks()) {
            chunkIndex.computeIfAbsent(chunk, k -> new HashSet<>()).add(id);
        }

        // Update type index
        typeIndex.computeIfAbsent(defName, k -> new HashSet<>()).add(id);

        // Update core exclusivity
        coreExclusivity.computeIfAbsent(corePos, k -> new HashMap<>()).put(defName, id);

        // Remove from tracked set — it's now part of a registered structure
        trackedCores.remove(corePos);

        // Dispatch effect handler
        StructureDefinition def = definitions.get(defName);
        if (def != null && def.getEffectHandler() != null) {
            def.getEffectHandler().onFormed(result, level);
        }

        setDirty();
        Ideomod.LOGGER.debug("Registered structure '{}' at {} (ID: {})", defName, corePos, id);
        notifyDebugPlayers(level, Component.literal("[Structure] ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("FORMED: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(defName).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" at ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(corePos.toShortString()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (" + result.getEffectorPositions().size() + " cores)")
                        .withStyle(ChatFormatting.GRAY)));
        return true;
    }

    /**
     * Deregisters a structure. Cleans up all indexes and dispatches the
     * effect handler's onRemoved callback.
     */
    private void deregisterStructure(UUID id, ServerLevel level) {
        StructureRegistration reg = structures.remove(id);
        if (reg == null) return;

        String defName = reg.getDefinitionName();
        StructureResult lastResult = reg.getCurrentResult();
        BlockPos corePos = lastResult.getCorePos();

        // Clean up chunk index
        for (ChunkPos chunk : reg.getOverlappingChunks()) {
            Set<UUID> chunkStructures = chunkIndex.get(chunk);
            if (chunkStructures != null) {
                chunkStructures.remove(id);
                if (chunkStructures.isEmpty()) {
                    chunkIndex.remove(chunk);
                }
            }
        }

        // Clean up type index
        Set<UUID> typeSet = typeIndex.get(defName);
        if (typeSet != null) {
            typeSet.remove(id);
            if (typeSet.isEmpty()) {
                typeIndex.remove(defName);
            }
        }

        // Clean up core exclusivity
        Map<String, UUID> coreTypes = coreExclusivity.get(corePos);
        if (coreTypes != null) {
            coreTypes.remove(defName);
            if (coreTypes.isEmpty()) {
                coreExclusivity.remove(corePos);
            }
        }

        // Dispatch effect handler
        StructureDefinition def = definitions.get(defName);
        if (def != null && def.getEffectHandler() != null) {
            def.getEffectHandler().onRemoved(lastResult, level);
        }

        // Re-track the core if it still exists as a core block
        BlockState coreState = level.getBlockState(corePos);
        ResourceLocation coreBlockId = BuiltInRegistries.BLOCK.getKey(coreState.getBlock());
        if (!getDefinitionsForCore(coreBlockId).isEmpty()) {
            trackedCores.add(corePos.immutable());
        }

        setDirty();
        Ideomod.LOGGER.debug("Deregistered structure '{}' at {} (ID: {})", defName, corePos, id);
        notifyDebugPlayers(level, Component.literal("[Structure] ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal("REMOVED: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(defName).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" at ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(corePos.toShortString()).withStyle(ChatFormatting.AQUA)));
    }

    /**
     * Rescans a registered structure and updates or deregisters based on the result.
     */
    private void rescanStructure(UUID id, ServerLevel level) {
        StructureRegistration reg = structures.get(id);
        if (reg == null) return;

        String defName = reg.getDefinitionName();
        StructureDefinition def = definitions.get(defName);
        if (def == null) {
            // Definition was unloaded? Deregister.
            deregisterStructure(id, level);
            return;
        }

        StructureResult oldResult = reg.getCurrentResult();
        BlockPos corePos = oldResult.getCorePos();

        // Run a fresh scan from the core position
        StructureResult newResult = StructureScanner.scan(corePos, def, level);

        if (!newResult.isValid()) {
            // Structure is no longer valid — deregister
            deregisterStructure(id, level);
            return;
        }

        // Structure is still valid — check if anything changed
        boolean boundsChanged = !newResult.getBoundsMin().equals(oldResult.getBoundsMin())
                || !newResult.getBoundsMax().equals(oldResult.getBoundsMax());

        // Update the registration with the new result
        if (boundsChanged) {
            // Remove old chunk index entries
            for (ChunkPos chunk : reg.getOverlappingChunks()) {
                Set<UUID> chunkStructures = chunkIndex.get(chunk);
                if (chunkStructures != null) {
                    chunkStructures.remove(id);
                    if (chunkStructures.isEmpty()) {
                        chunkIndex.remove(chunk);
                    }
                }
            }
        }

        reg.updateResult(newResult);

        if (boundsChanged) {
            // Re-add chunk index entries with new bounds
            for (ChunkPos chunk : reg.getOverlappingChunks()) {
                chunkIndex.computeIfAbsent(chunk, k -> new HashSet<>()).add(id);
            }
        }

        // Dispatch effect handler update
        if (def.getEffectHandler() != null) {
            def.getEffectHandler().onUpdated(oldResult, newResult, level);
        }

        setDirty();
    }

    // ========================================================================
    // Block Change Detection
    // ========================================================================

    /**
     * Called when a block is placed or broken. Handles both registered
     * structure updates and new structure formation detection.
     *
     * Uses a reactive approach: instead of proactively tracking all core
     * blocks in loaded chunks (which required scanning ~98K blocks per chunk
     * on load), we search a small local area for core blocks only when a
     * potentially relevant block is placed. This is much cheaper and
     * eliminates the chunk load bottleneck entirely.
     */
    private void onBlockChanged(BlockPos pos, ServerLevel level, boolean isPlacement) {
        // === Step 1: Check registered structures ===
        // Look up this position's chunk in the chunk index
        ChunkPos chunk = new ChunkPos(pos);
        Set<UUID> affectedIds = chunkIndex.get(chunk);
        if (affectedIds != null && !affectedIds.isEmpty()) {
            // Copy to avoid concurrent modification during rescan/deregister
            List<UUID> toCheck = new ArrayList<>(affectedIds);
            for (UUID id : toCheck) {
                StructureRegistration reg = structures.get(id);
                if (reg != null && reg.isInExpandedBounds(pos)) {
                    rescanStructure(id, level);
                }
            }
        }

        // === Step 2: Check for new structure formation ===
        if (isPlacement) {
            BlockState placedState = level.getBlockState(pos);
            ResourceLocation placedBlockId = BuiltInRegistries.BLOCK.getKey(placedState.getBlock());

            // Is the placed block a core for any definition?
            List<StructureDefinition> coreDefs = getDefinitionsForCore(placedBlockId);
            for (StructureDefinition def : coreDefs) {
                // Check exclusivity: is this core already in a structure of this type?
                Map<String, UUID> coreTypes = coreExclusivity.get(pos);
                if (coreTypes != null && coreTypes.containsKey(def.getName())) {
                    continue; // Already registered
                }

                // Try a scan from this new core
                StructureResult result = StructureScanner.scan(pos, def, level);
                if (result.isValid()) {
                    registerStructure(result, level);
                } else {
                    // Not a valid structure yet — track for future completion.
                    // This is cheap (event-driven, no chunk scanning) and ensures
                    // non-block-entity cores are discoverable by findAndScanNearbyCores.
                    trackedCores.add(pos.immutable());
                }
            }

            // Is the placed block potentially a frame piece completing a structure?
            // Search nearby block entities and tracked cores for core blocks,
            // then attempt formation scans from any found.
            if (coreDefs.isEmpty()) {
                // Only search if this ISN'T a core (cores were handled above).
                // Check if the placed block could be a frame for any definition.
                if (isRelevantForAnyDefinition(placedState)) {
                    findAndScanNearbyCores(pos, level);
                }
            }

        } else {
            // Block was broken. Registered structure invalidation is already
            // handled by Step 1 (expanded bounds rescan).
            // Clean up tracked core if this was one.
            trackedCores.remove(pos);

            // Check if removing this block allows a nearby core to form a
            // structure (e.g., clearing a block from a front-face air opening).
            // Cheap with block entity iteration — just scans nearby chunk BE maps.
            findAndScanNearbyCores(pos, level);
        }
    }

    /**
     * Searches the area around a changed position for core blocks and
     * attempts structure formation scans from any found.
     *
     * Uses two complementary discovery methods:
     * 1. Block entity maps from nearby chunks — catches furnaces and any
     *    block-entity cores without needing to track them explicitly.
     * 2. trackedCores set — catches non-block-entity cores that were
     *    placed via events. Future-proofing for custom core blocks.
     *
     * Both are cheap: block entity maps have ~0-10 entries per chunk,
     * and the tracked set only contains event-placed cores.
     */
    private void findAndScanNearbyCores(BlockPos changedPos, ServerLevel level) {
        int radius = CORE_SEARCH_RADIUS;

        // Collect candidate core positions from both sources
        Set<BlockPos> candidates = new HashSet<>();

        // Source 1: Block entity maps from nearby chunks
        int minCX = (changedPos.getX() - radius) >> 4;
        int maxCX = (changedPos.getX() + radius) >> 4;
        int minCZ = (changedPos.getZ() - radius) >> 4;
        int maxCZ = (changedPos.getZ() + radius) >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;

                var chunk = level.getChunk(cx, cz);
                for (BlockPos bePos : chunk.getBlockEntities().keySet()) {
                    if (Math.abs(bePos.getX() - changedPos.getX()) > radius) continue;
                    if (Math.abs(bePos.getY() - changedPos.getY()) > radius) continue;
                    if (Math.abs(bePos.getZ() - changedPos.getZ()) > radius) continue;
                    candidates.add(bePos);
                }
            }
        }

        // Source 2: Event-tracked cores (for non-block-entity cores)
        for (BlockPos corePos : trackedCores) {
            if (Math.abs(corePos.getX() - changedPos.getX()) > radius) continue;
            if (Math.abs(corePos.getY() - changedPos.getY()) > radius) continue;
            if (Math.abs(corePos.getZ() - changedPos.getZ()) > radius) continue;
            candidates.add(corePos);
        }

        // Try scans from all candidates that are actually core blocks
        for (BlockPos candidatePos : candidates) {
            BlockState state = level.getBlockState(candidatePos);
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            List<StructureDefinition> coreDefs = getDefinitionsForCore(blockId);
            if (coreDefs.isEmpty()) continue;

            for (StructureDefinition def : coreDefs) {
                Map<String, UUID> coreTypes = coreExclusivity.get(candidatePos);
                if (coreTypes != null && coreTypes.containsKey(def.getName())) {
                    continue;
                }

                StructureResult result = StructureScanner.scan(candidatePos, def, level);
                if (result.isValid()) {
                    registerStructure(result, level);
                }
            }
        }
    }

    /**
     * Quick check: could this block be a frame piece for any registered definition?
     * Used to filter out irrelevant block placements before doing a core search.
     *
     * Blocks that aren't in any definition's frame tag and aren't core blocks
     * can never complete a structure, so we skip the nearby core search for them.
     */
    private boolean isRelevantForAnyDefinition(BlockState state) {
        for (StructureDefinition def : definitions.values()) {
            // Check frame tag
            if (def.getFrameTag() != null && state.is(def.getFrameTag())) {
                return true;
            }
            // Check core blocks (already handled separately, but defensive)
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (def.isCoreBlock(blockId)) {
                return true;
            }
        }
        return false;
    }


    // ========================================================================
    // Debug Stick Notifications
    // ========================================================================

    /**
     * Sends a chat message to all players in the level who are holding the
     * Structure Debug Stick in either hand. Does nothing if no players hold it.
     */
    private void notifyDebugPlayers(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            if (isHoldingDebugStick(player)) {
                player.sendSystemMessage(message);
            }
        }
    }

    /**
     * Checks if a player is holding the Structure Debug Stick in either hand.
     */
    private boolean isHoldingDebugStick(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        return mainHand.is(ModItems.STRUCTURE_DEBUG_STICK.get())
            || offHand.is(ModItems.STRUCTURE_DEBUG_STICK.get());
    }

    // ========================================================================
    // Query API — Primary output for consuming systems
    // ========================================================================

    /**
     * Gets the first registered structure whose bounds contain the given position.
     * Returns null if no structure contains this position.
     */
    @Nullable
    public StructureRegistration getStructureAt(BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        Set<UUID> candidates = chunkIndex.get(chunk);
        if (candidates == null) return null;

        for (UUID id : candidates) {
            StructureRegistration reg = structures.get(id);
            if (reg != null && reg.isInBounds(pos)) {
                return reg;
            }
        }
        return null;
    }

    /**
     * Gets a registered structure of a specific type whose bounds contain
     * the given position. Returns null if none found.
     */
    @Nullable
    public StructureRegistration getStructureOfType(String typeName, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        Set<UUID> candidates = chunkIndex.get(chunk);
        if (candidates == null) return null;

        for (UUID id : candidates) {
            StructureRegistration reg = structures.get(id);
            if (reg != null && reg.getDefinitionName().equals(typeName) && reg.isInBounds(pos)) {
                return reg;
            }
        }
        return null;
    }

    /**
     * Gets all registered structures of a specific type.
     * Returns an empty list if none exist.
     */
    public List<StructureRegistration> getStructuresOfType(String typeName) {
        Set<UUID> ids = typeIndex.get(typeName);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();

        List<StructureRegistration> result = new ArrayList<>();
        for (UUID id : ids) {
            StructureRegistration reg = structures.get(id);
            if (reg != null) {
                result.add(reg);
            }
        }
        return result;
    }

    /**
     * Gets all registered structures of a specific type within a given radius
     * of a position. Checks bounding box proximity, not exact block distance.
     *
     * Used by villager AI, proximity-based systems, etc.
     */
    public List<StructureRegistration> getStructuresNear(BlockPos pos, int radius, String typeName) {
        Set<UUID> ids = typeIndex.get(typeName);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();

        List<StructureRegistration> result = new ArrayList<>();
        for (UUID id : ids) {
            StructureRegistration reg = structures.get(id);
            if (reg == null) continue;

            // Check if the structure's bounding box is within radius of the query pos.
            // Use the closest point on the bounding box to the query position.
            BlockPos bMin = reg.getBoundsMin();
            BlockPos bMax = reg.getBoundsMax();
            int closestX = Math.max(bMin.getX(), Math.min(pos.getX(), bMax.getX()));
            int closestY = Math.max(bMin.getY(), Math.min(pos.getY(), bMax.getY()));
            int closestZ = Math.max(bMin.getZ(), Math.min(pos.getZ(), bMax.getZ()));

            double distSq = pos.distSqr(new BlockPos(closestX, closestY, closestZ));
            if (distSq <= (double) radius * radius) {
                result.add(reg);
            }
        }
        return result;
    }

    /**
     * Gets all registered structures whose bounds contain the given position.
     * Can return multiple results for shared walls / overlapping structures.
     */
    public List<StructureRegistration> getAllStructuresAt(BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        Set<UUID> candidates = chunkIndex.get(chunk);
        if (candidates == null) return Collections.emptyList();

        List<StructureRegistration> result = new ArrayList<>();
        for (UUID id : candidates) {
            StructureRegistration reg = structures.get(id);
            if (reg != null && reg.isInBounds(pos)) {
                result.add(reg);
            }
        }
        return result;
    }

    /** Total number of registered structures in this level. */
    public int getStructureCount() {
        return structures.size();
    }

    // ========================================================================
    // World Load — Rebuild Indexes from Persisted Data
    // ========================================================================

    /**
     * Called after SavedData is loaded from disk. Rebuilds all runtime indexes
     * (chunk index, type index, core exclusivity) from the persisted structure
     * registrations. Tracked cores are rebuilt as chunks load.
     */
    private void rebuildIndexes() {
        chunkIndex.clear();
        typeIndex.clear();
        coreExclusivity.clear();

        for (Map.Entry<UUID, StructureRegistration> entry : structures.entrySet()) {
            UUID id = entry.getKey();
            StructureRegistration reg = entry.getValue();

            // Chunk index
            for (ChunkPos chunk : reg.getOverlappingChunks()) {
                chunkIndex.computeIfAbsent(chunk, k -> new HashSet<>()).add(id);
            }

            // Type index
            typeIndex.computeIfAbsent(reg.getDefinitionName(), k -> new HashSet<>()).add(id);

            // Core exclusivity
            BlockPos corePos = reg.getCurrentResult().getCorePos();
            coreExclusivity.computeIfAbsent(corePos, k -> new HashMap<>())
                    .put(reg.getDefinitionName(), id);
        }

        Ideomod.LOGGER.info("Rebuilt structure indexes: {} structures across {} chunks",
                structures.size(), chunkIndex.size());
    }

    /**
     * Verifies a registered structure still matches the world.
     * Called after world load as chunks become available.
     */
    public void verifyStructure(UUID id, ServerLevel level) {
        rescanStructure(id, level);
    }

    // ========================================================================
    // NBT Persistence
    // ========================================================================

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag structureList = new ListTag();
        for (StructureRegistration reg : structures.values()) {
            structureList.add(reg.save());
        }
        root.put(TAG_STRUCTURES, structureList);
        return root;
    }

    private void load(CompoundTag root) {
        structures.clear();

        if (root.contains(TAG_STRUCTURES, Tag.TAG_LIST)) {
            ListTag structureList = root.getList(TAG_STRUCTURES, Tag.TAG_COMPOUND);
            for (int i = 0; i < structureList.size(); i++) {
                CompoundTag structureTag = structureList.getCompound(i);
                StructureRegistration reg = StructureRegistration.load(structureTag);
                if (reg != null) {
                    structures.put(reg.getId(), reg);
                } else {
                    Ideomod.LOGGER.warn("Skipped malformed structure registration at index {}", i);
                }
            }
        }

        rebuildIndexes();
        Ideomod.LOGGER.info("Loaded {} structure registrations from SavedData", structures.size());
    }

    // ========================================================================
    // Forge Event Handlers
    // ========================================================================

    /**
     * Event handler class. Registered with MinecraftForge.EVENT_BUS.
     * Delegates to the per-level StructureManager instance.
     *
     * This is a separate static inner class so event registration is clean.
     * The handlers get the StructureManager from the level, so they
     * naturally route to the correct per-dimension instance.
     */
    public static class EventHandler {

        @SubscribeEvent
        public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
            if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
            BlockPos pos = event.getPos();
            StructureManager manager = StructureManager.get(serverLevel);
            manager.onBlockChanged(pos, serverLevel, true);
        }

        @SubscribeEvent
        public void onBlockBreak(BlockEvent.BreakEvent event) {
            if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
            BlockPos pos = event.getPos();
            StructureManager manager = StructureManager.get(serverLevel);
            // Schedule for next tick — the block is still present during BreakEvent.
            // We need it to be gone before rescanning.
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                    serverLevel.getServer().getTickCount() + 1,
                    () -> manager.onBlockChanged(pos, serverLevel, false)
            ));
        }

        @SubscribeEvent
        public void onLevelLoad(LevelEvent.Load event) {
            if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
            // Access the manager to trigger SavedData loading
            StructureManager manager = StructureManager.get(serverLevel);
            Ideomod.LOGGER.info("StructureManager initialized for dimension: {}",
                    serverLevel.dimension().location());
        }
    }
}
