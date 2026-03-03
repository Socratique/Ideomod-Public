package com.ideomancer.ideomod.block.frame;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

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
 * Updated by Claude, directed and guided by The Ideomancer.
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
    // VANILLA BLOCK MAPPING
    // ========================================================================

    /**
     * Returns the FrameMaterial that a vanilla block naturally belongs to,
     * or null if it's not a natural frame conductor.
     *
     * This allows vanilla iron blocks and copper blocks to participate in
     * the FrameActivation network as native conductors for their material.
     * They don't need an ACTIVE property -- they're always conductive.
     *
     * @param block The block to check
     * @return IRON for iron_block, COPPER for copper_block, null otherwise
     */
    @Nullable
    public static FrameMaterial fromVanillaBlock(Block block) {
        if (block == Blocks.IRON_BLOCK) return IRON;
        if (block == Blocks.COPPER_BLOCK) return COPPER;
        return null;
    }

    /**
     * Checks whether a block is a vanilla conductor for this specific material.
     * Convenience method for activation chain checks.
     */
    public boolean isVanillaConductor(Block block) {
        return fromVanillaBlock(block) == this;
    }

    // ========================================================================
    // BLOCKSTATE CONDUCTOR VALUE
    // ========================================================================

    /**
     * Returns the integer value for storing this material in the
     * FramedBlock.INNER_CONDUCTOR blockstate property.
     * IRON=1, COPPER=2.
     */
    public int toConductorValue() {
        return this == IRON ? 1 : 2;
    }

    /**
     * Returns the FrameMaterial for a given INNER_CONDUCTOR blockstate value.
     * @param value 0=no conductor, 1=IRON, 2=COPPER
     * @return the FrameMaterial, or null if value is 0 or invalid
     */
    @Nullable
    public static FrameMaterial fromConductorValue(int value) {
        return switch (value) {
            case 1 -> IRON;
            case 2 -> COPPER;
            default -> null;
        };
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
