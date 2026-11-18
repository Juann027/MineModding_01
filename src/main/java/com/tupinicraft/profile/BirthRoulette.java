package com.tupinicraft.profile;

import java.util.List;
import java.util.random.RandomGenerator;

/** Regras da roleta de nascimento (puras; quem chama e sempre o servidor). */
public final class BirthRoulette {
    /** Pontos extras distribuidos ao acaso sobre a base, antes do bonus da origem. */
    public static final int EXTRA_POINTS = 12;
    /** Teto de um atributo antes do bonus da origem. */
    public static final int ROLL_CAP = 8;
    /** Novas tentativas permitidas depois do primeiro giro. */
    public static final int FREE_REROLLS = 1;

    /**
     * Resultado de um nascimento: origem (com seus bonus), os seis atributos e os tracos de nascimento. Os tracos
     * sao sorteados a parte e nao dependem da origem.
     */
    public record Result(BirthOrigin origin, int[] attributes, List<BirthTrait> traits) {
        public Result {
            traits = traits == null ? List.of() : List.copyOf(traits);
        }

        public Result(BirthOrigin origin, int[] attributes) {
            this(origin, attributes, List.of());
        }
    }

    /**
     * Resposta do servidor a um pedido de giro. Todo pedido recebe uma resposta (a tela nunca fica esperando).
     * O ordinal viaja na rede: so acrescente valores no fim.
     */
    public enum Outcome {
        /** Sorteou: a ficha nova ja foi enviada antes da resposta. */
        ROLLED,
        /** Ja nasceu e nao tem mais novo giro. */
        NO_REROLLS,
        /** O pedido foi feito sobre uma ficha desatualizada (outro giro ja aconteceu, ficha zerada etc.). */
        STALE;

        public static Outcome byOrdinal(int ordinal) {
            Outcome[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : STALE;
        }
    }

    /**
     * Valida um pedido de giro contra a ficha atual do servidor. {@code expectedRollCount} e o {@code rollCount}
     * que o cliente via ao pedir: se outro giro ja aconteceu, o pedido e recusado (sem resultado duplicado e sem
     * gastar o novo giro por engano, mesmo com resposta atrasada ou clique repetido).
     */
    public static Outcome validate(CharacterProfile profile, boolean reroll, int expectedRollCount) {
        if (expectedRollCount != profile.rollCount() || reroll != profile.born()) return Outcome.STALE;
        if (profile.born() && !profile.canReroll()) return Outcome.NO_REROLLS;
        return Outcome.ROLLED;
    }

    /**
     * Confirmacao do resultado (Continuar). Aceita so sobre a ficha que o cliente via: depois dela nao ha mais
     * novo giro, entao reabrir a roleta nao permite trocar um resultado ja aceito. Devolve a ficha nova, ou a
     * mesma ficha quando nao ha o que confirmar.
     */
    public static CharacterProfile confirm(CharacterProfile profile, int expectedRollCount) {
        if (!profile.born() || expectedRollCount != profile.rollCount() || profile.birthConfirmed()) return profile;
        return profile.confirmBirth();
    }

    /** Novos giros que restam depois de um giro aceito por {@link #validate}. */
    public static int rerollsAfter(CharacterProfile profile) {
        return profile.born() ? profile.rerolls() - 1 : FREE_REROLLS;
    }

    private BirthRoulette() {}

    public static BirthOrigin pickOrigin(RandomGenerator random) {
        int roll = random.nextInt(BirthOrigin.totalWeight());
        for (BirthOrigin origin : BirthOrigin.values()) {
            roll -= origin.weight();
            if (roll < 0) return origin;
        }
        return BirthOrigin.values()[0];
    }

    /** Sorteio com o conjunto de tracos do corpo informado (o servidor le o corpo da aparencia salva). */
    public static Result roll(RandomGenerator random, BirthTrait.Body body) {
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
        // Tracos depois dos atributos: independentes da origem; o corpo so filtra quais podem sair.
        return new Result(origin, values, BirthTrait.roll(random, body));
    }

    /** Pontos de habilidade iniciais: 2, mais 1 a cada 2 pontos de Inteligencia acima da base. */
    public static int startingSkillPoints(int inteligencia) {
        return 2 + Math.max(0, (inteligencia - CharacterAttribute.BASE) / 2);
    }
}
