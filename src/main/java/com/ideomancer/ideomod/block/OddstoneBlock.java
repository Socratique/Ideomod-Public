package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.config.OddstoneConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.jetbrains.annotations.Nullable;

/**
 * Oddstone - a strange stone block with three unique properties:
 * 
 * 1. MOB AVOIDANCE: Mobs refuse to pathfind over this block.
 *    Uses BOTH the vanilla isPathfindable override AND the Forge
 *    getBlockPathType hook for maximum compatibility.
 * 
 * 2. REDSTONE INSULATION: Does not conduct redstone signal through it.
 *    Handled via .isRedstoneConductor((s,l,p) -> false) in ModBlocks.
 * 
 * 3. SYMPATHETIC MOVEMENT: When pushed by a piston, nearby Oddstone
 *    blocks also move. See OddstoneMovementHandler.
 */
public class OddstoneBlock extends Block {

    public OddstoneBlock(Properties properties) {
        super(properties);
    }

    // ========================================================================
    // FEATURE 1: Mob Pathfinding Avoidance
    // ========================================================================

    /**
     * PRIMARY pathfinding control - vanilla method.
     * Returning false tells ALL pathfinding that this block cannot be
     * traversed. This is the same approach used by blocks like fences,
     * walls, and closed doors.
     * 
     * This method is checked by the vanilla pathfinder before any Forge
     * hooks, so it's the most reliable way to block pathfinding.
     */
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos,
                                   PathComputationType type) {
        // Block ALL pathfinding types: LAND, WATER, and AIR
        return false;
    }

    /**
     * SECONDARY pathfinding control - Forge hook.
     * Makes the block appear "dangerous" to mob AI, so they avoid
     * routing NEAR it (not just through it). This creates a buffer zone.
     * 
     * Mobs treat DANGER_OTHER like nearby cactus/fire - they try to
     * stay at least 1 block away when possible.
     */
    @Override
    public @Nullable BlockPathTypes getBlockPathType(BlockState state, BlockGetter level,
                                                      BlockPos pos, @Nullable Mob mob) {
        // If mob is null or is in the affected list, report as dangerous
        if (mob == null) {
            return BlockPathTypes.DAMAGE_OTHER;
        }
        
        String mobId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(mob.getType()).toString();
        
        if (OddstoneConfig.isMobAffected(mobId)) {
            return BlockPathTypes.DAMAGE_OTHER;
        }
        
        return null; // Unaffected mob - use default
    }

    /**
     * TERTIARY - makes blocks ADJACENT to Oddstone also register as
     * dangerous, so mobs route around with a 1-block buffer.
     */
    @Override
    public @Nullable BlockPathTypes getAdjacentBlockPathType(BlockState state, BlockGetter level,
                                                              BlockPos pos, @Nullable Mob mob,
                                                              BlockPathTypes originalType) {
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
}
