package com.respectcraft.bounty;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /respectcraft bounty} (Etapa 4.11): o mesmo que a aba Bounty Hunt do menu K, pelo chat. {@code list} vale
 * no console (teste de fumaca); {@code admin clear} (permissao 2) fecha tudo e devolve as recompensas.
 */
public final class BountyCommands {
    private BountyCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("bounty")
                .then(Commands.literal("list").executes(BountyCommands::list))
                .then(Commands.literal("place").then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 7))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    return report(c, BountyService.place(p, EntityArgument.getPlayer(c, "target").getUUID(),
                                            IntegerArgumentType.getInteger(c, "days")));
                                }))))
                .then(byId("accept", (p, b) -> BountyService.accept(p, b == null ? null : b.id())))
                .then(byId("abandon", (p, b) -> BountyService.abandon(p, b == null ? null : b.id())))
                .then(byId("cancel", (p, b) -> BountyService.cancel(p, b == null ? null : b.id(), false)))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("clear").executes(c -> {
                            int n = BountyService.clearAll(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> Component.literal("Closed " + n + " bounties; rewards returned."), true);
                            return n;
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> byId(String name,
                                                                   BiFunction<ServerPlayer, Bounty, BountyRules.Result> action) {
        return Commands.literal(name).then(Commands.argument("id", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                        BountyBoard.get(c.getSource().getServer()).all().stream().map(Bounty::shortId), b))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Bounty bounty = BountyBoard.get(c.getSource().getServer()).find(StringArgumentType.getString(c, "id"));
                    return report(c, action.apply(p, bounty));
                }));
    }

    private static int report(CommandContext<CommandSourceStack> c, BountyRules.Result r) {
        if (r == BountyRules.Result.OK) {
            c.getSource().sendSuccess(() -> Component.translatable("bounty.respectcraft.result.ok"), false);
            return 1;
        }
        c.getSource().sendFailure(Component.translatable(r.translationKey()));
        return 0;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        BountyBoard board = BountyBoard.get(c.getSource().getServer());
        long now = c.getSource().getServer().overworld().getGameTime();
        c.getSource().sendSuccess(() -> Component.literal("Bounties: " + board.all().size() + " active, "
                + board.pendingCount() + " rewards waiting for their owners"), false);
        for (Bounty b : board.all()) {
            long minutes = BountyRules.remaining(b, now) / 1200L;
            c.getSource().sendSuccess(() -> Component.literal(" [" + b.shortId() + "] " + b.targetName() + " · ")
                    .append(BountyService.rewardText(b.reward()))
                    .append(" · by " + b.posterName() + " · hunters " + b.hunters().size() + " · " + minutes + " min left"), false);
        }
        return board.all().size();
    }
}
