package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.item.RedstoneRodSignalManager;
import com.ideomancer.ideomod.mixin.LivingEntityAccessor;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The seat of a Machine Seat: an invisible entity the rider sits on, anchored to the block.
 *
 * Each server tick it
 *  1. follows its block. When a piston or other mover carries the Seat, the block becomes a
 *     moving piston block next to the anchor; the seat moves along with it and takes the new
 *     position as its anchor when the block lands. If the block is gone, the rider gets off
 *     and the seat is removed;
 *  2. reads the rider's keys (the client sends them every tick while riding) and sends pulses:
 *     forward, back, left and right out of the face they point to, relative to the rider's
 *     facing; jump out of the bottom. A key held down repeats every seat.repeat_interval ticks.
 *     No pulse goes out while the block is moving; a due pulse goes out when it lands.
 *
 * The seat is removed as soon as nobody sits on it.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineSeatEntity extends Entity {

    /** Height of the seat above the bottom of the block: the top of its floor. */
    public static final double SEAT_HEIGHT = 0.25;

    private static final String TAG_ANCHOR = "Anchor";

    /** Keys, in order: forward, back, left, right, jump. */
    private static final int KEYS = 5;

    private BlockPos anchor = BlockPos.ZERO;
    @Nullable private BlockPos movingTo = null;
    private final boolean[] held = new boolean[KEYS];
    private final int[] untilRepeat = new int[KEYS];

    public MachineSeatEntity(EntityType<? extends MachineSeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public static MachineSeatEntity create(Level level, BlockPos pos) {
        MachineSeatEntity seat = new MachineSeatEntity(ModEntities.MACHINE_SEAT.get(), level);
        seat.anchor = pos.immutable();
        Vec3 at = seatPos(pos);
        seat.setPos(at.x, at.y, at.z);
        return seat;
    }

    /** The seat belonging to the Machine Seat at pos, if there is one. */
    @Nullable
    public static MachineSeatEntity findAt(Level level, BlockPos pos) {
        List<MachineSeatEntity> seats = level.getEntitiesOfClass(MachineSeatEntity.class, new AABB(pos).inflate(0.5));
        for (MachineSeatEntity seat : seats) {
            if (seat.anchor.equals(pos) && !seat.isRemoved()) return seat;
        }
        return null;
    }

    public BlockPos getAnchor() {
        return this.anchor;
    }

    private static Vec3 seatPos(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + SEAT_HEIGHT, pos.getZ() + 0.5);
    }

    private boolean isSeat(BlockPos pos) {
        return this.level().getBlockState(pos).is(ModBlocks.MACHINE_SEAT.get());
    }

    @Nullable
    private PistonMovingBlockEntity movingSeatAt(BlockPos pos) {
        BlockEntity be = this.level().getBlockEntity(pos);
        if (be instanceof PistonMovingBlockEntity moving && moving.getMovedState().is(ModBlocks.MACHINE_SEAT.get())) {
            return moving;
        }
        return null;
    }

    // ========================================================================
    // TICK
    // ========================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        if (!this.isVehicle()) {
            this.discard();
            return;
        }
        if (!followBlock()) {
            this.ejectPassengers();
            this.discard();
            return;
        }
        readKeys();
    }

    /** Keeps the seat on its block; false if the block is gone. */
    private boolean followBlock() {
        if (this.movingTo == null && isSeat(this.anchor)) {
            placeAt(seatPos(this.anchor));
            return true;
        }
        if (this.movingTo != null) {
            if (isSeat(this.movingTo)) {
                this.anchor = this.movingTo;
                this.movingTo = null;
                placeAt(seatPos(this.anchor));
                return true;
            }
            PistonMovingBlockEntity moving = movingSeatAt(this.movingTo);
            if (moving == null) return false;
            placeAt(seatPos(this.movingTo).add(moving.getXOff(1.0f), moving.getYOff(1.0f), moving.getZOff(1.0f)));
            return true;
        }
        // The block has left the anchor: find it moving into a neighbouring position.
        for (Direction dir : Direction.values()) {
            BlockPos next = this.anchor.relative(dir);
            PistonMovingBlockEntity moving = movingSeatAt(next);
            if (moving != null && moving.getMovementDirection() == dir) {
                this.movingTo = next.immutable();
                placeAt(seatPos(next).add(moving.getXOff(1.0f), moving.getYOff(1.0f), moving.getZOff(1.0f)));
                return true;
            }
        }
        return false;
    }

    private void placeAt(Vec3 at) {
        if (!at.equals(this.position())) this.setPos(at.x, at.y, at.z);
    }

    private void readKeys() {
        if (!(this.getFirstPassenger() instanceof LivingEntity rider)) return;
        Direction facing = rider.getDirection();
        boolean[] pressed = {
                rider.zza > 0.1f,
                rider.zza < -0.1f,
                rider.xxa > 0.1f,
                rider.xxa < -0.1f,
                ((LivingEntityAccessor) rider).ideomod$isJumping()
        };
        Direction[] faces = {
                facing,
                facing.getOpposite(),
                facing.getCounterClockWise(),
                facing.getClockWise(),
                Direction.DOWN
        };
        boolean landed = this.movingTo == null;
        int interval = IdeomodConfig.getSeatRepeatInterval();
        for (int key = 0; key < KEYS; key++) {
            if (!pressed[key]) {
                this.held[key] = false;
                this.untilRepeat[key] = 0;
                continue;
            }
            if (this.held[key] && this.untilRepeat[key] > 0) this.untilRepeat[key]--;
            boolean due = !this.held[key] || this.untilRepeat[key] <= 0;
            this.held[key] = true;
            if (due && landed) {
                pulse(faces[key]);
                this.untilRepeat[key] = interval;
            }
        }
    }

    /** A pulse out of one face: the neighbour on that face sees the Seat give a 15 signal. */
    private void pulse(Direction face) {
        RedstoneRodSignalManager.injectPulse(this.level(), this.anchor.relative(face), face.getOpposite());
    }

    // ========================================================================
    // RIDING
    // ========================================================================

    @Override
    public double getPassengersRidingOffset() {
        return 0.0;
    }

    // An empty seat is removed on its next tick, not as the rider leaves: vanilla asks the
    // seat where to put the rider after removing them, and asks a removed seat nothing
    // (the rider would be left standing inside the block).

    /**
     * Getting off steps out beside the Seat, as out of a boat: forward first (the way the
     * rider looks), then left, right and back. Not on top: the open top is wider than a
     * player, who would drop straight back in. With every side blocked, the rider stands
     * up inside the Seat.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos seat = this.movingTo != null ? this.movingTo : this.anchor;
        Direction facing = passenger.getDirection();
        for (Direction side : new Direction[]{facing, facing.getCounterClockWise(), facing.getClockWise(), facing.getOpposite()}) {
            for (BlockPos at : new BlockPos[]{seat.relative(side), seat.relative(side).below()}) {
                Vec3 spot = net.minecraft.world.entity.vehicle.DismountHelper.findSafeDismountLocation(
                        passenger.getType(), this.level(), at, true);
                if (spot != null) return spot;
            }
        }
        return Vec3.atBottomCenterOf(seat).add(0.0, SEAT_HEIGHT, 0.0);
    }

    // ========================================================================
    // AN INVISIBLE, UNTOUCHABLE ENTITY
    // ========================================================================

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.anchor = BlockPos.of(tag.getLong(TAG_ANCHOR));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong(TAG_ANCHOR, this.anchor.asLong());
    }
}
