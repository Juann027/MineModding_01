package com.respectcraft.notice;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /respectcraft notice} (Etapa 4.11, permissao 2): {@code kinds} lista as categorias e
 * {@code test <kind> [players]} manda um aviso de exemplo (com farol do Calamidade no local de quem rodou, se for
 * categoria com farol). Sem jogadores: todos os online.
 */
public final class NoticeCommands {
    private NoticeCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("notice").requires(s -> s.hasPermission(2))
                .then(Commands.literal("kinds").executes(c -> {
                    StringBuilder sb = new StringBuilder("Notice kinds:");
                    for (NoticeKind k : NoticeKind.values()) sb.append(' ').append(k.id()).append(k.beacon() == NoticeKind.Beacon.NONE ? "" : "*");
                    c.getSource().sendSuccess(() -> Component.literal(sb + " (* = Calamidade beacon)"), false);
                    return NoticeKind.values().length;
                }))
                .then(Commands.literal("test").then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(NoticeKind.values()).map(NoticeKind::id), b))
                        .executes(c -> test(c, c.getSource().getServer().getPlayerList().getPlayers()))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> test(c, EntityArgument.getPlayers(c, "players"))))));
    }

    private static int test(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "kind");
        NoticeKind kind = NoticeKind.byId(id);
        if (kind == null) {
            c.getSource().sendFailure(Component.literal("Unknown notice kind: " + id));
            return 0;
        }
        BlockPos pos = c.getSource().getEntity() == null ? null : BlockPos.containing(c.getSource().getPosition());
        int n = HoloNotices.test(players, kind, c.getSource().getLevel(), pos);
        c.getSource().sendSuccess(() -> Component.literal("Notice " + kind.id() + " sent to " + n + " players"), true);
        return Math.max(1, n);
    }
}
