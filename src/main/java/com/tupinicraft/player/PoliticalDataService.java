package com.tupinicraft.player;

import com.tupinicraft.network.PoliticalDataSyncPayload;
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

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new PoliticalDataSyncPayload(get(player)));
    }
}
