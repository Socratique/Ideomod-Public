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
 * updateBlockstateFromInner() is called from:
 *   - FramedBlock.onPlace() — after item NBT is loaded into the BE
 *   - loadMovementData() — after piston restore
 *   - load() via onLoad() — after chunk loading (level available)
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
     * Sync the INNER_STICKY blockstate property from the current inner block.
     * Called after any operation that changes the inner block identity:
     * placement, piston restore, chunk load.
     *
     * Also preserves ACTIVE and FACING from the existing blockstate.
     */
    public void updateBlockstateFromInner() {
        if (level == null || level.isClientSide()) return;

        BlockState currentState = getBlockState();
        if (!(currentState.getBlock() instanceof FramedBlock)) return;

        int stickyType = computeStickyType(getInnerBlock());
        int currentSticky = currentState.getValue(FramedBlock.INNER_STICKY);

        if (stickyType != currentSticky) {
            BlockState newState = currentState.setValue(FramedBlock.INNER_STICKY, stickyType);
            // Use flag 2 (UPDATE_CLIENTS) — no neighbor cascade needed for this
            level.setBlock(worldPosition, newState, Block.UPDATE_CLIENTS);
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

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_INNER_BLOCK)) {
            innerBlockId = new ResourceLocation(tag.getString(TAG_INNER_BLOCK));
            cachedInnerBlock = null; // Force re-resolve
        }
    }

    /**
     * Called when the block entity's level is set (after chunk load).
     * This is the earliest point where we have both the inner block data
     * (from load()) and a valid level reference for blockstate updates.
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
            // Sync INNER_STICKY to blockstate after piston restore
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
