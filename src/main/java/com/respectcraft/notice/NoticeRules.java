package com.respectcraft.notice;

/**
 * Regras puras dos avisos holograficos (Etapa 4.11), cobertas pelo {@code SelfTest}: duplicatas, quantos cartoes
 * cabem na tela, tamanho do historico e a leitura grosseira de distancia e direcao (tambem usada no rastreio da caca
 * a recompensa: nunca a posicao exata).
 */
public final class NoticeRules {
    private NoticeRules() {}

    /** Mesmo aviso (mesma chave) dentro desta janela e ignorado pelo cliente. */
    public static final long DEDUPE_MILLIS = 20_000L;
    /** Cartoes ao mesmo tempo na tela; o resto espera na fila. */
    public static final int VISIBLE_TOASTS = 3;
    /** Avisos guardados na secao Alerts do menu K (so no cliente, limpos no logout). */
    public static final int HISTORY_LIMIT = 40;
    /** Raio (blocos) em que jogadores recebem o aviso de chefe que apareceu. */
    public static final double BOSS_RADIUS = 160.0;
    /** Raio (blocos) em que jogadores recebem o aviso de portal aceso. */
    public static final double PORTAL_RADIUS = 64.0;

    /** Direcoes em oito ventos, a partir do norte (-Z) no sentido horario. */
    public static final String[] WINDS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /** Chave vazia nunca e duplicata; senao, a mesma chave dentro da janela e. */
    public static boolean isDuplicate(String key, String lastKey, long lastMillis, long nowMillis) {
        if (key == null || key.isEmpty() || !key.equals(lastKey)) return false;
        return nowMillis >= lastMillis && nowMillis - lastMillis < DEDUPE_MILLIS;
    }

    /** Indice em {@link #WINDS} do vetor horizontal (dx, dz); -1 se estiver em cima. */
    public static int direction(double dx, double dz) {
        if (dx * dx + dz * dz < 1e-6) return -1;
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        int i = (int) Math.round(deg / 45.0);
        return Math.floorMod(i, 8);
    }

    /** Distancia arredondada: 5 em 5 ate 100, 10 em 10 ate 1000, depois 100 em 100. */
    public static int roughDistance(double d) {
        if (d < 0) return 0;
        int step = d < 100 ? 5 : d < 1000 ? 10 : 100;
        return (int) (Math.round(d / step) * step);
    }

    /** Dentro do raio (distancia 3D)? */
    public static boolean inRadius(double dx, double dy, double dz, double radius) {
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** 0..1 do cartao na tela: entra em 0.25 s, fica e sai em 0.4 s no fim da duracao. */
    public static float toastVisibility(float age, float seconds) {
        if (age < 0 || age > seconds) return 0F;
        float in = Math.min(1F, age / 0.25F);
        float out = Math.min(1F, (seconds - age) / 0.4F);
        return Math.max(0F, Math.min(in, out));
    }
}
