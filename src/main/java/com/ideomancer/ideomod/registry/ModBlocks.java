package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.DrillBlock;
import com.ideomancer.ideomod.block.frame.FrameBlock;
import com.ideomancer.ideomod.block.frame.FrameMaterial;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.machine.MachineBraceBlock;
import com.ideomancer.ideomod.block.machine.MachineConductorBlock;
import com.ideomancer.ideomod.block.machine.MachineFluidPipeBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;
import com.ideomancer.ideomod.block.machine.MachinePipeBlock;
import com.ideomancer.ideomod.block.machine.MachinePistonBlock;
import com.ideomancer.ideomod.block.machine.MachinePistonHeadBlock;
import com.ideomancer.ideomod.block.machine.MachineRepeaterBlock;
import com.ideomancer.ideomod.block.machine.MachineStickyPistonBlock;
import com.ideomancer.ideomod.block.machine.MachinePulserBlock;
import com.ideomancer.ideomod.block.machine.MachineTimerBlock;
import com.ideomancer.ideomod.block.machine.MachineToggleBlock;
import com.ideomancer.ideomod.block.machine.MachineTransformerBlock;
import com.ideomancer.ideomod.block.machine.MachineTrackBlock;
import com.ideomancer.ideomod.block.machine.MachineTrolleyBlock;
import com.ideomancer.ideomod.block.CobbledOddstoneBlock;
import com.ideomancer.ideomod.block.OddstoneBricksBlock;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.SympatheticWallBlock;
import com.ideomancer.ideomod.block.SympatheticStairBlock;
import com.ideomancer.ideomod.block.SympatheticSlabBlock;
import com.ideomancer.ideomod.block.piston.OddstoneAttractorBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.portal.OddstonePortalBlock;
import com.ideomancer.ideomod.block.piston.OddstoneRepulserBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all mod blocks.
 * Uses DeferredRegister for safe, ordered registration.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class ModBlocks {
    
    public static final DeferredRegister<Block> BLOCKS = 
            DeferredRegister.create(ForgeRegistries.BLOCKS, Ideomod.MOD_ID);

    // ========================================================================
    // SHARED PROPERTY HELPERS
    // ========================================================================

    private static final BlockBehaviour.StatePredicate FRAMED_REDSTONE_CONDUCTOR =
            (state, level, pos) -> {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof FramedBlockEntity fbe && fbe.hasInnerBlock()) {
                    return fbe.getInnerDefaultState().isRedstoneConductor(level, pos);
                }
                return true;
            };

    // ========================================================================
    // BASIC BLOCKS
    // ========================================================================
    
    public static final RegistryObject<Block> IDEOBLOCK = BLOCKS.register("ideoblock",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()
            ));
    
    public static final RegistryObject<Block> DRILL = BLOCKS.register("drill",
            () -> new DrillBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
            ));
    
    public static final RegistryObject<Block> ODDSTONE = BLOCKS.register("oddstone",
            () -> new OddstoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .isRedstoneConductor((state, level, pos) -> false)
            ));

    // ========================================================================
    // ODDSTONE VARIANTS (Cobbled, Bricks) + Walls/Stairs/Slabs
    // ========================================================================

    public static final RegistryObject<Block> COBBLED_ODDSTONE = BLOCKS.register("cobbled_oddstone",
            () -> new CobbledOddstoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .isRedstoneConductor((state, level, pos) -> false)
            ));

    public static final RegistryObject<Block> ODDSTONE_BRICKS = BLOCKS.register("oddstone_bricks",
            () -> new OddstoneBricksBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .isRedstoneConductor((state, level, pos) -> false)
            ));

    // --- Oddstone walls/stairs/slabs (each shape = own isolated channel) ---
    public static final RegistryObject<Block> ODDSTONE_WALL = BLOCKS.register("oddstone_wall",
            () -> new SympatheticWallBlock("oddstone_wall", BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            ));

    public static final RegistryObject<Block> ODDSTONE_STAIRS = BLOCKS.register("oddstone_stairs",
            () -> new SympatheticStairBlock("oddstone_stairs",
                    () -> ODDSTONE.get().defaultBlockState(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(2.0f, 6.0f)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.STONE)
            ));

    public static final RegistryObject<Block> ODDSTONE_SLAB = BLOCKS.register("oddstone_slab",
            () -> new SympatheticSlabBlock("oddstone_slab", BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            ));

    // --- Cobbled Oddstone walls/stairs/slabs (each shape = own isolated channel) ---
    public static final RegistryObject<Block> COBBLED_ODDSTONE_WALL = BLOCKS.register("cobbled_oddstone_wall",
            () -> new SympatheticWallBlock("cobbled_oddstone_wall", BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            ));

    public static final RegistryObject<Block> COBBLED_ODDSTONE_STAIRS = BLOCKS.register("cobbled_oddstone_stairs",
            () -> new SympatheticStairBlock("cobbled_oddstone_stairs",
                    () -> COBBLED_ODDSTONE.get().defaultBlockState(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(2.0f, 6.0f)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.STONE)
            ));

    public static final RegistryObject<Block> COBBLED_ODDSTONE_SLAB = BLOCKS.register("cobbled_oddstone_slab",
            () -> new SympatheticSlabBlock("cobbled_oddstone_slab", BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            ));

    // --- Oddstone Brick walls/stairs/slabs (each shape = own isolated channel) ---
    public static final RegistryObject<Block> ODDSTONE_BRICK_WALL = BLOCKS.register("oddstone_brick_wall",
            () -> new SympatheticWallBlock("oddstone_brick_wall", BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            ));

    public static final RegistryObject<Block> ODDSTONE_BRICK_STAIRS = BLOCKS.register("oddstone_brick_stairs",
            () -> new SympatheticStairBlock("oddstone_brick_stairs",
                    () -> ODDSTONE_BRICKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(2.0f, 6.0f)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.STONE)
            ));

    public static final RegistryObject<Block> ODDSTONE_BRICK_SLAB = BLOCKS.register("oddstone_brick_slab",
            () -> new SympatheticSlabBlock("oddstone_brick_slab", BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            ));

    // Registry ID remains "machine_block" for world compatibility.
    public static final RegistryObject<Block> MACHINE_BLOCK = BLOCKS.register("machine_block",
            () -> new MachineFrameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.NORMAL)
            ));
    
    public static final RegistryObject<Block> ODD_SLIME = BLOCKS.register("odd_slime",
            () -> new OddSlimeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .friction(0.8F)
                    .sound(SoundType.SLIME_BLOCK)
                    .noOcclusion()
            ));
    
    public static final RegistryObject<Block> ODDSTONE_PORTAL = BLOCKS.register("oddstone_portal",
            () -> new OddstonePortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noCollission()
                    .strength(-1.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable()
            ));

    public static final RegistryObject<Block> ODDSTONE_REPULSER = BLOCKS.register("oddstone_repulser",
            () -> new OddstoneRepulserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            ));

    public static final RegistryObject<Block> ODDSTONE_ATTRACTOR = BLOCKS.register("oddstone_attractor",
            () -> new OddstoneAttractorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            ));

    // Machine Track: now an EntityBlock sharing MachineFrameBlockEntity.
    public static final RegistryObject<Block> MACHINE_TRACK = BLOCKS.register("machine_track",
            () -> new MachineTrackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.NORMAL)
            ));

    public static final RegistryObject<Block> MACHINE_TROLLEY = BLOCKS.register("machine_trolley",
            () -> new MachineTrolleyBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.NORMAL)
            ));

    public static final RegistryObject<Block> MACHINE_CONDUCTOR = BLOCKS.register("machine_conductor",
            () -> new MachineConductorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.NORMAL)
            ));

    public static final RegistryObject<Block> MACHINE_PIPE = BLOCKS.register("machine_pipe",
            () -> new MachinePipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.NORMAL)
            ));

    public static final RegistryObject<Block> MACHINE_FLUID_PIPE = BLOCKS.register("machine_fluid_pipe",
            () -> new MachineFluidPipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.NORMAL)
                    .lightLevel(state -> state.getValue(MachineFluidPipeBlock.FLUID_TYPE) == com.ideomancer.ideomod.block.machine.FluidType.LAVA ? 10 : 0)
            ));

    public static final RegistryObject<Block> MACHINE_PISTON = BLOCKS.register("machine_piston",
            () -> new MachinePistonBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.NORMAL)
            ));

    public static final RegistryObject<Block> MACHINE_STICKY_PISTON = BLOCKS.register("machine_sticky_piston",
            () -> new MachineStickyPistonBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.NORMAL)
            ));

    public static final RegistryObject<Block> MACHINE_PISTON_HEAD = BLOCKS.register("machine_piston_head",
            () -> new MachinePistonHeadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable()
                    .noOcclusion()
            ));

    public static final RegistryObject<Block> MACHINE_BRACE = BLOCKS.register("machine_brace",
            () -> new MachineBraceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.NORMAL)
            ));

    public static final RegistryObject<Block> MACHINE_REPEATER = BLOCKS.register("machine_repeater",
            () -> new MachineRepeaterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.NORMAL)
            ));

    public static final RegistryObject<Block> MACHINE_TOGGLE = BLOCKS.register("machine_toggle",
            () -> new MachineToggleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.NORMAL)
            ));

    public static final RegistryObject<Block> MACHINE_TRANSFORMER = BLOCKS.register("machine_transformer",
            () -> new MachineTransformerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.NORMAL)
            ));

    // Machine Timer: edge-triggered timed output with configurable duration.
    // isRedstoneConductor=false so vanilla doesn't treat it as a solid conductor.
    public static final RegistryObject<Block> MACHINE_TIMER = BLOCKS.register("machine_timer",
            () -> new MachineTimerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.NORMAL)
            ));

    // Machine Pulser: fixed-duration pulse generator. Rising-edge triggered.
    // isRedstoneConductor=false so vanilla doesn't treat it as a solid conductor.
    public static final RegistryObject<Block> MACHINE_PULSER = BLOCKS.register("machine_pulser",
            () -> new MachinePulserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.NORMAL)
            ));

    // ========================================================================
    // FRAME BLOCKS
    // ========================================================================

    public static final RegistryObject<Block> IRON_FRAME = BLOCKS.register("iron_frame",
            () -> new FrameBlock(FrameMaterial.IRON, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final RegistryObject<Block> COPPER_FRAME = BLOCKS.register("copper_frame",
            () -> new FrameBlock(FrameMaterial.COPPER, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(2.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER)
                    .noOcclusion()
            ));

    // ========================================================================
    // FRAMED BLOCKS (inner block stored in block entity)
    // ========================================================================

    public static final RegistryObject<Block> IRON_FRAMED_BLOCK = BLOCKS.register("iron_framed_block",
            () -> new FramedBlock(FrameMaterial.IRON, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.NORMAL)
                    .noOcclusion()
                    .isRedstoneConductor(FRAMED_REDSTONE_CONDUCTOR)
                    .lightLevel(state -> state.getValue(FramedBlock.INNER_LIGHT))
            ));

    public static final RegistryObject<Block> COPPER_FRAMED_BLOCK = BLOCKS.register("copper_framed_block",
            () -> new FramedBlock(FrameMaterial.COPPER, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.COPPER)
                    .pushReaction(PushReaction.NORMAL)
                    .noOcclusion()
                    .isRedstoneConductor(FRAMED_REDSTONE_CONDUCTOR)
                    .lightLevel(state -> state.getValue(FramedBlock.INNER_LIGHT))
            ));
}
