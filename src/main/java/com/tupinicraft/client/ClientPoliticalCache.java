package com.tupinicraft.client;

import com.tupinicraft.player.PoliticalData;

/**
 * Cópia visual (somente leitura) dos dados políticos do jogador local, recebida do servidor.
 * Não é fonte de verdade; é limpa ao desconectar.
 */
public final class ClientPoliticalCache {
    private static PoliticalData data;

    private ClientPoliticalCache() {
    }

    public static PoliticalData get() {
        return data;
    }

    static void set(PoliticalData newData) {
        data = newData;
    }

    static void clear() {
        data = null;
    }
}
