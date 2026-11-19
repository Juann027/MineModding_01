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

    /** Distancia maxima (ao quadrado) entre os participantes antes de a cena ser cancelada. */
    public static final double MAX_SESSION_DISTANCE_SQUARED = 16.0D;

    /**
     * Etapa 4.8: o contador de derrotas (🍆💦) e so de JOGADOR. Mob ou chefe vencido nunca ganha contador,
     * estatistica nem plaqueta; jogador vencido por mob continua contando, como antes.
     */
    public static boolean countsDefeat(com.respectcraft.finisher.FinisherParticipants.Kind victim) {
        return victim == com.respectcraft.finisher.FinisherParticipants.Kind.PLAYER;
    }

    /**
     * Motivo para cancelar a cena neste tick, ou null para seguir. "Presente" = vivo, nao removido (despawn,
     * descarregado) e, se jogador, conectado. Dimensao diferente e distancia excessiva tambem cancelam.
     */
    public static String abortReason(boolean attackerPresent, boolean victimPresent, boolean sameLevel, double distanceSquared) {
        if (!attackerPresent || !victimPresent) return "participant unavailable";
        if (!sameLevel) return "participant changed dimension";
        if (distanceSquared > MAX_SESSION_DISTANCE_SQUARED) return "participants too far apart";
        return null;
    }
}
