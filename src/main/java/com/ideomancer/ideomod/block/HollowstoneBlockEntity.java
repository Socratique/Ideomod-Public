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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for Hollowstone. Stores the inner decorative block identity.
 *
 * Unlike FramedBlockEntity, this is purely cosmetic — no behavior delegation,
 * no sticky type, no light level sync. Just stores which block to render inside.
 *
 * Client sync via getUpdateTag/getUpdatePacket so the renderer knows what to draw.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class HollowstoneBlockEntity extends BlockEntity {

    public static final String TAG_INNER_BLOCK = "InnerBlock";

    /** The inner block's registry name (e.g. "minecraft:stone") */
    private ResourceLocation innerBlockId = null;

    /** Cached Block instance, resolved lazily from innerBlockId */
    private Block cachedInnerBlock = null;

    /** Flag for inner block placement on break (set by playerWillDestroy) */
    private boolean placeInnerOnRemove = false;

    public HollowstoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOLLOW_STONE.get(), pos, state);
    }

    // ========================================================================
    // INNER BLOCK ACCESS
    // ========================================================================

    public void setInnerBlock(ResourceLocation blockId) {
        this.innerBlockId = blockId;
        this.cachedInnerBlock = null;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_ALL);
        }
    }

    @Nullable
    public ResourceLocation getInnerBlockId() {
        return innerBlockId;
    }

    public boolean hasInnerBlock() {
        return innerBlockId != null;
    }

    @Nullable
    public Block getInnerBlock() {
        if (innerBlockId == null) return null;
        if (cachedInnerBlock == null) {
            cachedInnerBlock = BuiltInRegistries.BLOCK.get(innerBlockId);
            // If the block ID is invalid, BuiltInRegistries returns air
            if (cachedInnerBlock == net.minecraft.world.level.block.Blocks.AIR
                    && !innerBlockId.equals(BuiltInRegistries.BLOCK.getDefaultKey())) {
                cachedInnerBlock = null;
            }
        }
        return cachedInnerBlock;
    }

    // ========================================================================
    // INNER BLOCK PLACEMENT ON BREAK
    // ========================================================================

    public void markForInnerBlockPlacement() {
        this.placeInnerOnRemove = true;
    }

    public boolean shouldPlaceInnerBlock() {
        return placeInnerOnRemove && hasInnerBlock();
    }

    // ========================================================================
    // NBT SAVE / LOAD
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
            cachedInnerBlock = null;
        } else {
            innerBlockId = null;
            cachedInnerBlock = null;
        }
    }

    // ========================================================================
    // CLIENT SYNC (same pattern as FramedBlockEntity)
    // ========================================================================

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
