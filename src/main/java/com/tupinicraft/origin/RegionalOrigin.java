package com.tupinicraft.origin;

import java.util.Locale;
import java.util.Optional;

/**
 * As 27 unidades federativas brasileiras usadas como origem regional do jogador.
 * <p>
 * A sigla (UF) é o identificador persistido; nunca renomeie as siglas.
 * Benefícios futuros por origem/macrorregião devem ser aplicados em
 * {@link OriginService#applyOriginEffects}, sem alterar este cadastro.
 */
public enum RegionalOrigin {
    AC("Acre", "Acriano", Macroregion.NORTE),
    AL("Alagoas", "Alagoano", Macroregion.NORDESTE),
    AP("Amapá", "Amapaense", Macroregion.NORTE),
    AM("Amazonas", "Amazonense", Macroregion.NORTE),
    BA("Bahia", "Baiano", Macroregion.NORDESTE),
    CE("Ceará", "Cearense", Macroregion.NORDESTE),
    DF("Distrito Federal", "Brasiliense", Macroregion.CENTRO_OESTE),
    ES("Espírito Santo", "Capixaba", Macroregion.SUDESTE),
    GO("Goiás", "Goiano", Macroregion.CENTRO_OESTE),
    MA("Maranhão", "Maranhense", Macroregion.NORDESTE),
    MT("Mato Grosso", "Mato-grossense", Macroregion.CENTRO_OESTE),
    MS("Mato Grosso do Sul", "Sul-mato-grossense", Macroregion.CENTRO_OESTE),
    MG("Minas Gerais", "Mineiro", Macroregion.SUDESTE),
    PA("Pará", "Paraense", Macroregion.NORTE),
    PB("Paraíba", "Paraibano", Macroregion.NORDESTE),
    PR("Paraná", "Paranaense", Macroregion.SUL),
    PE("Pernambuco", "Pernambucano", Macroregion.NORDESTE),
    PI("Piauí", "Piauiense", Macroregion.NORDESTE),
    // Propositalmente "Carioca" (gentílico da capital), por decisão de design.
    RJ("Rio de Janeiro", "Carioca", Macroregion.SUDESTE),
    RN("Rio Grande do Norte", "Potiguar", Macroregion.NORDESTE),
    RS("Rio Grande do Sul", "Gaúcho", Macroregion.SUL),
    RO("Rondônia", "Rondoniense", Macroregion.NORTE),
    RR("Roraima", "Roraimense", Macroregion.NORTE),
    SC("Santa Catarina", "Catarinense", Macroregion.SUL),
    SP("São Paulo", "Paulista", Macroregion.SUDESTE),
    SE("Sergipe", "Sergipano", Macroregion.NORDESTE),
    TO("Tocantins", "Tocantinense", Macroregion.NORTE);

    private final String stateName;
    private final String demonym;
    private final Macroregion macroregion;

    RegionalOrigin(String stateName, String demonym, Macroregion macroregion) {
        this.stateName = stateName;
        this.demonym = demonym;
        this.macroregion = macroregion;
    }

    /** Sigla da UF, ex.: "MA". */
    public String code() {
        return name();
    }

    public String stateName() {
        return stateName;
    }

    /** Gentílico, ex.: "Maranhense". */
    public String demonym() {
        return demonym;
    }

    public Macroregion macroregion() {
        return macroregion;
    }

    public static Optional<RegionalOrigin> byCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (RegionalOrigin origin : values()) {
            if (origin.name().equals(normalized)) {
                return Optional.of(origin);
            }
        }
        return Optional.empty();
    }
}
