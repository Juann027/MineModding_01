package com.tupinicraft.client.hud;

/**
 * Ponte de leitura para as telas. Não altera o jogador nem grava dados.
 * Todos os valores visíveis são de demonstração até existir sincronização.
 */
public final class HudStatusClient {
    private HudStatusClient() {}

    public static HudMeters current() {
        return HudMeters.PREVIEW;
    }
}
