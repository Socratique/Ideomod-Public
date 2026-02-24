package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.Ideomod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.ideomancer.ideomod.block.frame.FramedBlockRecipe;

/**
 * Registry for custom recipe serializers.
 *
 * The RECIPE_SERIALIZERS DeferredRegister must be wired to the mod event bus
 * in the Ideomod constructor, same as blocks/items/etc.
 *
 * Written by Claude, directed and guided by The Ideomancer.
 */
public class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Ideomod.MOD_ID);

    public static final RegistryObject<RecipeSerializer<FramedBlockRecipe>> FRAMED_BLOCK =
            RECIPE_SERIALIZERS.register("framed_block",
                    FramedBlockRecipe.Serializer::new);
}
