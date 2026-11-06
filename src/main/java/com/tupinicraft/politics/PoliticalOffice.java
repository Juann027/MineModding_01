package com.tupinicraft.politics;

import com.tupinicraft.territory.TerritoryType;

import java.util.Locale;
import java.util.Optional;

/**
 * Cargos políticos em ordem de progressão (progressão fictícia de RPG).
 * <p>
 * Os requisitos aqui são apenas os <b>valores padrão</b> do arquivo de configuração. Em jogo, leia
 * sempre os valores efetivos por {@code ServerSettings} (TOML + overrides administrativos do mundo).
 * <p>
 * O {@link #id()} é o identificador persistido e usado nos comandos; não o renomeie.
 */
public enum PoliticalOffice {
    //  id, nome exibido, XP mín. padrão, popularidade mín. padrão, mandato padrão (min), jurisdição
    DESEMPREGADO("desempregado", "Desempregado", 0, 0, 0, null),
    VEREADOR("vereador", "Vereador", 0, 10, 30, TerritoryType.MUNICIPALITY),
    DEPUTADO_ESTADUAL("deputado_estadual", "Deputado Estadual", 50, 30, 30, TerritoryType.STATE),
    PREFEITO("prefeito", "Prefeito", 150, 60, 30, TerritoryType.MUNICIPALITY),
    DEPUTADO_FEDERAL("deputado_federal", "Deputado Federal", 300, 100, 30, TerritoryType.STATE),
    SENADOR("senador", "Senador", 500, 160, 45, TerritoryType.STATE),
    GOVERNADOR("governador", "Governador", 800, 240, 30, TerritoryType.STATE),
    PRESIDENTE("presidente", "Presidente da República", 1200, 350, 30, TerritoryType.COUNTRY);

    private final String id;
    private final String displayName;
    private final int defaultMinPoliticalXp;
    private final int defaultMinPopularity;
    private final int defaultTermMinutes;
    private final TerritoryType jurisdiction;

    PoliticalOffice(String id, String displayName, int defaultMinPoliticalXp, int defaultMinPopularity,
                    int defaultTermMinutes, TerritoryType jurisdiction) {
        this.id = id;
        this.displayName = displayName;
        this.defaultMinPoliticalXp = defaultMinPoliticalXp;
        this.defaultMinPopularity = defaultMinPopularity;
        this.defaultTermMinutes = defaultTermMinutes;
        this.jurisdiction = jurisdiction;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Padrão do TOML para o XP político mínimo de candidatura. */
    public int defaultMinPoliticalXp() {
        return defaultMinPoliticalXp;
    }

    /** Padrão do TOML para a popularidade mínima de candidatura. */
    public int defaultMinPopularity() {
        return defaultMinPopularity;
    }

    /** Padrão do TOML para a duração do mandato, em minutos. */
    public int defaultTermMinutes() {
        return defaultTermMinutes;
    }

    /** Nível territorial onde o cargo é exercido (null para Desempregado). */
    public TerritoryType jurisdiction() {
        return jurisdiction;
    }

    /** Cadeiras deste cargo por território. Hoje sempre 1; o modelo já suporta várias. */
    public int seatsPerTerritory() {
        return isElective() ? 1 : 0;
    }

    /** Cargos disputados nas eleições gerais (país e estados). */
    public boolean isGeneral() {
        return jurisdiction == TerritoryType.COUNTRY || jurisdiction == TerritoryType.STATE;
    }

    /** Cargos disputados nas eleições municipais. */
    public boolean isMunicipal() {
        return jurisdiction == TerritoryType.MUNICIPALITY;
    }

    /** Cargos que podem ser disputados em eleição (todos, exceto Desempregado). */
    public boolean isElective() {
        return this != DESEMPREGADO;
    }

    public Optional<PoliticalOffice> next() {
        PoliticalOffice[] all = values();
        return ordinal() + 1 < all.length ? Optional.of(all[ordinal() + 1]) : Optional.empty();
    }

    public Optional<PoliticalOffice> previous() {
        return ordinal() > 0 ? Optional.of(values()[ordinal() - 1]) : Optional.empty();
    }

    public static Optional<PoliticalOffice> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (PoliticalOffice office : values()) {
            if (office.id.equals(normalized)) {
                return Optional.of(office);
            }
        }
        return Optional.empty();
    }
}
