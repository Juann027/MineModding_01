package com.tupinicraft.mandate;

import com.tupinicraft.politics.PoliticalOffice;

/**
 * Identifica uma cadeira política: território + cargo + número da cadeira (1..N).
 * Permite várias cadeiras do mesmo cargo no mesmo território.
 */
public record SeatKey(String territoryId, PoliticalOffice office, int seat) {
    public SeatKey {
        if (territoryId == null || office == null || !office.isElective() || seat < 1) {
            throw new IllegalArgumentException("Cadeira inválida: " + territoryId + "/" + office + "/" + seat);
        }
    }

    /** Forma textual estável, ex.: {@code tupinicraft:es/governador/1}. */
    public String asString() {
        return territoryId + "/" + office.id() + "/" + seat;
    }
}
