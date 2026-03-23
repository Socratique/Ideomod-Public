package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.frame.FramedBlockEntity;
import com.ideomancer.ideomod.block.machine.MachineFluidPipeBlockEntity;
import com.ideomancer.ideomod.block.machine.MachineFrameBlockEntity;
import com.ideomancer.ideomod.block.machine.MachinePipeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all mod block entity types.
 * Uses DeferredRegister for safe, ordered registration.
 *
 * Updated by Claude, directed and guided by The Ideomancer.
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Ideomod.MOD_ID);

    /**
     * Machine Frame block entity type. Shared by all Machine Family blocks
     * that use the base MachineFrameBlockEntity (single specialization slot):
     * Machine Frame, Machine Track, Machine Trolley, Machine Conductor,
     * Machine Piston, Machine Sticky Piston, Machine Brace, Machine Repeater,
     * Machine Toggle, Machine Transformer, Machine Timer.
     * Each specialization stores its trigger item in the single slot.
     */
    public static final RegistryObject<BlockEntityType<MachineFrameBlockEntity>> MACHINE_FRAME =
            BLOCK_ENTITIES.register("machine_frame",
                    () -> BlockEntityType.Builder.of(
                            MachineFrameBlockEntity::new,
                            ModBlocks.MACHINE_BLOCK.get(),
                            ModBlocks.MACHINE_TRACK.get(),
                            ModBlocks.MACHINE_TROLLEY.get(),
                            ModBlocks.MACHINE_CONDUCTOR.get(),
                            ModBlocks.MACHINE_PISTON.get(),
                            ModBlocks.MACHINE_STICKY_PISTON.get(),
                            ModBlocks.MACHINE_BRACE.get(),
                            ModBlocks.MACHINE_REPEATER.get(),
                            ModBlocks.MACHINE_TOGGLE.get(),
                            ModBlocks.MACHINE_TRANSFORMER.get(),
                            ModBlocks.MACHINE_TIMER.get()
                    ).build(null));

    /**
     * Machine Pipe block entity type. Separate from MACHINE_FRAME because
     * the pipe has a transit slot, ticker, and pipe-specific NBT.
     * Only used by MachinePipeBlock.
     */
    public static final RegistryObject<BlockEntityType<MachinePipeBlockEntity>> MACHINE_PIPE =
            BLOCK_ENTITIES.register("machine_pipe",
                    () -> BlockEntityType.Builder.of(
                            MachinePipeBlockEntity::new,
                            ModBlocks.MACHINE_PIPE.get()
                    ).build(null));

    /**
     * Machine Fluid Pipe block entity type. Separate from MACHINE_FRAME because
     * the fluid pipe has fluid type tracking, ticker, IFluidHandler capability,
     * and fluid-pipe-specific NBT. Only used by MachineFluidPipeBlock.
     */
    public static final RegistryObject<BlockEntityType<MachineFluidPipeBlockEntity>> MACHINE_FLUID_PIPE =
            BLOCK_ENTITIES.register("machine_fluid_pipe",
                    () -> BlockEntityType.Builder.of(
                            MachineFluidPipeBlockEntity::new,
                            ModBlocks.MACHINE_FLUID_PIPE.get()
                    ).build(null));

    /**
     * Framed Block entity type. Shared by both iron and copper framed blocks.
     * Stores the inner block identity for behavioral delegation and rendering.
     */
    public static final RegistryObject<BlockEntityType<FramedBlockEntity>> FRAMED_BLOCK =
            BLOCK_ENTITIES.register("framed_block",
                    () -> BlockEntityType.Builder.of(
                            FramedBlockEntity::new,
                            ModBlocks.IRON_FRAMED_BLOCK.get(),
                            ModBlocks.COPPER_FRAMED_BLOCK.get()
                    ).build(null));
}
