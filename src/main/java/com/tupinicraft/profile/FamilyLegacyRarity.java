package com.tupinicraft.profile;

import java.util.Locale;

/**
 * Raridades do Family Legacy (Etapa 4.7), da mais comum para a mais alta. A ordem do enum e o ranking:
 * {@link #BONER} e a mais alta, acima de {@link #LEGENDARY}. Os ids serao gravados quando o sistema entrar na
 * roleta: nao renomeie.
 *
 * <p>{@code defaultPermille} e a chance padrao em milesimos (1000 = 100%, resolucao de 0,1%); a distribuicao
 * ativa fica em {@link FamilyLegacyOdds} (configuravel). Puro: sem classes do Minecraft.
 */
public enum FamilyLegacyRarity {
    //       chance padrao (milesimos)  cor
    COMMON(515, 0xFFB8C0C8),     // 51,5%
    UNCOMMON(300, 0xFF6BD37E),   // 30,0%
    RARE(100, 0xFF5C9DE0),       // 10,0%
    EPIC(50, 0xFFA66BE0),        //  5,0%
    LEGENDARY(30, 0xFFF2B84A),   //  3,0%
    BONER(5, 0xFFFF4FA3);        //  0,5%

    private final int defaultPermille;
    private final int color;

    FamilyLegacyRarity(int defaultPermille, int color) {
        this.defaultPermille = defaultPermille;
        this.color = color;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public int defaultPermille() { return defaultPermille; }
    public int color() { return color; }
    /** 0 = Common ... 5 = BONER (mais alta). */
    public int rank() { return ordinal(); }
    public boolean isHigherThan(FamilyLegacyRarity other) { return rank() > other.rank(); }
    public String translationKey() { return "family_legacy.tupinicraft.rarity." + id(); }

    public static FamilyLegacyRarity highest() { return BONER; }

    /** Null se o id nao existe. */
    public static FamilyLegacyRarity byId(String id) {
        for (FamilyLegacyRarity rarity : values()) if (rarity.id().equals(id)) return rarity;
        return null;
    }
}
