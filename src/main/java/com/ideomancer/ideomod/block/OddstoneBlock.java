package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.movement.StickyType;
import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
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
 *    No slime type can grab Oddstone back. Oddstone does NOT stick to other
 *    oddstone-family blocks via direct contact — those connections resolve
 *    exclusively through sympathetic movement (Feature 3).
 *    Also sticks to Framed versions of slime types (via INNER_STICKY check).
 * 5. PORTAL FRAME: Oddstone blocks form the edges of portal frames. Portals
 *    are activated by throwing an ender pearl into the frame interior (handled
 *    by EnderPearlPortalHandler). Deactivation occurs when the frame structure
 *    is broken (Oddstone or corner block removed).
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class OddstoneBlock extends Block implements ISympathetic {

    public static final String CHANNEL = "oddstone";

    public OddstoneBlock(Properties properties) {
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

        // Feature 6: Lava Transformation — convert adjacent lava sources to Hollowstone
        convertAdjacentLava(level, pos);

        OddstonePortalHandler.onNeighborChanged(serverLevel, pos, neighborPos);
    }

    /**
     * When Oddstone is placed next to existing lava, convert it immediately.
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            convertAdjacentLava(level, pos);
        }
    }

    // ========================================================================
    // FEATURE 6: Lava Transformation — lava sources become Hollowstone
    // ========================================================================

    /**
     * Check all 6 cardinal neighbors. If any are lava source blocks,
     * replace them with Hollowstone. Oddstone is NOT consumed.
     */
    private void convertAdjacentLava(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.is(Blocks.LAVA)) {
                FluidState fluidState = level.getFluidState(neighborPos);
                if (fluidState.isSource()) {
                    level.setBlock(neighborPos,
                            ModBlocks.HOLLOW_STONE.get().defaultBlockState(),
                            Block.UPDATE_ALL);
                    // Play sizzling/cooling sound
                    level.playSound(null, neighborPos,
                            SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS,
                            0.5f, 2.2f + level.getRandom().nextFloat() * 0.4f);
                    // Spawn steam particles
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                                neighborPos.getX() + 0.5, neighborPos.getY() + 1.0,
                                neighborPos.getZ() + 0.5,
                                8, 0.3, 0.3, 0.3, 0.0);
                    }
                }
            }
        }
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
     * Oddstone sticks to ALL slime-type blocks, including their Framed variants.
     * Oddstone does NOT stick to other oddstone-family blocks via direct contact;
     * those connections resolve exclusively through sympathetic movement.
     *
     * - Oddstone + Slime: YES (Oddstone grabs slime)
     * - Oddstone + Honey: YES (Oddstone grabs honey)
     * - Oddstone + Odd Slime: YES (Oddstone grabs odd slime)
     * - Oddstone + Framed Slime/Honey/OddSlime: YES (via INNER_STICKY)
     * - Oddstone + Oddstone: NO (resolved via sympathetic movement only)
     * - Oddstone + Machine Block: NO (Machine Block is its own exclusive family)
     * - Oddstone + anything else: NO (Oddstone only grabs sticky things)
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
