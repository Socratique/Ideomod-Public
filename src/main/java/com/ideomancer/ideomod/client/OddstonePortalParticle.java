package com.ideomancer.ideomod.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * The Oddstone portal's particle: vanilla's portal particle (same sprites,
 * drift, swirl back in and fade), tinted light blue instead of purple to match
 * the portal's texture. Vanilla sets a random brightness f in 0.4-1.0 and
 * colours (0.9f, 0.3f, f); this keeps f and uses (0.55f, 0.85f, f).
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class OddstonePortalParticle extends PortalParticle {

    protected OddstonePortalParticle(ClientLevel level, double x, double y, double z,
                                     double xd, double yd, double zd) {
        super(level, x, y, z, xd, yd, zd);
        float f = this.bCol;   // vanilla's random brightness (its blue channel is f)
        this.rCol = f * 0.55F;
        this.gCol = f * 0.85F;
        this.bCol = f;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            OddstonePortalParticle particle = new OddstonePortalParticle(level, x, y, z, xd, yd, zd);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
