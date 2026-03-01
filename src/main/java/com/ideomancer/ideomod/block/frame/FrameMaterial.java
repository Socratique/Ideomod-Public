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
 * Written by Claude, directed and guided by The Ideomancer.
 */
public enum FrameMaterial {
    IRON("Iron", "iron"),
    COPPER("Copper", "copper");

    private final String displayName;
    private final String registryPrefix;

    FrameMaterial(String displayName, String registryPrefix) {
        this.displayName = displayName;
        this.registryPrefix = registryPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRegistryPrefix() {
        return registryPrefix;
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
