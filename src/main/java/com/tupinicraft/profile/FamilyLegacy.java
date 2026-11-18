package com.tupinicraft.profile;

/**
 * Uma familia do Family Legacy: id persistente, raridade e quem pode recebe-la num sorteio novo (mesmo criterio
 * de corpo dos tracos de nascimento, {@link BirthTrait.Eligibility}). Familias reais so entram em
 * {@link FamilyLegacies} quando o catalogo for aprovado. Puro.
 */
public record FamilyLegacy(String id, FamilyLegacyRarity rarity, BirthTrait.Eligibility eligibility) {
    public FamilyLegacy {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("family legacy without id");
        if (rarity == null) throw new IllegalArgumentException(id + ": missing rarity");
        if (eligibility == null) eligibility = BirthTrait.Eligibility.ALL;
    }

    public boolean eligibleFor(BirthTrait.Body body) { return eligibility.allows(body); }

    public String translationKey() { return "family_legacy.tupinicraft." + id; }
}
