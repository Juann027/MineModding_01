package com.tupinicraft.player;

import com.tupinicraft.config.ServerSettings;
import com.tupinicraft.network.PoliticalDataSyncPayload;
import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** Único ponto de leitura/escrita dos dados políticos do jogador (lado servidor). */
public final class PoliticalDataService {
    private PoliticalDataService() {
    }

    public static PoliticalData get(Player player) {
        return player.getData(ModAttachments.POLITICAL_DATA);
    }

    /** Grava os dados (persistidos pelo Data Attachment) e sincroniza com o cliente do jogador. */
    public static void set(ServerPlayer player, PoliticalData data) {
        player.setData(ModAttachments.POLITICAL_DATA, data);
        sync(player);
    }

    /** Grava sem sincronizar (para contadores internos atualizados com frequência, ex.: tempo de mandato). */
    public static void setSilently(ServerPlayer player, PoliticalData data) {
        player.setData(ModAttachments.POLITICAL_DATA, data);
    }

    public static void sync(ServerPlayer player) {
        PoliticalData data = get(player);
        // Barra de XP: do maior XP mínimo já alcançado até o próximo cargo desbloqueável por XP.
        int floorXp = ServerSettings.xpFloor(player.server, data.politicalXp());
        int nextUnlockXp = ServerSettings.nextUnlock(player.server, data.politicalXp())
                .map((PoliticalOffice next) -> ServerSettings.minPoliticalXp(player.server, next))
                .orElse(-1);
        PacketDistributor.sendToPlayer(player, new PoliticalDataSyncPayload(data, floorXp, nextUnlockXp));
    }

    /** Ressincroniza todos (ex.: após mudar requisitos pela configuração). */
    public static void syncAll(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }
}
