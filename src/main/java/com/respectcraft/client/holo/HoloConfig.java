package com.respectcraft.client.holo;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Opcoes do menu holografico (Etapa 4.8.1), so no cliente: {@code respectcraft-holomenu-client.toml}. Ajustadas
 * tambem pelo painel "Display" dentro do menu. Nenhuma delas muda dados de jogo.
 */
public final class HoloConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SCALE;
    public static final ModConfigSpec.DoubleValue OPACITY;
    public static final ModConfigSpec.DoubleValue MOTION;
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION;
    public static final ModConfigSpec.BooleanValue HIGH_CONTRAST;
    public static final ModConfigSpec.BooleanValue WORLD_BACKDROP;
    public static final ModConfigSpec.EnumValue<Mode> MODE;
    public static final ModConfigSpec.DoubleValue WORLD_DISTANCE;
    public static final ModConfigSpec.DoubleValue SURFACE_DENSITY;
    public static final ModConfigSpec.DoubleValue DISMISS_DISTANCE;

    /** Etapa 4.8.4-A: holograma no mundo (padrao) ou a tela da 4.8.1 (reserva temporaria de desenvolvimento). */
    public enum Mode { WORLD, SCREEN }

    public static final float MIN_SCALE = 0.8F, MAX_SCALE = 1.25F;
    public static final float MIN_OPACITY = 0.5F;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Holographic character menu (K key). Visual only: nothing here changes game data.").push("holomenu");
        SCALE = b.comment("Size of the menu (0.8 = smaller, 1.25 = larger).").defineInRange("scale", 1.0, MIN_SCALE, MAX_SCALE);
        OPACITY = b.comment("Opacity of the glass panels (text always stays fully opaque).").defineInRange("opacity", 0.9, MIN_OPACITY, 1.0);
        MOTION = b.comment("Intensity of parallax, particles and glow (0 = static).").defineInRange("motion", 1.0, 0.0, 1.0);
        REDUCED_MOTION = b.comment("Short fades instead of the assembly animation; no parallax or drifting particles.")
                .define("reducedMotion", false);
        HIGH_CONTRAST = b.comment("Darker, opaque panels and brighter text; no scan lines.").define("highContrast", false);
        WORLD_BACKDROP = b.comment("Desaturate and tint the world behind the menu (Rimulib screen effect). Screen mode only.")
                .define("worldBackdrop", true);
        MODE = b.comment("WORLD = holographic panels anchored in the world (you keep walking, jumping and looking around).",
                        "SCREEN = the older full-screen menu, kept temporarily as a development fallback.")
                .defineEnum("mode", Mode.WORLD);
        WORLD_DISTANCE = b.comment("World mode: preferred distance from your eyes to the front panel, in blocks.",
                        "Closer spaces automatically use a shorter distance; the panels keep the same apparent size.")
                .defineInRange("worldDistance", 2.0, 1.2, 3.5);
        SURFACE_DENSITY = b.comment("World mode: texture pixels per panel unit (higher = sharper text, more video memory).")
                .defineInRange("surfaceDensity", 3.0, 1.5, 4.0);
        DISMISS_DISTANCE = b.comment("World mode: the panels dismiss themselves when you walk farther than this from them (blocks).")
                .defineInRange("dismissDistance", 16.0, 6.0, 64.0);
        b.pop();
        SPEC = b.build();
    }

    private HoloConfig() {}

    public static float scale() { return SPEC.isLoaded() ? (float) (double) SCALE.get() : 1F; }
    public static float opacity() { return SPEC.isLoaded() ? (float) (double) OPACITY.get() : 0.9F; }
    /** Intensidade efetiva dos efeitos (0 com movimento reduzido). */
    public static float motion() {
        if (reducedMotion()) return 0F;
        return SPEC.isLoaded() ? (float) (double) MOTION.get() : 1F;
    }
    public static float rawMotion() { return SPEC.isLoaded() ? (float) (double) MOTION.get() : 1F; }
    public static boolean reducedMotion() { return SPEC.isLoaded() && REDUCED_MOTION.get(); }
    public static boolean highContrast() { return SPEC.isLoaded() && HIGH_CONTRAST.get(); }
    public static boolean worldBackdrop() { return !SPEC.isLoaded() || WORLD_BACKDROP.get(); }

    public static boolean worldMode() { return !SPEC.isLoaded() || MODE.get() == Mode.WORLD; }
    public static float worldDistance() { return SPEC.isLoaded() ? (float) (double) WORLD_DISTANCE.get() : 2F; }
    public static float surfaceDensity() { return SPEC.isLoaded() ? (float) (double) SURFACE_DENSITY.get() : 3F; }
    public static float dismissDistance() { return SPEC.isLoaded() ? (float) (double) DISMISS_DISTANCE.get() : 16F; }

    static void setScale(float v) { set(SCALE, Math.round(v * 20F) / 20.0); }
    static void setOpacity(float v) { set(OPACITY, Math.round(v * 20F) / 20.0); }
    static void setMotion(float v) { set(MOTION, Math.round(v * 20F) / 20.0); }
    static void toggleReducedMotion() { if (SPEC.isLoaded()) REDUCED_MOTION.set(!REDUCED_MOTION.get()); }
    static void toggleHighContrast() { if (SPEC.isLoaded()) HIGH_CONTRAST.set(!HIGH_CONTRAST.get()); }
    static void toggleBackdrop() { if (SPEC.isLoaded()) WORLD_BACKDROP.set(!WORLD_BACKDROP.get()); }

    private static void set(ModConfigSpec.DoubleValue value, double v) {
        if (SPEC.isLoaded()) value.set(v);
    }

    /** Grava o arquivo (chamado ao fechar o painel Display, nao a cada arrasto). */
    static void save() {
        if (SPEC.isLoaded()) SPEC.save();
    }
}
