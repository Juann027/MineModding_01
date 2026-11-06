package com.tupinicraft.client;

import com.tupinicraft.network.PoliticalDataSyncPayload;
import com.tupinicraft.player.PoliticalData;

/**
 * Cópia visual (somente leitura) dos dados políticos do jogador local, recebida do servidor.
 * Não é fonte de verdade; é limpa ao desconectar.
 */
public final class ClientPoliticalCache {
    private static PoliticalDataSyncPayload state;

    private ClientPoliticalCache() {
    }

    public static PoliticalData get() {
        return state == null ? null : state.data();
    }

    /** XP mínimo efetivo do cargo atual (configuração do servidor). */
    public static int currentOfficeMinXp() {
        return state == null ? 0 : state.currentOfficeMinXp();
    }

    /** XP mínimo efetivo do próximo cargo, ou -1 se não houver. */
    public static int nextOfficeMinXp() {
        return state == null ? -1 : state.nextOfficeMinXp();
    }

    static void set(PoliticalDataSyncPayload payload) {
        state = payload;
    }

    static void clear() {
        state = null;
    }
}
