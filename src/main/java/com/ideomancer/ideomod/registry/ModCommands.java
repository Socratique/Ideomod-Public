package com.ideomancer.ideomod.registry;

import com.ideomancer.ideomod.config.IdeomodConfig;
import com.ideomancer.ideomod.effect.JudgementEffect;
import com.ideomancer.ideomod.Ideomod;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers all custom commands for the mod.
 * 
 * Commands are registered on the FORGE event bus (not MOD bus)
 * using RegisterCommandsEvent, which fires when the server starts.
 * 
 * Current commands:
 *   /judgement - Triggers the Judgement stare effect on the player
 *                Requires op level 2 (same as /give, /tp, etc.)
 *   /ideomod reload - Reloads the ideomod.json config from disk
 *                      Requires op level 2
 */
@Mod.EventBusSubscriber(modid = Ideomod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("judgement")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        JudgementEffect.trigger(player);
                        context.getSource().sendSuccess(
                                () -> Component.literal("They are watching."), false);
                        return 1;
                    }
                    context.getSource().sendFailure(
                            Component.literal("This command can only be run by a player."));
                    return 0;
                })
        );

        event.getDispatcher().register(
            Commands.literal("ideomod")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                    .executes(context -> {
                        IdeomodConfig.reloadConfig();
                        context.getSource().sendSuccess(
                                () -> Component.literal("Ideomod config reloaded."), true);
                        Ideomod.LOGGER.info("Config reloaded by command");
                        return 1;
                    })
                )
        );
    }
}
