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
        WORLD_BACKDROP = b.comment("Desaturate and tint the world behind the menu (Rimulib screen effect).").define("worldBackdrop", true);
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
