package com.respectcraft.testgame;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Testes de combate, contador e cancelamento; nao possui cenas sexuais explicitas. */
public final class TestGameCommands {
    private TestGameCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("stopitstopnow")
                .executes(ctx -> FinisherTestMechanic.emergencyStop(ctx.getSource().getPlayerOrException())));
    }

    /** Ramo {@code test} da arvore {@code /respectcraft} (registrada em RespectCraftCommands). */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("test")
                .then(Commands.literal("finisher")
                        .then(Commands.literal("accept").executes(ctx -> FinisherTestMechanic.accept(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("deny").executes(ctx -> FinisherTestMechanic.deny(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("mobtest").executes(ctx -> FinisherTestMechanic.mobDemo(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("mobs")
                                .then(Commands.literal("on").executes(ctx -> FinisherTestMechanic.mobOptIn(ctx.getSource().getPlayerOrException(), true)))
                                .then(Commands.literal("off").executes(ctx -> FinisherTestMechanic.mobOptIn(ctx.getSource().getPlayerOrException(), false)))))
                .then(Commands.literal("defeats")
                        .then(Commands.literal("get")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int count = DefeatCounter.get(player);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Special test defeats: " + count), false);
                                    return count;
                                }))
                        .then(Commands.literal("add")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            int count = DefeatCounter.increment(target);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Special defeats of " + target.getGameProfile().getName() + ": " + count), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    int count = DefeatCounter.set(target, IntegerArgumentType.getInteger(ctx, "value"));
                                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                            "Special defeats of " + target.getGameProfile().getName() + ": " + count), true);
                                                    return 1;
                                                })))));
    }
}
