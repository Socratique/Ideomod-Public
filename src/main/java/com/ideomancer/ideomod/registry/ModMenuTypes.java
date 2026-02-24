package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import com.ideomancer.ideomod.block.machine.MachineFrameMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all mod menu types (container types for GUIs).
 * Uses DeferredRegister for safe, ordered registration.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Ideomod.MOD_ID);

    public static final RegistryObject<MenuType<MachineFrameMenu>> MACHINE_FRAME =
            MENUS.register("machine_frame",
                    () -> IForgeMenuType.create(MachineFrameMenu::new));
}
