package com.tupinicraft.needs;

/**
 * Regras puras das necessidades, aplicadas uma vez por segundo pelo servidor.
 * Valores em unidades de {@link Needs} (10.000 = 100%).
 *
 * <p>Etapa 4.5: Hidratacao e Energia sao dados legados inativos ({@link Need#active()}): {@link #tickSecond}
 * as devolve sem mudanca e {@link #moodTarget} nao as considera. Os multiplicadores {@code hydrationMul} e
 * {@code energyMul} do {@link Context} continuam na assinatura por compatibilidade e sao ignorados.
 */
public final class NeedsRules {
    public static final int BLADDER_PER_SECOND = 10;     // ~17 min
    public static final int BOWEL_PER_SECOND = 5;        // ~33 min
    public static final int MOOD_STEP_PER_SECOND = 10;
    /** Acumulo base do Coom (~21 min de 0 a 100%), multiplicado pela configuracao do servidor. */
    public static final int COOM_PER_SECOND = 8;
    /** Humor perdido no alvo quando o Coom passa de {@link #URGENT}. */
    public static final int COOM_MOOD_PENALTY = 1_000;

    public static final int LOW = 1_500;        // abaixo: Emocao critica
    public static final int URGENT = 8_000;     // acima: bexiga/intestino apertados
    public static final int HAPPY = 8_000;

    /**
     * Situacao do jogador neste segundo. Multiplicadores de habilidades: 1 = normal, menor = gasta menos.
     */
    public record Context(boolean sprinting, boolean sleeping, boolean hot, boolean night, boolean swimming,
                          int vigor, int carisma, float hydrationMul, float energyMul, float bladderMul,
                          float bowelMul, int moodBonus, float coomMul) {
        /** Formato da Etapa 3 (Coom no ritmo normal). */
        public Context(boolean sprinting, boolean sleeping, boolean hot, boolean night, boolean swimming,
                       int vigor, int carisma, float hydrationMul, float energyMul, float bladderMul,
                       float bowelMul, int moodBonus) {
            this(sprinting, sleeping, hot, night, swimming, vigor, carisma, hydrationMul, energyMul, bladderMul,
                    bowelMul, moodBonus, 1F);
        }

        public static Context neutral() {
            return new Context(false, false, false, false, false, 3, 3, 1F, 1F, 1F, 1F, 0, 1F);
        }
    }

    /** Atributos no Coom: Vigor acelera (+5%/ponto acima da base), Carisma segura (-3%/ponto). Faixa 0,6-1,6. */
    public static float coomAttributeMultiplier(int vigor, int carisma) {
        float mul = 1F + (vigor - 3) * 0.05F - (carisma - 3) * 0.03F;
        return Math.max(0.6F, Math.min(1.6F, mul));
    }

    private NeedsRules() {}

    public static Needs tickSecond(Needs n, Context c) {
        // Dormindo, a bexiga e o intestino enchem devagar.
        float sleepMul = c.sleeping() ? 0.3F : 1F;
        int bladder = n.bladder() + Math.round(BLADDER_PER_SECOND * c.bladderMul() * sleepMul);
        int bowel = n.bowel() + Math.round(BOWEL_PER_SECOND * c.bowelMul() * sleepMul);
        int coom = n.coom() + Math.round(COOM_PER_SECOND * c.coomMul()
                * coomAttributeMultiplier(c.vigor(), c.carisma()) * sleepMul);
        // Hidratacao e energia (legadas) ficam como estavam no save.
        Needs partial = new Needs(n.hydration(), n.energy(), bladder, bowel, n.mood(), coom);
        int target = moodTarget(partial, c);
        int step = MOOD_STEP_PER_SECOND + Math.max(0, c.carisma() - 3) * 2;
        int mood = n.mood() < target ? Math.min(target, n.mood() + step) : Math.max(target, n.mood() - step);
        return new Needs(n.hydration(), n.energy(), bladder, bowel, mood, coom);
    }

    /** Para onde a Emocao caminha: base 60% + carisma + habilidades, menos as necessidades ativas mal cuidadas. */
    public static int moodTarget(Needs n, Context c) {
        int target = 6_000 + (c.carisma() - 3) * 250 + c.moodBonus();
        if (n.bladder() > URGENT) target -= 1_500;
        if (n.bowel() > URGENT) target -= 1_500;
        if (n.coom() > URGENT) target -= COOM_MOOD_PENALTY;
        return Needs.clamp(target);
    }

    /** Bexiga ou intestino estouraram: o "acidente" zera o valor e derruba o humor. */
    public static boolean accident(int value) { return value >= Needs.MAX; }

    public static final int ACCIDENT_MOOD_PENALTY = 2_500;
}
