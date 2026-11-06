package com.tupinicraft.election;

/** Máquina de estados: INATIVA → INSCRIÇÃO → VOTAÇÃO → APURAÇÃO → INATIVA. */
public enum ElectionPhase {
    INACTIVE("Inativa"),
    REGISTRATION("Inscrição"),
    VOTING("Votação"),
    COUNTING("Apuração");

    private final String displayName;

    ElectionPhase(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ElectionPhase byOrdinal(int ordinal) {
        ElectionPhase[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : INACTIVE;
    }
}
