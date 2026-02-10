package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.config.OddstoneConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.jetbrains.annotations.Nullable;

/**
 * Oddstone - a strange stone block with three unique properties:
 * 
 * 1. MOB AVOIDANCE: Mobs refuse to pathfind over this block.
 *    A JSON config lists which mobs are affected (all by default).
 * 
 * 2. REDSTONE INSULATION: Does not conduct redstone signal through it,
 *    similar to glass. Redstone dust can't be placed on top, and signals
 *    don't pass through it.
 * 
 * 3. SYMPATHETIC MOVEMENT: When pushed by a piston, other Oddstone blocks
 *    within a configurable radius also move in the same direction.
 *    See OddstoneMovementHandler for the piston logic.
 */
public class OddstoneBlock extends Block {

    public OddstoneBlock(Properties properties) {
        super(properties);
    }

    // ========================================================================
    // FEATURE 1: Mob Pathfinding Avoidance
    // ========================================================================
    
    /**
     * Tells the pathfinding system that this block is dangerous.
     * Mobs will avoid walking on/through it, routing around if possible.
     * 
     * DANGER_OTHER gives a high "malus" (penalty score) so mobs treat it
     * like walking near cactus or fire - they'll find another way.
     * 
     * The mob parameter may be null due to a known Forge issue, so per-mob
     * filtering via the config list is handled here with a null check.
     * When mob is null, we default to blocking (safer).
     */
    @Override
    public @Nullable BlockPathTypes getBlockPathType(BlockState state, BlockGetter level,
                                                      BlockPos pos, @Nullable Mob mob) {
        // If mob is null (Forge bug), default to danger - block everything
        if (mob == null) {
            return BlockPathTypes.DANGER_OTHER;
        }
        
        // Check if this mob type should be affected by Oddstone
        String mobId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(mob.getType()).toString();
        
        if (OddstoneConfig.isMobAffected(mobId)) {
            return BlockPathTypes.DANGER_OTHER;
        }
        
        // Mob is exempt - treat as normal walkable block
        return null; // null means "use default behavior"
    }

    /**
     * Also make adjacent blocks seem dangerous so mobs don't path right next
     * to the Oddstone either. This creates a 1-block "buffer zone" around it.
     * Mobs will try to stay at least 1 block away when pathing.
     */
    @Override
    public @Nullable BlockPathTypes getAdjacentBlockPathType(BlockState state, BlockGetter level,
                                                              BlockPos pos, @Nullable Mob mob,
                                                              BlockPathTypes originalType) {
        // If mob is null, default to danger
        if (mob == null) {
            return BlockPathTypes.DANGER_OTHER;
        }
        
        String mobId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(mob.getType()).toString();
        
        if (OddstoneConfig.isMobAffected(mobId)) {
            return BlockPathTypes.DANGER_OTHER;
        }
        
        return null;
    }

    // ========================================================================
    // FEATURE 2: Redstone Insulation
    // ========================================================================

    /**
     * Returns false so redstone signal does not propagate through this block.
     * This is the same approach glass uses - the block is not a "conductor"
     * of redstone power. A powered block next to Oddstone won't cause the
     * Oddstone to become powered, and things on the other side won't activate.
     */
    @Override
    public boolean isSignalSource(BlockState state) {
        return false;
    }

    /**
     * Returns true to tell the game this block should NOT conduct redstone.
     * When this is true, the block behaves like glass/leaves for redstone:
     * - Redstone dust won't connect to it
     * - Repeaters/comparators won't read through it
     * - It won't become "strongly powered" or "weakly powered"
     * 
     * Technically the method name is misleading - "useShapeForLightOcclusion"
     * is NOT what controls redstone. The actual key is that we need the block
     * to not be a normal solid opaque cube for redstone purposes.
     * We do this by making isRedstoneConductor return false.
     */
    // Note: Redstone conductivity in 1.20.1 is controlled by the block being
    // a "normal cube" - specifically BlockStateBase.isRedstoneConductor().
    // We handle this through the block properties in ModBlocks.java by setting
    // .isRedstoneConductor((state, level, pos) -> false)
}
