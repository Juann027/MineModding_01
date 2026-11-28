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
    public static final ModConfigSpec.BooleanValue NOTICE_TOASTS;
    public static final ModConfigSpec.BooleanValue NOTICE_SOUNDS;
    // Etapa 4.14.1: Notification HUD V2 (janela cinematografica) e Etapa 4.14.3: rastreador de missoes.
    public static final ModConfigSpec.BooleanValue CINEMATIC;
    public static final ModConfigSpec.DoubleValue CINEMATIC_SCALE;
    public static final ModConfigSpec.DoubleValue CINEMATIC_X;
    public static final ModConfigSpec.DoubleValue CINEMATIC_Y;
    public static final ModConfigSpec.DoubleValue CINEMATIC_OPACITY;
    public static final ModConfigSpec.DoubleValue NOTICE_VOLUME;
    public static final ModConfigSpec.DoubleValue CINEMATIC_SECONDS;
    public static final ModConfigSpec.DoubleValue CINEMATIC_ANIM;
    public static final ModConfigSpec.DoubleValue CINEMATIC_FX;
    public static final ModConfigSpec.BooleanValue TRACKER;
    public static final ModConfigSpec.DoubleValue TRACKER_SCALE;
    public static final ModConfigSpec.DoubleValue TRACKER_X;
    public static final ModConfigSpec.DoubleValue TRACKER_Y;
    public static final ModConfigSpec.DoubleValue TRACKER_OPACITY;

    /** Etapa 4.8.4-A: holograma no mundo (padrao) ou a tela da 4.8.1 (reserva temporaria de desenvolvimento). */
    public enum Mode { WORLD, SCREEN }

    public static final float MIN_SCALE = 0.8F, MAX_SCALE = 1.25F;
    public static final float MIN_OPACITY = 0.5F;
    public static final float MIN_CINEMATIC_SCALE = 0.6F, MAX_CINEMATIC_SCALE = 1.5F;

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
        // Etapa 4.11: avisos holograficos automaticos (chefes, portais, eventos, caca a recompensa).
        NOTICE_TOASTS = b.comment("Show holographic notice cards on the game screen (they always stay in the Alerts section).")
                .define("noticeToasts", true);
        NOTICE_SOUNDS = b.comment("Play a sound when a holographic notice card appears.").define("noticeSounds", true);
        // Etapa 4.14.1
        CINEMATIC = b.comment("Show cinematic system windows (quest discovered, completed, warnings...) in front of your view.",
                "Turned off, they show up as normal cards instead. Nothing in the game depends on this.").define("cinematicNotices", true);
        CINEMATIC_SCALE = b.comment("Size of the cinematic window.").defineInRange("cinematicScale", 1.0, MIN_CINEMATIC_SCALE, MAX_CINEMATIC_SCALE);
        CINEMATIC_X = b.comment("Horizontal position of the cinematic window (0 = left, 0.5 = center, 1 = right).").defineInRange("cinematicX", 0.5, 0.0, 1.0);
        CINEMATIC_Y = b.comment("Vertical position of the cinematic window (0 = top, 1 = bottom).").defineInRange("cinematicY", 0.3, 0.0, 1.0);
        CINEMATIC_OPACITY = b.comment("Opacity of the cinematic window glass (text stays opaque).").defineInRange("cinematicOpacity", 0.88, MIN_OPACITY, 1.0);
        NOTICE_VOLUME = b.comment("Volume of notice sounds (cards and cinematic windows). 0 = silent.").defineInRange("noticeVolume", 0.8, 0.0, 1.0);
        CINEMATIC_SECONDS = b.comment("Default duration of a cinematic window in seconds (a notice may ask for its own).")
                .defineInRange("cinematicSeconds", 4.0, 1.0, 10.0);
        CINEMATIC_ANIM = b.comment("Intensity of the cinematic window's opening and closing animation (0 = simple fade).")
                .defineInRange("cinematicAnimation", 1.0, 0.0, 1.0);
        CINEMATIC_FX = b.comment("Intensity of the cinematic window's glow, scan lines and flicker (0 = none).")
                .defineInRange("cinematicEffects", 1.0, 0.0, 1.0);
        // Etapa 4.14.3
        TRACKER = b.comment("Show the quest tracker (the quests you chose to track) on the game screen.").define("questTracker", true);
        TRACKER_SCALE = b.comment("Size of the quest tracker.").defineInRange("trackerScale", 1.0, MIN_CINEMATIC_SCALE, MAX_CINEMATIC_SCALE);
        TRACKER_X = b.comment("Horizontal position of the quest tracker (0 = left, 1 = right).").defineInRange("trackerX", 1.0, 0.0, 1.0);
        TRACKER_Y = b.comment("Vertical position of the quest tracker (0 = top, 1 = bottom).").defineInRange("trackerY", 0.38, 0.0, 1.0);
        TRACKER_OPACITY = b.comment("Opacity of the quest tracker glass.").defineInRange("trackerOpacity", 0.75, 0.3, 1.0);
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

    public static boolean noticeToasts() { return !SPEC.isLoaded() || NOTICE_TOASTS.get(); }
    public static boolean noticeSounds() { return !SPEC.isLoaded() || NOTICE_SOUNDS.get(); }

    // Etapa 4.14.1 / 4.14.3
    public static boolean cinematic() { return !SPEC.isLoaded() || CINEMATIC.get(); }
    public static float cinematicScale() { return d(CINEMATIC_SCALE, 1F); }
    public static float cinematicX() { return d(CINEMATIC_X, 0.5F); }
    public static float cinematicY() { return d(CINEMATIC_Y, 0.3F); }
    public static float cinematicOpacity() { return d(CINEMATIC_OPACITY, 0.88F); }
    public static float noticeVolume() { return noticeSounds() ? d(NOTICE_VOLUME, 0.8F) : 0F; }
    public static float rawNoticeVolume() { return d(NOTICE_VOLUME, 0.8F); }
    public static float cinematicSeconds() { return d(CINEMATIC_SECONDS, 4F); }
    /** Animacao efetiva da janela (0 com movimento reduzido). */
    public static float cinematicAnim() { return reducedMotion() ? 0F : d(CINEMATIC_ANIM, 1F); }
    public static float rawCinematicAnim() { return d(CINEMATIC_ANIM, 1F); }
    /** Efeitos efetivos da janela (0 no alto contraste; metade com movimento reduzido: brilho sim, tremor nao). */
    public static float cinematicFx() {
        if (highContrast()) return 0F;
        float v = d(CINEMATIC_FX, 1F);
        return reducedMotion() ? v * 0.5F : v;
    }
    public static float rawCinematicFx() { return d(CINEMATIC_FX, 1F); }
    public static boolean tracker() { return !SPEC.isLoaded() || TRACKER.get(); }
    public static float trackerScale() { return d(TRACKER_SCALE, 1F); }
    public static float trackerX() { return d(TRACKER_X, 1F); }
    public static float trackerY() { return d(TRACKER_Y, 0.38F); }
    public static float trackerOpacity() { return d(TRACKER_OPACITY, 0.75F); }

    private static float d(ModConfigSpec.DoubleValue v, float fallback) {
        return SPEC.isLoaded() ? (float) (double) v.get() : fallback;
    }

    static void setDouble(ModConfigSpec.DoubleValue value, float v) { set(value, Math.round(v * 20F) / 20.0); }
    static void toggle(ModConfigSpec.BooleanValue value) { if (SPEC.isLoaded()) value.set(!value.get()); }

    public static boolean worldMode() { return !SPEC.isLoaded() || MODE.get() == Mode.WORLD; }
    public static float worldDistance() { return SPEC.isLoaded() ? (float) (double) WORLD_DISTANCE.get() : 2F; }
    public static float surfaceDensity() { return SPEC.isLoaded() ? (float) (double) SURFACE_DENSITY.get() : 3F; }
    public static float dismissDistance() { return SPEC.isLoaded() ? (float) (double) DISMISS_DISTANCE.get() : 16F; }

    static void setScale(float v) { set(SCALE, Math.round(v * 20F) / 20.0); }
    static void setOpacity(float v) { set(OPACITY, Math.round(v * 20F) / 20.0); }
    static void setMotion(float v) { set(MOTION, Math.round(v * 20F) / 20.0); }
    static void toggleReducedMotion() { if (SPEC.isLoaded()) REDUCED_MOTION.set(!REDUCED_MOTION.get()); }
    static void toggleHighContrast() { if (SPEC.isLoaded()) HIGH_CONTRAST.set(!HIGH_CONTRAST.get()); }
    static void toggleNoticeToasts() { if (SPEC.isLoaded()) NOTICE_TOASTS.set(!NOTICE_TOASTS.get()); }
    static void toggleBackdrop() { if (SPEC.isLoaded()) WORLD_BACKDROP.set(!WORLD_BACKDROP.get()); }

    private static void set(ModConfigSpec.DoubleValue value, double v) {
        if (SPEC.isLoaded()) value.set(v);
    }

    /** Grava o arquivo (chamado ao fechar o painel Display, nao a cada arrasto). */
    static void save() {
        if (SPEC.isLoaded()) SPEC.save();
    }
}
