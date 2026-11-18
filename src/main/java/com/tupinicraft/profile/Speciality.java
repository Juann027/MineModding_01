package com.tupinicraft.profile;

/**
 * Specialities (Etapa 4.7): o que a roleta de nascimento sorteia no lugar das antigas origens sociais
 * ({@link BirthOrigin}, que so continuam para fichas antigas). Exatamente as 15 aprovadas, com nome e raridade
 * definidos pelo dono; nao acrescente nem mude raridades sem pedido.
 *
 * <p>Os ids sao gravados na ficha ({@code birth_speciality}/{@code current_speciality}): nao renomeie. Nome e
 * descricao ficam no lang ({@code speciality.tupinicraft.<id>} e {@code .desc}). <b>Nenhuma Speciality tem perk,
 * modificador ou efeito</b>: as perks virao depois, nas Skill Trees. {@link #eligibility} usa o mesmo criterio de
 * corpo dos tracos; hoje todas sao {@code ALL}. Puro: sem classes do Minecraft.
 */
public enum Speciality {
    // COMMON
    VANILLA("vanilla", SpecialityRarity.COMMON),
    VOYEURS("voyeurs", SpecialityRarity.COMMON),
    EXHIBITIONISTS("exhibitionists", SpecialityRarity.COMMON),
    FOOT_WORSHIPPERS("foot_worshippers", SpecialityRarity.COMMON),
    // UNCOMMON
    ROPEBOUND("ropebound", SpecialityRarity.UNCOMMON),
    SPITTERS("spitters", SpecialityRarity.UNCOMMON),
    // RARE
    SHOWERS_OF_GOLD("showers_of_gold", SpecialityRarity.RARE),
    BOOTSTOMPERS("bootstompers", SpecialityRarity.RARE),
    DOMINANTS("dominants", SpecialityRarity.RARE),
    LATEX_ROYALS("latex_royals", SpecialityRarity.RARE),
    // EPIC
    SCATBORN("scatborn", SpecialityRarity.EPIC),
    CUCKHOLDERS("cuckholders", SpecialityRarity.EPIC),
    HUMILIATIONISTS("humiliationists", SpecialityRarity.EPIC),
    // LEGENDARY
    ULTRA_GOONER("ultra_gooner", SpecialityRarity.LEGENDARY),
    // BONER
    ISLAND_GUY("island_guy", SpecialityRarity.BONER);

    private final String id;
    private final SpecialityRarity rarity;
    private final BirthTrait.Eligibility eligibility;

    Speciality(String id, SpecialityRarity rarity) {
        this.id = id;
        this.rarity = rarity;
        this.eligibility = BirthTrait.Eligibility.ALL;
    }

    public String id() { return id; }
    public SpecialityRarity rarity() { return rarity; }
    public BirthTrait.Eligibility eligibility() { return eligibility; }
    public boolean eligibleFor(BirthTrait.Body body) { return eligibility.allows(body); }
    public String translationKey() { return "speciality.tupinicraft." + id; }

    /** Null se o id nao existe (dado salvo de versao futura, por exemplo). */
    public static Speciality byId(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Speciality speciality : values()) if (speciality.id.equals(id)) return speciality;
        return null;
    }
}
