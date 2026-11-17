package com.ideomancer.ideomod.block.portal;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.TickTask;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
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
import org.joml.Vector3f;

/**
 * Oddstone Portal Block - fills the interior of a lit Oddstone portal frame.
 * 
 * TELEPORTATION preserves relative position within the portal frame,
 * similar to nether portals. If you walk through the left side of
 * portal A, you come out the left side of portal B.
 * 
 * When portals have different orientations (X-axis vs Z-axis), the
 * player's facing is rotated 90° to align with the destination axis,
 * then an additional 180° is applied. All teleports include a 180°
 * rotation so the player exits facing away from the destination portal,
 * as if walking through a doorway.
 * 
 * LINKED portals: position-preserving teleport to partner.
 * UNLINKED portals: scatter-teleport 200-1000 blocks + 5 hearts damage
 * (damage to living things only).
 *
 * WHAT GOES THROUGH: as with a nether portal, players, mobs, items,
 * projectiles and empty minecarts and boats; not riders, the vehicles
 * carrying them, or bosses. Moving things keep moving, turned with the exit.
 * 
 * DEACTIVATION: portal blocks break (via updateShape) when any adjacent
 * frame or corner block is removed. onRemove unregisters the portal address.
 *
 * PERFORMANCE: Portal geometry (interior dimensions) is cached in
 * OddstonePortalSavedData at registration time. findPortalBounds()
 * reads cached data first, only falling back to live PortalGeometry.detect()
 * if the cache is missing (old saves, registration without level).
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class OddstonePortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    protected static final VoxelShape X_AXIS_AABB = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    protected static final VoxelShape Z_AXIS_AABB = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    // Teleport cooldown now read from IdeomodConfig.getPortalTeleportCooldown()

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
        return state.getValue(AXIS) == Direction.Axis.X ? Z_AXIS_AABB : X_AXIS_AABB;
    }

    // ========================================================================
    // AMBIENCE - particles and hum, like a nether portal (client side)
    // ========================================================================

    /** Near-white sparks, like the bright crests in the portal texture. */
    private static final DustParticleOptions SPARK =
            new DustParticleOptions(new Vector3f(0.90f, 0.97f, 1.00f), 0.8f);

    /**
     * Light-blue portal particles (ModParticles.ODDSTONE_PORTAL: vanilla's
     * portal swirl in the portal's colours) drifting out of both faces, a few
     * per block per tick, with the odd white spark, and now and then the portal hum (pitched a
     * little higher than a nether portal's). The portal is thin along its
     * AXIS value, so particles start on either side along that axis.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5F,
                    random.nextFloat() * 0.4F + 1.2F, false);
        }
        boolean thinX = state.getValue(AXIS) == Direction.Axis.X;
        for (int i = 0; i < 4; i++) {
            int side = random.nextBoolean() ? 1 : -1;
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double dx = (random.nextDouble() - 0.5) * 0.5;
            double dy = (random.nextDouble() - 0.5) * 0.5;
            double dz = (random.nextDouble() - 0.5) * 0.5;
            if (thinX) {
                x = pos.getX() + 0.5 + 0.25 * side;
                dx = random.nextDouble() * 2.0 * side;
            } else {
                z = pos.getZ() + 0.5 + 0.25 * side;
                dz = random.nextDouble() * 2.0 * side;
            }
            level.addParticle(ModParticles.ODDSTONE_PORTAL.get(), x, y, z, dx, dy, dz);
        }
        if (random.nextInt(6) == 0) {
            double off = random.nextBoolean() ? 0.7 : 0.3;
            level.addParticle(SPARK,
                    thinX ? pos.getX() + off : pos.getX() + random.nextDouble(),
                    pos.getY() + random.nextDouble(),
                    thinX ? pos.getZ() + random.nextDouble() : pos.getZ() + off,
                    0.0, 0.02, 0.0);
        }
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
                && !PortalUtils.isFrameBlock(checkState)
                && !PortalUtils.isCornerBlock(checkState)) {
                return Blocks.AIR.defaultBlockState();
            }
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ========================================================================
    // TELEPORTATION
    // ========================================================================

    /**
     * Anything that could use a nether portal goes through: players, mobs, dropped
     * items, empty minecarts and boats. As with a nether portal (1.20.1), riders and
     * the vehicles carrying them do not, nor do bosses (canChangeDimensions).
     * Like a nether portal, staying inside keeps the cooldown topped up, so whatever
     * comes out and stays in the far portal does not bounce back; it has to leave first.
     */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        // Something already taken out of the world doesn't go through. The ender pearl that lights a
        // portal is consumed (EnderPearlPortalHandler), but it is still inside the opening that tick:
        // sending it through put down a live copy at the partner, which landed there and teleported
        // its thrower after it.
        if (entity.isRemoved()) return;
        if (entity.isPassenger() || entity.isVehicle() || !entity.canChangeDimensions()) return;
        if (entity.isOnPortalCooldown()) {
            entity.setPortalCooldown(IdeomodConfig.getPortalTeleportCooldown());
            return;
        }

        entity.setPortalCooldown(IdeomodConfig.getPortalTeleportCooldown());

        if (entity instanceof ServerPlayer player) {
            // A player walks in while the server is handling their own movement packet, and that
            // handler puts a survival player back where the client said they were, undoing a
            // teleport made inside it (only creative skipped that check). Vanilla moves players
            // through its portals in their tick instead; this queues the move to run straight
            // after the packet, still on the server thread.
            // (entityInside is handed a mutable position the game keeps reusing: keep a copy.)
            BlockPos at = pos.immutable();
            serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount(), () -> {
                if (player.isRemoved() || player.level() != serverLevel) return;
                BlockState now = serverLevel.getBlockState(at);
                if (!now.is(this)) return;
                sendThrough(serverLevel, at, now, player);
            }));
            return;
        }
        sendThrough(serverLevel, pos, state, entity);
    }

    /** Sends an entity standing in this portal block to the partner portal, or scatters it if unlinked. */
    private void sendThrough(ServerLevel serverLevel, BlockPos pos, BlockState state, Entity entity) {
        OddstonePortalSavedData data = OddstonePortalSavedData.get(serverLevel);
        OddstonePortalSavedData.PortalEntry entry = findPortalEntry(serverLevel, pos, state, data);

        if (entry == null) {
            Ideomod.LOGGER.warn("Orphaned portal block at {} - no registration found", pos);
            scatterTeleport(serverLevel, entity);
            return;
        }

        if (entry.isLinked()) {
            linkedTeleport(serverLevel, entity, entry, pos, state);
        } else {
            scatterTeleport(serverLevel, entity);
        }
    }

    /**
     * Teleports the entity to the linked partner portal, preserving their
     * relative position within the portal frame.
     * 
     * HOW POSITION TRANSFER WORKS:
     * ============================
     * 1. Find the entity's offset from the bottom-left of THIS portal's interior
     *    (as a fraction: 0.0 = left/bottom edge, 1.0 = right/top edge)
     * 2. Apply that same fraction to the PARTNER portal's interior
     * 3. Teleport to the resulting position
     * 
     * This means walking through the left side of portal A puts you on
     * the left side of portal B, even if the portals are different sizes.
     * 
     * HOW ROTATION WORKS:
     * ===================
     * All teleports apply a 180° yaw rotation so the entity exits
     * facing AWAY from the destination portal — like walking through
     * a doorway.
     *
     * When source and destination portals have different axes (X vs Z),
     * an additional 90° rotation is applied first to align the entity
     * with the destination portal's orientation.
     * 
     * Same-axis portals: 180° rotation (face away from destination).
     * Cross-axis portals: 90° + 180° rotation.
     */
    private void linkedTeleport(ServerLevel level, Entity entity,
                                 OddstonePortalSavedData.PortalEntry entry,
                                 BlockPos portalBlockPos, BlockState portalState) {
        BlockPos partnerPos = entry.getPartnerPos();
        if (partnerPos == null) return;

        OddstonePortalSavedData data = OddstonePortalSavedData.get(level);
        OddstonePortalSavedData.PortalEntry partnerEntry = data.getPortal(partnerPos);

        if (partnerEntry == null) {
            Ideomod.LOGGER.warn("Partner portal at {} not found in registry", partnerPos);
            scatterTeleport(level, entity);
            return;
        }

        // -- Step 1: Find THIS portal's interior bounds --
        Direction.Axis thisAxis = portalState.getValue(AXIS);
        Direction thisHDir = PortalUtils.getHorizontalDir(thisAxis);
        PortalBounds thisBounds = findPortalBounds(level, portalBlockPos, thisHDir, entry);

        if (thisBounds == null) {
            simpleTeleport(level, entity, partnerPos, partnerEntry);
            return;
        }

        // -- Step 2: Calculate entity's relative position (0.0 to 1.0) --
        double playerX = entity.getX();
        double playerY = entity.getY();
        double playerZ = entity.getZ();

        // Horizontal fraction along the portal
        // Guard: if portal has zero span (corrupted SavedData), default to center (0.5)
        double hSpan = thisBounds.maxH - thisBounds.minH;
        double vSpan = thisBounds.maxV - thisBounds.minV;

        double hFraction;
        if (hSpan <= 0.0) {
            hFraction = 0.5;
        } else if (thisAxis == Direction.Axis.X) {
            // Portal extends along Z
            hFraction = (playerZ - thisBounds.minH) / hSpan;
        } else {
            // Portal extends along X
            hFraction = (playerX - thisBounds.minH) / hSpan;
        }

        // Vertical fraction
        double vFraction = (vSpan <= 0.0) ? 0.5
                : (playerY - thisBounds.minV) / vSpan;

        // Clamp to 0-1
        hFraction = Math.max(0.0, Math.min(1.0, hFraction));
        vFraction = Math.max(0.0, Math.min(1.0, vFraction));

        // -- Step 3: Find PARTNER portal's interior bounds --
        Direction.Axis partnerAxis = partnerEntry.getAxisAsEnum();
        Direction partnerHDir = PortalUtils.getHorizontalDir(partnerAxis);

        // Try cached geometry first, fall back to live scan
        PortalBounds partnerBounds = findPortalBounds(level, null, partnerHDir, partnerEntry);

        if (partnerBounds == null) {
            // Cached geometry missing — need to find a portal block for live scan
            BlockPos partnerPortalBlock = findAnyPortalBlock(level, partnerPos, partnerHDir);
            if (partnerPortalBlock == null) {
                simpleTeleport(level, entity, partnerPos, partnerEntry);
                return;
            }
            partnerBounds = findPortalBoundsLive(level, partnerPortalBlock);
            if (partnerBounds == null) {
                simpleTeleport(level, entity, partnerPos, partnerEntry);
                return;
            }
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

        // -- Step 5: Calculate rotation for cross-axis teleports --
        float yaw = entity.getYRot();
        float pitch = entity.getXRot();

        if (thisAxis != partnerAxis) {
            // Portals are on different axes — rotate 90°.
            //
            // The "through" direction of a portal is its thin axis:
            //   X-axis portal: you walk through along X (East/West)
            //   Z-axis portal: you walk through along Z (North/South)
            //
            // When going from X-axis → Z-axis, the through direction
            // rotates from X to Z, which is a 90° clockwise rotation.
            // When going from Z-axis → X-axis, it's 90° counterclockwise.
            //
            // We rotate the entity's yaw by the same amount so they
            // exit facing through the destination portal naturally.
            //
            // Yaw in Minecraft:
            //   0 = South (+Z), 90 = West (-X), 180 = North (-Z), 270 = East (+X)
            //
            // X-axis portal thin direction is along X (East/West faces)
            // Z-axis portal thin direction is along Z (North/South faces)
            // Going from X→Z thin axis: rotate yaw +90 (clockwise)
            // Going from Z→X thin axis: rotate yaw -90 (counterclockwise)
            if (thisAxis == Direction.Axis.X) {
                // Source is X-axis, destination is Z-axis
                yaw += 90.0F;
            } else {
                // Source is Z-axis, destination is X-axis
                yaw -= 90.0F;
            }

        }

        // Rotate 180° so the entity exits facing AWAY from the destination
        // portal — as if they walked straight through a doorway. This stacks
        // with the cross-axis 90° rotation above:
        //   Same-axis portals: 0° + 180° = 180° (face away from destination)
        //   Cross-axis portals: ±90° + 180° = effective -90°/+90° from original
        yaw += 180.0F;

        // Normalize to -180..180
        while (yaw > 180.0F) yaw -= 360.0F;
        while (yaw <= -180.0F) yaw += 360.0F;

        // Teleport with rotation. Anything moving (an item thrown in, a minecart)
        // has its motion turned by the same amount, so it carries on out of the
        // far portal the way a player walking through would.
        float turn = yaw - entity.getYRot();
        Vec3 motion = entity.getDeltaMovement().yRot((float) -Math.toRadians(turn));
        moveThrough(level, entity, targetX, targetY, targetZ, yaw, pitch, motion);

        Ideomod.LOGGER.debug("{} teleported via Oddstone Portal to ({}, {}, {}), yaw={}",
                entity.getName().getString(), targetX, targetY, targetZ, yaw);
    }

    /**
     * Finds any portal block in the partner portal by checking the expected
     * interior position first (1 block in + 1 block up from frame corner),
     * then falling back to a small scan if the expected position isn't valid.
     *
     * The frame corner is always the bottom-left of the frame, so position
     * (hDir+1, above+1) is the bottom-left of the interior. For any valid
     * portal, this MUST be a portal block. The fallback scan handles edge
     * cases like misaligned SavedData.
     *
     * Previously used a 20x20 brute-force scan (400 block checks).
     * Now checks 1 block in the common case, up to ~25 in the fallback.
     */
    @Nullable
    private BlockPos findAnyPortalBlock(ServerLevel level, BlockPos frameCorner, Direction hDir) {
        // Fast path: interior always starts 1 in and 1 up from the frame corner
        BlockPos expected = frameCorner.relative(hDir, 1).above(1);
        if (level.getBlockState(expected).is(ModBlocks.ODDSTONE_PORTAL.get())) {
            return expected;
        }

        // Fallback: small scan for edge cases (misaligned SavedData, unusual geometry).
        // Oddstone portals have a max interior of ~5x5 (frame limits), so scanning
        // a small area around the expected position covers all realistic cases.
        for (int h = 1; h <= 5; h++) {
            for (int v = 1; v <= 5; v++) {
                // Skip (1,1) — already checked above
                if (h == 1 && v == 1) continue;
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
        final double fixedCoord; // The coordinate on the thin axis (for placing the entity)

        PortalBounds(double minH, double maxH, double minV, double maxV, double fixedCoord) {
            this.minH = minH;
            this.maxH = maxH;
            this.minV = minV;
            this.maxV = maxV;
            this.fixedCoord = fixedCoord;
        }
    }

    /**
     * Finds the world-coordinate bounds of a portal's interior.
     * 
     * Uses cached geometry from the PortalEntry if available (the fast path).
     * Falls back to live PortalGeometry.detect() if no cache exists (old saves
     * or registration without a level reference). The portalBlock parameter
     * is only needed for the fallback path — pass null if you only want cached data.
     * 
     * @param level The world
     * @param portalBlock A known portal block position (for live fallback), or null for cache-only
     * @param hDir The horizontal direction of the portal
     * @param entry The portal entry with potential cached geometry
     * @return PortalBounds, or null if neither cache nor live scan succeeded
     */
    @Nullable
    private PortalBounds findPortalBounds(Level level, @Nullable BlockPos portalBlock,
                                           Direction hDir,
                                           OddstonePortalSavedData.PortalEntry entry) {
        // Fast path: use cached geometry from SavedData
        if (entry.hasGeometry()) {
            BlockPos bottomLeft = entry.getInteriorBottomLeft();
            int width = entry.getInteriorWidth();
            int height = entry.getInteriorHeight();
            Direction.Axis axis = entry.getAxisAsEnum();

            double minV = bottomLeft.getY();
            double maxV = bottomLeft.getY() + height;

            double minH, maxH, fixedCoord;
            if (axis == Direction.Axis.X) {
                minH = bottomLeft.getZ();
                maxH = bottomLeft.getZ() + width;
                fixedCoord = bottomLeft.getX();
            } else {
                minH = bottomLeft.getX();
                maxH = bottomLeft.getX() + width;
                fixedCoord = bottomLeft.getZ();
            }

            return new PortalBounds(minH, maxH, minV, maxV, fixedCoord);
        }

        // Slow path: live scan (for old saves without cached geometry)
        if (portalBlock != null) {
            return findPortalBoundsLive(level, portalBlock);
        }

        return null;
    }

    /**
     * Live fallback: scans the portal frame to determine bounds.
     * Used when cached geometry is not available (old saves, legacy registrations).
     */
    @Nullable
    private PortalBounds findPortalBoundsLive(Level level, BlockPos portalBlock) {
        PortalGeometry geom = PortalGeometry.detect(level, portalBlock);
        if (geom == null) return null;

        Direction.Axis axis = geom.axis();
        BlockPos bottomLeft = geom.interiorBottomLeft();
        int width = geom.interiorWidth();
        int height = geom.interiorHeight();

        double minV = bottomLeft.getY();
        double maxV = bottomLeft.getY() + height;

        double minH, maxH, fixedCoord;
        if (axis == Direction.Axis.X) {
            minH = bottomLeft.getZ();
            maxH = bottomLeft.getZ() + width;
            fixedCoord = bottomLeft.getX();
        } else {
            minH = bottomLeft.getX();
            maxH = bottomLeft.getX() + width;
            fixedCoord = bottomLeft.getZ();
        }

        return new PortalBounds(minH, maxH, minV, maxV, fixedCoord);
    }

    // ========================================================================
    // SCATTER TELEPORT
    // ========================================================================

    private void scatterTeleport(ServerLevel level, Entity entity) {
        BlockPos targetPos = findScatterTarget(level, entity.blockPosition());
        if (targetPos == null) {
            // Every try landed over the void (the End): nothing is thrown out into it.
            Ideomod.LOGGER.info("{} not scatter-teleported: no ground found around {}",
                    entity.getName().getString(), entity.blockPosition());
            return;
        }

        Entity arrived = moveThrough(level, entity, targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5,
                entity.getYRot(), entity.getXRot(), entity.getDeltaMovement());

        // Only living things take the damage: an item or a minecart would be destroyed by it.
        if (arrived instanceof LivingEntity) {
            arrived.hurt(level.damageSources().magic(), IdeomodConfig.getScatterDamage());
        }

        Ideomod.LOGGER.info("{} scatter-teleported by unlinked Oddstone Portal to {}",
                entity.getName().getString(), targetPos);
    }

    /** How many random spots a scatter tries before giving up (only the End's void makes it give up). */
    private static final int SCATTER_TRIES = 8;

    /**
     * Where a scatter lands: a random spot scatter_distance_min to _max blocks away, in a random
     * direction (kept inside the world border), standing on top of whatever is highest there. The
     * spot's chunk is loaded (or generated) first, as a nether portal does for its far side: an
     * unloaded column has no height, which used to put arrivals at the bottom of the world. A column
     * with nothing in it (the End's void) is skipped for another spot; null if every try was void.
     *
     * Under a roof (the Nether) the highest block is the bedrock ceiling, so there the spot is a
     * random place to stand inside the column instead: a solid floor with two blocks of air above
     * it, below the ceiling. Only if no try finds one does the arrival land on the roof.
     */
    @Nullable
    public static BlockPos findScatterTarget(ServerLevel level, BlockPos from) {
        int minDist = IdeomodConfig.getScatterDistanceMin();
        int maxDist = IdeomodConfig.getScatterDistanceMax();
        net.minecraft.world.level.border.WorldBorder border = level.getWorldBorder();
        boolean ceiling = level.dimensionType().hasCeiling();
        BlockPos roof = null;
        for (int attempt = 0; attempt < SCATTER_TRIES; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double distance = minDist + level.getRandom().nextDouble() * (maxDist - minDist);
            BlockPos column = border.clampToBounds(
                    from.getX() + Math.cos(angle) * distance, from.getY(), from.getZ() + Math.sin(angle) * distance);
            level.getChunk(column.getX() >> 4, column.getZ() >> 4);
            // The first free Y above the highest motion-blocking block: standing on it.
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            if (y <= level.getMinBuildHeight()) continue;
            if (!ceiling) return new BlockPos(column.getX(), y, column.getZ());
            BlockPos inside = randomPlaceToStand(level, column.getX(), column.getZ());
            if (inside != null) return inside;
            if (roof == null) roof = new BlockPos(column.getX(), y, column.getZ());
        }
        return roof;
    }

    /** A random spot in the column, below the ceiling, with a solid floor and two blocks of air; null if none. */
    @Nullable
    private static BlockPos randomPlaceToStand(ServerLevel level, int x, int z) {
        int bottom = level.getMinBuildHeight() + 1;
        int top = level.getMinBuildHeight() + level.dimensionType().logicalHeight() - 2;
        java.util.List<Integer> spots = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int y = bottom; y < top; y++) {
            at.set(x, y, z);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) continue;
            BlockPos below = at.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) spots.add(y);
        }
        if (spots.isEmpty()) return null;
        return new BlockPos(x, spots.get(level.getRandom().nextInt(spots.size())), z);
    }

    // ========================================================================
    // SIMPLE FALLBACK TELEPORT
    // ========================================================================

    /**
     * Simple fallback teleport to the first portal block we can find.
     */
    private void simpleTeleport(ServerLevel level, Entity entity,
                                 BlockPos frameCorner,
                                 OddstonePortalSavedData.PortalEntry partnerEntry) {
        Direction.Axis partnerAxis = partnerEntry.getAxisAsEnum();
        Direction hDir = PortalUtils.getHorizontalDir(partnerAxis);

        BlockPos target = findAnyPortalBlock(level, frameCorner, hDir);
        if (target == null) target = frameCorner.above();

        moveThrough(level, entity, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                entity.getYRot(), entity.getXRot(), entity.getDeltaMovement());
    }

    // ========================================================================
    // MOVING THROUGH
    // ========================================================================

    /**
     * Moves an entity to the far side and returns the entity that arrived.
     *
     * A player is teleported. Anything else is put down as a fresh copy and the
     * original removed, the way a nether portal moves things between dimensions.
     * Simply moving it would make clients slide boats and minecarts (which smooth
     * their movement over 10 ticks) the whole way between the two portals.
     * The copy keeps the same UUID and everything saved on it (contents, name,
     * leash, health), and the cooldown, so it does not go straight back.
     */
    private static Entity moveThrough(ServerLevel level, Entity entity, double x, double y, double z,
                                      float yaw, float pitch, Vec3 motion) {
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(level, x, y, z, yaw, pitch);
            return player;
        }
        Entity copy = entity.getType().create(level);
        if (copy == null) {
            entity.teleportTo(level, x, y, z, Set.of(), yaw, pitch);
            entity.setDeltaMovement(motion);
            entity.hurtMarked = true;
            return entity;
        }
        // Like a nether portal, hold the arrival area loaded for a while (TicketType.PORTAL,
        // 15 seconds), so what arrives in an unloaded area is loaded in, ticks and is saved.
        BlockPos arrival = BlockPos.containing(x, y, z);
        level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(arrival), 3, arrival);
        copy.restoreFrom(entity);
        copy.moveTo(x, y, z, yaw, pitch);
        copy.setYHeadRot(yaw);
        copy.setDeltaMovement(motion);
        entity.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        level.addWithUUID(copy);
        return copy;
    }

    // ========================================================================
    // PORTAL ENTRY LOOKUP
    // ========================================================================

    /**
     * Finds the portal registration by navigating from a portal block
     * to the bottom-left interior, then to the frame corner.
     */
    @Nullable
    private OddstonePortalSavedData.PortalEntry findPortalEntry(
            ServerLevel level, BlockPos portalBlockPos, BlockState state,
            OddstonePortalSavedData data) {

        Direction.Axis axis = state.getValue(AXIS);
        Direction hDir = PortalUtils.getHorizontalDir(axis);

        BlockPos bottomLeft = PortalUtils.findBottomLeft(level, portalBlockPos, hDir);
        BlockPos frameCorner = PortalUtils.getFrameCorner(bottomLeft, hDir);
        return data.getPortal(frameCorner);
    }

    // ========================================================================
    // UNREGISTRATION ON REMOVAL
    // ========================================================================

    /**
     * When a portal block is removed, unregister the portal.
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
