package com.tupinicraft.politics;

import java.util.Locale;
import java.util.Optional;

/**
 * Cargos políticos em ordem de progressão (progressão fictícia de RPG).
 * <p>
 * <b>Fonte única de balanceamento:</b> os requisitos de XP político e popularidade
 * de cada cargo ficam apenas aqui. Os valores são provisórios.
 * <p>
 * O {@link #id()} é o identificador persistido e usado nos comandos; não o renomeie.
 */
public enum PoliticalOffice {
    //                 id                    nome exibido               XP mín.  popularidade mín.
    DESEMPREGADO("desempregado", "Desempregado", 0, 0),
    VEREADOR("vereador", "Vereador", 0, 10),
    DEPUTADO_ESTADUAL("deputado_estadual", "Deputado Estadual", 50, 30),
    PREFEITO("prefeito", "Prefeito", 150, 60),
    DEPUTADO_FEDERAL("deputado_federal", "Deputado Federal", 300, 100),
    SENADOR("senador", "Senador", 500, 160),
    GOVERNADOR("governador", "Governador", 800, 240),
    PRESIDENTE("presidente", "Presidente da República", 1200, 350);

    private final String id;
    private final String displayName;
    private final int minPoliticalXp;
    private final int minPopularity;

    PoliticalOffice(String id, String displayName, int minPoliticalXp, int minPopularity) {
        this.id = id;
        this.displayName = displayName;
        this.minPoliticalXp = minPoliticalXp;
        this.minPopularity = minPopularity;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** XP político mínimo para se candidatar a este cargo. */
    public int minPoliticalXp() {
        return minPoliticalXp;
    }

    /** Popularidade mínima para se candidatar a este cargo. */
    public int minPopularity() {
        return minPopularity;
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

    /** Verifica XP e popularidade (o cargo anterior é verificado por quem chama, via {@link #next()}). */
    public boolean meetsRequirements(int politicalXp, int popularity) {
        return politicalXp >= minPoliticalXp && popularity >= minPopularity;
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
