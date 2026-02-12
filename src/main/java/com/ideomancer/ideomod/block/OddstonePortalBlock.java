package com.ideomancer.ideomod.block;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Oddstone Portal Block - fills the interior of a lit Oddstone portal frame.
 * 
 * TELEPORTATION preserves relative position within the portal frame,
 * similar to nether portals. If you walk through the left side of
 * portal A, you come out the left side of portal B.
 * 
 * LINKED portals: position-preserving teleport to partner.
 * UNLINKED portals: scatter-teleport 200-1000 blocks + 5 hearts damage.
 * 
 * DEACTIVATION: portal blocks break (via updateShape) when any adjacent
 * frame or corner block is removed. onRemove unregisters the portal address.
 */
public class OddstonePortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    protected static final VoxelShape X_AXIS_AABB = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    protected static final VoxelShape Z_AXIS_AABB = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    private static final int TELEPORT_COOLDOWN = 80; // 4 seconds
    private static final Random RANDOM = new Random();

    public OddstonePortalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    // ========================================================================
    // SHAPE
    // ========================================================================

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AXIS_AABB : X_AXIS_AABB;
    }

    // ========================================================================
    // PORTAL BREAKING - cascade when frame/corner is removed
    // ========================================================================

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis portalAxis = state.getValue(AXIS);

        Direction[] checkDirs;
        if (portalAxis == Direction.Axis.X) {
            checkDirs = new Direction[]{Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH};
        } else {
            checkDirs = new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST};
        }

        for (Direction dir : checkDirs) {
            BlockPos checkPos = pos.relative(dir);
            BlockState checkState = level.getBlockState(checkPos);
            if (!checkState.is(ModBlocks.ODDSTONE_PORTAL.get())
                && !checkState.is(ModBlocks.ODDSTONE.get())
                && !isCornerBlock(checkState)) {
                return Blocks.AIR.defaultBlockState();
            }
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    static boolean isCornerBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.COAL_BLOCK
            || block == Blocks.IRON_BLOCK
            || block == Blocks.COPPER_BLOCK
            || block == Blocks.GOLD_BLOCK
            || block == Blocks.REDSTONE_BLOCK
            || block == Blocks.LAPIS_BLOCK
            || block == Blocks.DIAMOND_BLOCK
            || block == Blocks.EMERALD_BLOCK
            || block == Blocks.NETHERITE_BLOCK
            || block == Blocks.QUARTZ_BLOCK
            || block == Blocks.AMETHYST_BLOCK
            || block == Blocks.RAW_IRON_BLOCK
            || block == Blocks.RAW_COPPER_BLOCK
            || block == Blocks.RAW_GOLD_BLOCK;
    }

    // ========================================================================
    // TELEPORTATION
    // ========================================================================

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!(entity instanceof ServerPlayer player)) return;
        if (player.isOnPortalCooldown()) return;

        player.setPortalCooldown(TELEPORT_COOLDOWN);

        OddstonePortalSavedData data = OddstonePortalSavedData.get(serverLevel);
        OddstonePortalSavedData.PortalEntry entry = findPortalEntry(serverLevel, pos, state, data);

        if (entry == null) {
            Ideomod.LOGGER.warn("Orphaned portal block at {} - no registration found", pos);
            scatterTeleport(serverLevel, player);
            return;
        }

        if (entry.isLinked()) {
            linkedTeleport(serverLevel, player, entry, pos, state);
        } else {
            scatterTeleport(serverLevel, player);
        }
    }

    /**
     * Teleports the player to the linked partner portal, preserving their
     * relative position within the portal frame.
     * 
     * HOW POSITION TRANSFER WORKS:
     * ============================
     * 1. Find the player's offset from the bottom-left of THIS portal's interior
     *    (as a fraction: 0.0 = left/bottom edge, 1.0 = right/top edge)
     * 2. Apply that same fraction to the PARTNER portal's interior
     * 3. Teleport to the resulting position
     * 
     * This means walking through the left side of portal A puts you on
     * the left side of portal B, even if the portals are different sizes.
     */
    private void linkedTeleport(ServerLevel level, ServerPlayer player,
                                 OddstonePortalSavedData.PortalEntry entry,
                                 BlockPos portalBlockPos, BlockState portalState) {
        BlockPos partnerPos = entry.partnerPos;
        if (partnerPos == null) return;

        OddstonePortalSavedData data = OddstonePortalSavedData.get(level);
        OddstonePortalSavedData.PortalEntry partnerEntry = data.getPortal(partnerPos);

        if (partnerEntry == null) {
            Ideomod.LOGGER.warn("Partner portal at {} not found in registry", partnerPos);
            scatterTeleport(level, player);
            return;
        }

        // -- Step 1: Find THIS portal's interior bounds --
        Direction.Axis thisAxis = portalState.getValue(AXIS);
        Direction thisHDir = (thisAxis == Direction.Axis.X) ? Direction.SOUTH : Direction.EAST;
        PortalBounds thisBounds = findPortalBounds(level, portalBlockPos, thisHDir);

        if (thisBounds == null) {
            // Fallback
            simpleTeleport(level, player, partnerPos, partnerEntry);
            return;
        }

        // -- Step 2: Calculate player's relative position (0.0 to 1.0) --
        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();

        // Horizontal fraction along the portal
        double hFraction;
        if (thisAxis == Direction.Axis.X) {
            // Portal extends along Z
            hFraction = (playerZ - thisBounds.minH) / (thisBounds.maxH - thisBounds.minH);
        } else {
            // Portal extends along X
            hFraction = (playerX - thisBounds.minH) / (thisBounds.maxH - thisBounds.minH);
        }

        // Vertical fraction
        double vFraction = (playerY - thisBounds.minV) / (thisBounds.maxV - thisBounds.minV);

        // Clamp to 0-1
        hFraction = Math.max(0.0, Math.min(1.0, hFraction));
        vFraction = Math.max(0.0, Math.min(1.0, vFraction));

        // -- Step 3: Find PARTNER portal's interior bounds --
        Direction.Axis partnerAxis = partnerEntry.axis.equals("X")
                ? Direction.Axis.X : Direction.Axis.Z;
        Direction partnerHDir = (partnerAxis == Direction.Axis.X) ? Direction.SOUTH : Direction.EAST;

        // Find a portal block in the partner
        BlockPos partnerPortalBlock = findAnyPortalBlock(level, partnerPos, partnerHDir);
        if (partnerPortalBlock == null) {
            simpleTeleport(level, player, partnerPos, partnerEntry);
            return;
        }

        PortalBounds partnerBounds = findPortalBounds(level, partnerPortalBlock, partnerHDir);
        if (partnerBounds == null) {
            simpleTeleport(level, player, partnerPos, partnerEntry);
            return;
        }

        // -- Step 4: Map fractions to partner coordinates --
        double targetH = partnerBounds.minH + hFraction * (partnerBounds.maxH - partnerBounds.minH);
        double targetV = partnerBounds.minV + vFraction * (partnerBounds.maxV - partnerBounds.minV);

        double targetX, targetY, targetZ;
        if (partnerAxis == Direction.Axis.X) {
            // Partner portal extends along Z, is thin along X
            targetX = partnerBounds.fixedCoord + 0.5; // Center of the thin axis
            targetY = targetV;
            targetZ = targetH;
        } else {
            // Partner portal extends along X, is thin along Z
            targetX = targetH;
            targetY = targetV;
            targetZ = partnerBounds.fixedCoord + 0.5;
        }

        player.teleportTo(targetX, targetY, targetZ);

        Ideomod.LOGGER.debug("Player {} teleported via Oddstone Portal to ({}, {}, {})",
                player.getName().getString(), targetX, targetY, targetZ);
    }

    /**
     * Simple fallback teleport to the first portal block we can find.
     */
    private void simpleTeleport(ServerLevel level, ServerPlayer player,
                                 BlockPos frameCorner,
                                 OddstonePortalSavedData.PortalEntry partnerEntry) {
        Direction.Axis partnerAxis = partnerEntry.axis.equals("X")
                ? Direction.Axis.X : Direction.Axis.Z;
        Direction hDir = (partnerAxis == Direction.Axis.X) ? Direction.SOUTH : Direction.EAST;

        BlockPos target = findAnyPortalBlock(level, frameCorner, hDir);
        if (target == null) target = frameCorner.above();

        player.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
    }

    /**
     * Finds any portal block in the partner portal by scanning from the
     * frame's registered corner position (bottom-left of frame).
     */
    @Nullable
    private BlockPos findAnyPortalBlock(ServerLevel level, BlockPos frameCorner, Direction hDir) {
        // Interior starts 1 block in and 1 block up from the frame corner
        for (int h = 1; h <= 20; h++) {
            for (int v = 1; v <= 20; v++) {
                BlockPos check = frameCorner.relative(hDir, h).above(v);
                if (level.getBlockState(check).is(ModBlocks.ODDSTONE_PORTAL.get())) {
                    return check;
                }
            }
        }
        return null;
    }

    // ========================================================================
    // PORTAL BOUNDS - for position mapping
    // ========================================================================

    /**
     * Stores the world-coordinate bounds of a portal's interior.
     * Used to calculate relative position for teleport mapping.
     */
    private static class PortalBounds {
        final double minH;   // Minimum horizontal coordinate (X or Z depending on axis)
        final double maxH;   // Maximum horizontal coordinate
        final double minV;   // Minimum Y
        final double maxV;   // Maximum Y
        final double fixedCoord; // The coordinate on the thin axis (for placing the player)

        PortalBounds(double minH, double maxH, double minV, double maxV, double fixedCoord) {
            this.minH = minH;
            this.maxH = maxH;
            this.minV = minV;
            this.maxV = maxV;
            this.fixedCoord = fixedCoord;
        }
    }

    /**
     * Finds the world-coordinate bounds of a portal's interior
     * by scanning from a known portal block.
     */
    @Nullable
    private PortalBounds findPortalBounds(Level level, BlockPos portalBlock, Direction hDir) {
        // Find bottom-left of interior
        BlockPos bottom = portalBlock;
        while (level.getBlockState(bottom.below()).is(ModBlocks.ODDSTONE_PORTAL.get())) {
            bottom = bottom.below();
        }
        BlockPos bottomLeft = bottom;
        while (level.getBlockState(bottomLeft.relative(hDir.getOpposite()))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            bottomLeft = bottomLeft.relative(hDir.getOpposite());
        }

        // Measure width
        int width = 0;
        while (level.getBlockState(bottomLeft.relative(hDir, width))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            width++;
        }

        // Measure height
        int height = 0;
        while (level.getBlockState(bottomLeft.above(height))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            height++;
        }

        if (width == 0 || height == 0) return null;

        // Get the axis this portal uses
        BlockState blState = level.getBlockState(bottomLeft);
        if (!blState.is(ModBlocks.ODDSTONE_PORTAL.get())) return null;
        Direction.Axis axis = blState.getValue(AXIS);

        // Calculate world-coordinate bounds
        // The interior of a portal block at (x,y,z) occupies the full block space
        double minV = bottomLeft.getY();
        double maxV = bottomLeft.getY() + height;

        double minH, maxH, fixedCoord;
        if (axis == Direction.Axis.X) {
            // Portal extends along Z
            minH = bottomLeft.getZ();
            maxH = bottomLeft.getZ() + width;
            fixedCoord = bottomLeft.getX();
        } else {
            // Portal extends along X
            minH = bottomLeft.getX();
            maxH = bottomLeft.getX() + width;
            fixedCoord = bottomLeft.getZ();
        }

        return new PortalBounds(minH, maxH, minV, maxV, fixedCoord);
    }

    // ========================================================================
    // SCATTER TELEPORT
    // ========================================================================

    private void scatterTeleport(ServerLevel level, ServerPlayer player) {
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        double distance = 200 + RANDOM.nextDouble() * 800;
        int targetX = player.blockPosition().getX() + (int) (Math.cos(angle) * distance);
        int targetZ = player.blockPosition().getZ() + (int) (Math.sin(angle) * distance);

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, targetX, targetZ);
        BlockPos targetPos = new BlockPos(targetX, surfaceY, targetZ);

        player.teleportTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5
        );

        player.hurt(level.damageSources().magic(), 10.0F);

        Ideomod.LOGGER.info("Player {} scatter-teleported by unlinked Oddstone Portal to {}",
                player.getName().getString(), targetPos);
    }

    // ========================================================================
    // PORTAL ENTRY LOOKUP
    // ========================================================================

    /**
     * Finds the portal registration by navigating from a portal block
     * to the bottom-left interior, then to the frame corner.
     * 
     * Robust against partial cascade - captures the state immediately
     * before further blocks are removed.
     */
    @Nullable
    private OddstonePortalSavedData.PortalEntry findPortalEntry(
            ServerLevel level, BlockPos portalBlockPos, BlockState state,
            OddstonePortalSavedData data) {

        Direction.Axis axis = state.getValue(AXIS);
        Direction hDir = (axis == Direction.Axis.X) ? Direction.SOUTH : Direction.EAST;

        BlockPos bottom = portalBlockPos;
        while (level.getBlockState(bottom.below()).is(ModBlocks.ODDSTONE_PORTAL.get())) {
            bottom = bottom.below();
        }

        BlockPos bottomLeft = bottom;
        while (level.getBlockState(bottomLeft.relative(hDir.getOpposite()))
                .is(ModBlocks.ODDSTONE_PORTAL.get())) {
            bottomLeft = bottomLeft.relative(hDir.getOpposite());
        }

        BlockPos frameCorner = bottomLeft.below().relative(hDir.getOpposite());
        return data.getPortal(frameCorner);
    }

    // ========================================================================
    // UNREGISTRATION ON REMOVAL
    // ========================================================================

    /**
     * When a portal block is removed (corner broken, frame broken, redstone lost),
     * unregister the portal. The first portal block in the cascade to be removed
     * handles the unregistration; subsequent removals find no entry and skip.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !newState.is(this)) {
            if (level instanceof ServerLevel serverLevel) {
                OddstonePortalSavedData data = OddstonePortalSavedData.get(serverLevel);
                OddstonePortalSavedData.PortalEntry entry = findPortalEntry(serverLevel, pos, state, data);
                if (entry != null) {
                    data.unregisterPortal(entry.pos);
                    Ideomod.LOGGER.debug("Portal unregistered due to block removal at {}", pos);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ========================================================================
    // BLOCK PROPERTIES
    // ========================================================================

    @Override
    public boolean canBeReplaced(BlockState state,
                                  net.minecraft.world.item.context.BlockPlaceContext context) {
        return false;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> switch (state.getValue(AXIS)) {
                case Z -> state.setValue(AXIS, Direction.Axis.X);
                case X -> state.setValue(AXIS, Direction.Axis.Z);
                default -> state;
            };
            default -> state;
        };
    }
}
