package com.tupinicraft.client.hud;

/**
 * Instantâneo somente visual dos medidores do jogador (todos variam de 0 a 100).
 *
 * <p>Por enquanto não existe sistema de atributos, persistência ou pacotes de
 * sincronização no projeto. PREVIEW é propositalmente uma amostra fixa, e não
 * deve ser confundido com valores do jogo. Quando as mecânicas existirem,
 * a camada de cliente poderá receber instantâneos enviados pelo servidor.
 */
public record HudMeters(int coom, int bladder, int bowel, int emotion) {
    public static final HudMeters PREVIEW = new HudMeters(72, 85, 43, 65);

    public HudMeters {
        coom = clamp(coom);
        bladder = clamp(bladder);
        bowel = clamp(bowel);
        emotion = clamp(emotion);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
