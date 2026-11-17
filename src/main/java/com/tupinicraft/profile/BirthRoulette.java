package com.tupinicraft.profile;

import java.util.random.RandomGenerator;

/** Regras da roleta de nascimento (puras; quem chama e sempre o servidor). */
public final class BirthRoulette {
    /** Pontos extras distribuidos ao acaso sobre a base, antes do bonus da origem. */
    public static final int EXTRA_POINTS = 12;
    /** Teto de um atributo antes do bonus da origem. */
    public static final int ROLL_CAP = 8;
    /** Novas tentativas permitidas depois do primeiro giro. */
    public static final int FREE_REROLLS = 1;

    public record Result(BirthOrigin origin, int[] attributes) {}

    private BirthRoulette() {}

    public static BirthOrigin pickOrigin(RandomGenerator random) {
        int roll = random.nextInt(BirthOrigin.totalWeight());
        for (BirthOrigin origin : BirthOrigin.values()) {
            roll -= origin.weight();
            if (roll < 0) return origin;
        }
        return BirthOrigin.values()[0];
    }

    public static Result roll(RandomGenerator random) {
        BirthOrigin origin = pickOrigin(random);
        CharacterAttribute[] all = CharacterAttribute.values();
        int[] values = new int[all.length];
        java.util.Arrays.fill(values, CharacterAttribute.BASE);
        int remaining = EXTRA_POINTS;
        int guard = 0;
        while (remaining > 0 && guard++ < 1000) {
            int index = random.nextInt(values.length);
            if (values[index] < ROLL_CAP) {
                values[index]++;
                remaining--;
            }
        }
        for (CharacterAttribute attribute : all) {
            values[attribute.ordinal()] = CharacterAttribute.clamp(values[attribute.ordinal()] + origin.bonus(attribute));
        }
        return new Result(origin, values);
    }

    /** Pontos de habilidade iniciais: 2, mais 1 a cada 2 pontos de Inteligencia acima da base. */
    public static int startingSkillPoints(int inteligencia) {
        return 2 + Math.max(0, (inteligencia - CharacterAttribute.BASE) / 2);
    }
}
