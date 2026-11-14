package com.ideomancer.ideomod.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a living entity's jump key. A player riding sends it to the server every tick
 * (ServerboundPlayerInputPacket); the Machine Seat turns it into a pulse out of its bottom face.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("jumping")
    boolean ideomod$isJumping();
}
