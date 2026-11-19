package com.respectcraft.profile;

/**
 * Origens sociais LEGADAS (ate a Etapa 4.6). Desde a Etapa 4.7 a roleta sorteia {@link Speciality}; estas origens
 * nao sao mais sorteadas e so existem para exibir fichas antigas (o bonus delas ja esta nos atributos gravados).
 * Os ids sao persistidos (nao renomear; ids aposentados ficam em {@link LegacyOriginIds}).
 * Puro: sem classes do Minecraft.
 */
public enum BirthOrigin {
    //                id                  peso  cor          icone (textures/item/skilltree)  F  A  V  I  C  S
    FAVELA("favela", 20, 0xFFD9822B, "favela", 0, 2, 1, 0, 0, 0),
    SERTAO("sertao", 17, 0xFFC9A04A, "sertao", 1, 0, 2, 0, 0, 0),
    PAMPA("pampa", 14, 0xFF6FA35A, "chimarrao", 2, 0, 1, 0, 0, 0),
    AMAZONIA("amazonia", 13, 0xFF2E9E5B, "amazonia", 0, 1, 1, 0, 0, 1),
    CLASSE_MEDIA("classe_media", 17, 0xFF5C88C9, "carteira_trabalho", 0, 0, 0, 2, 1, 0),
    INTELECTUAL("intelectual", 9, 0xFF8B6FD6, "cafe_extraforte", -1, 0, 0, 3, 0, 1),
    FAMILIA_ARTISTICA("familia_artistica", 6, 0xFFB43C3C, "trevo", 0, 0, -1, 0, 3, 1),
    BERCO_DE_OURO("berco_de_ouro", 4, 0xFFF2CF4A, "moeda_real", 0, 0, -1, 1, 2, 2);

    private final String id;
    private final int weight;
    private final int color;
    private final String icon;
    private final int[] bonus;

    BirthOrigin(String id, int weight, int color, String icon, int... bonus) {
        if (bonus.length != CharacterAttribute.values().length) throw new IllegalArgumentException(id);
        this.id = id;
        this.weight = weight;
        this.color = color;
        this.icon = icon;
        this.bonus = bonus;
    }

    public String id() { return id; }
    public int weight() { return weight; }
    public int color() { return color; }
    public String icon() { return icon; }
    public int bonus(CharacterAttribute attribute) { return bonus[attribute.ordinal()]; }

    public static int totalWeight() {
        int total = 0;
        for (BirthOrigin origin : values()) total += origin.weight;
        return total;
    }

    /** Null se o id nao existe (dado salvo de versao futura, por exemplo). */
    public static BirthOrigin byId(String id) {
        for (BirthOrigin origin : values()) if (origin.id.equals(id)) return origin;
        return null;
    }
}
