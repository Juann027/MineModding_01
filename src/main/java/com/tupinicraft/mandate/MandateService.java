package com.tupinicraft.mandate;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.config.ConfigKeys;
import com.tupinicraft.config.ServerSettings;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.player.PoliticalDataService;
import com.tupinicraft.player.SkillProgression;
import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.territory.Territories;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras de mandato no servidor: posse, encerramento, expiração automática e pontos de habilidade.
 * O relógio dos mandatos é o tempo de jogo do Overworld (só avança com o servidor ligado).
 * <p>
 * O cargo do jogador acompanha o mandato: na posse ele passa a ter o cargo da cadeira; ao perder ou
 * terminar o mandato volta a Desempregado, mantendo XP político, popularidade e o progresso de
 * habilidades daquele cargo. Um jogador ocupa no máximo uma cadeira vigente: ao tomar posse de outra,
 * renuncia automaticamente à anterior.
 */
public final class MandateService {
    private static final int TICKS_PER_SECOND = ServerSettings.TICKS_PER_SECOND;

    private MandateService() {
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static boolean expirationEnabled(MinecraftServer server) {
        return ServerSettings.getBool(server, ConfigKeys.TERM_EXPIRATION);
    }

    /** Consultas somente leitura sobre o estado atual (mandatos expirados aparecem vagos). */
    public static MandateQueries queries(MinecraftServer server) {
        return new MandateQueries(MandateSavedData.get(server).registry(), now(server), expirationEnabled(server));
    }

    public static boolean isVacant(MinecraftServer server, SeatKey key) {
        return queries(server).view(key).isVacant();
    }

    public static Optional<UUID> lastOccupant(MinecraftServer server, SeatKey key) {
        return MandateSavedData.get(server).registry().lastOccupant(key);
    }

    /**
     * Dá posse com a duração de mandato configurada agora (mandatos já em curso não mudam).
     * Para jogadores: encerra antes um eventual mandato vigente em outra cadeira e atualiza o cargo.
     * Retorna false se a cadeira já tem ocupante vigente.
     */
    public static boolean inaugurate(MinecraftServer server, SeatKey key, Occupant occupant, Optional<UUID> electionId) {
        MandateSavedData data = MandateSavedData.get(server);
        long start = now(server);
        boolean expiration = expirationEnabled(server);
        if (!data.registry().isVacant(key, start, expiration)) {
            return false;
        }
        if (occupant.type() == OccupantType.PLAYER) {
            data.registry().activeSeatOf(occupant.id(), start, expiration).ifPresent(previous -> {
                data.registry().vacate(previous);
                notifyPlayer(server, occupant.id(), Component.literal("Você renunciou ao mandato de " + seatLabel(previous)
                        + " para assumir " + seatLabel(key) + ".").withStyle(ChatFormatting.YELLOW));
                TupiniCraft.LOGGER.info("{} renunciou a {} para assumir {}", occupant.displayName(), previous.asString(), key.asString());
            });
        }
        Mandate mandate = new Mandate(occupant, start, start + ServerSettings.termTicks(server, key.office()),
                Optional.empty(), electionId);
        if (!data.registry().inaugurate(key, mandate, start, expiration)) {
            return false;
        }
        data.setDirty();
        TupiniCraft.LOGGER.info("Posse: {} em {} até o tick {}", occupant.displayName(), key.asString(), mandate.termEnd());
        if (occupant.type() == OccupantType.PLAYER) {
            setPlayerOffice(server, occupant.id(), key.office());
            notifyPlayer(server, occupant.id(), Component.literal("Parabéns! Você tomou posse como " + seatLabel(key) + ".")
                    .withStyle(ChatFormatting.GOLD));
        }
        return true;
    }

    /** Encerra o mandato da cadeira (ação administrativa). O jogador ocupante volta a Desempregado. */
    public static Optional<Mandate> vacate(MinecraftServer server, SeatKey key) {
        MandateSavedData data = MandateSavedData.get(server);
        Optional<Mandate> removed = data.registry().vacate(key);
        removed.ifPresent(mandate -> {
            data.setDirty();
            onMandateEnded(server, key, mandate, "foi encerrado pela administração");
        });
        return removed;
    }

    /** Aplica, no login, mudanças de cargo que aconteceram com o jogador offline. */
    public static void onPlayerLogin(ServerPlayer player) {
        MandateSavedData data = MandateSavedData.get(player.server);
        PoliticalOffice pending = data.pendingOfficeUpdates().remove(player.getUUID());
        if (pending != null) {
            data.setDirty();
            PoliticalDataService.set(player, PoliticalDataService.get(player).withOffice(pending));
            player.sendSystemMessage(Component.literal(pending == PoliticalOffice.DESEMPREGADO
                    ? "Seu mandato terminou enquanto você estava fora. Você agora é Desempregado (XP, popularidade e habilidades foram mantidos)."
                    : "Enquanto você estava fora, você tomou posse como " + pending.displayName() + ".")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Chamado a cada tick do servidor; o trabalho real acontece uma vez por segundo. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % TICKS_PER_SECOND != 0) {
            return;
        }
        expireMandates(server);
        awardSkillPoints(server);
    }

    private static void expireMandates(MinecraftServer server) {
        if (!expirationEnabled(server)) {
            return;
        }
        MandateSavedData data = MandateSavedData.get(server);
        long now = now(server);
        List<SeatKey> expired = new ArrayList<>();
        for (Map.Entry<SeatKey, Mandate> entry : data.registry().mandates().entrySet()) {
            if (now >= entry.getValue().termEnd()) {
                expired.add(entry.getKey());
            }
        }
        for (SeatKey key : expired) {
            data.registry().vacate(key).ifPresent(mandate -> onMandateEnded(server, key, mandate, "terminou"));
        }
        if (!expired.isEmpty()) {
            data.setDirty();
        }
    }

    private static void onMandateEnded(MinecraftServer server, SeatKey key, Mandate mandate, String how) {
        Component message = Component.literal("[MANDATO] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal("O mandato de " + mandate.occupant().displayName() + " como "
                        + seatLabel(key) + " " + how + ".").withStyle(ChatFormatting.WHITE));
        server.getPlayerList().broadcastSystemMessage(message, false);
        if (mandate.occupant().type() == OccupantType.PLAYER) {
            setPlayerOffice(server, mandate.occupant().id(), PoliticalOffice.DESEMPREGADO);
            notifyPlayer(server, mandate.occupant().id(), Component.literal("Você agora é Desempregado. Seu XP político, "
                    + "popularidade e habilidades de " + key.office().displayName() + " foram mantidos.").withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Atualiza o cargo do jogador agora (online) ou no próximo login (offline). */
    private static void setPlayerOffice(MinecraftServer server, UUID playerId, PoliticalOffice office) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        MandateSavedData data = MandateSavedData.get(server);
        if (player == null) {
            data.pendingOfficeUpdates().put(playerId, office);
            data.setDirty();
            return;
        }
        data.pendingOfficeUpdates().remove(playerId);
        PoliticalDataService.set(player, PoliticalDataService.get(player).withOffice(office));
    }

    private static void notifyPlayer(MinecraftServer server, UUID playerId, Component message) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    /** +1 segundo de mandato no cargo da cadeira vigente; converte em pontos de habilidade daquele cargo. */
    private static void awardSkillPoints(MinecraftServer server) {
        if (!ServerSettings.getBool(server, ConfigKeys.SKILL_POINTS_ENABLED)) {
            return;
        }
        int secondsPerPoint = ServerSettings.getInt(server, ConfigKeys.MINUTES_PER_SKILL_POINT) * 60;
        MandateRegistry registry = MandateSavedData.get(server).registry();
        long now = now(server);
        boolean expiration = expirationEnabled(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<SeatKey> seat = registry.activeSeatOf(player.getUUID(), now, expiration);
            if (seat.isEmpty()) {
                continue;
            }
            PoliticalOffice office = seat.get().office();
            PoliticalData data = PoliticalDataService.get(player);
            SkillProgression.Tick tick = SkillProgression.addOnlineSecond(data.officeProgress(), office, secondsPerPoint);
            PoliticalData updated = data.withOfficeProgress(tick.progress());
            if (tick.gainedPoint()) {
                PoliticalDataService.set(player, updated);
                player.sendSystemMessage(Component.literal("Você ganhou 1 ponto de habilidade de " + office.displayName()
                        + "! Total neste cargo: " + updated.skillPoints(office)).withStyle(ChatFormatting.AQUA));
            } else {
                PoliticalDataService.setSilently(player, updated);
            }
        }
    }

    /** Ex.: "Governador (Espírito Santo)" ou "Presidente da República". */
    public static String seatLabel(SeatKey key) {
        String territoryName = Territories.get(key.territoryId()).map(t -> t.name()).orElse(key.territoryId());
        String base = key.office().displayName() + (key.territoryId().equals(Territories.BRASIL_ID) ? "" : " (" + territoryName + ")");
        return key.office().seatsPerTerritory() > 1 ? base + " #" + key.seat() : base;
    }
}
