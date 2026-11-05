package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.config.IdeomodConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Land-mob avoidance for the Oddstone walls, stairs and slabs: the same rule the
 * three full Oddstone blocks use. A mob on the configured list treats the block as
 * damaging to stand on and the blocks beside it as dangerous, so it paths around.
 * A null mob (vanilla's own path cache) gets the avoiding answer too.
 *
 * Ideomancer, 2026-11-05: every Oddstone variant keeps Oddstone's behaviour.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public final class OddstonePathing {

    private OddstonePathing() {}

    /** Path type of the block itself. */
    public static @Nullable BlockPathTypes onBlock(@Nullable Mob mob) {
        return avoids(mob) ? BlockPathTypes.DAMAGE_OTHER : null;
    }

    /** Path type of the blocks next to it. */
    public static @Nullable BlockPathTypes beside(@Nullable Mob mob) {
        return avoids(mob) ? BlockPathTypes.DANGER_OTHER : null;
    }

    private static boolean avoids(@Nullable Mob mob) {
        if (mob == null) return true;
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        return key == null || IdeomodConfig.isMobAffected(key.toString());
    }
}
