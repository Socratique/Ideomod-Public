package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for mod particle types. The client-side look (sprites, colour,
 * motion) is registered separately in ClientSetup, so a dedicated server only
 * ever sees the type.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Ideomod.MOD_ID);

    /** Light-blue portal particle: vanilla's portal swirl, Oddstone portal colours. */
    public static final RegistryObject<SimpleParticleType> ODDSTONE_PORTAL =
            PARTICLES.register("oddstone_portal", () -> new SimpleParticleType(false));
}
