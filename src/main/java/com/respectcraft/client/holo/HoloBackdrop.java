package com.respectcraft.client.holo;

import com.respectcraft.RespectCraft;
import cyou.rimuru.rimulib.mc1211.client.api.FxApi;

/**
 * Fundo do menu holografico pelo Rimulib ({@code FxApi}): enquanto o menu esta aberto, o mundo atras dele perde
 * cor, ganha uma vinheta azul e uma varredura fraca. O efeito e uma passada propria
 * ({@code assets/respectcraft/shaders/fx/holo_backdrop.fsh}) que roda no pos-processamento do mundo, antes da GUI:
 * nunca toca no texto dos paineis. A intensidade segue o envelope de abertura/fechamento; com 0 a camada sai.
 *
 * <p>Conferido no jar 1.3.0: os campos de {@code Frame.screen()} ({@code desaturate}, {@code vignette}...) so viram
 * uniformes, e o {@code composite.fsh} embutido copia a cena sem usa-los; por isso o menu usa a propria passada
 * ({@code Frame.pass}), o caminho documentado para efeitos de mod.
 */
final class HoloBackdrop {
    private static final String PASS = "holo_backdrop";
    private static FxApi.Stage stage;
    private static float intensity;
    private static boolean attached;
    private static boolean failed;

    private static final FxApi.Layer LAYER = frame -> {
        float k = intensity;
        if (k <= 0.001F) return;
        frame.pass(PASS, u -> {
            u.f1("Strength", k);
            u.f3("Tint", 0.05F, 0.22F, 0.36F);
        });
    };

    private HoloBackdrop() {}

    /** Cria o estagio uma unica vez, na thread de render (FMLClientSetupEvent.enqueueWork). */
    static void init() {
        if (stage != null || failed) return;
        try {
            stage = FxApi.stage(RespectCraft.MOD_ID);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            RespectCraft.LOGGER.warn("Rimulib FxApi unavailable; holographic menu keeps a plain dimmed backdrop", e);
        }
    }

    /** 0 = sem efeito (camada removida), 1 = efeito inteiro. */
    static void set(float k) {
        intensity = HoloConfig.worldBackdrop() ? Math.max(0F, Math.min(1F, k)) : 0F;
        if (stage == null) return;
        if (intensity > 0.001F && !attached) {
            stage.add(LAYER);
            attached = true;
        } else if (intensity <= 0.001F && attached) {
            stage.remove(LAYER);
            attached = false;
        }
    }

    static void clear() { set(0F); }
}
