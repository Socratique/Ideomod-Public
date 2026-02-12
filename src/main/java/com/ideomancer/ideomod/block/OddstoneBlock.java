package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.config.OddstoneConfig;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.jetbrains.annotations.Nullable;

/**
 * Oddstone - a strange stone block with five unique properties:
 * 
 * 1. MOB AVOIDANCE: Mobs refuse to pathfind over this block.
 * 2. REDSTONE INSULATION: Does not conduct redstone signal through it.
 * 3. SYMPATHETIC MOVEMENT: Wireless slime block behavior via piston events.
 * 4. ONE-WAY STICKINESS: Oddstone grabs slime, slime doesn't grab Oddstone.
 * 5. PORTAL FRAME: When ALL Oddstone blocks in a valid frame are powered,
 *    the portal activates. If ANY Oddstone loses power, it deactivates.
 */
public class OddstoneBlock extends Block {

    public OddstoneBlock(Properties properties) {
        super(properties);
    }

    // ========================================================================
    // FEATURE 5: Portal Frame Activation & Deactivation
    // ========================================================================

    /**
     * On neighbor change:
     * - Power ON: try to activate (frame detector checks ALL are powered)
     * - Power OFF: if adjacent to portal blocks, deactivate the portal
     * - Corner block broken: if adjacent to portal blocks, deactivate
     * 
     * When a corner block (iron_block, diamond_block, etc.) is broken,
     * the Oddstone blocks next to it receive neighborChanged. We detect
     * this by checking if we're adjacent to portal blocks AND the
     * changed neighbor is no longer a valid corner block.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        boolean hasPower = level.hasNeighborSignal(pos);
        boolean adjacentToPortal = isAdjacentToPortal(level, pos);

        if (hasPower && !adjacentToPortal) {
            // Got power, no portal yet - try to activate
            tryActivatePortal(serverLevel, pos);
        } else if (!hasPower && adjacentToPortal) {
            // Lost power while part of lit portal - deactivate
            tryDeactivatePortal(serverLevel, pos);
        }

        // Corner block broken check: if we're next to portal blocks
        // and the neighbor that changed is NOT a valid corner or oddstone,
        // a corner might have been broken - deactivate
        if (adjacentToPortal) {
            BlockState changedState = level.getBlockState(neighborPos);
            if (!changedState.is(ModBlocks.ODDSTONE.get())
                && !OddstonePortalBlock.isCornerBlock(changedState)
                && !changedState.is(ModBlocks.ODDSTONE_PORTAL.get())) {
                tryDeactivatePortal(serverLevel, pos);
            }
        }
    }

    /**
     * Quick check: is this Oddstone adjacent to any portal blocks?
     */
    private boolean isAdjacentToPortal(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).is(ModBlocks.ODDSTONE_PORTAL.get())) {
                return true;
            }
        }
        return false;
    }

    private void tryActivatePortal(ServerLevel level, BlockPos pos) {
        // detectFrame now checks all oddstone blocks are powered internally
        OddstonePortalFrameDetector.FrameResult frame =
                OddstonePortalFrameDetector.detectFrame(level, pos);

        if (frame == null) return;

        // Check if already lit
        for (BlockPos interior : frame.interiorPositions) {
            if (level.getBlockState(interior).is(ModBlocks.ODDSTONE_PORTAL.get())) {
                return; // Already active
            }
        }

        // Try to register
        OddstonePortalSavedData data = OddstonePortalSavedData.get(level);
        BlockPos registrationPos = frame.corners[3]; // BL corner
        String axisStr = frame.axis == Direction.Axis.X ? "X" : "Z";

        OddstonePortalSavedData.RegisterResult result =
                data.registerPortal(frame.address, registrationPos, axisStr);

        switch (result) {
            case FAILED_ADDRESS_FULL:
                Ideomod.LOGGER.info("Oddstone Portal at {} failed to light - address [{}] is taken",
                        pos, frame.address);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 0.5F, 1.0F);
                return;

            case FAILED_ALREADY_EXISTS:
                return;

            case REGISTERED_UNLINKED:
            case REGISTERED_LINKED:
                Direction.Axis portalAxis = frame.axis;
                BlockState portalState = ModBlocks.ODDSTONE_PORTAL.get().defaultBlockState()
                        .setValue(OddstonePortalBlock.AXIS, portalAxis);

                for (BlockPos interior : frame.interiorPositions) {
                    level.setBlock(interior, portalState, 2);
                }

                // Flint & steel sound - same as lighting a nether portal
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);

                if (result == OddstonePortalSavedData.RegisterResult.REGISTERED_LINKED) {
                    Ideomod.LOGGER.info("Oddstone Portal activated and LINKED at {} [{}]",
                            registrationPos, frame.address);
                } else {
                    Ideomod.LOGGER.info("Oddstone Portal activated (unlinked) at {} [{}]",
                            registrationPos, frame.address);
                }
                break;
        }
    }

    /**
     * This Oddstone lost power. If it's adjacent to portal blocks,
     * the portal must be deactivated.
     */
    private void tryDeactivatePortal(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);

            if (neighborState.is(ModBlocks.ODDSTONE_PORTAL.get())) {
                unregisterAndClearPortal(level, neighbor, neighborState);
                return;
            }
        }
    }

    /**
     * When this Oddstone is broken, deactivate any adjacent portal.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !newState.is(this)) {
            if (level instanceof ServerLevel serverLevel) {
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = pos.relative(dir);
                    BlockState neighborState = level.getBlockState(neighbor);
                    if (neighborState.is(ModBlocks.ODDSTONE_PORTAL.get())) {
                        unregisterAndClearPortal(serverLevel, neighbor, neighborState);
                        break;
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Finds the portal registration from a portal block, unregisters it,
     * and clears all portal blocks.
     */
    private void unregisterAndClearPortal(ServerLevel level, BlockPos portalBlockPos,
                                           BlockState portalState) {
        OddstonePortalSavedData data = OddstonePortalSavedData.get(level);
        Direction.Axis axis = portalState.getValue(OddstonePortalBlock.AXIS);
        Direction hDir = (axis == Direction.Axis.X) ? Direction.SOUTH : Direction.EAST;

        // Navigate to bottom-left of interior
        BlockPos bottom = portalBlockPos;
        while (level.getBlockState(bottom.below()).is(ModBlocks.ODDSTONE_PORTAL.get())) {
            bottom = bottom.below();
        }
        BlockPos bottomLeft = bottom;
        while (level.getBlockState(bottomLeft.relative(hDir.getOpposite()))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            bottomLeft = bottomLeft.relative(hDir.getOpposite());
        }

        // Frame corner is one step outside interior
        BlockPos frameCorner = bottomLeft.below().relative(hDir.getOpposite());
        data.unregisterPortal(frameCorner);

        // Clear all portal blocks
        clearPortalBlocks(level, bottomLeft, hDir);

        Ideomod.LOGGER.info("Oddstone Portal deactivated (frame corner {})", frameCorner);
    }

    private void clearPortalBlocks(ServerLevel level, BlockPos bottomLeft, Direction hDir) {
        int width = 0;
        while (level.getBlockState(bottomLeft.relative(hDir, width))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            width++;
        }

        int height = 0;
        while (level.getBlockState(bottomLeft.above(height))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            height++;
        }

        for (int h = 0; h < width; h++) {
            for (int v = 0; v < height; v++) {
                BlockPos portalPos = bottomLeft.relative(hDir, h).above(v);
                if (level.getBlockState(portalPos).is(ModBlocks.ODDSTONE_PORTAL.get())) {
                    level.setBlock(portalPos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
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
