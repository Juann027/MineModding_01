package com.tupinicraft.mandate;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.config.ConfigKeys;
import com.tupinicraft.config.ServerSettings;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.player.PoliticalDataService;
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
 */
public final class MandateService {
    private static final int TICKS_PER_SECOND = ServerSettings.TICKS_PER_SECOND;

    private MandateService() {
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Consultas somente leitura sobre o estado atual (mandatos expirados aparecem vagos). */
    public static MandateQueries queries(MinecraftServer server) {
        return new MandateQueries(MandateSavedData.get(server).registry(), now(server),
                ServerSettings.getBool(server, ConfigKeys.TERM_EXPIRATION));
    }

    public static boolean isVacant(MinecraftServer server, SeatKey key) {
        return queries(server).view(key).isVacant();
    }

    public static Optional<UUID> lastOccupant(MinecraftServer server, SeatKey key) {
        return MandateSavedData.get(server).registry().lastOccupant(key);
    }

    /**
     * Dá posse com a duração de mandato configurada agora (mandatos já em curso não mudam).
     * Retorna false se a cadeira já tem ocupante vigente.
     */
    public static boolean inaugurate(MinecraftServer server, SeatKey key, Occupant occupant, Optional<UUID> electionId) {
        MandateSavedData data = MandateSavedData.get(server);
        long start = now(server);
        Mandate mandate = new Mandate(occupant, start, start + ServerSettings.termTicks(server, key.office()),
                Optional.empty(), electionId);
        boolean ok = data.registry().inaugurate(key, mandate, start, ServerSettings.getBool(server, ConfigKeys.TERM_EXPIRATION));
        if (ok) {
            data.setDirty();
            TupiniCraft.LOGGER.info("Posse: {} em {} até o tick {}", occupant.displayName(), key.asString(), mandate.termEnd());
        }
        return ok;
    }

    public static Optional<Mandate> vacate(MinecraftServer server, SeatKey key) {
        MandateSavedData data = MandateSavedData.get(server);
        Optional<Mandate> removed = data.registry().vacate(key);
        removed.ifPresent(mandate -> data.setDirty());
        return removed;
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
        if (!ServerSettings.getBool(server, ConfigKeys.TERM_EXPIRATION)) {
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
            data.registry().vacate(key).ifPresent(mandate -> {
                Component message = Component.literal("[MANDATO] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                        .append(Component.literal("Terminou o mandato de " + mandate.occupant().displayName()
                                + " como " + seatLabel(key) + ".").withStyle(ChatFormatting.WHITE));
                server.getPlayerList().broadcastSystemMessage(message, false);
            });
        }
        if (!expired.isEmpty()) {
            data.setDirty();
        }
    }

    /** +1 segundo de mandato para cada jogador online com mandato vigente; converte em pontos de habilidade. */
    private static void awardSkillPoints(MinecraftServer server) {
        if (!ServerSettings.getBool(server, ConfigKeys.SKILL_POINTS_ENABLED)) {
            return;
        }
        int secondsPerPoint = ServerSettings.getInt(server, ConfigKeys.MINUTES_PER_SKILL_POINT) * 60;
        MandateQueries queries = queries(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (queries.getMandatesOf(player.getUUID()).isEmpty()) {
                continue;
            }
            PoliticalData data = PoliticalDataService.get(player);
            int seconds = data.mandateOnlineSeconds() + 1;
            if (seconds >= secondsPerPoint) {
                PoliticalDataService.set(player, data.withSkillProgress(data.skillPoints() + 1, seconds - secondsPerPoint));
                player.sendSystemMessage(Component.literal("Você ganhou 1 ponto de habilidade pelo tempo de mandato! Total: "
                        + (data.skillPoints() + 1)).withStyle(ChatFormatting.AQUA));
            } else {
                PoliticalDataService.setSilently(player, data.withSkillProgress(data.skillPoints(), seconds));
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
