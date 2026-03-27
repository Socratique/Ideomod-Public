package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.block.movement.StickyType;
import com.ideomancer.ideomod.config.IdeomodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.jetbrains.annotations.Nullable;

/**
 * Cobbled Oddstone - a cobblestone variant of Oddstone.
 *
 * Shares all core Oddstone features EXCEPT portal frame behavior:
 *   1. MOB AVOIDANCE: Mobs refuse to pathfind over this block.
 *   2. REDSTONE INSULATION: Set via block properties in ModBlocks.
 *   3. SYMPATHETIC MOVEMENT: Isolated channel "cobbled_oddstone" --
 *      only links with other cobbled oddstone variants (walls/stairs/slabs).
 *   4. ONE-WAY STICKINESS: Same as Oddstone (grabs all slime types, NOT other oddstone).
 *      Oddstone-to-oddstone connections resolve via sympathetic movement only.
 *
 * Does NOT serve as a portal frame block.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class CobbledOddstoneBlock extends Block implements ISympathetic {

    public static final String CHANNEL = "cobbled_oddstone";

    public CobbledOddstoneBlock(Properties properties) {
        super(properties);
    }

    // ========================================================================
    // ISympathetic
    // ========================================================================

    @Override
    public String getSympatheticChannel() {
        return CHANNEL;
    }

    // ========================================================================
    // ONE-WAY STICKINESS (same as OddstoneBlock)
    // ========================================================================

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        StickyType otherType = StickyType.of(other);
        return StickyType.ODDSTONE.canGrab(otherType) && otherType != StickyType.NONE;
    }

    // ========================================================================
    // MOB PATHFINDING AVOIDANCE (same as OddstoneBlock)
    // ========================================================================

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos,
                                   PathComputationType type) {
        return false;
    }

    @Override
    public @Nullable BlockPathTypes getBlockPathType(BlockState state, BlockGetter level,
                                                      BlockPos pos, @Nullable Mob mob) {
        if (mob == null) return BlockPathTypes.DAMAGE_OTHER;
        net.minecraft.resources.ResourceLocation mobKey = net.minecraftforge.registries.ForgeRegistries
                .ENTITY_TYPES.getKey(mob.getType());
        if (mobKey == null) return BlockPathTypes.DAMAGE_OTHER;
        if (IdeomodConfig.isMobAffected(mobKey.toString())) return BlockPathTypes.DAMAGE_OTHER;
        return null;
    }

    @Override
    public @Nullable BlockPathTypes getAdjacentBlockPathType(BlockState state, BlockGetter level,
                                                              BlockPos pos, @Nullable Mob mob,
                                                              BlockPathTypes originalType) {
        if (mob == null) return BlockPathTypes.DANGER_OTHER;
        net.minecraft.resources.ResourceLocation mobKey = net.minecraftforge.registries.ForgeRegistries
                .ENTITY_TYPES.getKey(mob.getType());
        if (mobKey == null) return BlockPathTypes.DANGER_OTHER;
        if (IdeomodConfig.isMobAffected(mobKey.toString())) return BlockPathTypes.DANGER_OTHER;
        return null;
    }
}
