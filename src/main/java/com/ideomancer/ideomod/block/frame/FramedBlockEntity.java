package com.ideomancer.ideomod.block.frame;

import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.movement.IPistonMovable;

/**
 * Block entity for Framed Blocks. Stores the inner block's identity
 * and provides cached access to the inner Block instance for behavioral
 * delegation.
 *
 * Implements IPistonMovable so the inner block data survives piston moves.
 * Client sync via getUpdateTag/getUpdatePacket so the renderer knows
 * which inner block texture to draw.
 *
 * INNER STICKY TYPE:
 * ==================
 * When the inner block is a sticky type (slime, honey, odd slime, oddstone),
 * the block entity computes the sticky type value and syncs it to the
 * blockstate's INNER_STICKY property via updateBlockstateFromInner().
 * This is needed because isStickyBlock() and canStickTo() only receive
 * a BlockState (no position context) and cannot look up the block entity.
 *
 * INNER LIGHT:
 * ============
 * When the inner block emits light (glowstone, sea lantern, etc.), the
 * block entity syncs the light level to the blockstate's INNER_LIGHT
 * property. This is needed because Minecraft's lighting engine uses the
 * lightLevel predicate from BlockBehaviour.Properties, which only receives
 * a BlockState — no position, no level, no block entity access.
 *
 * LIGHTING ENGINE INVALIDATION:
 * =============================
 * After changing INNER_LIGHT via setBlock(), we explicitly call
 * level.getLightEngine().checkBlock(pos) to force the lighting engine
 * to re-evaluate this position. Without this explicit poke, the engine
 * may not notice the light change — vanilla's internal light-change
 * detection in LevelChunk.setBlockState() can miss dynamic lightLevel
 * predicates that read blockstate properties, because it compares old
 * and new states but both may evaluate against stale context.
 *
 * LIFECYCLE NOTE — BLOCKSTATE SYNC TIMING:
 * =========================================
 * During fresh placement, vanilla's lifecycle order is:
 *   1. setBlock() — places block with default state (INNER_LIGHT=0)
 *   2. onPlace() — fires BEFORE block entity exists
 *   3. newBlockEntity() — creates empty BE
 *   4. BlockEntityTag NBT applied — load() fires, innerBlockId set
 *   5. onLoad() — fires on chunk load path, NOT guaranteed on placement
 *
 * Because onPlace() fires before the BE exists, it can't sync blockstate
 * properties. The fix is to call updateBlockstateFromInner() from load()
 * when the level is already available (placement path), AND from onLoad()
 * when the level becomes available later (chunk load path). Both are
 * guarded by the server-side + level-exists check so only one actually
 * fires per placement.
 *
 * updateBlockstateFromInner() is called from:
 *   - setInnerBlock() — programmatic inner block changes
 *   - load() — when level is available (fresh placement via BlockEntityTag)
 *   - onLoad() — when level becomes available (chunk load from disk)
 *   - loadMovementData() — after piston restore
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class FramedBlockEntity extends BlockEntity implements IPistonMovable {

    /** NBT key for the inner block's registry name */
    public static final String TAG_INNER_BLOCK = "InnerBlock";

    /** The inner block's registry name (e.g. "minecraft:stone") */
    private ResourceLocation innerBlockId = null;

    /** Cached Block instance for fast delegation. Resolved lazily from innerBlockId. */
    private Block cachedInnerBlock = null;

    public FramedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FRAMED_BLOCK.get(), pos, state);
    }

    // ========================================================================
    // INNER BLOCK ACCESS
    // ========================================================================

    /**
     * Set the inner block by registry name. Invalidates the cached Block instance.
     * Call setChanged() + sync to client after calling this.
     */
    public void setInnerBlock(ResourceLocation blockId) {
        this.innerBlockId = blockId;
        this.cachedInnerBlock = null;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_ALL);
            updateBlockstateFromInner();
        }
    }

    /**
     * Get the inner block's registry name.
     * @return ResourceLocation or null if not set
     */
    @Nullable
    public ResourceLocation getInnerBlockId() {
        return innerBlockId;
    }

    /**
     * Get the inner Block instance. Resolved lazily and cached.
     * Returns Blocks.STONE as fallback if the ID is missing or invalid
     * (prevents crashes from corrupt data).
     */
    public Block getInnerBlock() {
        if (cachedInnerBlock != null) return cachedInnerBlock;

        if (innerBlockId != null) {
            Block resolved = BuiltInRegistries.BLOCK.get(innerBlockId);
            // BuiltInRegistries returns Blocks.AIR for unknown keys
            if (resolved != Blocks.AIR || innerBlockId.equals(BuiltInRegistries.BLOCK.getKey(Blocks.AIR))) {
                cachedInnerBlock = resolved;
                return cachedInnerBlock;
            }
        }

        // Fallback: stone (safe, full cube, no special behavior)
        cachedInnerBlock = Blocks.STONE;
        return cachedInnerBlock;
    }

    /**
     * Get the inner block's default BlockState for delegation queries.
     * All behavioral delegation uses this — we can't use the framed block's
     * own state because the inner block wouldn't recognize our properties.
     */
    public BlockState getInnerDefaultState() {
        return getInnerBlock().defaultBlockState();
    }

    /**
     * Check if this entity has a valid inner block set.
     */
    public boolean hasInnerBlock() {
        return innerBlockId != null;
    }

    // ========================================================================
    // INNER STICKY TYPE — blockstate sync
    // ========================================================================

    /**
     * Compute the sticky type for the current inner block.
     *
     * @return 0 (none), 1 (slime), 2 (honey), 3 (odd slime), 4 (oddstone)
     */
    public static int computeStickyType(Block innerBlock) {
        return com.ideomancer.ideomod.block.movement.StickyType.fromBlock(innerBlock).getInnerStickyValue();
    }

    /**
     * Sync the INNER_STICKY, INNER_CONDUCTOR, and INNER_LIGHT blockstate
     * properties from the current inner block. Called after any operation
     * that changes the inner block identity: placement, piston restore,
     * chunk load.
     *
     * INNER_STICKY: tracks sticky inner types (slime, honey, odd slime, oddstone)
     * INNER_CONDUCTOR: tracks vanilla metal conductor inners (iron_block, copper_block)
     *   for cross-frame mediation in canStickTo()
     * INNER_LIGHT: tracks inner block's light emission level (0-15)
     *   for Minecraft's lighting engine via lightLevel predicate
     *
     * When the light value changes, explicitly pokes the lighting engine
     * via getLightEngine().checkBlock() after the setBlock() call. This
     * is necessary because vanilla's internal light-change detection in
     * LevelChunk.setBlockState() can miss dynamic lightLevel predicates.
     *
     * Also preserves ACTIVE and FACING from the existing blockstate.
     */
    public void updateBlockstateFromInner() {
        if (level == null || level.isClientSide()) return;

        BlockState currentState = getBlockState();
        if (!(currentState.getBlock() instanceof FramedBlock)) return;

        Block inner = getInnerBlock();

        int stickyType = computeStickyType(inner);
        int currentSticky = currentState.getValue(FramedBlock.INNER_STICKY);

        FrameMaterial conductorMat = FrameMaterial.fromVanillaBlock(inner);
        int conductorValue = conductorMat != null ? conductorMat.toConductorValue() : 0;
        int currentConductor = currentState.getValue(FramedBlock.INNER_CONDUCTOR);

        int lightLevel = inner.defaultBlockState().getLightEmission();
        int currentLight = currentState.getValue(FramedBlock.INNER_LIGHT);

        boolean lightChanged = lightLevel != currentLight;

        if (stickyType != currentSticky || conductorValue != currentConductor
                || lightChanged) {
            BlockState newState = currentState
                .setValue(FramedBlock.INNER_STICKY, stickyType)
                .setValue(FramedBlock.INNER_CONDUCTOR, conductorValue)
                .setValue(FramedBlock.INNER_LIGHT, lightLevel);
            level.setBlock(worldPosition, newState, Block.UPDATE_ALL);

            // Explicitly tell the lighting engine to re-evaluate this position.
            // Vanilla's LevelChunk.setBlockState() should do this internally when
            // it detects a light emission change, but dynamic lightLevel predicates
            // that read blockstate properties can be missed. This direct poke
            // guarantees the engine recalculates light propagation.
            if (lightChanged) {
                level.getLightEngine().checkBlock(worldPosition);
            }
        }
    }

    // ========================================================================
    // NBT SERIALIZATION
    // ========================================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (innerBlockId != null) {
            tag.putString(TAG_INNER_BLOCK, innerBlockId.toString());
        }
    }

    /**
     * Load inner block data from NBT. Called in two contexts:
     *
     * 1. FRESH PLACEMENT: Vanilla applies BlockEntityTag from the item stack
     *    after creating the block entity. At this point the level IS available
     *    (the block is already placed in the world), so we can immediately
     *    sync blockstate properties including INNER_LIGHT for the lighting engine.
     *
     * 2. CHUNK LOAD FROM DISK: level may or may not be available yet. If not,
     *    onLoad() will handle the blockstate sync when the level becomes available.
     *
     * The updateBlockstateFromInner() call is guarded by the standard
     * level != null && !isClientSide check, so it's safe in both contexts —
     * it simply no-ops if the level isn't ready yet.
     */
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_INNER_BLOCK)) {
            innerBlockId = new ResourceLocation(tag.getString(TAG_INNER_BLOCK));
            cachedInnerBlock = null; // Force re-resolve
        }
        // Attempt immediate blockstate sync. During fresh placement, the level
        // is available and this sets INNER_LIGHT (etc.) right away — critical
        // for the lighting engine to propagate light from the moment of placement.
        // During chunk load, level is typically null here, so this no-ops and
        // onLoad() handles it instead.
        if (level != null && !level.isClientSide() && hasInnerBlock()) {
            updateBlockstateFromInner();
        }
    }

    /**
     * Called when the block entity's level is set (after chunk load).
     * This is the earliest point where we have both the inner block data
     * (from load()) and a valid level reference for blockstate updates.
     *
     * During fresh placement, updateBlockstateFromInner() may have already
     * run in load() — the dirty check in updateBlockstateFromInner() ensures
     * no redundant setBlock() calls if the values already match.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide() && hasInnerBlock()) {
            updateBlockstateFromInner();
        }
    }

    // ========================================================================
    // CLIENT SYNC
    // ========================================================================

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (innerBlockId != null) {
            tag.putString(TAG_INNER_BLOCK, innerBlockId.toString());
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ========================================================================
    // IPISTON MOVABLE — survive piston moves
    // ========================================================================

    @Override
    public CompoundTag saveMovementData() {
        CompoundTag tag = new CompoundTag();
        if (innerBlockId != null) {
            tag.putString(TAG_INNER_BLOCK, innerBlockId.toString());
        }
        return tag;
    }

    @Override
    public void loadMovementData(CompoundTag tag) {
        if (tag.contains(TAG_INNER_BLOCK)) {
            innerBlockId = new ResourceLocation(tag.getString(TAG_INNER_BLOCK));
            cachedInnerBlock = null;
            setChanged();
            // Sync INNER_STICKY, INNER_CONDUCTOR, and INNER_LIGHT to blockstate after piston restore
            updateBlockstateFromInner();
        }
    }

    // ========================================================================
    // NBT PATH CENTRALIZATION
    // ========================================================================

    /**
     * Get the inner block ID from a framed block ItemStack's NBT.
     * Centralizes the stack -> tag -> BlockEntityTag -> InnerBlock path.
     *
     * @param stack The ItemStack to read from
     * @return The inner block's ResourceLocation, or null if not set
     */
    @Nullable
    public static ResourceLocation getInnerBlockIdFromStack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("BlockEntityTag")) {
            CompoundTag beTag = tag.getCompound("BlockEntityTag");
            if (beTag.contains(TAG_INNER_BLOCK)) {
                return new ResourceLocation(beTag.getString(TAG_INNER_BLOCK));
            }
        }
        return null;
    }

    /**
     * Set the inner block ID on a framed block ItemStack's NBT.
     * Centralizes the stack -> tag -> BlockEntityTag -> InnerBlock path.
     *
     * @param stack         The ItemStack to modify
     * @param innerBlockId  The inner block's ResourceLocation
     */
    public static void setInnerBlockIdOnStack(ItemStack stack, ResourceLocation innerBlockId) {
        CompoundTag beTag = new CompoundTag();
        beTag.putString(TAG_INNER_BLOCK, innerBlockId.toString());
        CompoundTag itemTag = stack.getOrCreateTag();
        itemTag.put("BlockEntityTag", beTag);
    }
}
