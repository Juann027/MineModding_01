package com.respectcraft.profile;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Sorteio da Speciality (servidor autoritativo): primeiro a raridade pela {@link SpecialityOdds}, depois uma
 * Speciality elegivel <i>dentro</i> dessa raridade (uniforme entre as elegiveis).
 *
 * <p>Sem redistribuicao silenciosa: se a raridade sorteada nao tem Speciality elegivel, o resultado fica com essa
 * raridade, sem Speciality e com {@link Status#NO_SPECIALITY_IN_RARITY}. Nunca se sorteia de novo nem se cai para
 * outra raridade. (No catalogo oficial toda raridade tem Speciality para os dois corpos: o autoteste confere.) Puro.
 */
public final class SpecialityRoll {
    private SpecialityRoll() {}

    public enum Status {
        /** Raridade e Speciality sorteadas. */
        ROLLED,
        /** A raridade sorteada nao tem Speciality cadastrada (ou nenhuma elegivel para o corpo). */
        NO_SPECIALITY_IN_RARITY
    }

    /** {@code speciality} e null quando {@code status == NO_SPECIALITY_IN_RARITY}. */
    public record Result(SpecialityRarity rarity, Speciality speciality, Status status) {
        public boolean hasSpeciality() { return speciality != null; }
    }

    public static Result roll(RandomGenerator random, SpecialityOdds odds, Specialities catalog, BirthTrait.Body body) {
        SpecialityRarity rarity = odds.roll(random);
        return pickWithin(random, rarity, catalog, body);
    }

    /**
     * Segunda etapa do sorteio: Speciality elegivel dentro da raridade ja sorteada. Tambem e o caminho do teste
     * administrativo que forca uma raridade ({@code /respectcraft admin roulette <player> <rarity>}).
     */
    public static Result pickWithin(RandomGenerator random, SpecialityRarity rarity, Specialities catalog,
            BirthTrait.Body body) {
        List<Speciality> eligible = catalog.eligible(rarity, body);
        if (eligible.isEmpty()) return new Result(rarity, null, Status.NO_SPECIALITY_IN_RARITY);
        return new Result(rarity, eligible.get(random.nextInt(eligible.size())), Status.ROLLED);
    }
}
