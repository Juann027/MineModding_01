package com.respectcraft.profile;

import java.util.List;
import java.util.random.RandomGenerator;

/** Regras da roleta de nascimento (puras; quem chama e sempre o servidor). */
public final class BirthRoulette {
    /** Pontos extras distribuidos ao acaso sobre a base. */
    public static final int EXTRA_POINTS = 12;
    /** Teto de um atributo no sorteio. */
    public static final int ROLL_CAP = 8;
    /** Novas tentativas permitidas depois do primeiro giro. */
    public static final int FREE_REROLLS = 1;

    /**
     * Resultado de um nascimento (Etapa 4.7): a Speciality sorteada (raridade primeiro), os seis atributos e os
     * tracos de nascimento. Specialities nao dao bonus de atributo nem efeito; os tracos sao sorteados a parte.
     * As origens sociais ({@link BirthOrigin}) nao sao mais sorteadas: so existem em fichas antigas.
     */
    public record Result(Speciality speciality, int[] attributes, List<BirthTrait> traits) {
        public Result {
            if (speciality == null) throw new IllegalArgumentException("birth without speciality");
            traits = traits == null ? List.of() : List.copyOf(traits);
        }

        public SpecialityRarity rarity() { return speciality.rarity(); }
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

    /** Sorteio com a distribuicao aprovada (testes e ferramentas puras). */
    public static Result roll(RandomGenerator random, BirthTrait.Body body) {
        return roll(random, body, SpecialityOdds.DEFAULT, null);
    }

    /**
     * Sorteio do nascimento (o servidor passa a distribuicao da config). Ordem: raridade, Speciality dentro da
     * raridade, atributos, tracos. {@code forcedRarity} so vem do teste administrativo; null = sorteio normal.
     * O corpo filtra quais tracos (e Specialities) podem sair.
     */
    public static Result roll(RandomGenerator random, BirthTrait.Body body, SpecialityOdds odds, SpecialityRarity forcedRarity) {
        SpecialityRoll.Result picked = forcedRarity == null
                ? SpecialityRoll.roll(random, odds, Specialities.CATALOG, body)
                : SpecialityRoll.pickWithin(random, forcedRarity, Specialities.CATALOG, body);
        if (!picked.hasSpeciality()) {
            // Nunca redistribui: no catalogo oficial toda raridade tem Speciality (o autoteste garante).
            throw new IllegalStateException("no speciality registered for rarity " + picked.rarity().id());
        }
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
        for (int i = 0; i < values.length; i++) values[i] = CharacterAttribute.clamp(values[i]);
        // Tracos depois dos atributos: independentes da Speciality; o corpo so filtra quais podem sair.
        return new Result(picked.speciality(), values, BirthTrait.roll(random, body));
    }

    /** Pontos de habilidade iniciais: 2, mais 1 a cada 2 pontos de Inteligencia acima da base. */
    public static int startingSkillPoints(int inteligencia) {
        return 2 + Math.max(0, (inteligencia - CharacterAttribute.BASE) / 2);
    }
}
