package com.tupinicraft.origin;

/** As cinco macrorregiões oficiais do Brasil (IBGE). */
public enum Macroregion {
    NORTE("Norte"),
    NORDESTE("Nordeste"),
    CENTRO_OESTE("Centro-Oeste"),
    SUDESTE("Sudeste"),
    SUL("Sul");

    private final String displayName;

    Macroregion(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
