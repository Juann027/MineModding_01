package com.tupinicraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tupinicraft.election.Candidate;
import com.tupinicraft.election.ElectionManager;
import com.tupinicraft.election.ElectionSavedData;
import com.tupinicraft.origin.OriginService;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.player.PoliticalDataService;
import com.tupinicraft.politics.PoliticalOffice;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;
import java.util.UUID;

/** Comandos de jogador: /brasil perfil | origem | eleicao ... ; e a raiz de /brasil admin. */
public final class BrasilCommands {
    private static final SuggestionProvider<CommandSourceStack> CANDIDATE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(ElectionSavedData.get(context.getSource().getServer()).races().stream()
                    .flatMap(race -> race.candidates().stream())
                    .map(Candidate::name), builder);

    private BrasilCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("brasil")
                .then(Commands.literal("perfil").executes(BrasilCommands::profile))
                .then(Commands.literal("origem").executes(BrasilCommands::origin))
                .then(Commands.literal("eleicao")
                        .then(Commands.literal("status").executes(BrasilCommands::electionStatus))
                        .then(Commands.literal("candidatar").executes(BrasilCommands::candidate))
                        .then(Commands.literal("votar")
                                .then(Commands.argument("jogador", StringArgumentType.word())
                                        .suggests(CANDIDATE_SUGGESTIONS)
                                        .executes(BrasilCommands::vote))))
                .then(AdminCommands.build());
        dispatcher.register(root);
    }

    private static int profile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PoliticalData data = PoliticalDataService.get(player);
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal("=== Perfil político de " + player.getGameProfile().getName() + " ===")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Origem: ").withStyle(ChatFormatting.WHITE)
                .append(data.origin().map(OriginService::describe)
                        .orElse(Component.literal("ainda não sorteada").withStyle(ChatFormatting.GRAY))), false);
        source.sendSuccess(() -> line("Cargo: ", data.office().displayName()), false);
        source.sendSuccess(() -> line("XP político: ", String.valueOf(data.politicalXp())), false);
        source.sendSuccess(() -> line("Popularidade: ", String.valueOf(data.popularity())), false);

        Optional<PoliticalOffice> next = data.office().next();
        if (next.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Você chegou ao topo da carreira política!")
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            PoliticalOffice target = next.get();
            boolean ready = target.meetsRequirements(data.politicalXp(), data.popularity());
            source.sendSuccess(() -> line("Próximo cargo: ", target.displayName() + " (XP " + target.minPoliticalXp()
                    + ", popularidade " + target.minPopularity() + ")")
                    .append(Component.literal(ready ? " — requisitos atendidos, vença uma eleição!" : " — requisitos pendentes")
                            .withStyle(ready ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        }
        return 1;
    }

    private static int origin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PoliticalData data = PoliticalDataService.get(player);
        if (data.origin().isEmpty()) {
            context.getSource().sendFailure(Component.literal("Sua origem ainda não foi sorteada."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("[BRASIL] Sua origem: ")
                .withStyle(ChatFormatting.GREEN)
                .append(OriginService.describe(data.origin().get())), false);
        return 1;
    }

    private static int electionStatus(CommandContext<CommandSourceStack> context) {
        for (Component line : ElectionManager.statusLines(context.getSource().getServer())) {
            context.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int candidate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return report(context.getSource(), ElectionManager.register(player), false);
    }

    private static int vote(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer voter = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "jogador");
        Optional<UUID> candidate = ElectionManager.findCandidateByName(context.getSource().getServer(), name);
        if (candidate.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Candidato inexistente nesta eleição: " + name));
            return 0;
        }
        return report(context.getSource(), ElectionManager.castVote(voter, candidate.get()), false);
    }

    /** @param notifyAdmins repassa o sucesso aos operadores (não usar em votos: o voto é secreto). */
    static int report(CommandSourceStack source, ElectionManager.Outcome outcome, boolean notifyAdmins) {
        if (outcome.success()) {
            source.sendSuccess(outcome::message, notifyAdmins);
            return 1;
        }
        source.sendFailure(outcome.message());
        return 0;
    }

    private static MutableComponent line(String label, String value) {
        return Component.literal(label).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(value).withStyle(ChatFormatting.YELLOW));
    }
}
