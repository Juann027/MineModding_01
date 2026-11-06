package com.tupinicraft.election;

import java.util.UUID;

/**
 * Candidatura em uma disputa.
 *
 * @param popularity popularidade de candidatura (congelada ao encerrar as inscrições)
 */
public record Candidate(UUID id, String name, int popularity) {
    public Candidate withPopularity(int newPopularity) {
        return new Candidate(id, name, newPopularity);
    }
}
