package com.ideomancer.ideomod.block.frame;

import net.minecraft.world.item.Item;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Material type for Frame Family blocks.
 *
 * Determines activation chain propagation (iron chains through iron only,
 * copper through copper only) and canStickTo compatibility (cross-material
 * frames are mutually exclusive).
 *
 * Also stores the mining time modifier -- the additive bonus to destroy time
 * that this frame material contributes on top of the inner block's base
 * destroy time. Iron frames are slightly tougher than copper.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public enum FrameMaterial {
    IRON("Iron", "iron", 0.75f),
    COPPER("Copper", "copper", 0.5f);

    private final String displayName;
    private final String registryPrefix;

    /**
     * Additive modifier to the inner block's destroy speed when framed.
     * Higher = takes longer to mine. Applied on top of the inner block's
     * base hardness in FramedBlock.getDestroyProgress().
     *
     * For reference: stone hardness = 1.5, iron ore = 3.0, obsidian = 50.
     * A modifier of 0.75 (iron) means framing stone adds ~50% extra time.
     * A modifier of 0.5 (copper) means framing stone adds ~33% extra time.
     */
    private final float miningTimeModifier;

    FrameMaterial(String displayName, String registryPrefix, float miningTimeModifier) {
        this.displayName = displayName;
        this.registryPrefix = registryPrefix;
        this.miningTimeModifier = miningTimeModifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRegistryPrefix() {
        return registryPrefix;
    }

    /**
     * Get the additive mining time modifier for this frame material.
     * This is added to the inner block's hardness when computing destroy progress.
     */
    public float getMiningTimeModifier() {
        return miningTimeModifier;
    }

    // ========================================================================
    // FRAMED BLOCK ITEM REGISTRY
    // ========================================================================

    private static final Map<FrameMaterial, Supplier<Item>> framedBlockItems = new EnumMap<>(FrameMaterial.class);

    /**
     * Register a framed block item for this material.
     * Called during mod initialization in Ideomod.commonSetup().
     */
    public static void registerFramedBlockItem(FrameMaterial material, Supplier<Item> itemSupplier) {
        framedBlockItems.put(material, itemSupplier);
    }

    /**
     * Get the framed block item for this material.
     * @return The Item instance for this material's framed block.
     * @throws IllegalStateException if the item has not been registered.
     */
    public Item getFramedBlockItem() {
        Supplier<Item> supplier = framedBlockItems.get(this);
        if (supplier == null) {
            throw new IllegalStateException("No framed block item registered for " + this);
        }
        return supplier.get();
    }
}
