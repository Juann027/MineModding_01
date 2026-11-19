package com.respectcraft.profile;

import java.util.Locale;

/**
 * Raridades das Specialities (Etapa 4.7), da mais comum para a mais alta. A ordem do enum e o ranking:
 * {@link #BONER} e a mais alta, acima de {@link #LEGENDARY}. Os ids aparecem em config e comandos: nao renomeie.
 *
 * <p>{@code defaultPermille} e a chance padrao em milesimos (1000 = 100%, resolucao de 0,1%); a distribuicao
 * ativa fica em {@link SpecialityOdds} (configuravel). Puro: sem classes do Minecraft.
 */
public enum SpecialityRarity {
    //       chance padrao (milesimos)  cor
    COMMON(415, 0xFFB8C0C8),     // 41,5%
    UNCOMMON(250, 0xFF6BD37E),   // 25,0%
    RARE(200, 0xFF5C9DE0),       // 20,0%
    EPIC(100, 0xFFA66BE0),       // 10,0%
    LEGENDARY(30, 0xFFF2B84A),   //  3,0%
    BONER(5, 0xFFFF4FA3);        //  0,5%

    private final int defaultPermille;
    private final int color;

    SpecialityRarity(int defaultPermille, int color) {
        this.defaultPermille = defaultPermille;
        this.color = color;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public int defaultPermille() { return defaultPermille; }
    public int color() { return color; }
    /** 0 = Common ... 5 = BONER (mais alta). */
    public int rank() { return ordinal(); }
    public boolean isHigherThan(SpecialityRarity other) { return rank() > other.rank(); }
    public String translationKey() { return "speciality.respectcraft.rarity." + id(); }

    public static SpecialityRarity highest() { return BONER; }

    /** Null se o id nao existe. */
    public static SpecialityRarity byId(String id) {
        for (SpecialityRarity rarity : values()) if (rarity.id().equals(id)) return rarity;
        return null;
    }
}
