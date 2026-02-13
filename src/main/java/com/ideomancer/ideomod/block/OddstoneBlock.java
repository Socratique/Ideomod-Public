package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.config.OddstoneConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.jetbrains.annotations.Nullable;

/**
 * Oddstone - a strange stone block with five unique properties:
 * 
 * 1. MOB AVOIDANCE: Mobs refuse to pathfind over this block.
 * 2. REDSTONE INSULATION: Does not conduct redstone signal through it.
 *    (Set via .isRedstoneConductor(false) in ModBlocks registration)
 * 3. SYMPATHETIC MOVEMENT: Wireless slime block behavior via piston events.
 *    (Handled by OddstoneMovementHandler)
 * 4. ONE-WAY STICKINESS: Oddstone grabs slime, slime doesn't grab Oddstone.
 * 5. PORTAL FRAME: When ALL Oddstone blocks in a valid frame are powered,
 *    the portal activates. If ANY Oddstone loses power, it deactivates.
 *    (Handled by OddstonePortalHandler)
 */
public class OddstoneBlock extends Block {

    public OddstoneBlock(Properties properties) {
        super(properties);
    }

    // ========================================================================
    // FEATURE 5: Portal Frame — delegates to OddstonePortalHandler
    // ========================================================================

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        OddstonePortalHandler.onNeighborChanged(serverLevel, pos, neighborPos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !newState.is(this)) {
            if (level instanceof ServerLevel serverLevel) {
                OddstonePortalHandler.onBlockRemoved(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ========================================================================
    // FEATURE 4: One-Way Stickiness
    // ========================================================================

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        if (other.getBlock() instanceof HoneyBlock) return false;
        if (other.getBlock() instanceof OddstoneBlock) return true;
        if (other.getBlock() instanceof SlimeBlock) return true;
        return false;
    }

    // ========================================================================
    // FEATURE 1: Mob Pathfinding Avoidance
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
        String mobId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(mob.getType()).toString();
        if (OddstoneConfig.isMobAffected(mobId)) return BlockPathTypes.DAMAGE_OTHER;
        return null;
    }

    @Override
    public @Nullable BlockPathTypes getAdjacentBlockPathType(BlockState state, BlockGetter level,
                                                              BlockPos pos, @Nullable Mob mob,
                                                              BlockPathTypes originalType) {
        if (mob == null) return BlockPathTypes.DANGER_OTHER;
        String mobId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(mob.getType()).toString();
        if (OddstoneConfig.isMobAffected(mobId)) return BlockPathTypes.DANGER_OTHER;
        return null;
    }
}
