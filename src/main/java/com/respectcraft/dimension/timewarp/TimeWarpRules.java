package com.respectcraft.dimension.timewarp;

/**
 * Regras puras do Time Warp (Etapa 4.15), sem Minecraft vivo: escala de viagem, estagios de exposicao, recuperacao
 * fora da dimensao, carga do relogio, cooldown e o espacamento dos acontecimentos. Conferidas no {@code SelfTest}.
 */
public final class TimeWarpRules {
    private TimeWarpRules() {}

    /** {@code coordinate_scale} do {@code dimension_type/time_warp.json}: 1 bloco no Time Warp = 3,33 no Overworld. */
    public static final double SCALE = 3.33;
    public static final int TICKS_PER_SECOND = 20;

    /** Estagios de exposicao (o pacote e o log usam o ordinal/id: novos entram no fim). */
    public enum Stage {
        STABLE("stable"), ANOMALY("anomaly"), INSTABILITY("instability"), CRITICAL("critical");

        private final String id;

        Stage(String id) { this.id = id; }

        public String id() { return id; }

        public static Stage byOrdinal(int o) {
            Stage[] all = values();
            return o >= 0 && o < all.length ? all[o] : STABLE;
        }
    }

    // ================================================================================================ escala

    /**
     * Overworld -> Time Warp (eixo x ou z). O fator vem dos dois {@code coordinate_scale} pelo vanilla
     * ({@code DimensionType.getTeleportationScale}); aqui e so a conta, aplicada uma unica vez.
     */
    public static double toWarp(double overworld, double factor) {
        return overworld * factor;
    }

    /** Fator Overworld -> Time Warp a partir dos dois {@code coordinate_scale} (mesma formula do vanilla). */
    public static double factor(double fromScale, double toScale) {
        return fromScale / toScale;
    }

    /** Aperta a coordenada dentro da borda do mundo, com margem. */
    public static double clampToBorder(double v, double min, double max, double margin) {
        double lo = min + margin, hi = max - margin;
        if (lo > hi) return (min + max) / 2;
        return Math.max(lo, Math.min(hi, v));
    }

    // ================================================================================================ exposicao

    /** Segundos de cada faixa: 0-60 estavel, 60-120 anomalia, 120-180 instabilidade, 180+ critico. */
    public static Stage stage(int exposureTicks, int stageSeconds) {
        int s = exposureTicks / TICKS_PER_SECOND;
        int len = Math.max(1, stageSeconds);
        if (s >= len * 3) return Stage.CRITICAL;
        if (s >= len * 2) return Stage.INSTABILITY;
        if (s >= len) return Stage.ANOMALY;
        return Stage.STABLE;
    }

    /** Exposicao depois de um tick fora do Time Warp: cai {@code recoveryRate} ticks por tick (nunca abaixo de 0). */
    public static int recover(int exposureTicks, int recoveryRate) {
        return Math.max(0, exposureTicks - Math.max(0, recoveryRate));
    }

    /** Teto da exposicao gravada (evita numero crescendo para sempre): 4 estagios + 1. */
    public static int cap(int exposureTicks, int stageSeconds) {
        return Math.min(exposureTicks, Math.max(1, stageSeconds) * TICKS_PER_SECOND * 5);
    }

    // ================================================================================================ relogio

    /**
     * Carga do relogio: o servidor recebe um "uso" a cada ~4 ticks enquanto o botao fica apertado. A carga continua
     * se o uso anterior foi ha no maximo {@link #CHARGE_GAP_TICKS}; senao recomeca.
     */
    public static final int CHARGE_GAP_TICKS = 10;

    public static boolean chargeContinues(long lastUseTick, long now) {
        return lastUseTick >= 0 && now - lastUseTick <= CHARGE_GAP_TICKS && now >= lastUseTick;
    }

    public static boolean chargeComplete(long chargeStart, long now, int chargeTicks) {
        return chargeStart >= 0 && now - chargeStart >= chargeTicks;
    }

    /** Cooldown entre viagens (relogio do mundo, sobrevive a logout). */
    public static boolean cooldownOver(long lastTravelTick, long now, int cooldownTicks) {
        return lastTravelTick < 0 || now < lastTravelTick || now - lastTravelTick >= cooldownTicks;
    }

    public static int cooldownLeftSeconds(long lastTravelTick, long now, int cooldownTicks) {
        if (cooldownOver(lastTravelTick, now, cooldownTicks)) return 0;
        return (int) ((cooldownTicks - (now - lastTravelTick) + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }

    /** Recuperacao sem relogio: parado e olhando para o ceu por este tempo leva de volta ao ponto de entrada (sem escala). */
    public static final int DRIFT_TICKS = 15 * TICKS_PER_SECOND;
    /** "Olhando para o ceu": inclinacao do olhar (graus; -90 = reto para cima). */
    public static final float DRIFT_PITCH = -70F;

    // ================================================================================================ acontecimentos

    /** Intervalo minimo (segundos) entre acontecimentos de um jogador por estagio. Estavel quase nunca. */
    public static int minGapSeconds(Stage stage) {
        return switch (stage) {
            case STABLE -> 50;
            case ANOMALY -> 28;
            case INSTABILITY -> 16;
            case CRITICAL -> 9;
        };
    }

    /** Chance por segundo (em milesimos), depois do intervalo minimo, de algo acontecer. */
    public static int chancePerMille(Stage stage, double frequency) {
        int base = switch (stage) {
            case STABLE -> 6;
            case ANOMALY -> 30;
            case INSTABILITY -> 60;
            case CRITICAL -> 110;
        };
        return (int) Math.round(Math.max(0, Math.min(1000, base * Math.max(0, frequency))));
    }

    /** Pode haver um acontecimento agora? {@code roll} em [0, 1000). */
    public static boolean eventDue(Stage stage, long lastEventTick, long now, int roll, double frequency) {
        if (frequency <= 0) return false;
        long gap = (long) (minGapSeconds(stage) / Math.max(0.25, frequency)) * TICKS_PER_SECOND;
        if (lastEventTick >= 0 && now >= lastEventTick && now - lastEventTick < gap) return false;
        return roll < chancePerMille(stage, frequency);
    }

    /** Neblina (fim do plano distante, blocos) por estagio; o cliente aplica o fator do pulso. */
    public static float fogEnd(Stage stage) {
        return switch (stage) {
            case STABLE -> 34F;
            case ANOMALY -> 30F;
            case INSTABILITY -> 26F;
            case CRITICAL -> 22F;
        };
    }

    /** Dessaturacao da cor da neblina (0 = cor do bioma, 1 = cinza) por estagio. */
    public static float fogGray(Stage stage) {
        return switch (stage) {
            case STABLE -> 0.10F;
            case ANOMALY -> 0.20F;
            case INSTABILITY -> 0.32F;
            case CRITICAL -> 0.45F;
        };
    }
}
