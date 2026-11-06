package com.tupinicraft.election;

import com.tupinicraft.TupiniConfig;
import com.tupinicraft.TupiniCraft;
import com.tupinicraft.network.AnnouncementPayload;
import com.tupinicraft.network.ElectionSyncPayload;
import com.tupinicraft.network.OpenUrnaPayload;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.player.PoliticalDataService;
import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.registry.ModItems;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Regras da eleição global (lado servidor). Não guarda estado em campos estáticos:
 * tudo fica em {@link ElectionSavedData}. O tempo avança apenas pelo tick do servidor.
 */
public final class ElectionManager {
    public static final int MAX_RACES = 2;
    private static final int TICKS_PER_SECOND = 20;
    private static final int PERIODIC_SYNC_TICKS = 5 * TICKS_PER_SECOND;
    /** Fração do tempo de apuração usada para revelar os votos aos poucos. */
    private static final double COUNTING_REVEAL_FRACTION = 0.8;

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
        if (data.phase() == ElectionPhase.INACTIVE) {
            tickAutoScheduler(server, data);
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

    private static void tickAutoScheduler(MinecraftServer server, ElectionSavedData data) {
        if (!TupiniConfig.AUTO_ELECTIONS.get() || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        int interval = autoIntervalTicks();
        int remaining = data.ticksUntilAutoElection();
        if (remaining < 0 || remaining > interval) {
            remaining = interval;
        }
        remaining--;
        if (remaining > 0) {
            data.setTicksUntilAutoElection(remaining);
            return;
        }
        data.setTicksUntilAutoElection(interval);
        List<PoliticalOffice> offices = pickAutomaticOffices(server);
        Outcome outcome = start(server, offices);
        if (!outcome.success()) {
            TupiniCraft.LOGGER.warn("Falha ao iniciar eleição automática: {}", outcome.message().getString());
        }
    }

    /**
     * Escolhe 1 ou 2 cargos para a eleição automática: os próximos cargos para os quais
     * algum jogador online já cumpre os requisitos. Se ninguém cumpre, disputa-se Vereador.
     */
    private static List<PoliticalOffice> pickAutomaticOffices(MinecraftServer server) {
        Set<PoliticalOffice> contestable = new LinkedHashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PoliticalData data = PoliticalDataService.get(player);
            data.office().next()
                    .filter(next -> next.meetsRequirements(data.politicalXp(), data.popularity()))
                    .ifPresent(contestable::add);
        }
        List<PoliticalOffice> offices = new ArrayList<>(contestable);
        if (offices.isEmpty()) {
            return List.of(PoliticalOffice.VEREADOR);
        }
        Collections.shuffle(offices, new java.util.Random(server.overworld().getRandom().nextLong()));
        return List.copyOf(offices.subList(0, Math.min(MAX_RACES, offices.size())));
    }

    private static int autoIntervalTicks() {
        return TupiniConfig.AUTO_ELECTION_INTERVAL_MINUTES.get() * 60 * TICKS_PER_SECOND;
    }

    // ================================================================== transições

    public static Outcome start(MinecraftServer server, List<PoliticalOffice> offices) {
        ElectionSavedData data = ElectionSavedData.get(server);
        if (data.phase() != ElectionPhase.INACTIVE) {
            return Outcome.fail("Já existe uma eleição em andamento (fase: " + data.phase().displayName() + ").");
        }
        if (offices.isEmpty() || offices.size() > MAX_RACES) {
            return Outcome.fail("Uma eleição deve disputar 1 ou 2 cargos.");
        }
        if (offices.stream().distinct().count() != offices.size()) {
            return Outcome.fail("Os cargos em disputa devem ser diferentes.");
        }
        for (PoliticalOffice office : offices) {
            if (!office.isElective()) {
                return Outcome.fail("O cargo " + office.displayName() + " não pode ser disputado em eleição.");
            }
        }

        data.races().clear();
        for (PoliticalOffice office : offices) {
            data.races().add(new ElectionRace(office));
        }
        data.enterPhase(ElectionPhase.REGISTRATION, TupiniConfig.REGISTRATION_SECONDS.get() * TICKS_PER_SECOND);
        announce(server, Component.literal("Começaram as inscrições para " + officeList(data) + "! Use ")
                .append(Component.literal("/brasil eleicao candidatar").withStyle(ChatFormatting.AQUA)));
        syncAll(server);
        return Outcome.ok("Eleição iniciada para " + officeList(data) + ".");
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
                data.enterPhase(ElectionPhase.COUNTING, TupiniConfig.COUNTING_SECONDS.get() * TICKS_PER_SECOND);
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
        announce(server, Component.literal("A eleição para " + officeList(data) + " foi cancelada pela administração."));
        endElection(server, data);
        return Outcome.ok("Eleição cancelada.");
    }

    private static void closeRegistration(MinecraftServer server, ElectionSavedData data) {
        // Congela candidatos e suas popularidades de candidatura (atualiza quem está online).
        for (ElectionRace race : data.races()) {
            List<Candidate> candidates = race.candidates();
            for (int i = 0; i < candidates.size(); i++) {
                Candidate candidate = candidates.get(i);
                ServerPlayer online = server.getPlayerList().getPlayer(candidate.id());
                if (online != null) {
                    candidates.set(i, candidate.withPopularity(PoliticalDataService.get(online).popularity()));
                }
            }
        }

        List<ElectionRace> empty = data.races().stream().filter(r -> r.candidates().isEmpty()).toList();
        for (ElectionRace race : empty) {
            announce(server, Component.literal("Ninguém se inscreveu para " + race.office().displayName()
                    + ". Disputa encerrada sem vencedor."));
        }
        data.races().removeAll(empty);
        data.setDirty();

        if (data.races().isEmpty()) {
            endElection(server, data);
            return;
        }

        data.enterPhase(ElectionPhase.VOTING, TupiniConfig.VOTING_SECONDS.get() * TICKS_PER_SECOND);
        announce(server, Component.literal("Inscrições encerradas. A votação começou! Use a Urna Eletrônica (botão direito)."));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            giveUrna(player);
        }
        syncAll(server);
    }

    private static void finishCounting(MinecraftServer server, ElectionSavedData data) {
        for (ElectionRace race : data.races()) {
            PoliticalOffice office = race.office();
            ElectionTally.Result result = ElectionTally.decide(office, race.candidates(), race.ballots());
            TupiniCraft.LOGGER.info("Resultado {}: {} pontos={}", office.id(), result.outcome(), result.scores());
            switch (result.outcome()) {
                case WINNER -> {
                    Candidate winner = result.winner().orElseThrow();
                    announce(server, Component.literal(winner.name()).withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal(" venceu a eleição para " + office.displayName()
                                    + " com " + result.bestScore() + " pontos!").withStyle(ChatFormatting.WHITE)));
                    promoteWinner(server, data, winner, office);
                }
                case NO_CANDIDATES -> announce(server, Component.literal("Ninguém se inscreveu para "
                        + office.displayName() + ". Eleição sem vencedor."));
                case BELOW_MINIMUM -> announce(server, Component.literal("Ninguém venceu para " + office.displayName()
                        + ": a melhor pontuação foi de " + leadersText(result.involved()) + " (" + result.bestScore()
                        + " pontos), mas era preciso " + (race.candidates().size() == 1 ? "superar " : "alcançar ")
                        + result.minimumScore() + "."));
                case TIE -> announce(server, Component.literal("Empate técnico para " + office.displayName() + " entre "
                        + leadersText(result.involved()) + " (" + result.bestScore()
                        + " pontos e mesma popularidade). Eleição sem vencedor."));
            }
        }
        endElection(server, data);
    }

    private static void promoteWinner(MinecraftServer server, ElectionSavedData data, Candidate winner, PoliticalOffice office) {
        ServerPlayer player = server.getPlayerList().getPlayer(winner.id());
        if (player == null) {
            data.pendingPromotions().put(winner.id(), office);
            data.setDirty();
            TupiniCraft.LOGGER.info("{} venceu offline; promoção a {} pendente.", winner.name(), office.id());
            return;
        }
        applyPromotion(player, office);
    }

    private static void applyPromotion(ServerPlayer player, PoliticalOffice office) {
        PoliticalData current = PoliticalDataService.get(player);
        // Só promove a partir do cargo imediatamente anterior (o cargo pode ter sido alterado por admin).
        if (current.office().next().orElse(null) != office) {
            player.sendSystemMessage(Component.literal("Você venceu para " + office.displayName()
                    + ", mas seu cargo atual (" + current.office().displayName()
                    + ") mudou durante a eleição. Promoção não aplicada.").withStyle(ChatFormatting.RED));
            return;
        }
        PoliticalDataService.set(player, current.withOffice(office));
        player.sendSystemMessage(Component.literal("Parabéns! Você agora é " + office.displayName() + ".")
                .withStyle(ChatFormatting.GOLD));
    }

    private static void endElection(MinecraftServer server, ElectionSavedData data) {
        data.reset();
        data.setTicksUntilAutoElection(autoIntervalTicks());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            removeUrnas(player);
        }
        syncAll(server);
    }

    // ================================================================== ações de jogador

    public static Outcome register(ServerPlayer player) {
        MinecraftServer server = player.server;
        ElectionSavedData data = ElectionSavedData.get(server);
        if (data.phase() != ElectionPhase.REGISTRATION) {
            return Outcome.fail(data.phase() == ElectionPhase.INACTIVE
                    ? "Não há eleição ativa no momento."
                    : "As inscrições estão encerradas (fase atual: " + data.phase().displayName() + ").");
        }
        PoliticalData political = PoliticalDataService.get(player);
        Optional<PoliticalOffice> next = political.office().next();
        if (next.isEmpty()) {
            return Outcome.fail("Você já é " + political.office().displayName() + ". Não há cargo acima!");
        }
        PoliticalOffice target = next.get();
        ElectionRace race = data.raceFor(target);
        if (race == null) {
            return Outcome.fail("Seu próximo cargo é " + target.displayName()
                    + ", mas esta eleição disputa apenas: " + officeList(data) + ".");
        }
        if (race.isCandidate(player.getUUID())) {
            return Outcome.fail("Você já está inscrito para " + target.displayName() + ".");
        }
        if (political.politicalXp() < target.minPoliticalXp()) {
            return Outcome.fail("XP político insuficiente para " + target.displayName() + ": você tem "
                    + political.politicalXp() + ", precisa de " + target.minPoliticalXp() + ".");
        }
        if (political.popularity() < target.minPopularity()) {
            return Outcome.fail("Popularidade insuficiente para " + target.displayName() + ": você tem "
                    + political.popularity() + ", precisa de " + target.minPopularity() + ".");
        }

        String name = player.getGameProfile().getName();
        race.candidates().add(new Candidate(player.getUUID(), name, political.popularity()));
        data.setDirty();
        announce(server, Component.literal(name).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" registrou sua candidatura para " + target.displayName() + "!")
                        .withStyle(ChatFormatting.WHITE)));
        syncAll(server);
        return Outcome.ok("Candidatura registrada para " + target.displayName() + ".");
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
            return Outcome.fail("Candidatos não podem votar na própria disputa (" + race.office().displayName() + ").");
        }
        if (race.hasVoted(voter.getUUID())) {
            return Outcome.fail("Você já votou para " + race.office().displayName()
                    + " nesta eleição. O voto não pode ser alterado.");
        }

        int weight = ElectionTally.voteWeight(PoliticalDataService.get(voter).popularity());
        race.ballots().add(new Ballot(voter.getUUID(), candidateId, weight));
        data.setDirty();
        sync(voter, data);
        String candidateName = race.findCandidate(candidateId).map(Candidate::name).orElse("?");
        return Outcome.ok("Voto registrado: " + candidateName + " para " + race.office().displayName()
                + " (peso " + weight + "). Confirma!");
    }

    public static Optional<UUID> findCandidateByName(MinecraftServer server, String name) {
        for (ElectionRace race : ElectionSavedData.get(server).races()) {
            Optional<Candidate> candidate = race.findCandidateByName(name);
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
        PoliticalOffice pending = data.pendingPromotions().remove(player.getUUID());
        if (pending != null) {
            data.setDirty();
            applyPromotion(player, pending);
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
            if (TupiniConfig.AUTO_ELECTIONS.get() && data.ticksUntilAutoElection() >= 0) {
                lines.add(Component.literal("Próxima eleição automática em ~" + formatTime(data.ticksUntilAutoElection())
                        + " (com jogadores online).").withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        lines.add(Component.literal("[ELEIÇÃO] Fase: " + data.phase().displayName() + " — tempo restante: "
                + formatTime(data.ticksRemaining())).withStyle(ChatFormatting.GOLD));
        for (ElectionRace race : data.races()) {
            PoliticalOffice office = race.office();
            lines.add(Component.literal(office.displayName() + " (requisitos: XP " + office.minPoliticalXp()
                    + ", popularidade " + office.minPopularity() + "; pontuação mínima "
                    + ElectionTally.minimumWinningScore(office) + ")").withStyle(ChatFormatting.YELLOW));
            if (race.candidates().isEmpty()) {
                lines.add(Component.literal("  Nenhum candidato inscrito.").withStyle(ChatFormatting.GRAY));
            }
            Map<UUID, Integer> partial = data.phase() == ElectionPhase.COUNTING
                    ? ElectionTally.scores(race.candidates(), race.ballots().subList(0, revealedBallots(data, race)))
                    : Map.of();
            for (Candidate candidate : race.candidates()) {
                MutableComponent line = Component.literal("  • " + candidate.name() + " (popularidade "
                        + candidate.popularity() + ")");
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
        for (ElectionRace race : data.races()) {
            int revealed = revealedBallots(data, race);
            Map<UUID, Integer> partial = counting
                    ? ElectionTally.scores(race.candidates(), race.ballots().subList(0, revealed))
                    : Map.of();
            List<ElectionSyncPayload.CandidateView> candidates = race.candidates().stream()
                    .map(c -> new ElectionSyncPayload.CandidateView(c.id(), c.name(), c.popularity(),
                            partial.getOrDefault(c.id(), -1)))
                    .collect(Collectors.toList());
            races.add(new ElectionSyncPayload.RaceView(race.office().ordinal(), candidates,
                    counting ? revealed : 0, counting ? race.ballots().size() : 0));
            if (race.hasVoted(player.getUUID())) {
                voted.add(race.office().ordinal());
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

    private static String officeList(ElectionSavedData data) {
        return data.races().stream().map(r -> r.office().displayName()).collect(Collectors.joining(" e "));
    }

    private static String leadersText(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::name).collect(Collectors.joining(" e "));
    }

    public static String formatTime(int ticks) {
        int seconds = Math.max(0, (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
