package com.ideomancer.ideomod.block.machine;

import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.frame.FrameActivation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.ticks.TickPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Machine Grabber — toggleable sticky block.
 *
 * Two variants: SLIME and HONEY. The specialization item is the
 * corresponding vanilla sticky block (Slime Block or Honey Block).
 *
 * When POWERED=false: normal Machine Family stickiness only (sticks
 * to same type + FRAME + active frames).
 *
 * When POWERED=true: additionally behaves like the corresponding
 * sticky block. Slime grabber grabs everything a slime block would
 * (except honey and odd slime — mutual exclusion). Honey grabber
 * grabs everything a honey block would (except slime and odd slime).
 *
 * Redstone behavior: edge-triggered toggle. A rising edge (signal
 * appears after being absent) flips the POWERED state. Any face
 * can receive the toggle signal. Uses the shared inputWasLow flag
 * from MachineFrameBlockEntity for edge detection (same pattern as
 * MachineToggleBlock). No redstone output, so no anti-feedback
 * architecture is needed.
 *
 * Visual: transparent center in the outer texture reveals the BESR-
 * rendered inner slime/honey block. Two textures: machine_grabber
 * (off) and machine_grabber_on (on), both with transparent centers.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class MachineGrabberBlock extends AbstractMachineBlock {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineGrabberBlock.class);

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    /**
     * Identifies which sticky type this grabber emulates when powered.
     */
    public enum GrabberType {
        SLIME, HONEY
    }

    private final GrabberType grabberType;

    public MachineGrabberBlock(GrabberType type, Properties properties) {
        super(properties);
        this.grabberType = type;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public MachineType getMachineType() {
        return grabberType == GrabberType.SLIME
                ? MachineType.SLIME_GRABBER
                : MachineType.HONEY_GRABBER;
    }

    public GrabberType getGrabberType() {
        return grabberType;
    }

    @Override
    public int getRedstoneOutput(BlockState state) {
        return 0; // No redstone output
    }

    // ==================== Stickiness ====================

    /**
     * When powered, the grabber acts like a slime/honey block in addition
     * to its Machine Family stickiness. The canStickTo is PERMISSIVE
     * (Layer 1) — grab immunity rules handle the restrictions.
     *
     * A carve-out in Ideomod.registerStickinessRules() Rule 2 exempts
     * powered grabbers from "Machine Family can't grab sticky blocks"
     * when the target matches the grabber's type.
     */
    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        // Always maintain machine family stickiness
        if (IMachineFamily.canMachineFamilyStickTo(getMachineType(), other)) return true;

        // When powered, act like the corresponding sticky block
        if (state.getValue(POWERED)) {
            Block otherBlock = other.getBlock();
            if (grabberType == GrabberType.SLIME) {
                // Slime mutual exclusion: no honey, no odd slime
                if (otherBlock instanceof HoneyBlock) return false;
                if (otherBlock instanceof OddSlimeBlock) return false;
                // Stick to everything else (slime, normal blocks, etc.)
                return true;
            } else {
                // Honey mutual exclusion: no slime, no odd slime
                if (otherBlock instanceof SlimeBlock) return false;
                if (otherBlock instanceof OddSlimeBlock) return false;
                return true;
            }
        }

        return false;
    }

    // ==================== Redstone Input ====================

    /**
     * Edge-triggered toggle: detects rising edge (signal appears after
     * being absent) and flips POWERED. Uses the inputWasLow flag from
     * MachineFrameBlockEntity for edge detection.
     *
     * No output means no feedback loop — no anti-feedback needed.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block sourceBlock, BlockPos sourcePos, boolean movedByPiston) {
        if (level.isClientSide()) return;

        if (level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be) {
            // Suppress edge detection during the 1-tick piston settlement
            // window. All blocks in a push group are placed sequentially,
            // so some neighbors may be missing temporarily. Without this
            // guard, the grabber sees a spurious signal drop (transmitter
            // not placed yet) followed by a rise (transmitter placed),
            // which falsely triggers a toggle.
            if (be.isPistonSettling()) return;

            boolean hasSignal = level.hasNeighborSignal(pos);

            if (hasSignal && be.isInputWasLow()) {
                // Rising edge detected — toggle POWERED state
                be.setInputWasLow(false);
                boolean newPowered = !state.getValue(POWERED);
                level.setBlock(pos, state.setValue(POWERED, newPowered), 3);
                LOGGER.debug("[GRABBER] {} at {} toggle -> POWERED={}", grabberType, pos, newPowered);
            } else if (!hasSignal && !be.isInputWasLow()) {
                // Signal dropped — arm for next rising edge
                be.setInputWasLow(true);
            }
        }

        FrameActivation.markDirty(level, pos);
    }

    // ==================== Tick (piston settlement) ====================

    /**
     * Called 1 tick after piston placement to end the settlement window.
     * Clears the pistonSettling flag and re-snapshots the signal state
     * so the edge detector is correctly armed for future changes.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be) {
            if (be.isPistonSettling()) {
                be.setPistonSettling(false);
                // Re-snapshot signal now that all blocks have settled
                boolean hasSignal = level.hasNeighborSignal(pos);
                be.setInputWasLow(!hasSignal);
                LOGGER.debug("[GRABBER] {} at {} piston settled, signal={}, inputWasLow={}",
                        grabberType, pos, hasSignal, !hasSignal);
            }
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                         BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            if (movedByPiston) {
                // Piston placement: suppress edge detection for 1 tick while
                // the rest of the push group settles. The BE's inputWasLow is
                // already restored from movement data — don't overwrite it.
                if (level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be) {
                    be.setPistonSettling(true);
                }
                level.scheduleTick(pos, this, 1, TickPriority.HIGH);
            } else {
                // Normal placement: snapshot current signal — don't toggle.
                // If placed into active signal, mark inputWasLow=false so
                // the signal must drop and rise before the first toggle fires.
                boolean hasSignal = level.hasNeighborSignal(pos);
                if (level.getBlockEntity(pos) instanceof MachineFrameBlockEntity be) {
                    be.setInputWasLow(!hasSignal);
                }
            }
            FrameActivation.markDirty(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && !movedByPiston) {
                FrameActivation.markDirty(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                             @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity placer,
                             ItemStack stack) {
        // No-op — grabber doesn't need placement specialization swap logic.
    }
}
