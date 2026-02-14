package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.DrillBlock;
import com.ideomancer.ideomod.block.MachineBlock;
import com.ideomancer.ideomod.block.OddSlimeBlock;
import com.ideomancer.ideomod.block.OddstoneAttractorBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import com.ideomancer.ideomod.block.OddstonePortalBlock;
import com.ideomancer.ideomod.block.OddstoneRepulserBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all mod blocks
 * Uses DeferredRegister for safe, ordered registration
 */
public class ModBlocks {
    
    // Create the DeferredRegister for blocks
    public static final DeferredRegister<Block> BLOCKS = 
            DeferredRegister.create(ForgeRegistries.BLOCKS, Ideomod.MOD_ID);
    
    // Register Ideoblock - a basic decorative block
    public static final RegistryObject<Block> IDEOBLOCK = BLOCKS.register("ideoblock",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()
            ));
    
    // Register Drill Block - mines 3x3 when powered by redstone
    public static final RegistryObject<Block> DRILL = BLOCKS.register("drill",
            () -> new DrillBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
            ));
    
    // Register Oddstone - mob avoidance + redstone insulation + sympathetic movement + portal frame
    public static final RegistryObject<Block> ODDSTONE = BLOCKS.register("oddstone",
            () -> new OddstoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .isRedstoneConductor((state, level, pos) -> false)
            ));
    
    // Register Machine Block - selective stickiness + redstone conduction
    public static final RegistryObject<Block> MACHINE_BLOCK = BLOCKS.register("machine_block",
            () -> new MachineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .isRedstoneConductor((state, level, pos) -> false)
            ));
    
    // Register Odd Slime - directional slime block, only sticky when pushed in FACING direction
    // Properties mirror vanilla slime: soft, bouncy sound, no tool required
    public static final RegistryObject<Block> ODD_SLIME = BLOCKS.register("odd_slime",
            () -> new OddSlimeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .friction(0.8F)           // Slippery like slime
                    .sound(SoundType.SLIME_BLOCK)
                    .noOcclusion()             // Translucent like slime
            ));
    
    // Register Oddstone Portal - the interior fill block for lit Oddstone portals
    // Properties match nether portal concept but with Oddstone flavor:
    //   - No collision (entities walk through)
    //   - Light level 11 (same as nether portal)
    //   - Indestructible by hand (-1 hardness) - only breaks when frame breaks
    //   - Cannot be pushed by pistons
    //   - Light blue map color
    //   - Glass-like sound
    public static final RegistryObject<Block> ODDSTONE_PORTAL = BLOCKS.register("oddstone_portal",
            () -> new OddstonePortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noCollission()
                    .strength(-1.0F) // Indestructible by hand
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .pushReaction(PushReaction.BLOCK) // Can't be pushed by pistons
                    .noLootTable() // Drops nothing
            ));

    // ========================================================================
    // REPULSER / ATTRACTOR — ranged directional pistons
    // ========================================================================

    // Register Oddstone Repulser - scans line, pushes Oddstone away
    // Mechanical feel: stone base + metal + oddstone accent
    // Movable by pistons (can be part of contraptions)
    public static final RegistryObject<Block> ODDSTONE_REPULSER = BLOCKS.register("oddstone_repulser",
            () -> new OddstoneRepulserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            ));

    // Register Oddstone Attractor - scans line, pulls Oddstone toward it
    // Same properties as Repulser — they're a matched pair
    public static final RegistryObject<Block> ODDSTONE_ATTRACTOR = BLOCKS.register("oddstone_attractor",
            () -> new OddstoneAttractorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            ));
}
