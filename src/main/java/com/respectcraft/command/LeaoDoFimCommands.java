package com.respectcraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.respectcraft.entity.LeaoDoFimEntity;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/** Operator-only visual test hooks. Specials have no damage or projectile logic yet. */
public final class LeaoDoFimCommands {
    private static final List<String> ACTIONS = List.of("bite", "roar", "hit", "charge_attack",
            "release_attack", "sigil_charge", "sigil_spurt", "aura_on", "aura_off", "reset");

    private LeaoDoFimCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("leaodofim")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("action", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ACTIONS, builder))
                                .executes(context -> {
                                    String action = StringArgumentType.getString(context, "action");
                                    if (!ACTIONS.contains(action)) {
                                        context.getSource().sendFailure(Component.literal("Invalid action: " + action));
                                        return 0;
                                    }
                                    int count = 0;
                                    for (var entity : EntityArgument.getEntities(context, "targets")) {
                                        if (!(entity instanceof LeaoDoFimEntity lion) || !lion.isAlive()) continue;
                                        switch (action) {
                                            case "reset" -> lion.resetAnimationState();
                                            case "aura_on" -> lion.setAuraCharging(true);
                                            case "aura_off" -> lion.setAuraCharging(false);
                                            default -> lion.playAction(action);
                                        }
                                        count++;
                                    }
                                    if (count == 0) {
                                        context.getSource().sendFailure(Component.literal("No living End Lion selected."));
                                    } else {
                                        int affected = count;
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                "End Lion: " + action + " applied to " + affected + " entity(ies)."), true);
                                    }
                                    return count;
                                }))));
    }
}
