package com.tupinicraft.client;

import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * Mede (sem desenhar nada) até onde as barras de chefe vanilla chegam no topo central da tela neste frame,
 * para a faixa de avisos ficar abaixo delas. O NeoForge dispara {@link CustomizeGuiOverlayEvent.BossEventProgress}
 * para cada barra desenhada pela camada {@code BOSS_OVERLAY}, que roda antes da camada da faixa.
 * <p>
 * Estado estático apenas visual e de um único frame (lido e zerado pela faixa a cada renderização).
 */
final class BossBarArea {
    /** Altura da barra desenhada em {@code BossHealthOverlay.drawBar}. */
    private static final int BAR_HEIGHT = 5;

    private static int bottom;

    private BossBarArea() {
    }

    /** Barras canceladas por outros mods não chegam aqui (o listener não recebe eventos cancelados). */
    static void onBossEventProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
        bottom = Math.max(bottom, event.getY() + BAR_HEIGHT);
    }

    /** y logo abaixo da última barra de chefe desenhada neste frame (0 se nenhuma) e zera para o próximo. */
    static int consumeBottom() {
        int value = bottom;
        bottom = 0;
        return value;
    }
}
