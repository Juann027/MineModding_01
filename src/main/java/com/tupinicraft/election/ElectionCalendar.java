package com.tupinicraft.election;

import com.tupinicraft.politics.PoliticalOffice;

import java.util.Arrays;
import java.util.List;

/** Origem de uma eleição: calendário geral, calendário municipal ou início manual por administrador. */
public enum ElectionCalendar {
    GENERAL("Eleições gerais"),
    MUNICIPAL("Eleições municipais"),
    MANUAL("Eleição extraordinária");

    private final String displayName;

    ElectionCalendar(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Cargos que o calendário disputa (todos os eletivos para o manual). */
    public List<PoliticalOffice> offices() {
        return Arrays.stream(PoliticalOffice.values())
                .filter(PoliticalOffice::isElective)
                .filter(office -> switch (this) {
                    case GENERAL -> office.isGeneral();
                    case MUNICIPAL -> office.isMunicipal();
                    case MANUAL -> true;
                })
                .toList();
    }
}
