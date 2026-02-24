package com.ideomancer.ideomod.multiblock.effects;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.multiblock.StructureEffectHandler;
import com.ideomancer.ideomod.multiblock.StructureResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * EffectHandler for the Machine Furnace Array.
 *
 * On formation: stamps each furnace block entity with an "IdeomodEnhanced"
 * NBT flag and sets a custom GUI name ("Enhanced Furnace", etc.).
 *
 * On removal: clears the flag and custom name, reverting furnaces to normal.
 *
 * On update: diffs old vs new furnace lists — clears effects on furnaces
 * that left the structure, applies effects on furnaces that joined.
 *
 * The NBT flag is read by AbstractFurnaceBlockEntityMixin to apply the
 * +50% speed boost every tick. The custom name is read by vanilla's GUI
 * system automatically — no GUI mixin needed.
 */
public class FurnaceArrayEffect implements StructureEffectHandler {

    /** NBT key stamped into furnace persistent data. Read by the mixin. */
    public static final String NBT_ENHANCED = "IdeomodEnhanced";

    @Override
    public void onFormed(StructureResult result, ServerLevel level) {
        ResourceLocation furnaceType = result.getFurnaceType();
        String nameKey = getTranslationKey(furnaceType);

        for (BlockPos pos : result.getEffectorPositions()) {
            applyEffect(level, pos, nameKey);
        }

        Ideomod.LOGGER.debug("Applied furnace array effects to {} furnaces",
                result.getEffectorPositions().size());
    }

    @Override
    public void onUpdated(StructureResult oldResult, StructureResult newResult, ServerLevel level) {
        Set<BlockPos> oldPositions = new HashSet<>(oldResult.getEffectorPositions());
        Set<BlockPos> newPositions = new HashSet<>(newResult.getEffectorPositions());

        // Furnaces that left the structure — clear effects
        for (BlockPos pos : oldPositions) {
            if (!newPositions.contains(pos)) {
                clearEffect(level, pos);
            }
        }

        // Furnaces that joined the structure — apply effects
        ResourceLocation furnaceType = newResult.getFurnaceType();
        String nameKey = getTranslationKey(furnaceType);

        for (BlockPos pos : newPositions) {
            if (!oldPositions.contains(pos)) {
                applyEffect(level, pos, nameKey);
            }
        }
    }

    @Override
    public void onRemoved(StructureResult lastResult, ServerLevel level) {
        for (BlockPos pos : lastResult.getEffectorPositions()) {
            clearEffect(level, pos);
        }

        Ideomod.LOGGER.debug("Cleared furnace array effects from {} furnaces",
                lastResult.getEffectorPositions().size());
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    /**
     * Stamps the enhanced flag and custom name onto a furnace block entity.
     */
    private void applyEffect(ServerLevel level, BlockPos pos, String translationKey) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return;

        // Set the enhanced flag in Forge's persistent data compound
        furnace.getPersistentData().putBoolean(NBT_ENHANCED, true);

        // Set the custom GUI name
        furnace.setCustomName(Component.translatable(translationKey));

        // Mark the block entity as needing save + client sync
        furnace.setChanged();
    }

    /**
     * Clears the enhanced flag and custom name from a furnace block entity.
     */
    private void clearEffect(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return;

        // Remove the enhanced flag
        furnace.getPersistentData().remove(NBT_ENHANCED);

        // Clear the custom name — reverts GUI to the default title
        furnace.setCustomName(null);

        // Mark the block entity as needing save + client sync
        furnace.setChanged();
    }

    /**
     * Returns the translation key for the enhanced GUI name based on
     * the furnace block type.
     *
     * minecraft:furnace       → "ideomod.container.enhanced_furnace"
     * minecraft:blast_furnace → "ideomod.container.enhanced_blast_furnace"
     * minecraft:smoker        → "ideomod.container.enhanced_smoker"
     */
    private String getTranslationKey(@Nullable ResourceLocation furnaceType) {
        if (furnaceType == null) return "ideomod.container.enhanced_furnace";

        String path = furnaceType.getPath();
        return switch (path) {
            case "blast_furnace" -> "ideomod.container.enhanced_blast_furnace";
            case "smoker" -> "ideomod.container.enhanced_smoker";
            default -> "ideomod.container.enhanced_furnace";
        };
    }
}
