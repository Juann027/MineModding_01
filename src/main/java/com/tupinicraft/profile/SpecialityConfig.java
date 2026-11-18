package com.tupinicraft.profile;

import com.tupinicraft.TupiniCraft;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuracao do servidor para as chances de raridade das Specialities
 * ({@code config/tupinicraft-speciality-server.toml}, por mundo). Porcentagens com no maximo uma casa decimal,
 * somando exatamente 100.0. Uma configuracao invalida nunca e normalizada: o servidor registra o erro e usa a
 * distribuicao aprovada ({@link SpecialityOdds#DEFAULT}). Unica classe do pacote que depende do NeoForge.
 */
public final class SpecialityConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue[] PERCENT = new ModConfigSpec.DoubleValue[SpecialityRarity.values().length];

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Speciality rarity chances for the Birth Roulette (percent, one decimal place at most,",
                "must add up to exactly 100.0). Invalid values are reported in the log and the approved defaults",
                "are used instead. The roll picks the rarity first, then a Speciality of that rarity.")
                .push("speciality_rarity");
        for (SpecialityRarity rarity : SpecialityRarity.values()) {
            PERCENT[rarity.ordinal()] = b.defineInRange(rarity.id(), rarity.defaultPermille() / 10.0, 0.0, 100.0);
        }
        b.pop();
        SPEC = b.build();
    }

    private SpecialityConfig() {}

    /** Distribuicao ativa; a padrao enquanto a config nao carregou ou se ela for invalida. */
    public static SpecialityOdds odds() {
        String error = error();
        if (error != null) {
            TupiniCraft.LOGGER.error("Invalid Speciality rarity config ({}); using defaults: {}", error, SpecialityOdds.DEFAULT);
            return SpecialityOdds.DEFAULT;
        }
        return SPEC.isLoaded() ? SpecialityOdds.ofPercent(configured()) : SpecialityOdds.DEFAULT;
    }

    /** Null se a config esta valida (ou ainda nao carregou); senao, o problema. */
    public static String error() {
        if (!SPEC.isLoaded()) return null;
        try {
            SpecialityOdds.ofPercent(configured());
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private static double[] configured() {
        double[] values = new double[PERCENT.length];
        for (int i = 0; i < PERCENT.length; i++) values[i] = PERCENT[i].get();
        return values;
    }
}
