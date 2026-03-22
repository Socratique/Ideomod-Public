package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.movement.StickyType;
import com.ideomancer.ideomod.config.IdeomodConfig;
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
import com.ideomancer.ideomod.block.portal.OddstonePortalHandler;

/**
 * Oddstone - a strange stone block with five unique properties:
 *
 * 1. MOB AVOIDANCE: Mobs refuse to pathfind over this block.
 * 2. REDSTONE INSULATION: Does not conduct redstone signal through it.
 *    (Set via .isRedstoneConductor(false) in ModBlocks registration)
 * 3. SYMPATHETIC MOVEMENT: Wireless slime block behavior via piston events.
 *    (Handled by PistonStructureResolver mixin)
 * 4. ONE-WAY STICKINESS: Oddstone grabs ALL slime types (slime, honey, odd slime).
 *    No slime type can grab Oddstone back. Oddstone also sticks to other Oddstone.
 *    Also sticks to Framed versions of all the above (via INNER_STICKY check).
 * 5. PORTAL FRAME: Oddstone blocks form the edges of portal frames. Portals
 *    are activated by throwing an ender pearl into the frame interior (handled
 *    by EnderPearlPortalHandler). Deactivation occurs when the frame structure
 *    is broken (Oddstone or corner block removed).
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class OddstoneBlock extends Block {

    public OddstoneBlock(Properties properties) {
        super(properties);
    }

    // ========================================================================
    // FEATURE 5: Portal Frame — delegates to OddstonePortalHandler
    // ========================================================================

    /**
     * Neighbor changes are still relevant for portal DEACTIVATION validation.
     * When a neighbor changes next to an Oddstone that's adjacent to a portal,
     * the handler re-validates the frame structure (corners present, oddstone
     * present). If the frame is broken, the portal deactivates.
     *
     * Activation is handled by EnderPearlPortalHandler, not by neighborChanged.
     */
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

    /**
     * Oddstone sticks to ALL slime-type blocks and other Oddstone,
     * including their Framed variants.
     * It's the universal grabber — the "parent" of all sticky systems.
     *
     * - Oddstone + Slime: YES (Oddstone grabs slime)
     * - Oddstone + Honey: YES (Oddstone grabs honey)
     * - Oddstone + Odd Slime: YES (Oddstone grabs odd slime)
     * - Oddstone + Oddstone: YES (for sympathetic movement)
     * - Oddstone + Framed Slime/Honey/OddSlime/Oddstone: YES (via INNER_STICKY)
     * - Oddstone + Machine Block: NO (Machine Block is its own exclusive family)
     * - Oddstone + anything else: NO (Oddstone only grabs sticky things + itself)
     *
     * The reverse (slime/honey/odd slime grabbing Oddstone) is BLOCKED
     * by StickinessRules grab immunity rules registered in Ideomod.java.
     */
    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        StickyType otherType = StickyType.of(other);
        return StickyType.ODDSTONE.canGrab(otherType) && otherType != StickyType.NONE;
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
