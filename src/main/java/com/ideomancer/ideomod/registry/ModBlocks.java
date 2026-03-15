package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.DrillBlock;
import com.ideomancer.ideomod.block.frame.FrameBlock;
import com.ideomancer.ideomod.block.frame.FrameMaterial;
import com.ideomancer.ideomod.block.frame.FramedBlock;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.machine.MachineConductorBlock;
import com.ideomancer.ideomod.block.machine.MachineFluidPipeBlock;
import com.ideomancer.ideomod.block.machine.MachineFrameBlock;
import com.ideomancer.ideomod.block.machine.MachinePipeBlock;
import com.ideomancer.ideomod.block.machine.MachineTrackBlock;
import com.ideomancer.ideomod.block.machine.MachineTrolleyBlock;
import com.ideomancer.ideomod.block.OddSlimeBlock;
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

    /**
     * Shared isRedstoneConductor delegate for framed blocks.
     * Checks the inner block's redstone conductor status via block entity.
     * Falls back to true (conduct, like most solid blocks) if no inner block.
     *
     * Used by both IRON_FRAMED_BLOCK and COPPER_FRAMED_BLOCK to avoid
     * duplicating the same lambda.
     */
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

    // Registry ID remains "machine_block" for world compatibility.
    // Display name and class renamed to "Machine Frame".
    // TODO: Rename registry ID to "machine_frame" in a future breaking change version.
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
    // pushReaction NORMAL so IPistonMovable can preserve block entity data.
    public static final RegistryObject<Block> MACHINE_TRACK = BLOCKS.register("machine_track",
            () -> new MachineTrackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.NORMAL)
            ));

    // Machine Trolley: now an EntityBlock sharing MachineFrameBlockEntity.
    // pushReaction NORMAL so IPistonMovable can preserve block entity data.
    public static final RegistryObject<Block> MACHINE_TROLLEY = BLOCKS.register("machine_trolley",
            () -> new MachineTrolleyBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.NORMAL)
            ));

    // Machine Conductor: solid-block redstone wire. Carries vanilla redstone signal
    // (0-15, decays by 1). isRedstoneConductor=false prevents it from carrying
    // strong signal through itself (keeps it equivalent to dust, not repeater).
    // pushReaction NORMAL so IPistonMovable can preserve block entity data.
    public static final RegistryObject<Block> MACHINE_CONDUCTOR = BLOCKS.register("machine_conductor",
            () -> new MachineConductorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.NORMAL)
            ));

    // Machine Pipe: push-only item transport. FILLED blockstate drives texture swap.
    // pushReaction NORMAL so IPistonMovable can preserve block entity data.
    public static final RegistryObject<Block> MACHINE_PIPE = BLOCKS.register("machine_pipe",
            () -> new MachinePipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.NORMAL)
            ));

    // Machine Fluid Pipe: fluid vacuum and depositor. FLUID_TYPE blockstate
    // drives inner cuboid texture (empty/water/lava).
    // Light level 10 when containing lava (reduced from 15 -- less exposed surface).
    // pushReaction NORMAL so IPistonMovable can preserve block entity data.
    public static final RegistryObject<Block> MACHINE_FLUID_PIPE = BLOCKS.register("machine_fluid_pipe",
            () -> new MachineFluidPipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.NORMAL)
                    .lightLevel(state -> state.getValue(MachineFluidPipeBlock.FLUID_TYPE) == com.ideomancer.ideomod.block.machine.FluidType.LAVA ? 10 : 0)
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

    /**
     * Iron Framed Block. Base strength is a fallback for empty/corrupt frames.
     * Actual mining speed is computed dynamically in FramedBlock.getDestroyProgress()
     * from the inner block's hardness + iron frame modifier (0.75).
     *
     * No requiresCorrectToolForDrops() -- harvest tier is dynamic via
     * FramedBlock.canHarvestBlock() based on the inner block's tier tags.
     *
     * isRedstoneConductor delegates to the inner block via block entity lookup.
     * pushReaction NORMAL so pistons can move it (IPistonMovable preserves data).
     *
     * lightLevel reads INNER_LIGHT from blockstate so the lighting engine
     * knows this block can emit light when containing glowstone, sea lanterns,
     * etc. The INNER_LIGHT property is synced by FramedBlockEntity.updateBlockstateFromInner().
     * FIX 2026-03-07: Issue #11 -- framed light-emitting blocks now actually emit light.
     */
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

    /**
     * Copper Framed Block. Same dynamic mining pattern as Iron Framed Block.
     * Copper frame modifier is 0.5 (slightly less than iron's 0.75).
     *
     * lightLevel reads INNER_LIGHT from blockstate -- same as Iron Framed Block.
     * FIX 2026-03-07: Issue #11 -- framed light-emitting blocks now actually emit light.
     */
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
