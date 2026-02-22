package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for Framed Blocks. Stores the inner block's identity
 * and provides cached access to the inner Block instance for behavioral
 * delegation.
 *
 * Implements IPistonMovable so the inner block data survives piston moves.
 * Client sync via getUpdateTag/getUpdatePacket so the renderer knows
 * which inner block texture to draw.
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
        }
    }
}
