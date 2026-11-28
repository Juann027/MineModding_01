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

    // ------------------------------------------------------------------------------------------------ Etapa 4.14.1

    /** Janela cinematografica mais curta permitida (0,5 s) e mais longa (20 s). */
    public static final int MIN_CINEMATIC_MILLIS = 500, MAX_CINEMATIC_MILLIS = 20_000;
    /** Janelas esperando na fila (alem da que esta na tela); passou disso, a de menor prioridade sai. */
    public static final int CINEMATIC_QUEUE_LIMIT = 6;
    /** Chaves de duplicata lembradas ao mesmo tempo (a mais antiga sai). */
    public static final int DEDUPE_MEMORY = 32;
    /** Intervalo minimo entre dois sons de aviso (sem rajada de sons). */
    public static final long SOUND_GAP_MILLIS = 300L;
    /** Uma janela so e interrompida por outra pelo menos este tanto mais prioritaria... */
    public static final int INTERRUPT_MARGIN = 2;
    /** ...e depois de aparecer por este tempo (segundos), para nao piscar. */
    public static final float INTERRUPT_MIN_AGE = 0.25F;
    /** Saida acelerada de uma janela interrompida (segundos). */
    public static final float INTERRUPT_EXIT = 0.18F;

    public static int clampCinematicMillis(int millis) {
        return Math.max(MIN_CINEMATIC_MILLIS, Math.min(MAX_CINEMATIC_MILLIS, millis));
    }

    /** Duracao da janela: a do aviso, senao o padrao do jogador (segundos), sempre dentro dos limites. */
    public static int cinematicMillis(int explicitMillis, float defaultSeconds) {
        if (explicitMillis > 0) return clampCinematicMillis(explicitMillis);
        return clampCinematicMillis(Math.round(defaultSeconds * 1000F));
    }

    /** A nova janela corta a atual? So se for bem mais prioritaria e a atual ja tiver aparecido. */
    public static boolean shouldInterrupt(int currentPriority, int incomingPriority, float currentAgeSeconds) {
        return incomingPriority >= currentPriority + INTERRUPT_MARGIN && currentAgeSeconds >= INTERRUPT_MIN_AGE;
    }

    /** Pode tocar som agora? Respeita {@link #SOUND_GAP_MILLIS} desde o ultimo. */
    public static boolean soundAllowed(long lastSoundMillis, long nowMillis) {
        return lastSoundMillis <= 0L || nowMillis < lastSoundMillis || nowMillis - lastSoundMillis >= SOUND_GAP_MILLIS;
    }

    /**
     * Tempo de entrada da janela (s): mais curto em avisos breves (nunca passa de um terco da duracao) e com menos
     * animacao. {@code anim} 0..1 (0 = so esmaecer).
     */
    public static float cinematicIn(float seconds, float anim) {
        float t = 0.12F + 0.26F * clamp01(anim);
        return Math.min(t, seconds / 3F);
    }

    public static float cinematicOut(float seconds, float anim) {
        float t = 0.10F + 0.22F * clamp01(anim);
        return Math.min(t, seconds / 3F);
    }

    /** 0..1 de abertura da janela no instante {@code age} de {@code seconds}. */
    public static float cinematicVisibility(float age, float seconds, float anim) {
        if (age < 0 || age > seconds) return 0F;
        float in = cinematicIn(seconds, anim), out = cinematicOut(seconds, anim);
        float a = in <= 0 ? 1F : Math.min(1F, age / in);
        float b = out <= 0 ? 1F : Math.min(1F, (seconds - age) / out);
        return Math.max(0F, Math.min(a, b));
    }

    static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
}
