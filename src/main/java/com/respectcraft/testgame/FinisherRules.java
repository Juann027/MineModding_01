package com.respectcraft.testgame;

/** Regras sem dependencias do jogo: validaveis mesmo sem iniciar o Minecraft. */
public final class FinisherRules {
    public static final int ANIMATION_TICKS = 60;          // 3 segundos: placeholder, nao conteudo adulto
    public static final int INCAPACITATED_TICKS = 200;     // 10 segundos
    public static final int INVITE_TICKS = 300;           // 15 segundos
    public static final int SAME_PAIR_COOLDOWN_TICKS = 1200; // 60 segundos
    public static final int VICTIM_COOLDOWN_TICKS = 300;   // 15 segundos

    private FinisherRules() {}

    public static boolean isVulnerable(float current, float maximum) {
        return maximum > 0 && current > 0 && current < maximum * 0.25F;
    }

    public static boolean isValidMobSize(float width, float height) {
        return width > 0 && height > 0 && width <= 1.15F && height <= 2.1F;
    }

    public static boolean isCooldownOver(long now, long endsAt) {
        return now >= endsAt;
    }
}
