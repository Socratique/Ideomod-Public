package com.ideomancer.ideomod.gametest;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.block.HollowstoneBlock;
import com.ideomancer.ideomod.block.piston.AbstractRangedPistonBlock;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity;
import com.ideomancer.ideomod.block.machine.MachineFluidPipeBlockEntity;
import com.ideomancer.ideomod.block.machine.MachineFluidPipeBlock;
import com.ideomancer.ideomod.block.machine.FluidType;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.ideomancer.ideomod.block.machine.MachineRedstoneHelper;
import com.ideomancer.ideomod.block.frame.FrameBlock;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RepeaterBlock;
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

    // ==================== Frames don't carry redstone ====================

    /**
     * Iron and Copper Frames are see-through, like glass: a repeater powering
     * into one must not power the lamp behind it. Control lane: the same with
     * stone, which must light its lamp (proves the repeater points the right way).
     *
     *   [lamp] [frame / stone] [repeater, pointing west] [redstone block]
     */
    @GameTest(template = EMPTY)
    public static void framesDoNotCarryRedstone(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 5; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        BlockState repeater = Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.EAST);
        BlockState[] middles = {
                ModBlocks.IRON_FRAME.get().defaultBlockState(),
                ModBlocks.COPPER_FRAME.get().defaultBlockState(),
                Blocks.STONE.defaultBlockState()};
        for (int lane = 0; lane < 3; lane++) {
            int z = 1 + lane * 2;
            helper.setBlock(1, 1, z, Blocks.REDSTONE_LAMP);
            helper.setBlock(2, 1, z, middles[lane]);
            helper.setBlock(3, 1, z, repeater);
            helper.setBlock(4, 1, z, Blocks.REDSTONE_BLOCK);
        }
        helper.runAfterDelay(6, () -> {
            if (!helper.getBlockState(new BlockPos(1, 1, 5)).getValue(RedstoneLampBlock.LIT)) {
                helper.fail("Control: stone should carry the repeater's power to the lamp", new BlockPos(1, 1, 5));
                return;
            }
            for (int lane = 0; lane < 2; lane++) {
                BlockPos lamp = new BlockPos(1, 1, 1 + lane * 2);
                if (helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT)) {
                    helper.fail((lane == 0 ? "Iron" : "Copper") + " Frame carried redstone to the lamp", lamp);
                    return;
                }
            }
            helper.succeed();
        });
    }

    // ==================== Hollowstone fill ====================

    /**
     * Right-clicking empty Hollowstone with a block puts it inside, but only a
     * full cube: a button (or lever, torch, slab...) must be refused.
     */
    @GameTest(template = EMPTY)
    public static void hollowstoneTakesOnlyFullBlocks(GameTestHelper helper) {
        BlockPos refuses = new BlockPos(1, 1, 1);
        BlockPos takes = new BlockPos(3, 1, 1);
        helper.setBlock(refuses, ModBlocks.HOLLOW_STONE.get());
        helper.setBlock(takes, ModBlocks.HOLLOW_STONE.get());
        Player player = helper.makeMockPlayer();

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.STONE_BUTTON));
        helper.useBlock(refuses, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.GLOWSTONE));
        helper.useBlock(takes, player);

        helper.runAfterDelay(2, () -> {
            if (helper.getBlockState(refuses).getValue(HollowstoneBlock.FILLED)) {
                helper.fail("Hollowstone took a stone button", refuses);
                return;
            }
            if (!helper.getBlockState(takes).getValue(HollowstoneBlock.FILLED)) {
                helper.fail("Hollowstone refused glowstone", takes);
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

    // ==================== Machine Base look ====================

    /**
     * The Machine Base shows POWERED (the Ideomancer's "inactive" texture) while it has
     * redstone power and goes back when the power is removed. Display only:
     * FrameActivation reads the redstone level itself.
     */
    @GameTest(template = EMPTY)
    public static void machineFrameShowsPower(GameTestHelper helper) {
        BlockPos frame = new BlockPos(2, 1, 2);
        BlockPos power = new BlockPos(3, 1, 2);
        helper.setBlock(frame, ModBlocks.MACHINE_BLOCK.get());
        helper.runAfterDelay(2, () -> {
            if (helper.getBlockState(frame).getValue(MachineFrameBlock.POWERED)) {
                helper.fail("Machine Base shows powered with no power", frame);
                return;
            }
            helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(4, () -> {
            if (!helper.getBlockState(frame).getValue(MachineFrameBlock.POWERED)) {
                helper.fail("Machine Base did not show powered next to a redstone block", frame);
                return;
            }
            helper.setBlock(power, Blocks.AIR);
        });
        helper.runAfterDelay(6, () -> {
            if (helper.getBlockState(frame).getValue(MachineFrameBlock.POWERED)) helper.fail("Machine Base stayed powered after the power was removed", frame);
            else helper.succeed();
        });
    }

    // ==================== Frame metals (fixed 2026-10-25) ====================

    /**
     * A copper frame touching both an iron frame and a copper path from the same
     * Machine Base must still activate. Before the fix the iron neighbour got to
     * it first (the UP seed is processed before SOUTH), marked its spot as reached
     * with an iron-only signal, and the real copper signal was then skipped.
     *
     *   y=2:  [iron I] [copper C]       M = Machine Base (2,1,2), I above it,
     *   y=1:  [M]      [copper K]       K south of M, C above K and south of I.
     */
    @GameTest(template = EMPTY)
    public static void copperFrameBesideIronStillActivates(GameTestHelper helper) {
        BlockPos m = new BlockPos(2, 1, 2), iron = new BlockPos(2, 2, 2);
        BlockPos k = new BlockPos(2, 1, 3), c = new BlockPos(2, 2, 3);
        helper.setBlock(iron, ModBlocks.IRON_FRAME.get());
        helper.setBlock(k, ModBlocks.COPPER_FRAME.get());
        helper.setBlock(c, ModBlocks.COPPER_FRAME.get());
        helper.setBlock(m, ModBlocks.MACHINE_BLOCK.get());
        helper.runAfterDelay(6, () -> {
            if (!helper.getBlockState(k).getValue(FrameBlock.ACTIVE)) { helper.fail("copper frame next to the Machine Base is off", k); return; }
            if (!helper.getBlockState(iron).getValue(FrameBlock.ACTIVE)) { helper.fail("iron frame next to the Machine Base is off", iron); return; }
            if (!helper.getBlockState(c).getValue(FrameBlock.ACTIVE)) { helper.fail("copper frame reachable by copper stayed off beside an iron frame", c); return; }
            helper.succeed();
        });
    }

    // ==================== Transmitter strength (fixed 2026-10-25) ====================

    /**
     * Machine redstone blocks read a Transmitter receiver at its real strength
     * (source 15 -> receiver 14), not the rough "1" of getRedstoneOutput().
     */
    @GameTest(template = EMPTY)
    public static void machinesReadTransmitterStrength(GameTestHelper helper) {
        BlockPos power = new BlockPos(0, 1, 2), source = new BlockPos(1, 1, 2), receiver = new BlockPos(4, 1, 2);
        for (BlockPos p : new BlockPos[]{source, receiver}) {
            helper.setBlock(p, ModBlocks.MACHINE_TRANSMITTER.get());
            if (helper.getBlockEntity(p) instanceof MachineFrameBlockEntity be) be.setItemDirect(new ItemStack(ModBlocks.ODDSTONE.get()));
        }
        helper.runAfterDelay(2, () -> helper.setBlock(power, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(10, () -> {
            BlockPos reader = helper.absolutePos(receiver.east());
            int seen = MachineRedstoneHelper.getIncomingSignalFromFace(helper.getLevel(), reader, Direction.WEST, new ThreadLocal<>());
            if (seen != 14) helper.fail("a machine next to the receiver reads " + seen + ", expected 14", receiver);
            else helper.succeed();
        });
    }

    // ==================== Machine Drill (the Ideomancer, 2026-11-06) ====================

    /** Puts a Machine Base at pos with the given pickaxe in its slot; it should become a Machine Drill. */
    private static MachineFrameBlockEntity placeDrill(GameTestHelper helper, BlockPos pos, ItemStack pickaxe) {
        helper.setBlock(pos, ModBlocks.MACHINE_BLOCK.get());
        if (helper.getBlockEntity(pos) instanceof MachineFrameBlockEntity base) base.setItem(0, pickaxe);
        return helper.getBlockEntity(pos) instanceof MachineFrameBlockEntity be ? be : null;
    }

    /** Any pickaxe in a Machine Base's slot makes it a Machine Drill. */
    @GameTest(template = EMPTY)
    public static void pickaxeMakesAMachineDrill(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        placeDrill(helper, pos, new ItemStack(Items.STONE_PICKAXE));
        if (helper.getBlockState(pos).is(ModBlocks.MACHINE_DRILL.get())) helper.succeed();
        else helper.fail("a Machine Base with a pickaxe is " + helper.getBlockState(pos), pos);
    }

    /**
     * A signal on the west face mines the 3x3 on the east face, one point of wear per block cut.
     * An iron pickaxe cannot cut the obsidian in the middle, so it stays and costs nothing.
     */
    @GameTest(template = EMPTY)
    public static void machineDrillMinesOppositeTheSignal(GameTestHelper helper) {
        BlockPos drill = new BlockPos(2, 2, 3);
        for (int y = 1; y <= 3; y++) for (int z = 2; z <= 4; z++) helper.setBlock(3, y, z, Blocks.STONE);
        helper.setBlock(3, 2, 3, Blocks.OBSIDIAN);
        helper.setBlock(1, 2, 2, Blocks.STONE); // a block on the signal side, which must not be touched
        placeDrill(helper, drill, new ItemStack(Items.IRON_PICKAXE));
        helper.runAfterDelay(2, () -> helper.setBlock(1, 2, 3, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(20, () -> {
            for (int y = 1; y <= 3; y++) for (int z = 2; z <= 4; z++) {
                BlockPos p = new BlockPos(3, y, z);
                boolean middle = y == 2 && z == 3;
                if (middle && !helper.getBlockState(p).is(Blocks.OBSIDIAN)) { helper.fail("an iron pickaxe cut obsidian", p); return; }
                if (!middle && !helper.getBlockState(p).isAir()) { helper.fail("this block was not mined", p); return; }
            }
            if (!helper.getBlockState(new BlockPos(1, 2, 2)).is(Blocks.STONE)) { helper.fail("the Drill mined the signal side", new BlockPos(1, 2, 2)); return; }
            if (!(helper.getBlockEntity(drill) instanceof MachineFrameBlockEntity be)) { helper.fail("the Drill is gone", drill); return; }
            int wear = be.getSpecializationItem().getDamageValue();
            if (wear != 8) { helper.fail("pickaxe wear should be 8 (8 blocks cut), is " + wear, drill); return; }
            helper.succeed();
        });
    }

    /** A cut takes as long as mining by hand: stone with an iron pickaxe is 8 ticks, so it is still there 3 ticks in. */
    @GameTest(template = EMPTY)
    public static void machineDrillTakesMiningTime(GameTestHelper helper) {
        BlockPos drill = new BlockPos(2, 2, 3);
        BlockPos stone = new BlockPos(3, 2, 3);
        helper.setBlock(stone, Blocks.STONE);
        placeDrill(helper, drill, new ItemStack(Items.IRON_PICKAXE));
        helper.runAfterDelay(2, () -> helper.setBlock(1, 2, 3, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(5, () -> {
            if (!helper.getBlockState(stone).is(Blocks.STONE)) helper.fail("the stone broke at once; a cut should take time", stone);
        });
        helper.runAfterDelay(16, () -> {
            if (helper.getBlockState(stone).isAir()) helper.succeed();
            else helper.fail("the stone was not mined in time", stone);
        });
    }

    /** It needs a constant signal: taking the signal away part-way stops the cut, and the block stays. */
    @GameTest(template = EMPTY)
    public static void machineDrillStopsWhenTheSignalGoes(GameTestHelper helper) {
        BlockPos stone = new BlockPos(3, 2, 3);
        helper.setBlock(stone, Blocks.STONE);
        placeDrill(helper, new BlockPos(2, 2, 3), new ItemStack(Items.WOODEN_PICKAXE)); // stone: 23 ticks
        helper.runAfterDelay(2, () -> helper.setBlock(1, 2, 3, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(10, () -> helper.setBlock(1, 2, 3, Blocks.AIR));
        helper.runAfterDelay(40, () -> {
            if (helper.getBlockState(stone).is(Blocks.STONE)) helper.succeed();
            else helper.fail("the cut went on after the signal stopped", stone);
        });
    }

    /** While the signal lasts it keeps cutting: a block put back in front is cut too. */
    @GameTest(template = EMPTY)
    public static void machineDrillKeepsCuttingWhilePowered(GameTestHelper helper) {
        BlockPos stone = new BlockPos(3, 2, 3);
        helper.setBlock(stone, Blocks.STONE);
        placeDrill(helper, new BlockPos(2, 2, 3), new ItemStack(Items.IRON_PICKAXE)); // stone: 8 ticks
        helper.runAfterDelay(2, () -> helper.setBlock(1, 2, 3, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(16, () -> {
            if (!helper.getBlockState(stone).isAir()) helper.fail("the first stone was not cut", stone);
            helper.setBlock(stone, Blocks.STONE);
        });
        helper.runAfterDelay(30, () -> {
            if (helper.getBlockState(stone).isAir()) helper.succeed();
            else helper.fail("the stone put back was not cut while the signal stayed on", stone);
        });
    }

    /** While it is cutting, a piston cannot move the Drill (obsidian with a diamond pickaxe takes 188 ticks). */
    @GameTest(template = EMPTY)
    public static void drillingDrillCannotBePushed(GameTestHelper helper) {
        BlockPos drill = new BlockPos(2, 2, 3);
        helper.setBlock(3, 2, 3, Blocks.OBSIDIAN);
        placeDrill(helper, drill, new ItemStack(Items.DIAMOND_PICKAXE));
        helper.runAfterDelay(2, () -> helper.setBlock(1, 2, 3, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(4, () -> helper.setBlock(2, 1, 3, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP)));
        helper.runAfterDelay(5, () -> helper.setBlock(2, 0, 3, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(12, () -> {
            if (!helper.getBlockState(drill).is(ModBlocks.MACHINE_DRILL.get())) { helper.fail("the Drill was moved while cutting", drill); return; }
            if (!helper.getBlockState(new BlockPos(3, 2, 3)).is(Blocks.OBSIDIAN)) { helper.fail("the obsidian broke far too soon", new BlockPos(3, 2, 3)); return; }
            helper.succeed();
        });
    }

    /** When the pickaxe breaks, the slot empties and the Drill is a Machine Base again. */
    @GameTest(template = EMPTY)
    public static void brokenPickaxeTurnsTheDrillBackIntoABase(GameTestHelper helper) {
        BlockPos drill = new BlockPos(2, 2, 3);
        helper.setBlock(3, 2, 3, Blocks.STONE);
        ItemStack worn = new ItemStack(Items.WOODEN_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - 1); // one use left
        placeDrill(helper, drill, worn);
        helper.runAfterDelay(2, () -> helper.setBlock(1, 2, 3, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(40, () -> {
            if (!helper.getBlockState(new BlockPos(3, 2, 3)).isAir()) { helper.fail("the last use did not mine", new BlockPos(3, 2, 3)); return; }
            if (helper.getBlockState(drill).is(ModBlocks.MACHINE_BLOCK.get())
                    && helper.getBlockEntity(drill) instanceof MachineFrameBlockEntity be && be.getSpecializationItem().isEmpty()) {
                helper.succeed();
            } else {
                helper.fail("after the pickaxe broke the block is " + helper.getBlockState(drill), drill);
            }
        });
    }

    // ==================== Oddstone walls, stairs and slabs (the Ideomancer, 2026-11-05) ====================

    private static Block[] oddstoneShapes() {
        return new Block[] {
                ModBlocks.ODDSTONE_WALL.get(), ModBlocks.ODDSTONE_STAIRS.get(), ModBlocks.ODDSTONE_SLAB.get(),
                ModBlocks.COBBLED_ODDSTONE_WALL.get(), ModBlocks.COBBLED_ODDSTONE_STAIRS.get(), ModBlocks.COBBLED_ODDSTONE_SLAB.get(),
                ModBlocks.ODDSTONE_BRICK_WALL.get(), ModBlocks.ODDSTONE_BRICK_STAIRS.get(), ModBlocks.ODDSTONE_BRICK_SLAB.get() };
    }

    /** Land mobs avoid every Oddstone wall, stairs and slab, as they do the full blocks. */
    @GameTest(template = EMPTY)
    public static void landMobsAvoidOddstoneShapes(GameTestHelper helper) {
        net.minecraft.world.entity.Mob zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 4, 1, 4);
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos abs = helper.absolutePos(rel);
        for (Block shape : oddstoneShapes()) {
            helper.setBlock(rel, shape);
            BlockState state = helper.getBlockState(rel);
            net.minecraft.world.level.pathfinder.BlockPathTypes on =
                    state.getBlockPathType(helper.getLevel(), abs, zombie);
            net.minecraft.world.level.pathfinder.BlockPathTypes beside =
                    state.getAdjacentBlockPathType(helper.getLevel(), abs, zombie,
                            net.minecraft.world.level.pathfinder.BlockPathTypes.OPEN);
            if (on != net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_OTHER
                    || beside != net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_OTHER) {
                zombie.discard();
                helper.fail("a zombie does not avoid " + shape + " (on: " + on + ", beside: " + beside + ")", rel);
                return;
            }
        }
        zombie.discard();
        helper.succeed();
    }

    /** A double Oddstone slab of each kind does not carry redstone: a repeater into it does not light a lamp behind it. */
    @GameTest(template = EMPTY)
    public static void doubleOddstoneSlabsDoNotCarryRedstone(GameTestHelper helper) {
        Block[] slabs = { ModBlocks.ODDSTONE_SLAB.get(), ModBlocks.COBBLED_ODDSTONE_SLAB.get(), ModBlocks.ODDSTONE_BRICK_SLAB.get() };
        for (int x = 0; x <= 5; x++) for (int z = 0; z <= 5; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        for (int i = 0; i < slabs.length; i++) {
            int z = i * 2;
            helper.setBlock(3, 1, z, slabs[i].defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                    net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
            helper.setBlock(4, 1, z, Blocks.REDSTONE_LAMP);
            helper.setBlock(2, 1, z, Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.WEST));
            helper.setBlock(1, 1, z, Blocks.REDSTONE_BLOCK);
        }
        helper.runAfterDelay(8, () -> {
            for (int i = 0; i < slabs.length; i++) {
                int z = i * 2;
                if (!helper.getBlockState(new BlockPos(2, 1, z)).getValue(RepeaterBlock.POWERED)) {
                    helper.fail("test set-up: the repeater is not powered", new BlockPos(2, 1, z));
                    return;
                }
                if (helper.getBlockState(new BlockPos(4, 1, z)).getValue(RedstoneLampBlock.LIT)) {
                    helper.fail("a double " + slabs[i] + " carried redstone to the lamp", new BlockPos(3, 1, z));
                    return;
                }
            }
            helper.succeed();
        });
    }

    /** An Oddstone slab grabs slime by contact, as the full blocks do: the slime moves with it. */
    @GameTest(template = EMPTY)
    public static void oddstoneSlabGrabsSlime(GameTestHelper helper) {
        helper.setBlock(0, 2, 1, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(1, 2, 1, ModBlocks.ODDSTONE_SLAB.get());
        helper.setBlock(1, 2, 2, Blocks.SLIME_BLOCK);
        helper.runAfterDelay(2, () -> helper.setBlock(0, 2, 0, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(8, () -> {
            boolean slabMoved = helper.getBlockState(new BlockPos(2, 2, 1)).is(ModBlocks.ODDSTONE_SLAB.get());
            boolean slimeCame = helper.getBlockState(new BlockPos(2, 2, 2)).is(Blocks.SLIME_BLOCK);
            if (slabMoved && slimeCame) helper.succeed();
            else helper.fail("slab moved=" + slabMoved + ", slime came along=" + slimeCame, new BlockPos(1, 2, 2));
        });
    }

    /** Slime cannot grab Oddstone stairs, as it cannot grab the full blocks: the stairs stay put. */
    @GameTest(template = EMPTY)
    public static void slimeCannotGrabOddstoneStairs(GameTestHelper helper) {
        helper.setBlock(0, 2, 1, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(1, 2, 1, Blocks.SLIME_BLOCK);
        helper.setBlock(1, 2, 2, ModBlocks.COBBLED_ODDSTONE_STAIRS.get());
        helper.runAfterDelay(2, () -> helper.setBlock(0, 2, 0, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(8, () -> {
            boolean slimeMoved = helper.getBlockState(new BlockPos(2, 2, 1)).is(Blocks.SLIME_BLOCK);
            boolean stairsStayed = helper.getBlockState(new BlockPos(1, 2, 2)).is(ModBlocks.COBBLED_ODDSTONE_STAIRS.get());
            if (slimeMoved && stairsStayed) helper.succeed();
            else helper.fail("slime moved=" + slimeMoved + ", stairs stayed=" + stairsStayed, new BlockPos(1, 2, 2));
        });
    }

    // ==================== Repulser and Attractor targets ====================

    /** The Repulser pushes any Oddstone block, not only plain Oddstone: here Cobbled Oddstone. */
    @GameTest(template = EMPTY)
    public static void repulserPushesCobbledOddstone(GameTestHelper helper) {
        helper.setBlock(0, 1, 1, ModBlocks.ODDSTONE_REPULSER.get().defaultBlockState()
                .setValue(AbstractRangedPistonBlock.FACING, Direction.EAST));
        helper.setBlock(3, 1, 1, ModBlocks.COBBLED_ODDSTONE.get());
        helper.runAfterDelay(2, () -> helper.setBlock(0, 1, 0, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(8, () -> {
            if (helper.getBlockState(new BlockPos(4, 1, 1)).is(ModBlocks.COBBLED_ODDSTONE.get())
                    && helper.getBlockState(new BlockPos(3, 1, 1)).isAir()) helper.succeed();
            else helper.fail("Repulser did not push the Cobbled Oddstone one block", new BlockPos(3, 1, 1));
        });
    }

    /** The Attractor pulls any Oddstone block: here an Oddstone Brick Slab. */
    @GameTest(template = EMPTY)
    public static void attractorPullsOddstoneBrickSlab(GameTestHelper helper) {
        helper.setBlock(0, 1, 1, ModBlocks.ODDSTONE_ATTRACTOR.get().defaultBlockState()
                .setValue(AbstractRangedPistonBlock.FACING, Direction.EAST));
        helper.setBlock(4, 1, 1, ModBlocks.ODDSTONE_BRICK_SLAB.get());
        helper.runAfterDelay(2, () -> helper.setBlock(0, 1, 0, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(8, () -> {
            if (helper.getBlockState(new BlockPos(3, 1, 1)).is(ModBlocks.ODDSTONE_BRICK_SLAB.get())
                    && helper.getBlockState(new BlockPos(4, 1, 1)).isAir()) helper.succeed();
            else helper.fail("Attractor did not pull the Oddstone Brick Slab one block", new BlockPos(4, 1, 1));
        });
    }

    // ==================== Sympathetic links and the push limit (the Ideomancer, 2026-10-25) ====================
    // Run in their own batch: they lower the global push limit to 3 (a 7x7 test area cannot
    // hold 12-block lines) and must not overlap other tests that move bigger groups.

    // read once, before any test lowers it: both tests in the batch run at the same time
    private static final int CONFIGURED_PUSH_LIMIT = IdeomodConfig.getBasePushLimit();

    private static int setBasePushLimit(int limit) {
        try {
            java.lang.reflect.Field f = IdeomodConfig.class.getDeclaredField("basePushLimit");
            f.setAccessible(true);
            int old = f.getInt(null);
            f.setInt(null, limit);
            return old;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A linked Oddstone that would itself take the move over the limit is left behind;
     * the rest of the move goes ahead. Limit 3: piston -> [Oddstone A][stone][stone] is 3;
     * Oddstone B, linked 3 blocks south of A, would be the 4th.
     */
    @GameTest(template = EMPTY, batch = "pushlimit")
    public static void linkedOddstoneOverLimitIsLeftBehind(GameTestHelper helper) {
        setBasePushLimit(3);
        helper.setBlock(0, 1, 1, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(1, 1, 1, ModBlocks.ODDSTONE.get());
        helper.setBlock(2, 1, 1, Blocks.STONE);
        helper.setBlock(3, 1, 1, Blocks.STONE);
        helper.setBlock(1, 1, 4, ModBlocks.ODDSTONE.get());
        helper.runAfterDelay(2, () -> helper.setBlock(0, 1, 0, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(8, () -> {
            setBasePushLimit(CONFIGURED_PUSH_LIMIT);
            if (!helper.getBlockState(new BlockPos(2, 1, 1)).is(ModBlocks.ODDSTONE.get())
                    || !helper.getBlockState(new BlockPos(4, 1, 1)).is(Blocks.STONE)) {
                helper.fail("the main push (3 blocks, at the limit) did not happen", new BlockPos(1, 1, 1));
            } else if (!helper.getBlockState(new BlockPos(1, 1, 4)).is(ModBlocks.ODDSTONE.get())) {
                helper.fail("the linked Oddstone over the limit moved instead of being left behind", new BlockPos(1, 1, 4));
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * A linked Oddstone that would push blocks over the limit makes the whole move fail,
     * as a vanilla piston refuses. Limit 3: Oddstone A alone, but Oddstone B (linked) has
     * three stones in front of it: B's line alone is 4.
     */
    @GameTest(template = EMPTY, batch = "pushlimit")
    public static void linkedOddstonePushingOverLimitFailsTheMove(GameTestHelper helper) {
        setBasePushLimit(3);
        helper.setBlock(0, 1, 1, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(1, 1, 1, ModBlocks.ODDSTONE.get());
        helper.setBlock(1, 1, 4, ModBlocks.ODDSTONE.get());
        helper.setBlock(2, 1, 4, Blocks.STONE);
        helper.setBlock(3, 1, 4, Blocks.STONE);
        helper.setBlock(4, 1, 4, Blocks.STONE);
        helper.runAfterDelay(2, () -> helper.setBlock(0, 1, 0, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(8, () -> {
            setBasePushLimit(CONFIGURED_PUSH_LIMIT);
            boolean aStayed = helper.getBlockState(new BlockPos(1, 1, 1)).is(ModBlocks.ODDSTONE.get());
            boolean bStayed = helper.getBlockState(new BlockPos(1, 1, 4)).is(ModBlocks.ODDSTONE.get());
            boolean extended = helper.getBlockState(new BlockPos(0, 1, 1)).getValue(PistonBaseBlock.EXTENDED);
            if (aStayed && bStayed && !extended) helper.succeed();
            else helper.fail("move should have failed: A stayed=" + aStayed + ", B stayed=" + bStayed
                    + ", piston extended=" + extended, new BlockPos(1, 1, 1));
        });
    }

    // ==================== Server config sent to clients (fixed 2026-10-25) ====================

    /**
     * The server's config, sent as ConfigSyncPacket (through its real encode/decode), replaces
     * the client's values while connected, and leaving the server brings back the client's own
     * file. Own batch: it changes the global sympathetic radius for a moment.
     */
    @GameTest(template = EMPTY, batch = "config")
    public static void clientUsesServerConfigUntilItLeaves(GameTestHelper helper) {
        int own = IdeomodConfig.getSympatheticRadius();
        int sent = own == 7 ? 8 : 7;
        String json = IdeomodConfig.toNetworkString();
        String key = "\"sympathetic_radius\":" + own;
        if (!json.contains(key)) { helper.fail("config JSON has no " + key + ": " + json.substring(0, Math.min(200, json.length()))); return; }
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        com.ideomancer.ideomod.network.ConfigSyncPacket.encode(
                new com.ideomancer.ideomod.network.ConfigSyncPacket(json.replace(key, "\"sympathetic_radius\":" + sent)), buf);
        com.ideomancer.ideomod.network.ConfigSyncPacket received = com.ideomancer.ideomod.network.ConfigSyncPacket.decode(buf);

        String sentJson;
        try {
            java.lang.reflect.Field f = com.ideomancer.ideomod.network.ConfigSyncPacket.class.getDeclaredField("json");
            f.setAccessible(true);
            sentJson = (String) f.get(received);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        IdeomodConfig.applyServerConfig(sentJson);
        int during = IdeomodConfig.getSympatheticRadius();
        boolean flagged = IdeomodConfig.isUsingServerConfig();
        int pushDuring = IdeomodConfig.getBasePushLimit();
        IdeomodConfig.restoreLocalConfig();
        int after = IdeomodConfig.getSympatheticRadius();

        if (during != sent) helper.fail("client kept radius " + during + " instead of the server's " + sent);
        else if (!flagged) helper.fail("client not marked as using the server's config");
        else if (pushDuring != CONFIGURED_PUSH_LIMIT) helper.fail("other values changed in transit: push limit " + pushDuring);
        else if (after != own || IdeomodConfig.isUsingServerConfig()) helper.fail("after leaving, radius " + after + ", expected its own " + own);
        else helper.succeed();
    }

    // ==================== Fluid Pipe drop (fixed 2026-10-25) ====================

    /**
     * A full Fluid Pipe that is broken drops as a Fluid Pipe holding its water bucket; placed
     * again (load the drop's BlockEntityTag, then setPlacedBy, as a BlockItem does) it must
     * still be a Fluid Pipe full of water, not a plain Machine Base. A Machine Base given a
     * water bucket becomes a full Fluid Pipe too.
     */
    @GameTest(template = EMPTY)
    public static void fullFluidPipeSurvivesBreakAndPlace(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.MACHINE_FLUID_PIPE.get());
        BlockPos abs = helper.absolutePos(pos);
        if (!(helper.getBlockEntity(pos) instanceof MachineFluidPipeBlockEntity pipe)) { helper.fail("no pipe", pos); return; }
        pipe.setFluidType(FluidType.WATER);
        java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                helper.getBlockState(pos), helper.getLevel(), abs, pipe);
        if (drops.size() != 1 || !drops.get(0).is(ModBlocks.MACHINE_FLUID_PIPE.get().asItem())) {
            helper.fail("full pipe dropped " + drops, pos); return;
        }
        ItemStack drop = drops.get(0);
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ModBlocks.MACHINE_FLUID_PIPE.get());
        BlockEntity placed = helper.getBlockEntity(pos);
        net.minecraft.nbt.CompoundTag beTag = drop.getTag() == null ? null : drop.getTag().getCompound("BlockEntityTag");
        if (placed != null && beTag != null) placed.load(beTag);
        helper.getBlockState(pos).getBlock().setPlacedBy(helper.getLevel(), abs, helper.getBlockState(pos), null, drop);

        BlockPos frame = new BlockPos(4, 1, 2);
        helper.setBlock(frame, ModBlocks.MACHINE_BLOCK.get());
        if (helper.getBlockEntity(frame) instanceof MachineFrameBlockEntity frameBE) frameBE.setItem(0, new ItemStack(Items.LAVA_BUCKET));

        // Checked at once: a full pipe pours its fluid out (into the next pipe, or the world) on
        // its next tick, which is right but would hide whether it was placed full.
        {
            BlockState st = helper.getBlockState(pos);
            if (!st.is(ModBlocks.MACHINE_FLUID_PIPE.get())) { helper.fail("re-placed full pipe became " + st.getBlock(), pos); return; }
            if (!(helper.getBlockEntity(pos) instanceof MachineFluidPipeBlockEntity p2) || p2.getFluidType() != FluidType.WATER
                    || st.getValue(MachineFluidPipeBlock.FLUID_TYPE) != FluidType.WATER) {
                helper.fail("re-placed pipe lost its water", pos); return;
            }
            BlockState fs = helper.getBlockState(frame);
            if (!fs.is(ModBlocks.MACHINE_FLUID_PIPE.get()) || fs.getValue(MachineFluidPipeBlock.FLUID_TYPE) != FluidType.LAVA) {
                helper.fail("Machine Base given a lava bucket became " + fs, frame); return;
            }
            helper.succeed();
        }
    }

    /**
     * A powered empty Fluid Pipe takes a real water source beside it, but leaves a waterlogged block
     * alone (it used to take the water by deleting the whole slab, frame or chest).
     */
    @GameTest(template = EMPTY)
    public static void fluidPipeLeavesWaterloggedBlocksAlone(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(2, 2, 2), slab = new BlockPos(3, 2, 2);
        helper.setBlock(pipePos, ModBlocks.MACHINE_FLUID_PIPE.get());
        helper.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED, true));
        helper.setBlock(2, 3, 2, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(20, () -> {
            if (!helper.getBlockState(slab).is(Blocks.OAK_SLAB)) { helper.fail("the waterlogged slab was taken: " + helper.getBlockState(slab), slab); return; }
            if (helper.getBlockEntity(pipePos) instanceof MachineFluidPipeBlockEntity p && p.getFluidType() != FluidType.EMPTY) {
                helper.fail("the pipe took water out of a waterlogged block", pipePos); return;
            }
            // and a real source still works
            helper.setBlock(1, 2, 2, Blocks.WATER);
            helper.runAfterDelay(20, () -> {
                if (helper.getBlockEntity(pipePos) instanceof MachineFluidPipeBlockEntity p && p.getFluidType() == FluidType.WATER
                        && !helper.getBlockState(new BlockPos(1, 2, 2)).is(Blocks.WATER)) helper.succeed();
                else helper.fail("the pipe did not take a real water source", pipePos);
            });
        });
    }

    /** Stepping on a framed redstone ore leaves it framed (the ore used to replace the whole block with itself). */
    @GameTest(template = EMPTY)
    public static void steppingOnFramedRedstoneOreKeepsTheFrame(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.IRON_FRAMED_BLOCK.get());
        if (!(helper.getBlockEntity(pos) instanceof com.ideomancer.ideomod.block.frame.FramedBlockEntity fbe)) { helper.fail("no framed block entity", pos); return; }
        net.minecraft.nbt.CompoundTag tag = fbe.saveWithoutMetadata();
        tag.putString("InnerBlock", "minecraft:redstone_ore");
        fbe.load(tag);
        net.minecraft.world.entity.Entity pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, new BlockPos(2, 2, 2));
        BlockState state = helper.getBlockState(pos);
        state.getBlock().stepOn(helper.getLevel(), helper.absolutePos(pos), state, pig);
        helper.runAfterDelay(2, () -> {
            if (helper.getBlockState(pos).is(ModBlocks.IRON_FRAMED_BLOCK.get())) helper.succeed();
            else helper.fail("stepping on it turned the framed redstone ore into " + helper.getBlockState(pos), pos);
        });
    }

    /** A framed-block item with a malformed inner id (only via commands) has a name and no inner block, instead of throwing. */
    @GameTest(template = EMPTY)
    public static void malformedInnerBlockIdIsTreatedAsEmpty(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModBlocks.IRON_FRAMED_BLOCK.get());
        net.minecraft.nbt.CompoundTag be = new net.minecraft.nbt.CompoundTag();
        be.putString("InnerBlock", "Bad Id");
        stack.getOrCreateTag().put("BlockEntityTag", be);
        try {
            if (com.ideomancer.ideomod.block.frame.FramedBlockEntity.getInnerBlockIdFromStack(stack) != null) { helper.fail("a malformed id was read as a block"); return; }
            stack.getHoverName();
            BlockPos pos = new BlockPos(2, 1, 2);
            helper.setBlock(pos, ModBlocks.IRON_FRAMED_BLOCK.get());
            helper.getBlockEntity(pos).load(be);
            helper.succeed();
        } catch (RuntimeException e) {
            helper.fail("a malformed inner id threw: " + e);
        }
    }

    /**
     * A filled Hollowstone broken by a survival player (vanilla's own break path):
     * a pickaxe drops shards and leaves the inner block standing; a Silk Touch pickaxe drops the
     * filled Hollowstone and leaves nothing; a Silk Touch shovel (can't harvest it) drops nothing
     * but still leaves the inner block standing.
     */
    @GameTest(template = EMPTY)
    public static void minedFilledHollowstoneDropsAndLeavesItsInnerBlock(GameTestHelper helper) {
        ItemStack silkPick = new ItemStack(Items.DIAMOND_PICKAXE);
        silkPick.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);
        ItemStack silkShovel = new ItemStack(Items.DIAMOND_SHOVEL);
        silkShovel.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);
        ItemStack[] tools = {new ItemStack(Items.DIAMOND_PICKAXE), silkPick, silkShovel};
        BlockPos[] at = {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1)};
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        net.minecraftforge.common.util.FakePlayer fp = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        fp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        String[] result = new String[3];
        for (int i = 0; i < 3; i++) {
            helper.setBlock(at[i], ModBlocks.HOLLOW_STONE.get().defaultBlockState().setValue(HollowstoneBlock.FILLED, true));
            if (helper.getBlockEntity(at[i]) instanceof com.ideomancer.ideomod.block.HollowstoneBlockEntity h) {
                h.setInnerBlock(new ResourceLocation("minecraft:obsidian"));
            }
            BlockPos abs = helper.absolutePos(at[i]);
            fp.setItemInHand(InteractionHand.MAIN_HAND, tools[i].copy());
            fp.gameMode.destroyBlock(abs);
            List<net.minecraft.world.entity.item.ItemEntity> items = level.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(1.5));
            int shards = 0, hollowstone = 0;
            for (var e : items) {
                if (e.getItem().is(ModItems.HOLLOW_STONE_SHARD.get())) shards += e.getItem().getCount();
                if (e.getItem().is(ModItems.HOLLOW_STONE.get())) hollowstone += e.getItem().getCount();
                e.discard();
            }
            result[i] = level.getBlockState(abs).getBlock() + " shards=" + shards + " hollowstone=" + hollowstone;
            boolean ok = switch (i) {
                case 0 -> level.getBlockState(abs).is(Blocks.OBSIDIAN) && shards >= 5 && shards <= 9 && hollowstone == 0;
                case 1 -> level.getBlockState(abs).isAir() && shards == 0 && hollowstone == 1;
                default -> level.getBlockState(abs).is(Blocks.OBSIDIAN) && shards == 0 && hollowstone == 0;
            };
            if (!ok) { helper.fail(new String[]{"pickaxe", "Silk Touch pickaxe", "Silk Touch shovel"}[i] + ": " + result[i], at[i]); return; }
        }
        helper.succeed();
    }

    /**
     * A hopper under a Machine Drill leaves its pickaxe alone (it used to pull it out, turning the
     * Drill back into a Base), and a hopper pointing into an empty Machine Base still feeds it.
     */
    @GameTest(template = EMPTY)
    public static void hoppersFeedMachinesButNeverTakeTheirItem(GameTestHelper helper) {
        BlockPos drill = new BlockPos(1, 2, 1);
        helper.setBlock(1, 1, 1, Blocks.HOPPER);
        helper.setBlock(drill, ModBlocks.MACHINE_BLOCK.get());
        ((MachineFrameBlockEntity) helper.getBlockEntity(drill)).setItem(0, new ItemStack(Items.IRON_PICKAXE));
        BlockPos base = new BlockPos(4, 1, 1);
        helper.setBlock(base, ModBlocks.MACHINE_BLOCK.get());
        helper.setBlock(4, 2, 1, Blocks.HOPPER);   // a hopper above points down into the Base
        if (helper.getBlockEntity(new BlockPos(4, 2, 1)) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity hopper) {
            hopper.setItem(0, new ItemStack(Items.REPEATER));
        }
        helper.runAfterDelay(30, () -> {
            if (!helper.getBlockState(drill).is(ModBlocks.MACHINE_DRILL.get())) { helper.fail("the hopper took the Drill's pickaxe: " + helper.getBlockState(drill), drill); return; }
            if (!helper.getBlockState(base).is(ModBlocks.MACHINE_REPEATER.get())) { helper.fail("a hopper no longer feeds an empty Machine Base: " + helper.getBlockState(base), base); return; }
            helper.succeed();
        });
    }

    /**
     * Taking the Oddstone out of a powered source Transmitter (as the GUI does) switches its
     * channel's receivers off; they used to stay on.
     */
    @GameTest(template = EMPTY)
    public static void takingTheChannelOutOfASourceTransmitterTurnsReceiversOff(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2), receiver = new BlockPos(4, 1, 2), lamp = new BlockPos(5, 1, 2);
        helper.setBlock(source, ModBlocks.MACHINE_BLOCK.get());
        ((MachineFrameBlockEntity) helper.getBlockEntity(source)).setItem(0, new ItemStack(ModBlocks.ODDSTONE.get()));
        helper.setBlock(receiver, ModBlocks.MACHINE_BLOCK.get());
        ((MachineFrameBlockEntity) helper.getBlockEntity(receiver)).setItem(0, new ItemStack(ModBlocks.ODDSTONE.get()));
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        helper.setBlock(0, 1, 2, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(10, () -> {
            if (!helper.getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT)) { helper.fail("the receiver never came on", lamp); return; }
            ((net.minecraft.world.Container) helper.getBlockEntity(source)).removeItem(0, 1);
            helper.runAfterDelay(10, () -> {
                if (helper.getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT)) helper.fail("the receiver stayed on after the source lost its channel", lamp);
                else helper.succeed();
            });
        });
    }

    /**
     * Shift-clicking a stack of barrels into a Machine Base's menu (vanilla's own quick-move loop)
     * puts one barrel in, making a Storage, and stops: the rest of the stack used to pour into the
     * new Storage's slots.
     */
    @GameTest(template = EMPTY)
    public static void shiftClickingAStackIntoABaseMovesOnlyOne(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.MACHINE_BLOCK.get());
        net.minecraftforge.common.util.FakePlayer fp = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        fp.getInventory().clearContent();
        fp.getInventory().setItem(0, new ItemStack(Items.BARREL, 64));
        var menu = new com.ideomancer.ideomod.block.machine.MachineFrameMenu(1, fp.getInventory(), helper.absolutePos(pos));
        int index = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (slot.container == fp.getInventory() && slot.getContainerSlot() == 0) { index = i; break; }
        }
        if (index < 0) { helper.fail("no hotbar slot in the menu"); return; }
        menu.clicked(index, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, fp);
        int left = fp.getInventory().getItem(0).getCount();
        fp.getInventory().clearContent();
        if (!helper.getBlockState(pos).is(ModBlocks.MACHINE_STORAGE.get())) { helper.fail("the barrel didn't make a Storage: " + helper.getBlockState(pos), pos); return; }
        var storage = (com.ideomancer.ideomod.block.machine.MachineStorageBlockEntity) helper.getBlockEntity(pos);
        int stored = storage.countItem(Items.BARREL) - 1;   // minus the one in the machine slot
        if (left == 63 && stored == 0) helper.succeed();
        else helper.fail("shift-click left " + left + " barrels in hand and put " + stored + " in the Storage's slots");
    }

    /**
     * A Machine Sticky Piston, like a vanilla one, can't pull Oddstone directly: pushed out, the
     * Cobbled Oddstone stays when the piston retracts, while stone in the same setup comes back.
     */
    @GameTest(template = EMPTY)
    public static void machineStickyPistonDoesNotPullOddstone(GameTestHelper helper) {
        Block[] blocks = {ModBlocks.COBBLED_ODDSTONE.get(), Blocks.STONE};
        for (int i = 0; i < 2; i++) {
            int z = 1 + 3 * i;
            helper.setBlock(new BlockPos(1, 1, z), ModBlocks.MACHINE_BLOCK.get());
            ((MachineFrameBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, z))).setItem(0, new ItemStack(Items.STICKY_PISTON));
            helper.setBlock(new BlockPos(2, 1, z), blocks[i]);
        }
        helper.runAfterDelay(2, () -> { helper.setBlock(0, 1, 1, Blocks.REDSTONE_BLOCK); helper.setBlock(0, 1, 4, Blocks.REDSTONE_BLOCK); });
        helper.runAfterDelay(12, () -> {
            if (!helper.getBlockState(new BlockPos(3, 1, 1)).is(blocks[0]) || !helper.getBlockState(new BlockPos(3, 1, 4)).is(blocks[1])) {
                helper.fail("the Machine Sticky Pistons did not push"); return;
            }
            helper.setBlock(0, 1, 1, Blocks.AIR);
            helper.setBlock(0, 1, 4, Blocks.AIR);
        });
        helper.runAfterDelay(24, () -> {
            if (!helper.getBlockState(new BlockPos(3, 1, 1)).is(blocks[0])) helper.fail("the Machine Sticky Piston pulled Oddstone back", new BlockPos(2, 1, 1));
            else if (!helper.getBlockState(new BlockPos(2, 1, 4)).is(Blocks.STONE)) helper.fail("the Machine Sticky Piston no longer pulls stone", new BlockPos(2, 1, 4));
            else helper.succeed();
        });
    }

    // ==================== Track and Trolley (pulses, 2026-11-18) ====================
    // In their own batch: the 40-Track column reaches far above its own test.

    private static void trackLine(GameTestHelper helper, int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x++) helper.setBlock(x, y, z, ModBlocks.MACHINE_TRACK.get());
    }

    private static int trackPower(GameTestHelper helper, int x, int y, int z) {
        BlockState s = helper.getBlockState(new BlockPos(x, y, z));
        return s.is(ModBlocks.MACHINE_TRACK.get()) ? s.getValue(com.ideomancer.ideomod.block.machine.MachineTrackBlock.POWER) : -1;
    }

    /**
     * There is no limit to a line: a pulse started at the bottom of a column of 40 Tracks reaches the
     * top and pushes the Trolley beside it up one block (a signal that faded by 1 per Track stopped
     * after 15).
     */
    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "tracks")
    public static void trackPulseHasNoLengthLimit(GameTestHelper helper) {
        for (int y = 2; y <= 41; y++) helper.setBlock(2, y, 2, ModBlocks.MACHINE_TRACK.get());
        helper.setBlock(3, 41, 2, ModBlocks.MACHINE_TROLLEY.get());
        helper.setBlock(3, 42, 2, Blocks.AIR);  // above the test area the test world is solid rock
        helper.runAfterDelay(2, () -> helper.setBlock(2, 1, 2, Blocks.REDSTONE_BLOCK));
helper.runAfterDelay(60, () -> {
            if (helper.getBlockState(new BlockPos(3, 42, 2)).is(ModBlocks.MACHINE_TROLLEY.get())
                    && !helper.getBlockState(new BlockPos(3, 41, 2)).is(ModBlocks.MACHINE_TROLLEY.get())) helper.succeed();
            else helper.fail("the pulse did not push the Trolley beside the 40th Track up", new BlockPos(3, 41, 2));
        });
    }

    /**
     * A Track pushes away from the side the pulse came from, one block per pulse: powered from the
     * west end the Trolley goes one block east; then from the east end, one block back west.
     */
    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "tracks")
    public static void trackPushesAwayFromWhereThePulseCame(GameTestHelper helper) {
        trackLine(helper, 1, 6, 1, 2);
        helper.setBlock(3, 2, 2, ModBlocks.MACHINE_TROLLEY.get());
        helper.runAfterDelay(2, () -> helper.setBlock(0, 1, 2, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(20, () -> {
            if (!helper.getBlockState(new BlockPos(4, 2, 2)).is(ModBlocks.MACHINE_TROLLEY.get())) {
                helper.fail("a pulse from the west end did not move the Trolley exactly one block east", new BlockPos(4, 2, 2));
                return;
            }
            helper.setBlock(0, 1, 2, Blocks.AIR);
            helper.setBlock(7, 1, 2, Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(40, () -> {
            if (helper.getBlockState(new BlockPos(3, 2, 2)).is(ModBlocks.MACHINE_TROLLEY.get())) helper.succeed();
            else helper.fail("a pulse from the east end did not move the Trolley one block back west", new BlockPos(3, 2, 2));
        });
    }

    /** A Track never stays on: a signal held on gives one pulse, and the line goes dark behind it. */
    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "tracks")
    public static void trackHeldSignalIsOnePulse(GameTestHelper helper) {
        trackLine(helper, 1, 6, 1, 2);
        helper.runAfterDelay(2, () -> helper.setBlock(0, 1, 2, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(4, () -> {
            if (trackPower(helper, 1, 1, 2) <= 0 && trackPower(helper, 2, 1, 2) <= 0) helper.fail("no pulse went down the line");
        });
        helper.runAfterDelay(30, () -> {
            for (int x = 1; x <= 6; x++) {
                if (trackPower(helper, x, 1, 2) != 0) { helper.fail("Track " + x + " stayed lit while the signal was held", new BlockPos(x, 1, 2)); return; }
            }
            helper.succeed();
        });
    }

    /**
     * A pulse can't run round a closed ring of Tracks forever: each Track passes a pulse on once,
     * so the ring is dark again soon after.
     */
    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "tracks")
    public static void trackPulseDiesInALoop(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                if (x == 1 || x == 5 || z == 1 || z == 5) helper.setBlock(x, 1, z, ModBlocks.MACHINE_TRACK.get());
            }
        }
        helper.runAfterDelay(2, () -> helper.setBlock(0, 1, 1, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(6, () -> {
            boolean lit = false;
            for (int x = 1; x <= 5; x++) lit |= trackPower(helper, x, 1, 1) > 0 || trackPower(helper, x, 1, 5) > 0;
            if (!lit) helper.fail("no pulse went round the ring");
        });
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (++ticks[0] < 40) return;
            for (int x = 1; x <= 5; x++) {
                for (int z = 1; z <= 5; z++) {
                    if ((x == 1 || x == 5 || z == 1 || z == 5) && trackPower(helper, x, 1, z) != 0) {
                        helper.fail("the pulse was still going round the ring at Track " + x + "," + z, new BlockPos(x, 1, z));
                        return;
                    }
                }
            }
            if (ticks[0] >= 60) helper.succeed();
        });
    }

    /** Each rising edge is its own pulse: three pulses move a Trolley three blocks. */
    @GameTest(template = EMPTY, timeoutTicks = 160, batch = "tracks")
    public static void trackEveryPulseMovesTheTrolleyOnce(GameTestHelper helper) {
        trackLine(helper, 1, 7, 1, 2);
        helper.setBlock(2, 2, 2, ModBlocks.MACHINE_TROLLEY.get());
        for (int i = 0; i < 3; i++) {
            int t = 2 + i * 20;
            helper.runAfterDelay(t, () -> helper.setBlock(0, 1, 2, Blocks.REDSTONE_BLOCK));
            helper.runAfterDelay(t + 6, () -> helper.setBlock(0, 1, 2, Blocks.AIR));
        }
        helper.runAfterDelay(80, () -> {
            if (helper.getBlockState(new BlockPos(5, 2, 2)).is(ModBlocks.MACHINE_TROLLEY.get())) helper.succeed();
            else helper.fail("three pulses did not move the Trolley exactly three blocks (from x=2 to x=5)", new BlockPos(5, 2, 2));
        });
    }

    // ==================== Circuits, still and moving (2026-11-18) ====================
    // Each circuit is a row of machine blocks at y=2 on a strip of iron frames at y=1 (frames stick to
    // machine blocks but carry no redstone), along x from x=1, at z=2. The first block is a Toggle
    // latched on by a pulse from its west at the start, so the circuit carries its own power source.
    // In the moving version a sticky piston at (0,1,2), facing east, driven 3 ticks on / 3 off, pushes
    // and pulls the whole strip one block for MOVE_TICKS ticks, then rests.
    // Machine blocks may pause while they are moving pistons; between moves they must keep working:
    // no spurious flips, nothing lost, and the same rhythm once still again.

    // MOVE_TICKS is a whole number of piston cycles (6 and 20 ticks), so moving stops fully retracted:
    // a sticky piston that loses power mid-push leaves its block behind (vanilla).
    private static final int SETTLE = 40, OBSERVE = 200, MOVE_TICKS = 240, AFTER = 100;

    private record Part(int x, Item item, java.util.function.Consumer<net.minecraft.world.level.block.entity.BlockEntity> setup) {}

    private static Part part(int x, Item item) { return new Part(x, item, be -> {}); }

    private static Part engine(int x, int interval) {
        return new Part(x, Items.FURNACE, be -> {
            var e = (com.ideomancer.ideomod.block.machine.MachineEngineBlockEntity) be;
            e.setItem(com.ideomancer.ideomod.block.machine.MachineEngineBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
            e.setPulseInterval(interval);
        });
    }

    /** "powered" (or Conductor "power" > 0) of whatever machine block is there; null if it is not a settled block. */
    private static Integer outputOf(BlockState s) {
        for (net.minecraft.world.level.block.state.properties.Property<?> p : s.getProperties()) {
            if (p.getName().equals("power")) return (Integer) s.getValue(p);
            if (p.getName().equals("powered")) return ((Boolean) s.getValue(p)) ? 1 : 0;
        }
        return null;
    }

    /** Where the strip is now: 0 at rest, 1 pushed east; -1 while any part is moving (or missing). */
    private static int stripOffset(GameTestHelper h, Part[] parts) {
        for (int off = 0; off <= 1; off++) {
            boolean all = true;
            for (Part p : parts) {
                BlockState s = h.getBlockState(new BlockPos(p.x() + off, 2, 2));
                if (!(s.getBlock() instanceof com.ideomancer.ideomod.block.machine.AbstractMachineBlock) || outputOf(s) == null) { all = false; break; }
            }
            if (all) return off;
        }
        return -1;
    }

    /**
     * Builds the circuit, latches the first Toggle on, optionally drives the piston, and calls the check
     * with per-tick output readings: readings.get(tick)[i] = output of part i, or null while moving.
     */
    private static void runCircuit(GameTestHelper h, Part[] parts, int frameTo, boolean moving,
                                   java.util.function.BiFunction<java.util.List<Integer[]>, int[], String> check) {
        runCircuit(h, parts, frameTo, moving ? 3 : 0, check);
    }

    /** halfPeriod: piston driver on for this many ticks, then off as long; 0 = still. */
    private static void runCircuit(GameTestHelper h, Part[] parts, int frameTo, int halfPeriod,
                                   java.util.function.BiFunction<java.util.List<Integer[]>, int[], String> check) {
        boolean moving = halfPeriod > 0;
        for (int x = 1; x <= frameTo; x++) h.setBlock(x, 1, 2, ModBlocks.IRON_FRAME.get());
        for (Part p : parts) {
            h.setBlock(p.x(), 2, 2, ModBlocks.MACHINE_BLOCK.get());
            ((MachineFrameBlockEntity) h.getBlockEntity(new BlockPos(p.x(), 2, 2))).setItem(0, new ItemStack(p.item()));
        }
        h.runAfterDelay(2, () -> { for (Part p : parts) p.setup().accept(h.getBlockEntity(new BlockPos(p.x(), 2, 2))); });
        h.runAfterDelay(5, () -> h.setBlock(0, 2, 2, Blocks.REDSTONE_BLOCK));   // latch the first Toggle on
        h.runAfterDelay(7, () -> h.setBlock(0, 2, 2, Blocks.AIR));
        // the piston only after the latch: the latch block sits right above where it goes
        if (moving) h.runAfterDelay(10, () -> h.setBlock(0, 1, 2, Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST)));
        int moveFrom = SETTLE + OBSERVE, moveTo = moveFrom + MOVE_TICKS, end = moveTo + AFTER;
        java.util.List<Integer[]> readings = new java.util.ArrayList<>();
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = ++tick[0];
            if (moving && t >= moveFrom && t < moveTo) {
                boolean on = ((t - moveFrom) / halfPeriod) % 2 == 0;
                h.setBlock(0, 0, 2, on ? Blocks.REDSTONE_BLOCK : Blocks.STONE);
            } else if (moving && t == moveTo) {
                h.setBlock(0, 0, 2, Blocks.STONE);
            }
            if (t < SETTLE) return;
            int off = stripOffset(h, parts);
            Integer[] row = new Integer[parts.length];
            if (off >= 0) for (int i = 0; i < parts.length; i++) row[i] = outputOf(h.getBlockState(new BlockPos(parts[i].x() + off, 2, 2)));
            readings.add(row);
            if (t == end) {
                String problem = check.apply(readings, new int[]{OBSERVE, moving ? MOVE_TICKS : 0, AFTER});
                if (stripOffset(h, parts) != 0) problem = "the circuit did not come back to rest at its start" + (problem == null ? "" : "; " + problem);
                if (problem == null) h.succeed(); else h.fail(problem);
            }
        });
    }

    /** Rising edges of part i within readings[from, to) (ticks with no reading are skipped). */
    private static int risesOf(java.util.List<Integer[]> r, int i, int from, int to) {
        int n = 0; Integer last = null;
        for (int k = from; k < to && k < r.size(); k++) {
            Integer v = r.get(k)[i]; if (v == null) continue;
            if (last != null && last == 0 && v > 0) n++;
            last = v;
        }
        return n;
    }

    /** Changes (either way) of part i within readings[from, to). */
    private static int flipsOf(java.util.List<Integer[]> r, int i, int from, int to) {
        int n = 0; Integer last = null;
        for (int k = from; k < to && k < r.size(); k++) {
            Integer v = r.get(k)[i]; if (v == null) continue;
            if (last != null && !last.equals(v)) n++;
            last = v;
        }
        return n;
    }

    /** Gaps in ticks between rising edges of part i within readings[from, to). */
    private static java.util.List<Integer> pulseGaps(java.util.List<Integer[]> r, int i, int from, int to) {
        java.util.List<Integer> gaps = new java.util.ArrayList<>(); Integer last = null; int lastRise = -1;
        for (int k = from; k < to && k < r.size(); k++) {
            Integer v = r.get(k)[i]; if (v == null) { last = null; continue; }
            if (last != null && last == 0 && v > 0) { if (lastRise >= 0) gaps.add(k - lastRise); lastRise = k; }
            last = v;
        }
        return gaps;
    }

    /** Never off where it was read (the latched source Toggle must stay on). */
    private static String staysOn(java.util.List<Integer[]> r, int i, String name) {
        for (int k = 0; k < r.size(); k++) if (r.get(k)[i] != null && r.get(k)[i] == 0) return name + " went off at reading " + k;
        return null;
    }

    /**
     * The shared check for "a clock drives a Toggle": in each phase, the far Toggle flips once per clock
     * pulse; while still, the clock keeps its interval exactly; while moving it may be slowed but must keep
     * going; after moving it is back to its exact interval; the latched source never goes off.
     */
    private static String clockFlipsToggle(java.util.List<Integer[]> r, int[] phases, int clock, int toggle, int interval) {
        return clockFlipsToggle(r, phases, clock, toggle, interval, 2);
    }

    /**
     * minMoveFlips: how many flips the moving phase must show. 0 for the fast piston: there the circuit
     * gets no tick of its own between moves (the piston moves it again before block entities tick),
     * so an Engine has no time to count; what must hold is that nothing goes wrong.
     */
    private static String clockFlipsToggle(java.util.List<Integer[]> r, int[] phases, int clock, int toggle, int interval, int minMoveFlips) {
        String s = staysOn(r, 0, "the latched source Toggle");
        if (s != null) return s;
        int a = phases[0], b = a + phases[1], c = b + phases[2];
        int stillPulses = risesOf(r, clock, 0, a), stillFlips = flipsOf(r, toggle, 0, a);
        com.ideomancer.ideomod.Ideomod.LOGGER.info("[CIRCUIT] still: {} pulses, {} flips, gaps {}", stillPulses, stillFlips, pulseGaps(r, clock, 0, a));
        if (stillPulses < a / interval - 1) return "while still the clock gave " + stillPulses + " pulses in " + a + " ticks";
        if (stillFlips != stillPulses && stillFlips != stillPulses - 1 && stillFlips != stillPulses + 1)
            return "while still the Toggle flipped " + stillFlips + " times for " + stillPulses + " pulses";
        for (int g : pulseGaps(r, clock, 0, a)) if (g != interval) return "while still the clock's gaps were " + pulseGaps(r, clock, 0, a);
        if (phases[1] > 0) {
            int movePulses = risesOf(r, clock, a, b), moveFlips = flipsOf(r, toggle, a, b);
            com.ideomancer.ideomod.Ideomod.LOGGER.info("[CIRCUIT] moving: {} pulses seen, {} flips (still rate would be {})", movePulses, moveFlips, phases[1] / interval);
            if (moveFlips < minMoveFlips) return "while moving the Toggle flipped only " + moveFlips + " times in " + phases[1] + " ticks";
            if (moveFlips > phases[1] / interval + 2) return "while moving the Toggle flipped " + moveFlips + " times, more than the clock can drive";
            if (moveFlips > movePulses + 2) return "while moving the Toggle flipped " + moveFlips + " times for " + movePulses + " pulses seen";
        }
        int afterPulses = risesOf(r, clock, b + 20, c), afterFlips = flipsOf(r, toggle, b + 20, c);
        java.util.List<Integer> gaps = pulseGaps(r, clock, b + 20, c);
        com.ideomancer.ideomod.Ideomod.LOGGER.info("[CIRCUIT] after: {} pulses, {} flips, gaps {}", afterPulses, afterFlips, gaps);
        if (afterPulses < (c - b - 20) / interval - 1) return "after moving the clock gave only " + afterPulses + " pulses";
        for (int g : gaps) if (g != interval) return "after moving the clock's gaps were " + gaps + " (still: " + interval + ")";
        if (Math.abs(afterFlips - afterPulses) > 1) return "after moving the Toggle flipped " + afterFlips + " times for " + afterPulses + " pulses";
        return null;
    }

    private static Part[] clockToggleCircuit() {
        return new Part[]{part(1, Items.LEVER), engine(2, 10), part(3, Items.LEVER)};
    }

    /** Toggle (latched) -> Engine every 10 ticks -> Toggle: one flip per pulse, steady interval. */
    @GameTest(template = EMPTY, timeoutTicks = 700, batch = "circuits")
    public static void circuitClockFlipsAToggle(GameTestHelper helper) {
        runCircuit(helper, clockToggleCircuit(), 3, false, (r, ph) -> clockFlipsToggle(r, ph, 1, 2, 10));
    }

    @GameTest(template = EMPTY, timeoutTicks = 700, batch = "circuits")
    public static void circuitClockFlipsAToggleWhileMoving(GameTestHelper helper) {
        runCircuit(helper, clockToggleCircuit(), 3, true, (r, ph) -> clockFlipsToggle(r, ph, 1, 2, 10, 0));
    }

    private static Part[] timerChainCircuit() {
        return new Part[]{part(1, Items.LEVER), engine(2, 20),
                new Part(3, Items.CLOCK, be -> ((MachineFrameBlockEntity) be).setTimerDuration(5)),
                new Part(4, Items.REPEATER, be -> ((MachineFrameBlockEntity) be).setRepeaterDelay(2)),
                part(5, Items.LEVER)};
    }

    /** Toggle (latched) -> Engine every 20 -> Timer 5 ticks -> Repeater delay 2 -> Toggle: one flip per pulse. */
    @GameTest(template = EMPTY, timeoutTicks = 700, batch = "circuits")
    public static void circuitTimerChain(GameTestHelper helper) {
        runCircuit(helper, timerChainCircuit(), 5, false, (r, ph) -> {
            String s = clockFlipsToggle(r, ph, 1, 4, 20);
            if (s != null) return s;
            return Math.abs(risesOf(r, 2, 0, ph[0]) - risesOf(r, 1, 0, ph[0])) > 1 ? "the Timer did not fire once per Engine pulse" : null;
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 700, batch = "circuits")
    public static void circuitTimerChainWhileMoving(GameTestHelper helper) {
        runCircuit(helper, timerChainCircuit(), 5, true, (r, ph) -> clockFlipsToggle(r, ph, 1, 4, 20, 0));
    }

    private static Part[] transformerCircuit() {
        return new Part[]{part(1, Items.LEVER),
                new Part(2, Items.COMPARATOR, be -> ((MachineFrameBlockEntity) be).setTransformerLevel(7)),
                part(3, Items.REDSTONE), part(4, Items.REDSTONE)};
    }

    /** The Conductors after a Transformer at 7 hold the same strengths at every still reading. */
    private static String transformerHolds(java.util.List<Integer[]> r, int[] ph) {
        String s = staysOn(r, 0, "the latched source Toggle");
        if (s != null) return s;
        Integer c1 = null, c2 = null;
        for (int k = 10; k < ph[0]; k++) { if (r.get(k)[2] != null) { c1 = r.get(k)[2]; c2 = r.get(k)[3]; } }
        com.ideomancer.ideomod.Ideomod.LOGGER.info("[CIRCUIT] transformer: Conductors at {} and {}", c1, c2);
        if (c1 == null || c1 == 0) return "the first Conductor got no signal from the Transformer";
        if (c2 == null || c2 != c1 - 1) return "the second Conductor read " + c2 + " after " + c1;
        Integer lastMoveTick = null;
        for (int k = 0; k < r.size(); k++) {
            Integer[] row = r.get(k);
            if (row[2] == null) { lastMoveTick = k; continue; }
            if (lastMoveTick != null && k - lastMoveTick <= 3) continue;   // let it settle just after landing
            if (!row[2].equals(c1) || !row[3].equals(c2)) return "at reading " + k + " the Conductors read " + row[2] + "/" + row[3] + " instead of " + c1 + "/" + c2;
        }
        return null;
    }

    @GameTest(template = EMPTY, timeoutTicks = 700, batch = "circuits")
    public static void circuitTransformerHoldsItsLevel(GameTestHelper helper) {
        runCircuit(helper, transformerCircuit(), 4, false, IdeomodGameTests::transformerHolds);
    }

    @GameTest(template = EMPTY, timeoutTicks = 700, batch = "circuits")
    public static void circuitTransformerHoldsItsLevelWhileMoving(GameTestHelper helper) {
        runCircuit(helper, transformerCircuit(), 4, true, IdeomodGameTests::transformerHolds);
    }

    private static Part[] wirelessCircuit() {
        Item channel = ModBlocks.COBBLED_ODDSTONE.get().asItem();
        return new Part[]{part(1, Items.LEVER), engine(2, 10), part(3, channel), part(5, channel), part(6, Items.LEVER)};
    }

    /** Toggle (latched) -> Engine every 10 -> Transmitter ... gap ... Transmitter -> Toggle: one flip per pulse. */
    @GameTest(template = EMPTY, timeoutTicks = 700, batch = "circuits")
    public static void circuitWirelessFlipsAToggle(GameTestHelper helper) {
        runCircuit(helper, wirelessCircuit(), 6, false, (r, ph) -> clockFlipsToggle(r, ph, 1, 4, 10));
    }

    @GameTest(template = EMPTY, timeoutTicks = 700, batch = "circuits")
    public static void circuitWirelessFlipsAToggleWhileMoving(GameTestHelper helper) {
        runCircuit(helper, wirelessCircuit(), 6, true, (r, ph) -> clockFlipsToggle(r, ph, 1, 4, 10, 0));
    }

    /** The same circuits on a slower piston (10 ticks out, 10 back), with time to work between moves. */
    @GameTest(template = EMPTY, timeoutTicks = 700, batch = "circuits")
    public static void circuitClockFlipsAToggleMovingSlowly(GameTestHelper helper) {
        runCircuit(helper, clockToggleCircuit(), 3, 10, (r, ph) -> clockFlipsToggle(r, ph, 1, 2, 10));
    }

    @GameTest(template = EMPTY, timeoutTicks = 700, batch = "circuits")
    public static void circuitTimerChainMovingSlowly(GameTestHelper helper) {
        runCircuit(helper, timerChainCircuit(), 5, 10, (r, ph) -> clockFlipsToggle(r, ph, 1, 4, 20));
    }

    @GameTest(template = EMPTY, timeoutTicks = 700, batch = "circuits")
    public static void circuitWirelessFlipsAToggleMovingSlowly(GameTestHelper helper) {
        runCircuit(helper, wirelessCircuit(), 6, 10, (r, ph) -> clockFlipsToggle(r, ph, 1, 4, 10));
    }

    /**
     * A pulse of one tick (shorter than the 2-tick reaction time) still counts: a Toggle flips, a Timer
     * fires, a Repeater outputs (for its delay, as a vanilla repeater stretches a short pulse).
     */
    private static void oneTickPulse(GameTestHelper h, Item item, String what) {
        h.setBlock(1, 2, 2, ModBlocks.MACHINE_BLOCK.get());
        ((MachineFrameBlockEntity) h.getBlockEntity(new BlockPos(1, 2, 2))).setItem(0, new ItemStack(item));
        h.runAfterDelay(5, () -> h.setBlock(0, 2, 2, Blocks.REDSTONE_BLOCK));
        h.runAfterDelay(6, () -> h.setBlock(0, 2, 2, Blocks.AIR));
        boolean[] seen = {false};
        int[] tick = {0};
        h.onEachTick(() -> {
            Integer out = outputOf(h.getBlockState(new BlockPos(1, 2, 2)));
            if (out != null && out > 0) seen[0] = true;
            if (++tick[0] == 30) {
                if (seen[0]) h.succeed();
                else h.fail("a one-tick pulse did not " + what, new BlockPos(1, 2, 2));
            }
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "circuits")
    public static void oneTickPulseFlipsAToggle(GameTestHelper helper) { oneTickPulse(helper, Items.LEVER, "flip the Toggle"); }

    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "circuits")
    public static void oneTickPulseStartsATimer(GameTestHelper helper) { oneTickPulse(helper, Items.CLOCK, "start the Timer"); }

    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "circuits")
    public static void oneTickPulseGoesThroughARepeater(GameTestHelper helper) { oneTickPulse(helper, Items.REPEATER, "come out of the Repeater"); }

    // ==================== Recipes ====================

    /**
     * Shaped recipes come out of the game's recipe manager with the expected
     * item in every slot (read left to right, top to bottom; null = empty) and
     * the expected count. Catches a mistyped item id, which Minecraft would
     * otherwise just log and skip, leaving the item uncraftable.
     */
    @GameTest(template = EMPTY)
    public static void recipesHaveTheirShapes(GameTestHelper helper) {
        Item iron = Items.IRON_INGOT, red = Items.REDSTONE, cu = Items.COPPER_INGOT, nug = Items.IRON_NUGGET;
        Item hollow = ModBlocks.HOLLOW_STONE.get().asItem();
        String problem = checkShaped(helper, "machine_block", ModBlocks.MACHINE_BLOCK.get().asItem(), 1,
                iron, red, iron,
                cu, hollow, cu,
                iron, red, iron);
        if (problem == null) problem = checkShaped(helper, "iron_frame", ModItems.IRON_FRAME.get(), 4,
                iron, nug, iron,
                nug, null, nug,
                iron, nug, iron);
        Item cob = ModBlocks.COBBLED_ODDSTONE.get().asItem(), odd = ModBlocks.ODDSTONE.get().asItem();
        if (problem == null) problem = checkShaped(helper, "oddstone_repulser", ModItems.ODDSTONE_REPULSER.get(), 1,
                cob, cob, cob,
                red, Items.PISTON, odd,
                cob, cob, cob);
        if (problem == null) problem = checkShaped(helper, "oddstone_attractor", ModItems.ODDSTONE_ATTRACTOR.get(), 1,
                cob, cob, cob,
                red, Items.STICKY_PISTON, odd,
                cob, cob, cob);
        Item ball = Items.SLIME_BALL;
        if (problem == null) problem = checkShaped(helper, "odd_slime", ModItems.ODD_SLIME.get(), 1,
                ball, ball, ball,
                ball, odd, ball,
                ball, ball, ball);
        Item cuNug = ModItems.COPPER_NUGGET.get();
        if (problem == null) problem = checkShaped(helper, "copper_frame", ModItems.COPPER_FRAME.get(), 4,
                cu, cuNug, cu,
                cuNug, null, cuNug,
                cu, cuNug, cu);
        if (problem == null) problem = checkShaped(helper, "copper_ingot_from_nuggets", cu, 1,
                cuNug, cuNug, cuNug,
                cuNug, cuNug, cuNug,
                cuNug, cuNug, cuNug);
        if (problem == null) {
            Recipe<?> r = helper.getLevel().getRecipeManager()
                    .byKey(new ResourceLocation(Ideomod.MOD_ID, "copper_nugget")).orElse(null);
            ItemStack out = r == null ? ItemStack.EMPTY : r.getResultItem(helper.getLevel().registryAccess());
            if (r == null || r.getIngredients().size() != 1 || !r.getIngredients().get(0).test(new ItemStack(cu))
                    || !out.is(cuNug) || out.getCount() != 9)
                problem = "copper_nugget: expected 1 copper ingot -> 9 copper nuggets, got " + out;
        }
        if (problem != null) helper.fail(problem);
        else helper.succeed();
    }

    private static String checkShaped(GameTestHelper helper, String id, Item result, int count, Item... slots) {
        Recipe<?> recipe = helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation(Ideomod.MOD_ID, id)).orElse(null);
        if (!(recipe instanceof ShapedRecipe shaped)) return id + ": no shaped recipe loaded";
        if (shaped.getWidth() != 3 || shaped.getHeight() != 3) return id + ": not 3x3";
        ItemStack out = shaped.getResultItem(helper.getLevel().registryAccess());
        if (!out.is(result) || out.getCount() != count) return id + ": makes " + out + ", expected " + count + " " + result;
        for (int i = 0; i < 9; i++) {
            Ingredient ing = shaped.getIngredients().get(i);
            boolean ok = slots[i] == null ? ing.isEmpty() : ing.test(new ItemStack(slots[i]));
            if (!ok) return id + ": slot " + i + " should be " + slots[i];
        }
        return null;
    }

    private static boolean isPoweredPulser(GameTestHelper helper, int ringIndex, int dz) {
        BlockState s = helper.getBlockState(new BlockPos(RING[ringIndex][0], 1, RING[ringIndex][1] + dz));
        return s.is(ModBlocks.MACHINE_PULSER.get()) && s.getValue(MachinePulserBlock.POWERED);
    }

    private static Direction directionTo(BlockPos from, BlockPos to) {
        return Direction.fromDelta(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    // ==================== Grabbers grab like slime ====================

    /**
     * A switched-on Slime Grabber holds a machine block of another type (here a Machine Piston),
     * as slime does; switched off it doesn't. A vanilla piston pushes the Grabber east; the
     * Machine Piston beside it (north) comes along only when the Grabber is on.
     *
     *   z=1/4: [Machine Piston]          (north of the Grabber)
     *   z=2/5: [piston ->] [Grabber]
     */
    @GameTest(template = EMPTY)
    public static void grabberHoldsOtherMachineBlocksWhenOn(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) for (int z = 0; z <= 5; z++) helper.setBlock(x, 1, z, Blocks.AIR);
        boolean[] on = {true, false};
        for (int lane = 0; lane < 2; lane++) {
            int z = 2 + lane * 3; // lanes apart, so one Grabber can't reach the other lane
            helper.setBlock(1, 1, z, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
            helper.setBlock(2, 1, z, ModBlocks.MACHINE_SLIME_GRABBER.get().defaultBlockState()
                    .setValue(com.ideomancer.ideomod.block.machine.MachineGrabberBlock.POWERED, on[lane]));
            helper.setBlock(2, 1, z - 1, ModBlocks.MACHINE_PISTON.get());
        }
        helper.runAfterDelay(2, () -> { helper.setBlock(0, 1, 2, Blocks.REDSTONE_BLOCK); helper.setBlock(0, 1, 5, Blocks.REDSTONE_BLOCK); });
        helper.runAfterDelay(8, () -> {
            boolean carried = helper.getBlockState(new BlockPos(3, 1, 1)).is(ModBlocks.MACHINE_PISTON.get());
            boolean leftBehind = helper.getBlockState(new BlockPos(2, 1, 4)).is(ModBlocks.MACHINE_PISTON.get());
            if (!carried) helper.fail("a switched-on Grabber did not take the Machine Piston beside it along");
            else if (!leftBehind) helper.fail("a switched-off Grabber took the Machine Piston along");
            else helper.succeed();
        });
    }

    /**
     * A switched-on Grabber is grabbed like slime: a moving machine block does not drag it along
     * (Rule 5, as Rule 2 for real slime). A vanilla piston pushes a Machine Piston west; a
     * switched-on Grabber sits on top of it, and a Machine Sticky Piston behind the Grabber (east).
     * The Machine Piston moves alone; the Grabber and the Sticky Piston stay where they are.
     *
     *   y=2:            [Grabber on] [Sticky Piston]
     *   y=1:  [<- ...]  [Machine Piston] [vanilla piston facing west]
     */
    @GameTest(template = EMPTY)
    public static void pushedMachineBlockLeavesSwitchedOnGrabber(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) for (int y = 1; y <= 3; y++) helper.setBlock(x, y, 2, Blocks.AIR);
        helper.setBlock(4, 1, 2, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST));
        helper.setBlock(3, 1, 2, ModBlocks.MACHINE_PISTON.get());
        helper.setBlock(3, 2, 2, ModBlocks.MACHINE_SLIME_GRABBER.get().defaultBlockState()
                .setValue(com.ideomancer.ideomod.block.machine.MachineGrabberBlock.POWERED, true));
        helper.setBlock(4, 2, 2, ModBlocks.MACHINE_STICKY_PISTON.get());
        helper.runAfterDelay(2, () -> helper.setBlock(5, 1, 2, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(8, () -> {
            boolean moved = helper.getBlockState(new BlockPos(2, 1, 2)).is(ModBlocks.MACHINE_PISTON.get());
            boolean grabberStayed = helper.getBlockState(new BlockPos(3, 2, 2)).is(ModBlocks.MACHINE_SLIME_GRABBER.get());
            boolean stickyStayed = helper.getBlockState(new BlockPos(4, 2, 2)).is(ModBlocks.MACHINE_STICKY_PISTON.get());
            if (!moved) helper.fail("the Machine Piston was not pushed");
            else if (!grabberStayed) helper.fail("the pushed Machine Piston dragged the switched-on Grabber along");
            else if (!stickyStayed) helper.fail("the Sticky Piston behind the Grabber moved");
            else helper.succeed();
        });
    }

    // ========================================================================
    // MACHINE STORAGE
    // ========================================================================

    private static com.ideomancer.ideomod.block.machine.MachineStorageBlockEntity placeStorage(GameTestHelper helper, BlockPos pos, int cobble) {
        helper.setBlock(pos, ModBlocks.MACHINE_BLOCK.get());
        com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity base = (com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity) helper.getBlockEntity(pos);
        base.setItem(0, new ItemStack(Items.BARREL));
        com.ideomancer.ideomod.block.machine.MachineStorageBlockEntity storage = (com.ideomancer.ideomod.block.machine.MachineStorageBlockEntity) helper.getBlockEntity(pos);
        if (cobble > 0) storage.setItem(1, new ItemStack(Items.COBBLESTONE, cobble));
        return storage;
    }

    private static int cobbleIn(net.minecraft.world.Container container) {
        return container.countItem(Items.COBBLESTONE);
    }

    private static net.minecraft.world.level.block.entity.ChestBlockEntity placeChest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        return (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getBlockEntity(pos);
    }

    /** A barrel in a Machine Base makes a Machine Storage; a chest does not. */
    @GameTest(template = EMPTY)
    public static void barrelMakesAMachineStorage(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 3), b = new BlockPos(4, 2, 3);
        placeStorage(helper, a, 0);
        helper.setBlock(b, ModBlocks.MACHINE_BLOCK.get());
        com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity base = (com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity) helper.getBlockEntity(b);
        base.setItem(0, new ItemStack(Items.CHEST));
        if (!helper.getBlockState(a).is(ModBlocks.MACHINE_STORAGE.get())) helper.fail("a barrel did not make a Machine Storage", a);
        else if (helper.getBlockState(b).is(ModBlocks.MACHINE_STORAGE.get())) helper.fail("a chest made a Machine Storage; only a barrel should", b);
        else helper.succeed();
    }

    /** Each time the signal turns on, one item goes out; holding the signal sends no more. */
    @GameTest(template = EMPTY)
    public static void storagePushesOneItemPerPulse(GameTestHelper helper) {
        BlockPos signal = new BlockPos(1, 2, 3);
        var storage = placeStorage(helper, new BlockPos(2, 2, 3), 5);
        var chest = placeChest(helper, new BlockPos(3, 2, 3));
        helper.runAfterDelay(3, () -> helper.setBlock(signal, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(14, () -> {
            if (cobbleIn(chest) != 1) helper.fail("held signal: expected 1 item in the chest, found " + cobbleIn(chest));
            helper.setBlock(signal, Blocks.AIR);
        });
        helper.runAfterDelay(18, () -> helper.setBlock(signal, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(26, () -> {
            if (cobbleIn(chest) != 2) helper.fail("second pulse: expected 2 items in the chest, found " + cobbleIn(chest));
            else if (cobbleIn(storage) != 3) helper.fail("expected 3 items left, found " + cobbleIn(storage));
            else helper.succeed();
        });
    }

    /** With two outputs it takes turns. */
    @GameTest(template = EMPTY)
    public static void storageTakesTurnsBetweenOutputs(GameTestHelper helper) {
        BlockPos signal = new BlockPos(1, 2, 3);
        placeStorage(helper, new BlockPos(2, 2, 3), 5);
        var east = placeChest(helper, new BlockPos(3, 2, 3));
        var north = placeChest(helper, new BlockPos(2, 2, 2));
        helper.runAfterDelay(3, () -> helper.setBlock(signal, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(7, () -> helper.setBlock(signal, Blocks.AIR));
        helper.runAfterDelay(10, () -> helper.setBlock(signal, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(16, () -> {
            if (cobbleIn(east) == 1 && cobbleIn(north) == 1) helper.succeed();
            else helper.fail("expected one item each, found east " + cobbleIn(east) + ", north " + cobbleIn(north));
        });
    }

    /** It never pushes to a face that fed it, and forgets that face once the feeder is gone. */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void storageNeverPushesBackToAFeeder(GameTestHelper helper) {
        BlockPos signal = new BlockPos(1, 2, 3), side = new BlockPos(3, 2, 3);
        var storage = placeStorage(helper, new BlockPos(2, 2, 3), 0);
        helper.setBlock(side, ModBlocks.MACHINE_BLOCK.get());
        com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity base = (com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity) helper.getBlockEntity(side);
        base.setItem(0, new ItemStack(Items.HOPPER));
        com.ideomancer.ideomod.block.machine.MachinePipeBlockEntity pipe = (com.ideomancer.ideomod.block.machine.MachinePipeBlockEntity) helper.getBlockEntity(side);
        pipe.setTransitItem(new ItemStack(Items.COBBLESTONE, 2));
        pipe.onItemReceived(Direction.EAST);
        helper.runAfterDelay(30, () -> {
            if (cobbleIn(storage) != 2) helper.fail("the pipe did not fill the Storage: " + cobbleIn(storage));
            helper.setBlock(signal, Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(50, () -> {
            if (cobbleIn(storage) != 2) helper.fail("the Storage pushed back into the pipe that fed it");
            helper.setBlock(signal, Blocks.AIR);
            helper.setBlock(side, Blocks.AIR);
        });
        helper.runAfterDelay(54, () -> placeChest(helper, side));
        helper.runAfterDelay(58, () -> helper.setBlock(signal, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(66, () -> {
            net.minecraft.world.level.block.entity.ChestBlockEntity chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getBlockEntity(side);
            if (cobbleIn(chest) == 1) helper.succeed();
            else helper.fail("the feeder's face was not forgotten after the pipe went");
        });
    }

    /** One Storage pushing into another sets the direction: the second never pushes back. */
    @GameTest(template = EMPTY)
    public static void storageFedByStorageDoesNotPushBack(GameTestHelper helper) {
        BlockPos forA = new BlockPos(1, 2, 3), forB = new BlockPos(4, 2, 3);
        var a = placeStorage(helper, new BlockPos(2, 2, 3), 5);
        var b = placeStorage(helper, new BlockPos(3, 2, 3), 0);
        helper.runAfterDelay(3, () -> helper.setBlock(forA, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(9, () -> helper.setBlock(forB, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(16, () -> {
            if (cobbleIn(b) == 1 && cobbleIn(a) == 4) helper.succeed();
            else helper.fail("expected 4 and 1, found " + cobbleIn(a) + " and " + cobbleIn(b));
        });
    }

    /** A piston moves it with its contents. */
    @GameTest(template = EMPTY)
    public static void storageMovesWithItsContents(GameTestHelper helper) {
        BlockPos from = new BlockPos(2, 2, 3), to = new BlockPos(2, 3, 3);
        placeStorage(helper, from, 7);
        helper.setBlock(2, 1, 3, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP));
        helper.runAfterDelay(2, () -> helper.setBlock(2, 0, 3, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(12, () -> {
            if (!helper.getBlockState(to).is(ModBlocks.MACHINE_STORAGE.get())) { helper.fail("the Storage did not move", to); return; }
            com.ideomancer.ideomod.block.machine.MachineStorageBlockEntity moved = (com.ideomancer.ideomod.block.machine.MachineStorageBlockEntity) helper.getBlockEntity(to);
            if (cobbleIn(moved) == 7) helper.succeed();
            else helper.fail("the contents did not move with it: " + cobbleIn(moved));
        });
    }

    /**
     * Saved and loaded mid-move (a world closed and reopened while it slides): it still lands with its
     * contents. The test does what a reload does to the moving block: saves it, forgets the in-memory
     * copy, and loads a fresh moving block from the save.
     */
    @GameTest(template = EMPTY)
    public static void storageReloadedMidMoveKeepsItsContents(GameTestHelper helper) {
        BlockPos from = new BlockPos(2, 2, 3), to = new BlockPos(2, 3, 3);
        placeStorage(helper, from, 7);
        helper.setBlock(2, 1, 3, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP));
        helper.runAfterDelay(2, () -> helper.setBlock(2, 0, 3, Blocks.REDSTONE_BLOCK));
        boolean[] reloaded = {false};
        helper.onEachTick(() -> {
            if (reloaded[0]) return;
            net.minecraft.server.level.ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(to);
            if (!(level.getBlockEntity(abs) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity moving)) return;
            reloaded[0] = true;
            net.minecraft.nbt.CompoundTag saved = moving.saveWithFullMetadata();
            com.ideomancer.ideomod.block.movement.MovingBlockEntityData.retrieve(level.dimension().location().toString(), abs);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(abs);
            level.removeBlockEntity(abs);
            net.minecraft.world.level.block.entity.BlockEntity loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(abs, state, saved);
            if (loaded == null) { helper.fail("the moving block could not be loaded from its save", to); return; }
            level.setBlockEntity(loaded);
        });
        helper.runAfterDelay(14, () -> {
            if (!reloaded[0]) { helper.fail("never caught the Storage mid-move", to); return; }
            if (!helper.getBlockState(to).is(ModBlocks.MACHINE_STORAGE.get())) { helper.fail("the Storage did not land as a Storage", to); return; }
            com.ideomancer.ideomod.block.machine.MachineStorageBlockEntity moved = (com.ideomancer.ideomod.block.machine.MachineStorageBlockEntity) helper.getBlockEntity(to);
            if (cobbleIn(moved) == 7 && moved.getItem(0).is(Items.BARREL)) helper.succeed();
            else helper.fail("reloaded mid-move, it landed with " + cobbleIn(moved) + " cobblestone and " + moved.getItem(0) + " in its slot");
        });
    }

    /** Broken, it spills what it holds. */
    @GameTest(template = EMPTY)
    public static void brokenStorageSpillsItsContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 3);
        placeStorage(helper, pos, 5);
        helper.runAfterDelay(2, () -> helper.destroyBlock(pos));
        helper.runAfterDelay(4, () -> {
            helper.assertItemEntityPresent(Items.COBBLESTONE, pos, 3.0);
            helper.succeed();
        });
    }

    /** Taking the barrel out makes it a Machine Base again and drops what it held. */
    @GameTest(template = EMPTY)
    public static void takingTheBarrelOutDropsTheContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 3);
        var storage = placeStorage(helper, pos, 5);
        helper.runAfterDelay(2, () -> storage.setItem(0, ItemStack.EMPTY));
        helper.runAfterDelay(4, () -> {
            helper.assertBlockPresent(ModBlocks.MACHINE_BLOCK.get(), pos);
            helper.assertItemEntityPresent(Items.COBBLESTONE, pos, 3.0);
            helper.succeed();
        });
    }

    // ========================================================================
    // PORTALS: frames are Cobbled Oddstone (the Ideomancer, 2026-11-02)
    // ========================================================================

    /** An upright 5x5 ring (3x3 opening) in the x-y plane at z = 3: frame block on the edges, corner at the corners. */
    // Tests that build a portal run in their own batch ("portals"). Tests sit side by side in a grid whose
    // order changes between runs, and a neighbour moving Cobbled Oddstone drags any Cobbled Oddstone in line
    // with it up to 12 blocks away (sympathetic movement), which pulled portal frames apart at random.
    private static void buildPortalRing(GameTestHelper helper, Block frame, Block corner) {
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 5; y++) {
                boolean edgeX = x == 1 || x == 5, edgeY = y == 1 || y == 5;
                if (edgeX && edgeY) helper.setBlock(x, y, 3, corner);
                else if (edgeX || edgeY) helper.setBlock(x, y, 3, frame);
                else helper.setBlock(x, y, 3, Blocks.AIR);
            }
        }
    }

    private static boolean lightPortal(GameTestHelper helper, int x, int y, int z) {
        return com.ideomancer.ideomod.block.portal.OddstonePortalHandler.tryActivate(
                helper.getLevel(), helper.absolutePos(new BlockPos(x, y, z)));
    }

    /** A Cobbled Oddstone ring with mineral corners lights, and breaking one frame block closes it. */
    @GameTest(template = EMPTY, batch = "portals")
    public static void cobbledOddstoneRingLightsAndClosesWhenBroken(GameTestHelper helper) {
        buildPortalRing(helper, ModBlocks.COBBLED_ODDSTONE.get(), Blocks.IRON_BLOCK);
        if (!lightPortal(helper, 2, 1, 3)) {
            helper.fail("a Cobbled Oddstone ring with iron corners did not light", new BlockPos(2, 1, 3));
            return;
        }
        if (!helper.getBlockState(new BlockPos(3, 3, 3)).is(ModBlocks.ODDSTONE_PORTAL.get())) {
            helper.fail("the opening did not fill with portal", new BlockPos(3, 3, 3));
            return;
        }
        helper.setBlock(2, 1, 3, Blocks.AIR);
        helper.runAfterDelay(4, () -> {
            if (helper.getBlockState(new BlockPos(3, 3, 3)).is(ModBlocks.ODDSTONE_PORTAL.get())) {
                helper.fail("the portal stayed lit after a frame block was broken", new BlockPos(3, 3, 3));
            } else {
                helper.succeed();
            }
        });
    }

    /** The same ring as buildPortalRing, at any z. */
    private static void buildPortalRingAt(GameTestHelper helper, int z, Block frame, Block corner) {
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 5; y++) {
                boolean edgeX = x == 1 || x == 5, edgeY = y == 1 || y == 5;
                if (edgeX && edgeY) helper.setBlock(x, y, z, corner);
                else if (edgeX || edgeY) helper.setBlock(x, y, z, frame);
                else helper.setBlock(x, y, z, Blocks.AIR);
            }
        }
    }

    /** Whether an entity is in the opening of the far ring (z = 5) of a linked pair. */
    private static boolean atFarPortal(GameTestHelper helper, net.minecraft.world.entity.Entity entity) {
        BlockPos at = entity.blockPosition();
        BlockPos a = helper.absolutePos(new BlockPos(2, 2, 5)), b = helper.absolutePos(new BlockPos(4, 4, 5));
        return at.getX() >= Math.min(a.getX(), b.getX()) && at.getX() <= Math.max(a.getX(), b.getX())
                && at.getY() >= a.getY() && at.getY() <= b.getY()
                && at.getZ() >= Math.min(a.getZ(), b.getZ()) && at.getZ() <= Math.max(a.getZ(), b.getZ());
    }

    /** Two rings with the same corners, at z = 1 and z = 5: a linked pair. */
    private static boolean buildLinkedPair(GameTestHelper helper, Block corner) {
        buildPortalRingAt(helper, 1, ModBlocks.COBBLED_ODDSTONE.get(), corner);
        buildPortalRingAt(helper, 5, ModBlocks.COBBLED_ODDSTONE.get(), corner);
        return lightPortal(helper, 2, 1, 1) && lightPortal(helper, 2, 1, 5);
    }

    /**
     * Copper corners count as copper whatever their oxidation: a corner that weathers keeps the portal
     * open, and a ring with oxidized (and waxed) corners links to one with fresh copper corners.
     */
    @GameTest(template = EMPTY, batch = "portals")
    public static void oxidisedCopperCornersStillCountAsCopper(GameTestHelper helper) {
        buildPortalRingAt(helper, 1, ModBlocks.COBBLED_ODDSTONE.get(), Blocks.COPPER_BLOCK);
        buildPortalRingAt(helper, 5, ModBlocks.COBBLED_ODDSTONE.get(), Blocks.OXIDIZED_COPPER);
        helper.setBlock(1, 1, 5, Blocks.WAXED_WEATHERED_COPPER);
        if (!lightPortal(helper, 2, 1, 1)) { helper.fail("the copper ring did not light"); return; }
        if (!lightPortal(helper, 2, 1, 5)) { helper.fail("the oxidized ring did not light"); return; }
        helper.setBlock(5, 5, 1, Blocks.EXPOSED_COPPER);   // a corner of the first ring weathers
        helper.runAfterDelay(4, () -> {
            if (!helper.getBlockState(new BlockPos(3, 3, 1)).is(ModBlocks.ODDSTONE_PORTAL.get())) {
                helper.fail("the portal closed when a copper corner oxidized", new BlockPos(3, 3, 1)); return;
            }
            var data = com.ideomancer.ideomod.block.portal.OddstonePortalSavedData.get(helper.getLevel());
            var near = data.getPortal(helper.absolutePos(new BlockPos(1, 1, 1)));
            if (near == null || !near.isLinked()) helper.fail("the fresh and oxidized copper rings did not link");
            else helper.succeed();
        });
    }

    /** Oddstone placed against a portal's face isn't part of its frame: removing it leaves the portal open. */
    @GameTest(template = EMPTY, batch = "portals")
    public static void oddstoneAgainstThePortalFaceIsNotFrame(GameTestHelper helper) {
        // Coal corners: no other test uses them. Tests run side by side, and a portal with the same
        // corners as another test's would link to it and be affected when that one closes.
        buildPortalRingAt(helper, 2, ModBlocks.COBBLED_ODDSTONE.get(), Blocks.COAL_BLOCK);
        if (!lightPortal(helper, 2, 1, 2)) { helper.fail("the ring did not light"); return; }
        helper.setBlock(3, 3, 3, ModBlocks.COBBLED_ODDSTONE.get());
        helper.runAfterDelay(2, () -> helper.setBlock(3, 3, 3, Blocks.AIR));
        helper.runAfterDelay(6, () -> {
            if (helper.getBlockState(new BlockPos(3, 3, 2)).is(ModBlocks.ODDSTONE_PORTAL.get())) helper.succeed();
            else helper.fail("removing Oddstone from the portal's face closed it", new BlockPos(3, 3, 2));
        });
    }

    /**
     * Like a nether portal, a dropped item goes through to the linked portal (arriving as a fresh
     * copy with the same UUID); and it stays there after the cooldown instead of bouncing back,
     * because staying inside keeps the cooldown up.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "portals")
    public static void itemGoesThroughALinkedPortalAndStays(GameTestHelper helper) {
        if (!buildLinkedPair(helper, Blocks.EMERALD_BLOCK)) { helper.fail("the linked pair did not light"); return; }
        net.minecraft.world.entity.item.ItemEntity item = helper.spawnItem(Items.DIAMOND, 3.5F, 2.2F, 1.5F);
        item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        java.util.UUID id = item.getUUID();
        helper.runAfterDelay(5, () -> {
            net.minecraft.world.entity.Entity e = helper.getLevel().getEntity(id);
            if (e == null) helper.fail("the item was lost");
            else if (!atFarPortal(helper, e)) helper.fail("the item did not go through to the linked portal");
        });
        helper.runAfterDelay(120, () -> {
            net.minecraft.world.entity.Entity e = helper.getLevel().getEntity(id);
            if (e == null) helper.fail("the item was lost");
            else if (!atFarPortal(helper, e)) helper.fail("the item bounced back after the cooldown");
            else helper.succeed();
        });
    }

    /** Like a nether portal, a mob goes through to the linked portal. */
    @GameTest(template = EMPTY, batch = "portals")
    public static void mobGoesThroughALinkedPortal(GameTestHelper helper) {
        if (!buildLinkedPair(helper, Blocks.DIAMOND_BLOCK)) { helper.fail("the linked pair did not light"); return; }
        net.minecraft.world.entity.Mob pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, new net.minecraft.world.phys.Vec3(3.5, 2, 1.5));
        java.util.UUID id = pig.getUUID();
        helper.runAfterDelay(5, () -> {
            net.minecraft.world.entity.Entity e = helper.getLevel().getEntity(id);
            if (e != null && atFarPortal(helper, e)) helper.succeed();
            else helper.fail("the pig did not go through to the linked portal");
        });
    }

    /**
     * The ender pearl that lights a portal is used up, even when that portal links at once and the
     * pearl is still inside its opening: it must not go through and turn up at the partner (where it
     * would land and teleport its thrower). Ring A is lit first; a pearl dropped just inside ring B's
     * opening onto its bottom frame lights B, and that pearl (same UUID: a portal copy keeps it) must
     * not exist anywhere afterwards.
     */
    @GameTest(template = EMPTY, batch = "portals")
    public static void pearlThatLightsAPortalDoesNotGoThrough(GameTestHelper helper) {
        buildPortalRingAt(helper, 1, ModBlocks.COBBLED_ODDSTONE.get(), Blocks.AMETHYST_BLOCK);
        buildPortalRingAt(helper, 5, ModBlocks.COBBLED_ODDSTONE.get(), Blocks.AMETHYST_BLOCK);
        if (!lightPortal(helper, 2, 1, 1)) { helper.fail("ring A did not light"); return; }
        net.minecraft.world.entity.projectile.ThrownEnderpearl pearl =
                new net.minecraft.world.entity.projectile.ThrownEnderpearl(net.minecraft.world.entity.EntityType.ENDER_PEARL, helper.getLevel());
        net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2.3, 5.5));
        pearl.moveTo(at.x, at.y, at.z, 0, 0);
        pearl.setDeltaMovement(0, -0.5, 0);
        helper.getLevel().addFreshEntity(pearl);
        java.util.UUID id = pearl.getUUID();
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (++ticks[0] < 2) return;
            net.minecraft.world.entity.Entity e = helper.getLevel().getEntity(id);
            if (e != null && !e.isRemoved()) {
                helper.fail("the pearl that lit ring B went on (at " + e.blockPosition() + ") instead of being used up");
            } else if (ticks[0] >= 10) {
                if (!helper.getBlockState(new BlockPos(3, 3, 5)).is(ModBlocks.ODDSTONE_PORTAL.get())) helper.fail("the pearl did not light ring B");
                else helper.succeed();
            }
        });
    }

    /** An empty boat goes through too, arriving as a fresh copy (so clients don't slide it the whole way). */
    @GameTest(template = EMPTY, batch = "portals")
    public static void boatGoesThroughALinkedPortal(GameTestHelper helper) {
        if (!buildLinkedPair(helper, Blocks.GOLD_BLOCK)) { helper.fail("the linked pair did not light"); return; }
        net.minecraft.world.entity.Entity boat = helper.spawn(net.minecraft.world.entity.EntityType.BOAT, new net.minecraft.world.phys.Vec3(3.5, 2, 1.5));
        java.util.UUID id = boat.getUUID();
        helper.runAfterDelay(5, () -> {
            net.minecraft.world.entity.Entity e = helper.getLevel().getEntity(id);
            if (e == null) helper.fail("the boat was lost");
            else if (!atFarPortal(helper, e)) helper.fail("the boat did not go through to the linked portal");
            else if (e == boat) helper.fail("the boat was moved, not put down fresh; clients would slide it");
            else helper.succeed();
        });
    }

    /**
     * An unlinked portal scatters an item far away, but does not destroy it (the damage is for living things):
     * the item is moved on, as a fresh copy in the far chunk, not killed. (The far chunk generates slowly in
     * the test server, so this checks how the item left rather than finding where it landed.)
     */
    @GameTest(template = EMPTY, batch = "portals")
    public static void unlinkedPortalScattersAnItemWithoutDestroyingIt(GameTestHelper helper) {
        buildPortalRing(helper, ModBlocks.COBBLED_ODDSTONE.get(), Blocks.LAPIS_BLOCK);
        if (!lightPortal(helper, 2, 1, 3)) { helper.fail("the portal did not light"); return; }
        net.minecraft.world.entity.item.ItemEntity item = helper.spawnItem(Items.DIAMOND, 3.5F, 2.2F, 3.5F);
        helper.runAfterDelay(5, () -> {
            var reason = item.getRemovalReason();
            if (reason == null) helper.fail("the item did not go through the portal");
            else if (reason != net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION) helper.fail("the item was destroyed (" + reason + ")");
            else helper.succeed();
        });
    }

    /**
     * An unlinked portal throws things onto the surface: the spot it picks (hundreds of blocks away,
     * in a chunk nobody has loaded) has open space at the landing block and something solid under it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void unlinkedPortalLandsOnTheSurface(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos target = com.ideomancer.ideomod.block.portal.OddstonePortalBlock.findScatterTarget(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        if (target == null) { helper.fail("no landing spot found in the overworld"); return; }
        net.minecraft.world.level.block.state.BlockState at = level.getBlockState(target), below = level.getBlockState(target.below());
        if (target.getY() <= level.getMinBuildHeight() + 1) helper.fail("landed at the bottom of the world: " + target);
        else if (at.blocksMotion()) helper.fail("landed inside " + at + " at " + target);
        else if (!below.blocksMotion() && below.getFluidState().isEmpty()) helper.fail("landed in the air above " + below + " at " + target);
        else helper.succeed();
    }

    /**
     * Unlinked throws are random: 24 throws from one spot land at 24 different places, every one
     * scatter_distance_min to _max blocks away, spread over at least three of the four compass
     * quarters and over a wide band of distances.
     */
    // Own batch: these generate far-off chunks, and kept apart they leave the other tests' layout as it was.
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "portal_throws")
    public static void unlinkedPortalThrowsAreRandom(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(2, 2, 2));
        int min = com.ideomancer.ideomod.config.IdeomodConfig.getScatterDistanceMin();
        int max = com.ideomancer.ideomod.config.IdeomodConfig.getScatterDistanceMax();
        java.util.Set<BlockPos> spots = new java.util.HashSet<>();
        java.util.Set<Integer> quarters = new java.util.HashSet<>();
        double near = Double.MAX_VALUE, far = 0;
        for (int i = 0; i < 24; i++) {
            BlockPos t = com.ideomancer.ideomod.block.portal.OddstonePortalBlock.findScatterTarget(level, from);
            if (t == null) { helper.fail("throw " + i + " found no landing spot"); return; }
            double dx = t.getX() - from.getX(), dz = t.getZ() - from.getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d < min - 2 || d > max + 2) { helper.fail("throw " + i + " landed " + (int) d + " blocks away: " + t); return; }
            near = Math.min(near, d); far = Math.max(far, d);
            spots.add(t);
            quarters.add((dx >= 0 ? 1 : 0) + (dz >= 0 ? 2 : 0));
        }
        if (spots.size() < 24) helper.fail("only " + spots.size() + " different landing spots in 24 throws");
        else if (quarters.size() < 3) helper.fail("throws went into only " + quarters.size() + " compass quarters");
        else if (far - near < (max - min) / 3.0) helper.fail("distances only spread from " + (int) near + " to " + (int) far);
        else helper.succeed();
    }

    /**
     * In the Nether an unlinked throw lands at a random place to stand under the roof (solid floor,
     * two blocks of air), not on the bedrock roof every time, and at varying heights.
     */
    // Own batch: these generate far-off chunks, and kept apart they leave the other tests' layout as it was.
    @GameTest(template = EMPTY, timeoutTicks = 400, batch = "portal_throws")
    public static void netherThrowsLandUnderTheRoof(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether == null) { helper.fail("no Nether on this server"); return; }
        BlockPos from = new BlockPos(0, 64, 0);
        int roofY = nether.getMinBuildHeight() + nether.dimensionType().logicalHeight();
        int under = 0;
        java.util.Set<Integer> heights = new java.util.HashSet<>();
        for (int i = 0; i < 12; i++) {
            BlockPos t = com.ideomancer.ideomod.block.portal.OddstonePortalBlock.findScatterTarget(nether, from);
            if (t == null) { helper.fail("Nether throw " + i + " found no landing spot"); return; }
            if (t.getY() >= roofY) continue; // the roof: allowed only as a fallback
            BlockPos below = t.below();
            if (!nether.getBlockState(t).isAir() || !nether.getBlockState(t.above()).isAir()
                    || !nether.getBlockState(below).isFaceSturdy(nether, below, Direction.UP)) {
                helper.fail("Nether throw " + i + " landed where nobody can stand: " + t);
                return;
            }
            under++;
            heights.add(t.getY());
        }
        if (under < 10) helper.fail("only " + under + " of 12 Nether throws landed under the roof");
        else if (heights.size() < 3) helper.fail("Nether throws all landed at " + heights.size() + " height(s): " + heights);
        else helper.succeed();
    }

    /** A ring of plain Oddstone no longer forms a portal. */
    @GameTest(template = EMPTY, batch = "portals")
    public static void plainOddstoneRingDoesNotLight(GameTestHelper helper) {
        buildPortalRing(helper, ModBlocks.ODDSTONE.get(), Blocks.GOLD_BLOCK);
        if (lightPortal(helper, 2, 1, 3)
                || helper.getBlockState(new BlockPos(3, 3, 3)).is(ModBlocks.ODDSTONE_PORTAL.get())) {
            helper.fail("a plain Oddstone ring lit; frames should need Cobbled Oddstone", new BlockPos(3, 3, 3));
        } else {
            helper.succeed();
        }
    }

    /**
     * Every crafting and smelting recipe of this mod that belongs in the recipe book has an unlock
     * advancement (recipes/<name>) whose reward is that recipe, so it appears in the book.
     */
    @GameTest(template = EMPTY)
    public static void everyBookRecipeHasAnUnlock(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (net.minecraft.world.item.crafting.Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            net.minecraft.resources.ResourceLocation id = recipe.getId();
            if (!id.getNamespace().equals(Ideomod.MOD_ID) || recipe.isSpecial()) continue;
            var type = recipe.getType();
            if (type != net.minecraft.world.item.crafting.RecipeType.CRAFTING
                    && type != net.minecraft.world.item.crafting.RecipeType.SMELTING) continue;
            var adv = server.getAdvancements().getAdvancement(
                    new net.minecraft.resources.ResourceLocation(Ideomod.MOD_ID, "recipes/" + id.getPath()));
            if (adv == null || !java.util.Arrays.asList(adv.getRewards().getRecipes()).contains(id)) {
                missing.add(id.getPath());
            }
        }
        if (missing.isEmpty()) helper.succeed();
        else helper.fail("recipes with no recipe-book unlock: " + missing);
    }

    // ========================================================================
    // MACHINE SEAT
    // ========================================================================

    private static void placeSeat(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.MACHINE_BLOCK.get());
        ((com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity) helper.getBlockEntity(pos)).setItem(0, new ItemStack(Items.OAK_BOAT));
    }

    /**
     * Sits a stand-in player in the Seat at pos, facing the given way. Its aiStep does nothing:
     * a rider is ticked by its seat even after the test is over, and helper.makeMockPlayer()
     * would then pick up items from the tests around it (it took the diamond arriving through
     * a neighbouring portal test). It also keeps the keys set below from fading each tick.
     */
    private static Player sitIn(GameTestHelper helper, BlockPos pos, Direction facing) {
        Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0.0f,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "seat-test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return true; }
            @Override public void aiStep() {}
        };
        player.setYRot(facing.toYRot());
        var seat = com.ideomancer.ideomod.block.machine.MachineSeatEntity.create(helper.getLevel(), helper.absolutePos(pos));
        helper.getLevel().addFreshEntity(seat);
        if (!player.startRiding(seat)) helper.fail("the player could not sit in the Seat", pos);
        return player;
    }

    /** Redstone lamps on the four sides and under the Seat. */
    private static void lampsAround(GameTestHelper helper, BlockPos seat) {
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
            helper.setBlock(seat.relative(d), Blocks.REDSTONE_LAMP);
        }
    }

    private static boolean lit(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT);
    }

    /** Any boat or raft makes a Machine Seat; a chest boat does not. */
    @GameTest(template = EMPTY)
    public static void boatMakesAMachineSeat(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 2, 3), b = new BlockPos(3, 2, 3), c = new BlockPos(5, 2, 3);
        helper.setBlock(a, ModBlocks.MACHINE_BLOCK.get());
        helper.setBlock(b, ModBlocks.MACHINE_BLOCK.get());
        helper.setBlock(c, ModBlocks.MACHINE_BLOCK.get());
        ((com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity) helper.getBlockEntity(a)).setItem(0, new ItemStack(Items.SPRUCE_BOAT));
        ((com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity) helper.getBlockEntity(b)).setItem(0, new ItemStack(Items.BAMBOO_RAFT));
        ((com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity) helper.getBlockEntity(c)).setItem(0, new ItemStack(Items.OAK_CHEST_BOAT));
        if (!helper.getBlockState(a).is(ModBlocks.MACHINE_SEAT.get())) helper.fail("a boat did not make a Machine Seat", a);
        else if (!helper.getBlockState(b).is(ModBlocks.MACHINE_SEAT.get())) helper.fail("a raft did not make a Machine Seat", b);
        else if (helper.getBlockState(c).is(ModBlocks.MACHINE_SEAT.get())) helper.fail("a chest boat made a Machine Seat", c);
        else helper.succeed();
    }

    /** Facing north: forward pulses the north face, left the west face, jump the bottom; nothing else lights. */
    @GameTest(template = EMPTY)
    public static void seatKeysPulseTheFacesTheyPointTo(GameTestHelper helper) {
        BlockPos seat = new BlockPos(3, 2, 3);
        placeSeat(helper, seat);
        lampsAround(helper, seat);
        Player player = sitIn(helper, seat, Direction.NORTH);
        player.zza = 1.0f;
        helper.runAfterDelay(3, () -> {
            if (!lit(helper, seat.north())) { helper.fail("forward did not pulse the north face", seat.north()); return; }
            for (Direction d : new Direction[]{Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
                if (lit(helper, seat.relative(d))) { helper.fail("forward also lit the " + d + " face", seat.relative(d)); return; }
            }
            player.zza = 0.0f;
            player.xxa = 1.0f; // strafe left
            player.setJumping(true);
        });
        helper.runAfterDelay(7, () -> {
            if (!lit(helper, seat.west())) helper.fail("left did not pulse the west face (left of north)", seat.west());
            else if (!lit(helper, seat.below())) helper.fail("jump did not pulse the bottom face", seat.below());
            else if (lit(helper, seat.east())) helper.fail("left lit the east face", seat.east());
            else helper.succeed();
        });
    }

    /** A held key repeats its pulse every repeat_interval (10) ticks; let go, it stops. */
    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void seatHeldKeyRepeats(GameTestHelper helper) {
        BlockPos seat = new BlockPos(3, 2, 3);
        placeSeat(helper, seat);
        lampsAround(helper, seat);
        Player player = sitIn(helper, seat, Direction.EAST);
        player.zza = 1.0f;
        int[] rises = {0};
        boolean[] was = {false};
        helper.onEachTick(() -> {
            boolean now = lit(helper, seat.east());
            if (now && !was[0]) rises[0]++;
            was[0] = now;
        });
        helper.runAfterDelay(30, () -> player.zza = 0.0f);
        helper.runAfterDelay(60, () -> {
            if (rises[0] == 3) helper.succeed();
            else helper.fail("expected 3 pulses from holding forward for 30 ticks, saw " + rises[0], seat.east());
        });
    }

    /** A piston carries the Seat and the rider with it. */
    @GameTest(template = EMPTY)
    public static void seatCarriesItsRider(GameTestHelper helper) {
        BlockPos from = new BlockPos(2, 2, 3), to = new BlockPos(2, 3, 3);
        placeSeat(helper, from);
        Player player = sitIn(helper, from, Direction.NORTH);
        helper.setBlock(2, 1, 3, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP));
        helper.runAfterDelay(2, () -> helper.setBlock(2, 0, 3, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(12, () -> {
            if (!helper.getBlockState(to).is(ModBlocks.MACHINE_SEAT.get())) { helper.fail("the Seat did not move", to); return; }
            if (!(player.getVehicle() instanceof com.ideomancer.ideomod.block.machine.MachineSeatEntity seat)) { helper.fail("the rider fell out of the Seat", to); return; }
            if (!seat.getAnchor().equals(helper.absolutePos(to))) helper.fail("the seat did not follow the block: anchor " + seat.getAnchor(), to);
            else if (Math.abs(seat.getY() - (helper.absolutePos(to).getY() + com.ideomancer.ideomod.block.machine.MachineSeatEntity.SEAT_HEIGHT)) > 0.01) helper.fail("the seat is not on the block: y " + seat.getY(), to);
            else helper.succeed();
        });
    }

    /** Breaking the Seat puts the rider off and removes the seat. */
    @GameTest(template = EMPTY)
    public static void brokenSeatPutsTheRiderOff(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 3);
        placeSeat(helper, pos);
        Player player = sitIn(helper, pos, Direction.NORTH);
        var seat = player.getVehicle();
        helper.runAfterDelay(2, () -> helper.destroyBlock(pos));
        helper.runAfterDelay(4, () -> {
            if (player.isPassenger()) helper.fail("the rider is still sitting", pos);
            else if (seat == null || !seat.isRemoved()) helper.fail("the seat entity was not removed", pos);
            else helper.succeed();
        });
    }

    /** Getting off (sneak) steps out beside the Seat, forward first, like out of a boat; with every side blocked, inside. */
    @GameTest(template = EMPTY)
    public static void seatRiderStepsOutForward(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 2), boxed = new BlockPos(3, 2, 5);
        for (int x = 0; x <= 6; x++) for (int z = 0; z <= 6; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        placeSeat(helper, pos);
        placeSeat(helper, boxed);
        for (Direction d : Direction.Plane.HORIZONTAL) helper.setBlock(boxed.relative(d), Blocks.STONE);
        Player free = sitIn(helper, pos, Direction.NORTH);
        Player stuck = sitIn(helper, boxed, Direction.NORTH);
        helper.runAfterDelay(2, () -> {
            free.stopRiding();
            stuck.stopRiding();
            BlockPos expect = helper.absolutePos(pos.north());
            BlockPos stuckAt = helper.absolutePos(boxed);
            if (free.isPassenger() || stuck.isPassenger()) helper.fail("a rider is still sitting", pos);
            else if (!free.blockPosition().equals(expect)) helper.fail("the rider got off at " + free.blockPosition() + ", expected one block forward at " + expect, pos);
            else if (!stuck.blockPosition().equals(stuckAt)) helper.fail("a boxed-in rider got off at " + stuck.blockPosition() + ", expected inside the Seat", boxed);
            else helper.succeed();
        });
    }
}
