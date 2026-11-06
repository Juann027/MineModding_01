package com.tupinicraft.territory;

/** Nível territorial. A ordem vai do mais amplo ao mais local. */
public enum TerritoryType {
    COUNTRY("País"),
    STATE("Estado"),
    MUNICIPALITY("Município");

    private final String displayName;

    TerritoryType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
