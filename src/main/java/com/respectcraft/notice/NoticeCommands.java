package com.respectcraft.notice;

import com.mojang.brigadier.arguments.FloatArgumentType;
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
 * categoria com farol). Sem jogadores: todos os online. Etapa 4.14.1: {@code cinematic <style> [seconds] [players]}
 * mostra a janela cinematografica de exemplo (0,5 a 20 s; sem segundos = duracao padrao de cada jogador) e
 * {@code styles} lista os estilos.
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
                                .executes(c -> test(c, EntityArgument.getPlayers(c, "players"))))))
                .then(Commands.literal("styles").executes(c -> {
                    StringBuilder sb = new StringBuilder("Notice styles:");
                    for (NoticeStyle st : NoticeStyle.values()) sb.append(' ').append(st.id()).append('(').append(st.priority()).append(')');
                    c.getSource().sendSuccess(() -> Component.literal(sb + " (priority in parentheses)"), false);
                    return NoticeStyle.values().length;
                }))
                .then(Commands.literal("cinematic").then(Commands.argument("style", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(NoticeStyle.values()).map(NoticeStyle::id), b))
                        .executes(c -> cinematic(c, 0F, c.getSource().getServer().getPlayerList().getPlayers()))
                        .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.5F, 20F))
                                .executes(c -> cinematic(c, FloatArgumentType.getFloat(c, "seconds"), c.getSource().getServer().getPlayerList().getPlayers()))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(c -> cinematic(c, FloatArgumentType.getFloat(c, "seconds"), EntityArgument.getPlayers(c, "players")))))));
    }

    private static int cinematic(CommandContext<CommandSourceStack> c, float seconds, Collection<ServerPlayer> players) {
        String id = StringArgumentType.getString(c, "style");
        NoticeStyle style = NoticeStyle.byId(id);
        if (style == null) {
            c.getSource().sendFailure(Component.literal("Unknown notice style: " + id));
            return 0;
        }
        int millis = seconds <= 0F ? 0 : Math.round(seconds * 1000F);
        int n = HoloNotices.testCinematic(players, style, millis);
        c.getSource().sendSuccess(() -> Component.literal("Cinematic notice " + style.id() + " (" + (millis == 0 ? "default" : millis + " ms")
                + ") sent to " + n + " players"), true);
        return Math.max(1, n);
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
