package com.respectcraft.quest;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /respectcraft quest} (Etapa 4.14.2), so administracao/diagnostico (permissao 2; nada disso faz parte do
 * fluxo normal de jogo, que e o menu K):
 * <ul>
 *   <li>{@code list} — definicoes carregadas e as recusadas;</li>
 *   <li>{@code info <quest>} — condicoes, objetivos e recompensas da definicao (JSON);</li>
 *   <li>{@code player <player>} — estado e progresso de cada missao do jogador (inclusive ids sem definicao);</li>
 *   <li>{@code diagnose <player> <quest>} — avaliacao de cada condicao agora (descoberta e aceitacao);</li>
 *   <li>{@code discover <player> <quest>} — forca a descoberta (desenvolvimento);</li>
 *   <li>{@code reset <player> <quest>} — volta a missao ao comeco (oculta, sem progresso).</li>
 * </ul>
 * Janelas cinematograficas de teste: {@code /respectcraft notice cinematic}.
 */
public final class QuestCommands {
    private QuestCommands() {}

    private static final SuggestionProvider<CommandSourceStack> QUESTS = (c, b) -> SharedSuggestionProvider.suggestResource(
            QuestRegistry.catalog().all().stream().map(QuestDef::id), b);

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("quest").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(QuestCommands::list))
                .then(Commands.literal("info").then(Commands.argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                        .executes(QuestCommands::info)))
                .then(Commands.literal("player").then(Commands.argument("player", EntityArgument.player()).executes(QuestCommands::player)))
                .then(Commands.literal("diagnose").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("quest", ResourceLocationArgument.id()).suggests(QUESTS).executes(QuestCommands::diagnose))))
                .then(Commands.literal("discover").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("quest", ResourceLocationArgument.id()).suggests(QUESTS).executes(QuestCommands::discover))))
                .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("quest", ResourceLocationArgument.id()).suggests(QUESTS).executes(QuestCommands::reset))));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        QuestCatalog cat = QuestRegistry.catalog();
        StringBuilder sb = new StringBuilder("Quests (" + cat.size() + " loaded, " + cat.errors().size() + " invalid):");
        for (QuestDef d : cat.all()) {
            sb.append("\n ").append(d.id()).append(" [").append(d.category()).append(d.secret() ? ", secret" : "")
                    .append(d.flags().repeatable() ? ", repeatable" : "").append(d.flags().autoAccept() ? ", auto-accept" : "")
                    .append(d.flags().autoClaim() ? ", auto-claim" : "").append("] objectives ").append(d.objectives().size())
                    .append(", rewards ").append(d.rewards().size());
        }
        for (String e : cat.errors()) sb.append("\n INVALID ").append(e);
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return cat.size();
    }

    private static QuestDef def(CommandContext<CommandSourceStack> c) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "quest");
        QuestDef d = QuestRegistry.catalog().get(id);
        if (d == null) c.getSource().sendFailure(Component.literal("Unknown quest: " + id));
        return d;
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        QuestDef d = def(c);
        if (d == null) return 0;
        String json = QuestDef.FILE_CODEC.encodeStart(JsonOps.INSTANCE, d).result().map(Object::toString).orElse("(not encodable)");
        c.getSource().sendSuccess(() -> Component.literal("Quest " + d.id() + ": " + json), false);
        return 1;
    }

    private static int player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        QuestLog log = QuestService.get(p);
        QuestCatalog cat = QuestRegistry.catalog();
        StringBuilder sb = new StringBuilder("Quests of " + p.getGameProfile().getName() + " (" + log.quests().size() + "):");
        for (Map.Entry<String, QuestProgress> e : log.quests().entrySet()) {
            QuestProgress q = e.getValue();
            boolean known = cat.get(ResourceLocation.tryParse(e.getKey())) != null;
            sb.append("\n ").append(e.getKey()).append(": ").append(q.state().id()).append(" ").append(q.progress())
                    .append(" completions ").append(q.completions()).append(q.tracked() ? " tracked" : "")
                    .append(known ? "" : " (no definition loaded; kept)");
        }
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return log.quests().size();
    }

    private static int diagnose(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        QuestDef d = def(c);
        if (d == null) return 0;
        QuestContext ctx = QuestService.context(p);
        List<String> lines = new ArrayList<>();
        lines.add("Quest " + d.id() + " for " + p.getGameProfile().getName() + ": state " + QuestService.get(p).state(d.id()).id());
        if (d.discover().isPresent()) {
            lines.add("discover (" + (d.discover().get().persistent() ? "state" : "needs an event") + "):");
            QuestCondition.explain(d.discover().get(), ctx, "  ", lines);
        } else lines.add("discover: always (no condition)");
        if (d.requires().isPresent()) {
            lines.add("requires:");
            QuestCondition.explain(d.requires().get(), ctx, "  ", lines);
        }
        QuestProgress q = QuestService.get(p).get(d.id());
        for (QuestObjective o : d.objectives()) {
            QuestObjective.Type t = o.behavior();
            lines.add("objective " + o.id() + " (" + o.type() + " " + o.target().orElse("*") + "): " + q.value(o.id()) + "/" + o.count()
                    + (t == null ? "" : " retroactive " + t.retroactive(o, ctx)));
        }
        c.getSource().sendSuccess(() -> Component.literal(String.join("\n", lines)), false);
        return 1;
    }

    private static int discover(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        QuestDef d = def(c);
        if (d == null) return 0;
        QuestEngine.Result r = QuestEngine.forceDiscover(QuestService.get(p), QuestRegistry.catalog(), d.id(), QuestService.context(p));
        if (r.error != null) {
            c.getSource().sendFailure(Component.literal("Could not discover " + d.id() + ": " + r.error));
            return 0;
        }
        QuestService.apply(p, r, 0);
        c.getSource().sendSuccess(() -> Component.literal("Quest " + d.id() + " discovered for " + p.getGameProfile().getName()), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        ResourceLocation id = ResourceLocationArgument.getId(c, "quest");
        QuestEngine.Result r = QuestEngine.reset(QuestService.get(p), id);
        if (r.error != null) {
            c.getSource().sendFailure(Component.literal("Nothing to reset: " + id));
            return 0;
        }
        QuestService.apply(p, r, 0);
        // descobertas por estado voltam na hora (ex.: conquista que o jogador ja tem)
        QuestService.apply(p, QuestEngine.reconcile(QuestService.get(p), QuestRegistry.catalog(), QuestService.context(p)), 0);
        QuestService.snapshot(p);
        c.getSource().sendSuccess(() -> Component.literal("Quest " + id + " reset for " + p.getGameProfile().getName()), true);
        return 1;
    }
}
