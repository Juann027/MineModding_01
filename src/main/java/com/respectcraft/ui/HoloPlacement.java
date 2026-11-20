package com.respectcraft.ui;

import java.util.List;

/**
 * Escolha da ancora do holograma no mundo (Etapa 4.8.4-A): onde invocar o arco de placas sem enfia-lo em paredes.
 * Puro (sem classes do cliente nem do nivel): o teste de visada vem de fora ({@link Sight}, no cliente um
 * {@code level.clip}) e o {@code SelfTest} confere com paredes de mentira.
 *
 * <p>O arco inteiro escala em volta do olho: aproximar as placas nao muda o tamanho aparente nem a leitura, so a
 * distancia. Por isso a busca primeiro encolhe a distancia na direcao da mira, depois tenta girar o arco para os lados
 * (a placa da frente continua de frente para o olho) e, se nada couber, fica com o candidato menos obstruido (no empate,
 * o mais perto) e avisa.
 *
 * <p>Pontos de amostra ({@link Sample}) ficam em unidades do raio do arco: {@code forward = 1} e a placa da frente
 * na distancia escolhida. O ponto e aceito se a linha do olho ate ele nao bate em bloco.
 */
public final class HoloPlacement {
    /** Placa da frente, fita de identidade e chips: precisam estar livres. */
    public static final int ESSENTIAL = 0;
    /** Placas vizinhas (+-1 no arco): ate {@link #IMPORTANT_TOLERANCE} delas pode bater. */
    public static final int IMPORTANT = 1;
    /** Placas da ponta: so desempate (o teste de profundidade esconde o que entrar na parede). */
    public static final int OPTIONAL = 2;
    static final float IMPORTANT_TOLERANCE = 0.3F;
    /** Giros do arco tentados depois de encolher a distancia (graus). */
    static final float[] YAW_OFFSETS = {0F, -25F, 25F, -50F, 50F};

    private HoloPlacement() {}

    /** Linha livre (sem bloco solido) do ponto {@code from} ao ponto {@code to}, em coordenadas de mundo. */
    @FunctionalInterface
    public interface Sight {
        boolean clear(double fx, double fy, double fz, double tx, double ty, double tz);
    }

    /** Ponto do arranjo no referencial do olho: direita, cima e frente, em raios do arco. */
    public record Sample(float right, float up, float forward, int kind) {}

    /** Ancora escolhida: yaw do arco (graus do Minecraft), distancia da placa da frente (blocos) e se coube de fato. */
    public record Result(float yaw, float distance, boolean fits) {}

    /** Direcao da frente para um yaw do Minecraft (0 = sul/+z, 90 = oeste/-x). */
    public static double forwardX(float yaw) { return -Math.sin(Math.toRadians(yaw)); }
    public static double forwardZ(float yaw) { return Math.cos(Math.toRadians(yaw)); }
    /** Direita de quem olha para o yaw. */
    public static double rightX(float yaw) { return -Math.cos(Math.toRadians(yaw)); }
    public static double rightZ(float yaw) { return -Math.sin(Math.toRadians(yaw)); }

    /**
     * Procura a ancora. {@code preferred} e a distancia de conforto e {@code min} a menor aceita (blocos); o olho
     * {@code (ex, ey, ez)} vira o centro do arco.
     */
    public static Result choose(double ex, double ey, double ez, float yaw, float preferred, float min,
                                List<Sample> samples, Sight sight) {
        float lo = Math.min(min, preferred), step = Math.max(0.05F, preferred * 0.15F);
        Result best = null;
        float bestScore = Float.MAX_VALUE;
        for (float offset : YAW_OFFSETS) {
            float y = yaw + offset;
            for (float d = preferred; ; d = Math.max(lo, d - step)) {
                float score = score(ex, ey, ez, y, d, samples, sight);
                if (score >= 0F) return new Result(y, d, true);
                // nada coube ainda: menos obstrucao vence; no empate, mais perto (entra menos na parede) e mais reto
                float penalty = -score + d * 0.01F + Math.abs(offset) * 0.001F;
                if (penalty < bestScore) {
                    bestScore = penalty;
                    best = new Result(y, d, false);
                }
                if (d <= lo) break;
            }
        }
        return best == null ? new Result(yaw, lo, false) : best;
    }

    /**
     * Avalia um candidato: {@code >= 0} quando e aceito; negativo = obstrucao ponderada (essencial 4, vizinha 1,
     * ponta 0,25), para escolher o menos ruim quando nada couber.
     */
    static float score(double ex, double ey, double ez, float yaw, float distance, List<Sample> samples, Sight sight) {
        double fx = forwardX(yaw), fz = forwardZ(yaw), rx = rightX(yaw), rz = rightZ(yaw);
        int essential = 0, important = 0, importantTotal = 0, optional = 0;
        for (Sample s : samples) {
            double px = ex + distance * (rx * s.right() + fx * s.forward());
            double py = ey + distance * s.up();
            double pz = ez + distance * (rz * s.right() + fz * s.forward());
            if (s.kind() == IMPORTANT) importantTotal++;
            if (sight.clear(ex, ey, ez, px, py, pz)) continue;
            switch (s.kind()) {
                case ESSENTIAL -> essential++;
                case IMPORTANT -> important++;
                default -> optional++;
            }
        }
        if (essential == 0 && important <= importantTotal * IMPORTANT_TOLERANCE) return 0F;
        return -(essential * 4F + important + optional * 0.25F + 0.001F);
    }
}
