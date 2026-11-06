package com.tupinicraft.election;

import java.util.UUID;

/**
 * Candidatura em uma disputa.
 *
 * @param id               UUID do jogador ou ID persistente do NPC
 * @param popularity       popularidade de candidatura (congelada ao encerrar as inscrições)
 * @param npc              candidato aldeão gerado pelo servidor
 * @param simulatedSupport apoio eleitoral simulado (só NPCs; soma ao peso dos votos favoráveis)
 */
public record Candidate(UUID id, String name, int popularity, boolean npc, int simulatedSupport) {
    public static Candidate player(UUID id, String name, int popularity) {
        return new Candidate(id, name, popularity, false, 0);
    }

    public Candidate withPopularity(int newPopularity) {
        return new Candidate(id, name, newPopularity, npc, simulatedSupport);
    }
}
