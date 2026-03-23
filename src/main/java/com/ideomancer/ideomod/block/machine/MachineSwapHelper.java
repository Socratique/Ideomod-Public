package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Central utility for Machine Family specialization swaps.
 *
 * SWAP SEQUENCE:
 * ==============
 * 1. Determine target block type from the new item
 * 2. If target differs from current block:
 *    a. Set isSwapping flag (suppresses onRemove side effects)
 *    b. Replace block in-world via level.setBlock()
 *    c. Write the item into the fresh block entity via setItemDirect()
 *    d. Clear isSwapping flag
 *
 * The menu uses PositionBoundContainer which always looks up whatever
 * block entity is at the position. After the swap, the container
 * automatically talks to the new block entity — no rebind, no packets,
 * no close/reopen. The GUI just works.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public final class MachineSwapHelper {

    // ========================================================================
    // SWAP GUARD
    // ========================================================================

    private static final ThreadLocal<Boolean> isSwapping = ThreadLocal.withInitial(() -> false);

    public static boolean isSwapping() {
        return isSwapping.get();
    }

    // ========================================================================
    // SPECIALIZATION REGISTRY
    // ========================================================================

    private static final Map<net.minecraft.world.item.Item, RegistrySupplier> itemToBlock = new IdentityHashMap<>();
    private static Map<Block, net.minecraft.world.item.Item> blockToItem = null;

    @FunctionalInterface
    private interface RegistrySupplier {
        Block get();
    }

    public static void registerMappings() {
        itemToBlock.put(Items.POWERED_RAIL,   () -> ModBlocks.MACHINE_TRACK.get());
        itemToBlock.put(Items.MINECART,       () -> ModBlocks.MACHINE_TROLLEY.get());
        itemToBlock.put(Items.REDSTONE,       () -> ModBlocks.MACHINE_CONDUCTOR.get());
        itemToBlock.put(Items.HOPPER,         () -> ModBlocks.MACHINE_PIPE.get());
        itemToBlock.put(Items.BUCKET,         () -> ModBlocks.MACHINE_FLUID_PIPE.get());
        itemToBlock.put(Items.PISTON,         () -> ModBlocks.MACHINE_PISTON.get());
        itemToBlock.put(Items.STICKY_PISTON,  () -> ModBlocks.MACHINE_STICKY_PISTON.get());
        itemToBlock.put(Items.REPEATER,       () -> ModBlocks.MACHINE_REPEATER.get());
        itemToBlock.put(Items.LEVER,          () -> ModBlocks.MACHINE_TOGGLE.get());
        itemToBlock.put(Items.COMPARATOR,     () -> ModBlocks.MACHINE_TRANSFORMER.get());
        itemToBlock.put(Items.CLOCK,          () -> ModBlocks.MACHINE_TIMER.get());

        // Machine Brace: uses our own Machine Block item as the trigger.
        // Can't register here because registerMappings() runs during mod
        // construction, before DeferredRegister has resolved RegistryObjects.
        // Vanilla items (above) are fine — they exist at construction time.
        // Our own items don't exist yet. Registered lazily via ensureBraceMapping().

        // Future specializations:
        // itemToBlock.put(Items.FURNACE,         () -> ModBlocks.MACHINE_ENGINE.get());
        // Pulser: tag-based (any button item) — needs special handling
        // Grabber: slime/honey block items — needs special handling
    }

    // ========================================================================
    // LAZY BRACE MAPPING
    // ========================================================================

    /**
     * Lazy registration for specializations that use our own mod items as
     * trigger keys. Called on first lookup after blocks are available.
     *
     * This pattern is needed because registerMappings() runs during the Ideomod
     * constructor (mod construction phase), before DeferredRegister resolves
     * RegistryObjects. Vanilla items exist at that point but our own items don't.
     */
    private static boolean braceRegistered = false;

    private static void ensureBraceMapping() {
        if (braceRegistered) return;
        braceRegistered = true;
        try {
            itemToBlock.put(ModBlocks.MACHINE_BLOCK.get().asItem(), () -> ModBlocks.MACHINE_BRACE.get());
        } catch (NullPointerException e) {
            // Still too early — will retry next call
            braceRegistered = false;
        }
    }

    // ========================================================================
    // BLOCK LOOKUP
    // ========================================================================

    public static Block getSpecializationBlock(net.minecraft.world.item.Item item) {
        ensureBraceMapping();
        RegistrySupplier supplier = itemToBlock.get(item);
        return supplier != null ? supplier.get() : null;
    }

    public static ItemStack getSpecializationItem(Block block) {
        ensureBlockToItem();
        net.minecraft.world.item.Item item = blockToItem.get(block);
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    public static boolean isSpecialization(Block block) {
        ensureBlockToItem();
        return blockToItem.containsKey(block);
    }

    private static void ensureBlockToItem() {
        if (blockToItem != null) return;
        ensureBraceMapping();
        blockToItem = new IdentityHashMap<>();
        for (Map.Entry<net.minecraft.world.item.Item, RegistrySupplier> entry : itemToBlock.entrySet()) {
            blockToItem.put(entry.getValue().get(), entry.getKey());
        }
    }

    // ========================================================================
    // CREATIVE TAB / PRE-LOADED ITEM STACKS
    // ========================================================================

    public static ItemStack createPreloadedStack(net.minecraft.world.item.Item blockItem, net.minecraft.world.item.Item storedItem) {
        ItemStack stack = new ItemStack(blockItem);
        CompoundTag beTag = new CompoundTag();
        NonNullList<ItemStack> items = NonNullList.withSize(1, new ItemStack(storedItem));
        ContainerHelper.saveAllItems(beTag, items);
        stack.getOrCreateTag().put("BlockEntityTag", beTag);
        return stack;
    }

    public static ItemStack createPreloadedStack(Block block) {
        ItemStack specItem = getSpecializationItem(block);
        if (specItem.isEmpty()) return new ItemStack(block);
        ItemStack stack = new ItemStack(block);
        CompoundTag beTag = new CompoundTag();
        NonNullList<ItemStack> items = NonNullList.withSize(1, specItem);
        ContainerHelper.saveAllItems(beTag, items);
        stack.getOrCreateTag().put("BlockEntityTag", beTag);
        return stack;
    }

    // ========================================================================
    // DROP HELPER
    // ========================================================================

    public static ItemStack createDrop(Block block, MachineFrameBlockEntity blockEntity) {
        ItemStack drop = new ItemStack(block);
        if (blockEntity != null && !blockEntity.getSpecializationItem().isEmpty()) {
            CompoundTag beTag = new CompoundTag();
            NonNullList<ItemStack> items = NonNullList.withSize(1, blockEntity.getSpecializationItem().copy());
            ContainerHelper.saveAllItems(beTag, items);
            drop.getOrCreateTag().put("BlockEntityTag", beTag);
        }
        return drop;
    }

    // ========================================================================
    // CLONE (MIDDLE-CLICK) HELPER
    // ========================================================================

    /**
     * Creates the correct ItemStack for creative middle-click.
     * Uses the registry (not BE read) since this runs client-side
     * where the BE inventory isn't synced.
     */
    public static ItemStack createCloneStack(Block block) {
        if (isSpecialization(block)) return createPreloadedStack(block);
        return new ItemStack(block);
    }

    // ========================================================================
    // SWAP LOGIC
    // ========================================================================

    public static boolean checkAndSwap(Level level, BlockPos pos, ItemStack newItem) {
        if (level == null || level.isClientSide()) return false;

        Block currentBlock = level.getBlockState(pos).getBlock();

        // Determine target block
        Block targetBlock;
        if (newItem.isEmpty()) {
            targetBlock = ModBlocks.MACHINE_BLOCK.get();
        } else {
            Block specialization = getSpecializationBlock(newItem.getItem());
            targetBlock = specialization != null ? specialization : ModBlocks.MACHINE_BLOCK.get();
        }

        if (currentBlock == targetBlock) return false;

        // Swap the block. The menu's PositionBoundContainer will
        // automatically find the new block entity on its next read.
        isSwapping.set(true);
        try {
            level.setBlock(pos, targetBlock.defaultBlockState(), 3);

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineFrameBlockEntity machineEntity) {
                machineEntity.setItemDirect(newItem.copy());
                machineEntity.syncToClients();
            }
        } finally {
            isSwapping.set(false);
        }

        return true;
    }
}
