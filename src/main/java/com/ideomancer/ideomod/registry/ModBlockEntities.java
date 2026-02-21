package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.MachineFrameBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all mod block entity types.
 * Uses DeferredRegister for safe, ordered registration.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Ideomod.MOD_ID);

    public static final RegistryObject<BlockEntityType<MachineFrameBlockEntity>> MACHINE_FRAME =
            BLOCK_ENTITIES.register("machine_frame",
                    () -> BlockEntityType.Builder.of(
                            MachineFrameBlockEntity::new,
                            ModBlocks.MACHINE_BLOCK.get()
                    ).build(null));
}
