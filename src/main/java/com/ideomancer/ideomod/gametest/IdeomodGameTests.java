package com.ideomancer.ideomod.gametest;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity;
import com.ideomancer.ideomod.block.machine.MachinePulserBlock;
import com.ideomancer.ideomod.registry.ModBlocks;
import com.ideomancer.ideomod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * In-world regression tests for the Known Issues list, run headless by
 * `./gradlew runGameTestServer` (the server places the blocks, ticks the
 * world and reports pass/fail per test). Each test builds its own setup in
 * an empty 7x5x7 template (data/ideomod/structures/empty.nbt).
 *
 * Only active when the gametest namespace "ideomod" is enabled (the
 * gameTestServer run config does this); a normal game never runs them.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
@GameTestHolder(Ideomod.MOD_ID)
@PrefixGameTestTemplate(false)
public class IdeomodGameTests {

    private static final String EMPTY = "empty";
    private static final boolean TRACE = Boolean.getBoolean("ideomod.gametest.trace");

    // ==================== Known Issue #3 ====================

    /**
     * A Conductor strongly powers the solid block it outputs into (so the block
     * can drive a lamp or piston), but redstone dust must not pick that power up
     * through the block — vanilla dust doesn't do that for other dust, and the
     * Conductor is wire.
     *
     *   [redstone block] [Conductor] [stone] [dust]      lamp beside the stone,
     *                       dust touching the Conductor directly (must still light)
     */
    @GameTest(template = EMPTY)
    public static void conductorDoesNotPowerDustThroughBlock(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        BlockPos stone = new BlockPos(3, 1, 1);
        BlockPos dust = new BlockPos(4, 1, 1);
        BlockPos lamp = new BlockPos(3, 1, 2);
        BlockPos touchingDust = new BlockPos(2, 1, 0);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(touchingDust, Blocks.REDSTONE_WIRE);
        helper.setBlock(dust, Blocks.REDSTONE_WIRE);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        helper.setBlock(2, 1, 1, ModBlocks.MACHINE_CONDUCTOR.get());
        helper.setBlock(1, 1, 1, Blocks.REDSTONE_BLOCK); // last, so the Conductor sees it

        helper.runAfterDelay(6, () -> {
            if (!helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT)) {
                helper.fail("Conductor should still power a lamp through the block", lamp);
                return;
            }
            if (helper.getBlockState(touchingDust).getValue(RedStoneWireBlock.POWER) == 0) {
                helper.fail("Dust touching the Conductor should be powered", touchingDust);
                return;
            }
            int power = helper.getBlockState(dust).getValue(RedStoneWireBlock.POWER);
            if (power != 0) {
                helper.fail("Dust picked up the Conductor's power through a solid block (power " + power + ")", dust);
                return;
            }
            helper.succeed();
        });
    }

    // ==================== Known Issue #4 ====================

    /**
     * A Framed Redstone Block powers its neighbours as soon as it is placed
     * (through the real item placement path), and keeps powering them when a
     * Machine Family block activates its frame.
     *
     *   [lamp] [framed redstone] ... then a Machine Brace on the far side
     */
    @GameTest(template = EMPTY)
    public static void framedRedstonePowersOnPlaceAndActivation(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        BlockPos lamp = new BlockPos(2, 1, 1);
        BlockPos framed = new BlockPos(3, 1, 1);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);

        ItemStack stack = new ItemStack(ModItems.IRON_FRAMED_BLOCK.get());
        FramedBlockEntity.setInnerBlockIdOnStack(stack, new ResourceLocation("minecraft", "redstone_block"));
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack); // placeAt places what the player holds
        helper.placeAt(player, stack, framed.below(), Direction.UP);

        helper.runAfterDelay(3, () -> {
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(framed)) instanceof FramedBlockEntity fbe)
                    || !fbe.hasInnerBlock()) {
                helper.fail("Framed block was not placed with its inner block", framed);
                return;
            }
            if (!helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT)) {
                helper.fail("Lamp not lit right after placing a Framed Redstone Block", lamp);
                return;
            }
            helper.setBlock(4, 1, 1, ModBlocks.MACHINE_BRACE.get()); // activates the frame
        });

        helper.runAfterDelay(8, () -> {
            if (!helper.getBlockState(framed).getValue(FramedBlock.ACTIVE)) {
                helper.fail("Frame did not activate next to a Machine Brace", framed);
                return;
            }
            if (!helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT)) {
                helper.fail("Lamp went dark after the frame activated", lamp);
                return;
            }
            helper.succeed();
        });
    }

    // ==================== Known Issue #6 ====================

    /** The ring of Pulsers, in signal order, around a 3x3 square at y=1 (z 2..4). */
    private static final int[][] RING = {
            {1, 2}, {2, 2}, {3, 2}, {3, 3}, {3, 4}, {2, 4}, {1, 4}, {1, 3}
    };

    /**
     * A pulse circulating a ring of 8 Pulsers keeps stepping one block at a time
     * when a piston shoves the whole ring sideways mid-loop: no block fires out
     * of turn ("jump"), and the pulse doesn't die.
     *
     * The piston pushes a glass block into the ring (glass doesn't carry
     * redstone, so the Pulsers can't fire the piston themselves); the Pulsers
     * stick to each other, so the whole ring moves +z by one.
     */
    // The piston fires at four consecutive ticks, so the move lands at every
    // point of a Pulser's 2-tick pulse and the 2-tick hand-off between Pulsers.
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pulseRingMovedByPistonAtTick8(GameTestHelper helper) { pulseRingMovedByPiston(helper, 8); }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pulseRingMovedByPistonAtTick9(GameTestHelper helper) { pulseRingMovedByPiston(helper, 9); }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pulseRingMovedByPistonAtTick10(GameTestHelper helper) { pulseRingMovedByPiston(helper, 10); }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pulseRingMovedByPistonAtTick11(GameTestHelper helper) { pulseRingMovedByPiston(helper, 11); }

    private static void pulseRingMovedByPiston(GameTestHelper helper, int pistonTick) {
        BlockState pulser = ModBlocks.MACHINE_PULSER.get().defaultBlockState();
        for (int[] p : RING) {
            helper.setBlock(p[0], 1, p[1], pulser);
        }
        helper.setBlock(2, 1, 1, Blocks.GLASS);
        helper.setBlock(2, 1, 0, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.SOUTH));

        // Seed a one-way pulse: Pulser 0 on, as if it was fired by Pulser 7.
        BlockPos first = helper.absolutePos(new BlockPos(RING[0][0], 1, RING[0][1]));
        BlockPos last = helper.absolutePos(new BlockPos(RING[7][0], 1, RING[7][1]));
        if (helper.getLevel().getBlockEntity(first) instanceof MachineFrameBlockEntity be) {
            be.setInputFace(directionTo(first, last));
            be.setInputWasLow(false);
            be.setPulserTicksRemaining(2);
        }
        helper.getLevel().setBlock(first, pulser.setValue(MachinePulserBlock.POWERED, true), 3);
        for (Direction d : Direction.values()) {
            helper.getLevel().updateNeighborsAt(first.relative(d), ModBlocks.MACHINE_PULSER.get());
        }
        helper.getLevel().scheduleTick(first, ModBlocks.MACHINE_PULSER.get(), 1);

        List<Integer> firings = new ArrayList<>();   // ring index, each time a Pulser turns on
        boolean[] wasOn = new boolean[RING.length];
        wasOn[0] = true;
        firings.add(0);
        int[] maxOn = {0};

        helper.onEachTick(() -> {
            // Where is the ring? The glass shows it: before the push, or after.
            // While the blocks are in transit there is nothing to read.
            int dz;
            if (helper.getBlockState(new BlockPos(2, 1, 1)).is(Blocks.GLASS)) dz = 0;
            else if (helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.GLASS)) dz = 1;
            else { if (TRACE) Ideomod.LOGGER.info("[TRACE p{} t={}] in transit", pistonTick, helper.getTick()); return; }
            int on = 0;
            for (int i = 0; i < RING.length; i++) {
                boolean now = isPoweredPulser(helper, i, dz);
                if (now) on++;
                if (now && !wasOn[i]) firings.add(i);
                if (TRACE) {
                    BlockPos tp = helper.absolutePos(new BlockPos(RING[i][0], 1, RING[i][1] + dz));
                    if (helper.getLevel().getBlockEntity(tp) instanceof MachineFrameBlockEntity tbe) {
                        Ideomod.LOGGER.info("[TRACE p{} t={} dz={}] #{} on={} in={} low={} left={}", pistonTick, helper.getTick(), dz, i, now,
                                tbe.getInputFace(), tbe.isInputWasLow(), tbe.getPulserTicksRemaining());
                    }
                }
                wasOn[i] = now;
            }
            maxOn[0] = Math.max(maxOn[0], on);
        });

        helper.runAfterDelay(pistonTick, () -> helper.setBlock(1, 1, 0, Blocks.REDSTONE_BLOCK)); // fire the piston

        helper.runAfterDelay(80, () -> {
            boolean moved = helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.GLASS);
            if (!moved) {
                helper.fail("The ring did not move: " + firings);
                return;
            }
            for (int k = 1; k < firings.size(); k++) {
                int expected = (firings.get(k - 1) + 1) % RING.length;
                if (firings.get(k) != expected) {
                    helper.fail("Pulse jumped: fired " + firings.get(k) + " after " + firings.get(k - 1)
                            + " (expected " + expected + "). Order: " + firings);
                    return;
                }
            }
            if (firings.size() < RING.length + 4) {
                helper.fail("Pulse died after the move. Order: " + firings);
                return;
            }
            if (maxOn[0] > 2) {
                helper.fail("Up to " + maxOn[0] + " Pulsers on at once. Order: " + firings);
                return;
            }
            Ideomod.LOGGER.info("[GameTest] pulse ring (piston at t={}) order: {}", pistonTick, firings);
            helper.succeed();
        });
    }

    private static boolean isPoweredPulser(GameTestHelper helper, int ringIndex, int dz) {
        BlockState s = helper.getBlockState(new BlockPos(RING[ringIndex][0], 1, RING[ringIndex][1] + dz));
        return s.is(ModBlocks.MACHINE_PULSER.get()) && s.getValue(MachinePulserBlock.POWERED);
    }

    private static Direction directionTo(BlockPos from, BlockPos to) {
        return Direction.fromDelta(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }
}
