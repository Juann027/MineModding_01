package com.tupinicraft.needs;

/**
 * Regras puras das necessidades, aplicadas uma vez por segundo pelo servidor.
 * Valores em unidades de {@link Needs} (10.000 = 100%).
 */
public final class NeedsRules {
    public static final int HYDRATION_PER_SECOND = 12;   // ~14 min de cheio a vazio
    public static final int ENERGY_PER_SECOND = 6;       // ~28 min
    public static final int ENERGY_SLEEP_PER_SECOND = 80;
    public static final int BLADDER_PER_SECOND = 10;     // ~17 min
    public static final int BOWEL_PER_SECOND = 5;        // ~33 min
    public static final int MOOD_STEP_PER_SECOND = 10;

    public static final int LOW = 1_500;        // abaixo: efeitos negativos (sede, cansaco, tristeza)
    public static final int URGENT = 8_000;     // acima: bexiga/intestino apertados
    public static final int HAPPY = 8_000;

    /**
     * Situacao do jogador neste segundo. Multiplicadores de habilidades: 1 = normal, menor = gasta menos.
     */
    public record Context(boolean sprinting, boolean sleeping, boolean hot, boolean night, boolean swimming,
                          int vigor, int carisma, float hydrationMul, float energyMul, float bladderMul,
                          float bowelMul, int moodBonus) {
        public static Context neutral() {
            return new Context(false, false, false, false, false, 3, 3, 1F, 1F, 1F, 1F, 0);
        }
    }

    private NeedsRules() {}

    public static Needs tickSecond(Needs n, Context c) {
        float vigorMul = Math.max(0.6F, 1F - (c.vigor() - 3) * 0.04F);
        float hydrationLoss = HYDRATION_PER_SECOND * vigorMul * c.hydrationMul()
                * (c.sprinting() ? 2F : 1F) * (c.hot() ? 1.5F : 1F) * (c.swimming() ? 0.5F : 1F);
        int energy;
        if (c.sleeping()) {
            energy = n.energy() + ENERGY_SLEEP_PER_SECOND;
        } else {
            energy = n.energy() - Math.round(ENERGY_PER_SECOND * vigorMul * c.energyMul()
                    * (c.sprinting() ? 1.5F : 1F) * (c.night() ? 1.3F : 1F));
        }
        // Dormindo, a bexiga e o intestino enchem devagar.
        float sleepMul = c.sleeping() ? 0.3F : 1F;
        int bladder = n.bladder() + Math.round(BLADDER_PER_SECOND * c.bladderMul() * sleepMul);
        int bowel = n.bowel() + Math.round(BOWEL_PER_SECOND * c.bowelMul() * sleepMul);
        int hydration = n.hydration() - Math.round(hydrationLoss * sleepMul);
        Needs partial = new Needs(hydration, energy, bladder, bowel, n.mood());
        int target = moodTarget(partial, c);
        int step = MOOD_STEP_PER_SECOND + Math.max(0, c.carisma() - 3) * 2;
        int mood = n.mood() < target ? Math.min(target, n.mood() + step) : Math.max(target, n.mood() - step);
        return new Needs(hydration, energy, bladder, bowel, mood);
    }

    /** Para onde o humor caminha: base 60% + carisma + habilidades, menos as necessidades mal cuidadas. */
    public static int moodTarget(Needs n, Context c) {
        int target = 6_000 + (c.carisma() - 3) * 250 + c.moodBonus();
        if (n.hydration() < LOW) target -= 2_000;
        if (n.energy() < LOW) target -= 1_500;
        if (n.bladder() > URGENT) target -= 1_500;
        if (n.bowel() > URGENT) target -= 1_500;
        return Needs.clamp(target);
    }

    /** Bexiga ou intestino estouraram: o "acidente" zera o valor e derruba o humor. */
    public static boolean accident(int value) { return value >= Needs.MAX; }

    public static final int ACCIDENT_MOOD_PENALTY = 2_500;
}
