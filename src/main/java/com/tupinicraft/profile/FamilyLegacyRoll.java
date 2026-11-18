package com.tupinicraft.profile;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Sorteio do Family Legacy (servidor autoritativo): primeiro a raridade pela {@link FamilyLegacyOdds}, depois uma
 * familia elegivel <i>dentro</i> dessa raridade (uniforme entre as elegiveis).
 *
 * <p>Sem redistribuicao silenciosa: se a raridade sorteada nao tem familia elegivel, o resultado fica com essa
 * raridade, sem familia e com {@link Status#NO_FAMILY_IN_RARITY}. Nunca se sorteia de novo nem se cai para outra
 * raridade. Puro.
 */
public final class FamilyLegacyRoll {
    private FamilyLegacyRoll() {}

    public enum Status {
        /** Raridade e familia sorteadas. */
        ROLLED,
        /** A raridade sorteada nao tem familia cadastrada (ou nenhuma elegivel para o corpo). */
        NO_FAMILY_IN_RARITY
    }

    /** {@code family} e null quando {@code status == NO_FAMILY_IN_RARITY}. */
    public record Result(FamilyLegacyRarity rarity, FamilyLegacy family, Status status) {
        public boolean hasFamily() { return family != null; }
    }

    public static Result roll(RandomGenerator random, FamilyLegacyOdds odds, FamilyLegacies catalog,
            BirthTrait.Body body) {
        FamilyLegacyRarity rarity = odds.roll(random);
        return pickWithin(random, rarity, catalog, body);
    }

    /** Segunda etapa do sorteio: familia elegivel dentro da raridade ja sorteada. */
    public static Result pickWithin(RandomGenerator random, FamilyLegacyRarity rarity, FamilyLegacies catalog,
            BirthTrait.Body body) {
        List<FamilyLegacy> eligible = catalog.eligible(rarity, body);
        if (eligible.isEmpty()) return new Result(rarity, null, Status.NO_FAMILY_IN_RARITY);
        return new Result(rarity, eligible.get(random.nextInt(eligible.size())), Status.ROLLED);
    }
}
