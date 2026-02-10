package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.DrillBlock;
import com.ideomancer.ideomod.block.OddstoneBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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
                    .strength(1.5f, 6.0f)  // Same as cobblestone
                    .requiresCorrectToolForDrops()
            ));
    
    // Register Drill Block - mines 3x3 when powered by redstone
    public static final RegistryObject<Block> DRILL = BLOCKS.register("drill",
            () -> new DrillBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)  // Similar to iron block
                    .requiresCorrectToolForDrops()
            ));
    
    // Register Oddstone - mob avoidance + redstone insulation + sympathetic movement
    // Key property: isRedstoneConductor = false
    //   This tells the game NOT to propagate redstone signal through this block.
    //   Same approach as glass - the block is "transparent" to redstone.
    //   Without this, redstone power from one side would pass through to the other.
    public static final RegistryObject<Block> ODDSTONE = BLOCKS.register("oddstone",
            () -> new OddstoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE) // Distinctive purple-ish color on maps
                    .strength(2.0f, 6.0f)            // Slightly harder than stone
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    // THIS is what makes it not conduct redstone:
                    // Returns false for "is this a redstone conductor?" check.
                    // The lambda receives (blockstate, world, position) and returns boolean.
                    .isRedstoneConductor((state, level, pos) -> false)
            ));
}
