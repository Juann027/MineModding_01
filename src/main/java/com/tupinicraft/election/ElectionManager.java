package com.tupinicraft.election;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.config.ConfigKeys;
import com.tupinicraft.config.ServerSettings;
import com.tupinicraft.mandate.MandateService;
import com.tupinicraft.mandate.Occupant;
import com.tupinicraft.mandate.SeatKey;
import com.tupinicraft.mandate.SeatView;
import com.tupinicraft.network.AnnouncementPayload;
import com.tupinicraft.network.ElectionSyncPayload;
import com.tupinicraft.network.OpenUrnaPayload;
import com.tupinicraft.origin.RegionalOrigin;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.player.PoliticalDataService;
import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.registry.ModItems;
import com.tupinicraft.territory.Territories;
import com.tupinicraft.territory.Territory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Regras da eleição global (lado servidor). Não guarda estado em campos estáticos:
 * tudo fica em {@link ElectionSavedData}. O tempo avança apenas pelo tick do servidor.
 * <p>
 * Cada disputa é por uma cadeira (cargo + território). O vencedor toma posse da cadeira
 * ({@link MandateService}) e, se for jogador, também sobe de cargo pessoal.
 */
public final class ElectionManager {
    public static final int MAX_RACES = 2;
    private static final int TICKS_PER_SECOND = ServerSettings.TICKS_PER_SECOND;
    private static final int PERIODIC_SYNC_TICKS = 5 * TICKS_PER_SECOND;
    /** Fração do tempo de apuração usada para revelar os votos aos poucos. */
    private static final double COUNTING_REVEAL_FRACTION = 0.8;
    private static final int FALLBACK_SEAT_ATTEMPTS = 40;

    private ElectionManager() {
    }

    /** Resultado de uma ação de jogador/admin, com mensagem pronta para exibição. */
    public record Outcome(boolean success, Component message) {
        static Outcome ok(String text) {
            return new Outcome(true, Component.literal(text).withStyle(ChatFormatting.GREEN));
        }

        static Outcome fail(String text) {
            return new Outcome(false, Component.literal(text).withStyle(ChatFormatting.RED));
        }
    }

    // ================================================================== tick

    public static void tick(MinecraftServer server) {
        ElectionSavedData data = ElectionSavedData.get(server);
        tickCalendars(server, data);
        if (data.phase() == ElectionPhase.INACTIVE) {
            return;
        }

        data.decrementTicks();
        int remaining = data.ticksRemaining();
        if (remaining <= 0) {
            advance(server);
            return;
        }
        if (data.phase() == ElectionPhase.COUNTING && remaining % TICKS_PER_SECOND == 0) {
            syncAll(server); // apuração gradual: votos "sobem" uma vez por segundo
        } else if (remaining % PERIODIC_SYNC_TICKS == 0) {
            syncAll(server); // corrige o relógio exibido no cliente
        }
    }

    /**
     * Calendários geral e municipal. Só contam tempo com jogadores online. Se um calendário vence durante
     * outra eleição, a eleição dele começa assim que a atual terminar; se os dois venceram, eles se alternam.
     * Mudar o intervalo vale a partir do próximo ciclo (o ciclo em curso mantém o prazo).
     */
    private static void tickCalendars(MinecraftServer server, ElectionSavedData data) {
        if (!ServerSettings.getBool(server, ConfigKeys.AUTO_ELECTIONS) || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        if (data.ticksUntil(ElectionCalendar.GENERAL) < 0) {
            data.setTicksUntil(ElectionCalendar.GENERAL, ServerSettings.intervalTicks(server, ConfigKeys.GENERAL_INTERVAL));
        }
        if (data.ticksUntil(ElectionCalendar.MUNICIPAL) < 0) {
            data.setTicksUntil(ElectionCalendar.MUNICIPAL, data.ticksUntil(ElectionCalendar.GENERAL)
                    + ServerSettings.intervalTicks(server, ConfigKeys.CALENDAR_OFFSET));
        }
        for (ElectionCalendar calendar : List.of(ElectionCalendar.GENERAL, ElectionCalendar.MUNICIPAL)) {
            int ticks = data.ticksUntil(calendar);
            if (ticks > 0) {
                data.setTicksUntil(calendar, ticks - 1);
            }
        }
        if (data.phase() != ElectionPhase.INACTIVE) {
            return;
        }
        // Se os dois calendários venceram, começa o que não rodou por último (evita que um "engula" o outro).
        List<ElectionCalendar> order = data.lastAutoCalendar() == ElectionCalendar.GENERAL
                ? List.of(ElectionCalendar.MUNICIPAL, ElectionCalendar.GENERAL)
                : List.of(ElectionCalendar.GENERAL, ElectionCalendar.MUNICIPAL);
        for (ElectionCalendar calendar : order) {
            if (data.ticksUntil(calendar) <= 0) {
                data.setTicksUntil(calendar, ServerSettings.intervalTicks(server, calendar == ElectionCalendar.GENERAL
                        ? ConfigKeys.GENERAL_INTERVAL : ConfigKeys.MUNICIPAL_INTERVAL));
                List<SeatKey> seats = pickAutomaticSeats(server, calendar);
                if (seats.isEmpty()) {
                    TupiniCraft.LOGGER.info("{}: nenhuma cadeira vaga para disputar; ciclo ignorado.", calendar.displayName());
                    continue;
                }
                Outcome outcome = start(server, seats, calendar);
                if (!outcome.success()) {
                    TupiniCraft.LOGGER.warn("Falha ao iniciar {}: {}", calendar.displayName(), outcome.message().getString());
                }
                return;
            }
        }
    }

    /**
     * Escolhe até 2 cadeiras vagas para o calendário: primeiro as que algum jogador online pode disputar
     * (XP suficiente, no seu domicílio eleitoral); senão uma cadeira vaga qualquer do calendário,
     * que será disputada por aldeões.
     */
    private static List<SeatKey> pickAutomaticSeats(MinecraftServer server, ElectionCalendar calendar) {
        Set<SeatKey> options = new LinkedHashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PoliticalData data = PoliticalDataService.get(player);
            if (data.origin().isEmpty()) {
                continue;
            }
            for (PoliticalOffice office : calendar.offices()) {
                if (ServerSettings.hasRequiredXp(server, office, data.politicalXp())) {
                    SeatKey seat = new SeatKey(Territories.domicile(office, data.origin().get()).id(), office, 1);
                    if (MandateService.isVacant(server, seat)) {
                        options.add(seat);
                    }
                }
            }
        }
        Random random = new Random(server.overworld().getRandom().nextLong());
        if (options.isEmpty()) {
            List<PoliticalOffice> offices = calendar.offices();
            for (int attempt = 0; attempt < FALLBACK_SEAT_ATTEMPTS && options.isEmpty(); attempt++) {
                PoliticalOffice office = offices.get(random.nextInt(offices.size()));
                List<Territory> territories = Territories.ofType(office.jurisdiction());
                SeatKey seat = new SeatKey(territories.get(random.nextInt(territories.size())).id(), office, 1);
                if (MandateService.isVacant(server, seat)) {
                    options.add(seat);
                }
            }
        }
        List<SeatKey> list = new ArrayList<>(options);
        Collections.shuffle(list, random);
        return List.copyOf(list.subList(0, Math.min(MAX_RACES, list.size())));
    }

    // ================================================================== transições

    public static Outcome start(MinecraftServer server, List<SeatKey> seats, ElectionCalendar calendar) {
        ElectionSavedData data = ElectionSavedData.get(server);
        if (data.phase() != ElectionPhase.INACTIVE) {
            return Outcome.fail("Já existe uma eleição em andamento (fase: " + data.phase().displayName() + ").");
        }
        if (seats.isEmpty() || seats.size() > MAX_RACES) {
            return Outcome.fail("Uma eleição deve disputar 1 ou 2 cadeiras.");
        }
        if (seats.stream().distinct().count() != seats.size()) {
            return Outcome.fail("As cadeiras em disputa devem ser diferentes.");
        }
        for (SeatKey seat : seats) {
            Optional<Territory> territory = Territories.get(seat.territoryId());
            if (territory.isEmpty() || territory.get().type() != seat.office().jurisdiction()) {
                return Outcome.fail(seat.office().displayName() + " é disputado em territórios do tipo "
                        + seat.office().jurisdiction().displayName() + ".");
            }
            if (seat.seat() > seat.office().seatsPerTerritory()) {
                return Outcome.fail("Cadeira inexistente: " + seat.asString() + ".");
            }
            SeatView view = MandateService.queries(server).view(seat);
            if (!view.isVacant()) {
                return Outcome.fail("A cadeira de " + MandateService.seatLabel(seat) + " está ocupada por "
                        + view.displayName().orElse("?") + " (restam " + formatTime(view.remainingTicks().orElse(0)) + ").");
            }
        }

        List<ElectionRace> races = new ArrayList<>();
        for (SeatKey seat : seats) {
            int[] npcRange = ServerSettings.npcPopularityRange(server, seat.office());
            races.add(new ElectionRace(seat, ServerSettings.minimumWinningScore(server, seat.office()), npcRange[0], npcRange[1]));
        }
        data.begin(calendar, ServerSettings.snapshotElectionRules(server), races, ServerSettings.registrationTicks(server));
        String prefix = calendar == ElectionCalendar.MANUAL ? "" : calendar.displayName() + ": c";
        announce(server, Component.literal((prefix.isEmpty() ? "C" : prefix) + "omeçaram as inscrições para "
                        + raceList(data) + "! Use ")
                .append(Component.literal("/brasil eleicao candidatar").withStyle(ChatFormatting.AQUA)));
        syncAll(server);
        return Outcome.ok("Eleição iniciada para " + raceList(data) + ".");
    }

    /** Avança para a próxima fase (usado pelo cronômetro e pelo comando admin "avancar"). */
    public static Outcome advance(MinecraftServer server) {
        ElectionSavedData data = ElectionSavedData.get(server);
        switch (data.phase()) {
            case INACTIVE:
                return Outcome.fail("Não há eleição ativa.");
            case REGISTRATION:
                closeRegistration(server, data);
                return Outcome.ok("Inscrições encerradas.");
            case VOTING:
                data.enterPhase(ElectionPhase.COUNTING, data.rules().countingTicks());
                announce(server, Component.literal("Votação encerrada! Começou a apuração..."));
                syncAll(server);
                return Outcome.ok("Votação encerrada; apuração iniciada.");
            case COUNTING:
                finishCounting(server, data);
                return Outcome.ok("Apuração concluída.");
            default:
                return Outcome.fail("Fase desconhecida.");
        }
    }

    public static Outcome cancel(MinecraftServer server) {
        ElectionSavedData data = ElectionSavedData.get(server);
        if (data.phase() == ElectionPhase.INACTIVE) {
            return Outcome.fail("Não há eleição ativa para cancelar.");
        }
        announce(server, Component.literal("A eleição para " + raceList(data) + " foi cancelada pela administração."));
        endElection(server, data);
        return Outcome.ok("Eleição cancelada.");
    }

    private static void closeRegistration(MinecraftServer server, ElectionSavedData data) {
        ElectionRules rules = data.rules();
        // Congela candidatos e suas popularidades de candidatura (atualiza jogadores online).
        for (ElectionRace race : data.races()) {
            List<Candidate> candidates = race.candidates();
            for (int i = 0; i < candidates.size(); i++) {
                Candidate candidate = candidates.get(i);
                ServerPlayer online = candidate.npc() ? null : server.getPlayerList().getPlayer(candidate.id());
                if (online != null) {
                    candidates.set(i, candidate.withPopularity(PoliticalDataService.get(online).popularity()));
                }
            }
        }

        // Aldeões completam disputas com poucos candidatos.
        Random random = new Random(server.overworld().getRandom().nextLong());
        for (ElectionRace race : data.races()) {
            int needed = NpcCandidateFactory.npcsNeeded(rules, race.candidates().size());
            if (needed > 0) {
                List<String> namesInUse = data.races().stream().flatMap(r -> r.candidates().stream())
                        .map(Candidate::name).toList();
                List<Candidate> npcs = NpcCandidateFactory.create(needed, race.npcPopularityMin(), race.npcPopularityMax(),
                        rules, namesInUse, random);
                race.candidates().addAll(npcs);
                announce(server, Component.literal("Aldeões entram na disputa para " + raceLabel(race) + ": "
                        + npcs.stream().map(Candidate::name).collect(Collectors.joining(", ")) + "!"));
            }
        }

        List<ElectionRace> insufficient = new ArrayList<>();
        for (ElectionRace race : data.races()) {
            int count = race.candidates().size();
            if (count == 0) {
                announce(server, Component.literal("Ninguém se inscreveu para " + raceLabel(race) + ". Disputa encerrada sem vencedor."));
                insufficient.add(race);
            } else if (count < rules.minCandidates()) {
                announce(server, Component.literal("Candidatos insuficientes para " + raceLabel(race) + " (" + count
                        + " de " + rules.minCandidates() + "). Disputa encerrada sem vencedor."));
                insufficient.add(race);
            }
        }
        data.races().removeAll(insufficient);
        data.setDirty();

        if (data.races().isEmpty()) {
            endElection(server, data);
            return;
        }

        data.enterPhase(ElectionPhase.VOTING, rules.votingTicks());
        announce(server, Component.literal("Inscrições encerradas. A votação começou! Use a Urna Eletrônica (botão direito)."));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            giveUrna(player);
        }
        syncAll(server);
    }

    private static void finishCounting(MinecraftServer server, ElectionSavedData data) {
        int multiplier = data.rules().voteMultiplier();
        for (ElectionRace race : data.races()) {
            ElectionTally.Result result = ElectionTally.decide(race.minimumScore(), race.candidates(), race.ballots(), multiplier);
            TupiniCraft.LOGGER.info("Resultado {}: {} pontos={}", race.seat().asString(), result.outcome(), result.scores());
            String label = raceLabel(race);
            switch (result.outcome()) {
                case WINNER -> {
                    Candidate winner = result.winner().orElseThrow();
                    announce(server, Component.literal(winner.name()).withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal(" venceu a eleição para " + label + " com " + result.bestScore()
                                    + " pontos!").withStyle(ChatFormatting.WHITE)));
                    takeOffice(server, data, race, winner);
                }
                case NO_CANDIDATES -> announce(server, Component.literal("Ninguém se inscreveu para " + label + ". Eleição sem vencedor."));
                case BELOW_MINIMUM -> announce(server, Component.literal("Ninguém venceu para " + label
                        + ": a melhor pontuação foi de " + names(result.involved()) + " (" + result.bestScore()
                        + " pontos), mas era preciso " + (race.candidates().size() == 1 ? "superar " : "alcançar ")
                        + result.minimumScore() + "."));
                case TIE -> announce(server, Component.literal("Empate técnico para " + label + " entre "
                        + names(result.involved()) + " (" + result.bestScore()
                        + " pontos e mesma popularidade). Eleição sem vencedor."));
            }
        }
        endElection(server, data);
    }

    /** Posse na cadeira disputada; para jogadores, o cargo passa a ser o da cadeira (ver MandateService). */
    private static void takeOffice(MinecraftServer server, ElectionSavedData data, ElectionRace race, Candidate winner) {
        Occupant occupant = winner.npc() ? Occupant.npc(winner.id(), winner.name()) : Occupant.player(winner.id(), winner.name());
        if (!MandateService.inaugurate(server, race.seat(), occupant, Optional.ofNullable(data.electionId()))) {
            TupiniCraft.LOGGER.warn("Posse recusada em {}: cadeira já ocupada.", race.seat().asString());
            announce(server, Component.literal(winner.name() + " não pôde tomar posse: a cadeira de " + raceLabel(race)
                    + " já estava ocupada."));
        }
    }

    private static void endElection(MinecraftServer server, ElectionSavedData data) {
        data.reset();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            removeUrnas(player);
        }
        syncAll(server);
    }

    // ================================================================== ações de jogador

    /**
     * Candidatura direta: qualquer jogador (inclusive Desempregado) pode concorrer a qualquer cargo em disputa,
     * desde que esteja no seu domicílio eleitoral e tenha o XP político mínimo. Popularidade não é requisito.
     * Sem cargo informado, usa a primeira disputa em que o jogador pode concorrer.
     */
    public static Outcome register(ServerPlayer player, Optional<PoliticalOffice> requested) {
        MinecraftServer server = player.server;
        ElectionSavedData data = ElectionSavedData.get(server);
        if (data.phase() != ElectionPhase.REGISTRATION) {
            return Outcome.fail(data.phase() == ElectionPhase.INACTIVE
                    ? "Não há eleição ativa no momento."
                    : "As inscrições estão encerradas (fase atual: " + data.phase().displayName() + ").");
        }
        ElectionRace already = data.raceWithCandidate(player.getUUID());
        if (already != null) {
            return Outcome.fail("Você já está inscrito para " + raceLabel(already) + ".");
        }
        PoliticalData political = PoliticalDataService.get(player);
        if (political.origin().isEmpty()) {
            return Outcome.fail("Sua origem ainda não foi sorteada.");
        }

        List<ElectionRace> races = data.races().stream()
                .filter(race -> requested.isEmpty() || race.office() == requested.get())
                .toList();
        if (races.isEmpty()) {
            return Outcome.fail("Esta eleição não disputa " + requested.map(PoliticalOffice::displayName).orElse("?")
                    + ". Em disputa: " + raceList(data) + ".");
        }
        List<String> reasons = new ArrayList<>();
        for (ElectionRace race : races) {
            Optional<String> problem = eligibilityProblem(server, player.getUUID(), political, political.origin().get(), race);
            if (problem.isEmpty()) {
                String name = player.getGameProfile().getName();
                race.candidates().add(Candidate.player(player.getUUID(), name, political.popularity()));
                data.setDirty();
                announce(server, Component.literal(name).withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(" registrou sua candidatura para " + raceLabel(race) + "!")
                                .withStyle(ChatFormatting.WHITE)));
                syncAll(server);
                return Outcome.ok("Candidatura registrada para " + raceLabel(race) + ".");
            }
            reasons.add(problem.get());
        }
        return Outcome.fail(String.join(" | ", reasons));
    }

    private static Optional<String> eligibilityProblem(MinecraftServer server, UUID playerId, PoliticalData political,
                                                       RegionalOrigin origin, ElectionRace race) {
        PoliticalOffice office = race.office();
        Territory domicile = Territories.domicile(office, origin);
        int minXp = ServerSettings.minPoliticalXp(server, office);
        boolean lastOccupant = MandateService.lastOccupant(server, race.seat()).map(playerId::equals).orElse(false);
        Optional<CandidacyRules.Problem> problem = CandidacyRules.check(
                domicile.id().equals(race.territoryId()), political.politicalXp(), minXp,
                lastOccupant, ServerSettings.getBool(server, ConfigKeys.CONSECUTIVE_REELECTION));
        String label = raceLabel(race);
        return problem.map(p -> switch (p) {
            case OUTSIDE_DOMICILE -> label + ": seu domicílio eleitoral para " + office.displayName() + " é " + domicile.name() + ".";
            case INSUFFICIENT_XP -> label + ": XP político insuficiente (você tem " + political.politicalXp()
                    + ", precisa de " + minXp + ").";
            case REELECTION_DISABLED -> label + ": reeleição consecutiva está desativada neste servidor.";
        });
    }

    public static Outcome castVote(ServerPlayer voter, UUID candidateId) {
        ElectionSavedData data = ElectionSavedData.get(voter.server);
        if (data.phase() != ElectionPhase.VOTING) {
            return Outcome.fail("A votação não está aberta no momento.");
        }
        ElectionRace race = data.raceWithCandidate(candidateId);
        if (race == null) {
            return Outcome.fail("Candidato inexistente nesta eleição.");
        }
        if (candidateId.equals(voter.getUUID())) {
            return Outcome.fail("Você não pode votar em si mesmo.");
        }
        if (race.isCandidate(voter.getUUID())) {
            return Outcome.fail("Candidatos não podem votar na própria disputa (" + raceLabel(race) + ").");
        }
        if (race.hasVoted(voter.getUUID())) {
            return Outcome.fail("Você já votou para " + raceLabel(race) + " nesta eleição. O voto não pode ser alterado.");
        }

        int weight = data.rules().voteWeight(PoliticalDataService.get(voter).popularity());
        race.ballots().add(new Ballot(voter.getUUID(), candidateId, weight));
        data.setDirty();
        sync(voter, data);
        String candidateName = race.findCandidate(candidateId).map(Candidate::name).orElse("?");
        return Outcome.ok("Voto registrado: " + candidateName + " para " + raceLabel(race)
                + " (peso " + weight + "). Confirma!");
    }

    public static Optional<UUID> findCandidateByName(MinecraftServer server, String name) {
        for (ElectionRace race : ElectionSavedData.get(server).races()) {
            Optional<Candidate> candidate = race.findCandidateByName(name.trim());
            if (candidate.isPresent()) {
                return Optional.of(candidate.get().id());
            }
        }
        return Optional.empty();
    }

    /** Clique direito com a urna: valida a fase no servidor e pede ao cliente para abrir a tela. */
    public static void openBallot(ServerPlayer player) {
        ElectionSavedData data = ElectionSavedData.get(player.server);
        if (data.phase() != ElectionPhase.VOTING) {
            player.displayClientMessage(Component.literal("Não há votação em andamento.").withStyle(ChatFormatting.RED), true);
            return;
        }
        sync(player, data);
        PacketDistributor.sendToPlayer(player, OpenUrnaPayload.INSTANCE);
    }

    public static void onPlayerLogin(ServerPlayer player) {
        ElectionSavedData data = ElectionSavedData.get(player.server);
        // Promoções pendentes salvas pela v0.1.0 (sem cadeira) continuam sendo aplicadas como eram.
        PoliticalOffice legacyPending = data.pendingPromotions().remove(player.getUUID());
        if (legacyPending != null) {
            data.setDirty();
            PoliticalDataService.set(player, PoliticalDataService.get(player).withOffice(legacyPending));
        }
        if (data.phase() == ElectionPhase.VOTING) {
            giveUrna(player);
        } else {
            removeUrnas(player);
        }
        sync(player, data);
    }

    // ================================================================== urnas

    private static void giveUrna(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.URNA_ELETRONICA.get())) {
                return;
            }
        }
        ItemStack urna = new ItemStack(ModItems.URNA_ELETRONICA.get());
        if (!inventory.add(urna)) {
            player.drop(urna, false);
        }
    }

    private static void removeUrnas(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.URNA_ELETRONICA.get())) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    // ================================================================== status / sincronização

    public static List<Component> statusLines(MinecraftServer server) {
        ElectionSavedData data = ElectionSavedData.get(server);
        List<Component> lines = new ArrayList<>();
        if (data.phase() == ElectionPhase.INACTIVE) {
            lines.add(Component.literal("[ELEIÇÃO] Nenhuma eleição ativa.").withStyle(ChatFormatting.GOLD));
            if (ServerSettings.getBool(server, ConfigKeys.AUTO_ELECTIONS)) {
                for (ElectionCalendar calendar : List.of(ElectionCalendar.GENERAL, ElectionCalendar.MUNICIPAL)) {
                    int ticks = data.ticksUntil(calendar);
                    if (ticks >= 0) {
                        lines.add(Component.literal(calendar.displayName() + " em ~" + formatTime(ticks)
                                + " (com jogadores online).").withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            return lines;
        }
        lines.add(Component.literal("[ELEIÇÃO] " + data.calendar().displayName() + " — fase: " + data.phase().displayName()
                + " — tempo restante: " + formatTime(data.ticksRemaining())).withStyle(ChatFormatting.GOLD));
        for (ElectionRace race : data.races()) {
            PoliticalOffice office = race.office();
            lines.add(Component.literal(raceLabel(race) + " (requisito: XP " + ServerSettings.minPoliticalXp(server, office)
                    + "; pontuação mínima " + race.minimumScore() + ")").withStyle(ChatFormatting.YELLOW));
            if (race.candidates().isEmpty()) {
                lines.add(Component.literal("  Nenhum candidato inscrito.").withStyle(ChatFormatting.GRAY));
            }
            Map<UUID, Integer> partial = data.phase() == ElectionPhase.COUNTING
                    ? ElectionTally.scores(race.candidates(), race.ballots().subList(0, revealedBallots(data, race)),
                    data.rules().voteMultiplier())
                    : Map.of();
            for (Candidate candidate : race.candidates()) {
                MutableComponent line = Component.literal("  • " + candidate.name() + (candidate.npc() ? " [aldeão]" : "")
                        + " (popularidade " + candidate.popularity() + ")");
                if (partial.containsKey(candidate.id())) {
                    line.append(" — " + partial.get(candidate.id()) + " pontos parciais");
                }
                lines.add(line.withStyle(ChatFormatting.WHITE));
            }
        }
        return lines;
    }

    /**
     * Quantos votos já foram "apurados". A revelação é gradual e termina em
     * {@link #COUNTING_REVEAL_FRACTION} do tempo de apuração, para o placar final ficar
     * alguns segundos na tela antes do anúncio do resultado.
     */
    private static int revealedBallots(ElectionSavedData data, ElectionRace race) {
        int total = race.ballots().size();
        if (data.phase() != ElectionPhase.COUNTING || data.phaseTotalTicks() <= 0) {
            return 0;
        }
        int elapsed = data.phaseTotalTicks() - data.ticksRemaining();
        double revealWindow = data.phaseTotalTicks() * COUNTING_REVEAL_FRACTION;
        int revealed = (int) Math.floor(total * (elapsed / revealWindow));
        return Math.max(0, Math.min(total, revealed));
    }

    public static void syncAll(MinecraftServer server) {
        ElectionSavedData data = ElectionSavedData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player, data);
        }
    }

    private static void sync(ServerPlayer player, ElectionSavedData data) {
        PacketDistributor.sendToPlayer(player, buildPayload(player, data));
    }

    private static ElectionSyncPayload buildPayload(ServerPlayer player, ElectionSavedData data) {
        if (data.phase() == ElectionPhase.INACTIVE) {
            return ElectionSyncPayload.INACTIVE;
        }
        boolean counting = data.phase() == ElectionPhase.COUNTING;
        List<ElectionSyncPayload.RaceView> races = new ArrayList<>();
        List<Integer> voted = new ArrayList<>();
        for (int index = 0; index < data.races().size(); index++) {
            ElectionRace race = data.races().get(index);
            int revealed = revealedBallots(data, race);
            Map<UUID, Integer> partial = counting
                    ? ElectionTally.scores(race.candidates(), race.ballots().subList(0, revealed), data.rules().voteMultiplier())
                    : Map.of();
            List<ElectionSyncPayload.CandidateView> candidates = race.candidates().stream()
                    .map(c -> new ElectionSyncPayload.CandidateView(c.id(), c.name(), c.popularity(),
                            partial.getOrDefault(c.id(), -1), c.npc()))
                    .collect(Collectors.toList());
            races.add(new ElectionSyncPayload.RaceView(race.office().ordinal(), raceLabel(race), candidates,
                    counting ? revealed : 0, counting ? race.ballots().size() : 0));
            if (race.hasVoted(player.getUUID())) {
                voted.add(index);
            }
        }
        return new ElectionSyncPayload(data.phase().ordinal(), data.ticksRemaining(), data.phaseTotalTicks(), races, voted);
    }

    /** Aviso global exibido como faixa na tela dos jogadores (não usa o chat). */
    private static void announce(MinecraftServer server, MutableComponent message) {
        Component full = Component.literal("[ELEIÇÃO] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(message.withStyle(style -> style.getColor() == null ? style.withColor(ChatFormatting.WHITE) : style));
        TupiniCraft.LOGGER.info(full.getString());
        PacketDistributor.sendToAllPlayers(new AnnouncementPayload(full));
    }

    public static String raceLabel(ElectionRace race) {
        return MandateService.seatLabel(race.seat());
    }

    private static String raceList(ElectionSavedData data) {
        return data.races().stream().map(ElectionManager::raceLabel).collect(Collectors.joining(" e "));
    }

    private static String names(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::name).collect(Collectors.joining(" e "));
    }

    public static String formatTime(long ticks) {
        long seconds = Math.max(0, (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
