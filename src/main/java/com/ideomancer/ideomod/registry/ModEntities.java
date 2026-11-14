package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.machine.MachineSeatEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entity types. The Machine Seat's seat: an invisible entity the rider sits on.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public final class ModEntities {

    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Ideomod.MOD_ID);

    public static final RegistryObject<EntityType<MachineSeatEntity>> MACHINE_SEAT =
            ENTITY_TYPES.register("machine_seat",
                    () -> EntityType.Builder.<MachineSeatEntity>of(MachineSeatEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.25f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .noSummon()
                            .build("machine_seat"));
}
