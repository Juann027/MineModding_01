package com.tupinicraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tupinicraft.config.ServerSettings;
import com.tupinicraft.election.Candidate;
import com.tupinicraft.election.ElectionManager;
import com.tupinicraft.election.ElectionSavedData;
import com.tupinicraft.mandate.MandateQueries;
import com.tupinicraft.mandate.MandateService;
import com.tupinicraft.mandate.SeatView;
import com.tupinicraft.origin.OriginService;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.player.PoliticalDataService;
import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.territory.Territories;
import com.tupinicraft.territory.Territory;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Comandos de jogador: /brasil perfil | origem | mandatos | eleicao ... ; e a raiz de /brasil admin. */
public final class BrasilCommands {
    private static final int MAX_LIST_LINES = 20;

    private static final SuggestionProvider<CommandSourceStack> CANDIDATE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(ElectionSavedData.get(context.getSource().getServer()).races().stream()
                    .flatMap(race -> race.candidates().stream())
                    .map(Candidate::name), builder);
    static final SuggestionProvider<CommandSourceStack> ELECTIVE_OFFICE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(PoliticalOffice.values())
                    .filter(PoliticalOffice::isElective).map(PoliticalOffice::id), builder);
    static final SuggestionProvider<CommandSourceStack> TERRITORY_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Territories.all().stream()
                    .map(t -> t.id().substring(Territories.NAMESPACE.length() + 1)), builder);

    private BrasilCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("brasil")
                .then(Commands.literal("perfil").executes(BrasilCommands::profile))
                .then(Commands.literal("origem").executes(BrasilCommands::origin))
                .then(Commands.literal("mandatos")
                        .executes(context -> mandates(context, null))
                        .then(Commands.argument("territorio", StringArgumentType.word())
                                .suggests(TERRITORY_SUGGESTIONS)
                                .executes(context -> mandates(context, StringArgumentType.getString(context, "territorio")))))
                .then(Commands.literal("eleicao")
                        .then(Commands.literal("status").executes(BrasilCommands::electionStatus))
                        .then(Commands.literal("candidatar")
                                .executes(context -> candidate(context, null))
                                .then(Commands.argument("cargo", StringArgumentType.word())
                                        .suggests(ELECTIVE_OFFICE_SUGGESTIONS)
                                        .executes(context -> candidate(context, StringArgumentType.getString(context, "cargo")))))
                        .then(Commands.literal("votar")
                                .then(Commands.argument("candidato", StringArgumentType.greedyString())
                                        .suggests(CANDIDATE_SUGGESTIONS)
                                        .executes(BrasilCommands::vote))))
                .then(AdminCommands.build());
        dispatcher.register(root);
    }

    private static int profile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
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

        List<SeatView> mandates = MandateService.queries(server).getMandatesOf(player.getUUID());
        for (SeatView seat : mandates) {
            source.sendSuccess(() -> line("Mandato: ", MandateService.seatLabel(seat.key()) + " (restam "
                    + ElectionManager.formatTime(seat.remainingTicks().orElse(0)) + ")"), false);
        }

        // Pontos de habilidade ficam guardados por cargo e continuam disponíveis ao voltar ao cargo.
        if (data.officeProgress().isEmpty()) {
            source.sendSuccess(() -> line("Pontos de habilidade: ", "nenhum ainda (ganhos exercendo mandatos)"), false);
        } else {
            String points = data.officeProgress().entrySet().stream()
                    .map(entry -> entry.getKey().displayName() + " " + entry.getValue().skillPoints())
                    .collect(java.util.stream.Collectors.joining(", "));
            source.sendSuccess(() -> line("Pontos de habilidade: ", points), false);
        }

        // Sem progressão sequencial: qualquer cargo com XP suficiente pode ser disputado (popularidade não é requisito).
        List<PoliticalOffice> unlocked = ServerSettings.unlockedOffices(server, data.politicalXp());
        source.sendSuccess(() -> line("Pode disputar: ", unlocked.isEmpty() ? "nenhum cargo"
                : unlocked.stream().map(PoliticalOffice::displayName).collect(java.util.stream.Collectors.joining(", "))), false);
        Optional<PoliticalOffice> next = ServerSettings.nextUnlock(server, data.politicalXp());
        if (next.isPresent()) {
            source.sendSuccess(() -> line("Próximo desbloqueio: ", next.get().displayName() + " com "
                    + ServerSettings.minPoliticalXp(server, next.get()) + " de XP político"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Você já pode disputar qualquer cargo, inclusive Presidente!")
                    .withStyle(ChatFormatting.GREEN), false);
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

    /** Sem território: todos os mandatos vigentes. Com território: todas as cadeiras dele (inclusive vagas). */
    private static int mandates(CommandContext<CommandSourceStack> context, String territoryId) {
        CommandSourceStack source = context.getSource();
        MandateQueries queries = MandateService.queries(source.getServer());
        List<SeatView> seats;
        if (territoryId == null) {
            seats = new java.util.ArrayList<>(queries.getPlayerMandates());
            seats.addAll(queries.getNpcMandates());
            source.sendSuccess(() -> Component.literal("[MANDATOS] Mandatos vigentes: " + seats.size()
                    + " (jogadores: " + queries.getPlayerMandates().size() + ", aldeões: " + queries.getNpcMandates().size() + ")")
                    .withStyle(ChatFormatting.AQUA), false);
        } else {
            Optional<Territory> territory = queries.getTerritory(territoryId);
            if (territory.isEmpty()) {
                source.sendFailure(Component.literal("Território desconhecido: " + territoryId + ". Ex.: brasil, es, vitoria."));
                return 0;
            }
            seats = queries.getSeats(territory.get().id());
            source.sendSuccess(() -> Component.literal("[MANDATOS] " + territory.get().name() + " ("
                    + territory.get().type().displayName() + ", " + territory.get().id() + ")").withStyle(ChatFormatting.AQUA), false);
        }
        int shown = 0;
        for (SeatView seat : seats) {
            if (shown++ >= MAX_LIST_LINES) {
                int rest = seats.size() - MAX_LIST_LINES;
                source.sendSuccess(() -> Component.literal("  ... e mais " + rest).withStyle(ChatFormatting.GRAY), false);
                break;
            }
            source.sendSuccess(() -> describeSeat(seat), false);
        }
        return seats.size();
    }

    private static Component describeSeat(SeatView seat) {
        MutableComponent line = Component.literal("  " + MandateService.seatLabel(seat.key()) + ": ").withStyle(ChatFormatting.GRAY);
        if (seat.isVacant()) {
            return line.append(Component.literal("vaga").withStyle(ChatFormatting.DARK_GRAY));
        }
        String kind = switch (seat.occupantType()) {
            case PLAYER -> "";
            case NPC -> " [aldeão]";
            case VACANT -> "";
        };
        return line.append(Component.literal(seat.displayName().orElse("?") + kind).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" — restam " + ElectionManager.formatTime(seat.remainingTicks().orElse(0)))
                        .withStyle(ChatFormatting.GRAY));
    }

    private static int electionStatus(CommandContext<CommandSourceStack> context) {
        for (Component line : ElectionManager.statusLines(context.getSource().getServer())) {
            context.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int candidate(CommandContext<CommandSourceStack> context, String officeId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<PoliticalOffice> office = Optional.empty();
        if (officeId != null) {
            office = PoliticalOffice.byId(officeId);
            if (office.isEmpty() || !office.get().isElective()) {
                context.getSource().sendFailure(Component.literal("Cargo desconhecido: " + officeId));
                return 0;
            }
        }
        return report(context.getSource(), ElectionManager.register(player, office), false);
    }

    private static int vote(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer voter = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "candidato");
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

    static MutableComponent line(String label, String value) {
        return Component.literal(label).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(value).withStyle(ChatFormatting.YELLOW));
    }
}
