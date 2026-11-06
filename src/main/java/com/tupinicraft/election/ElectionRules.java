package com.tupinicraft.election;

/**
 * Regras eleitorais congeladas no início de cada eleição (cópia dos valores efetivos de configuração).
 * Mudar a configuração durante uma eleição afeta só as próximas; votos já registrados nunca são recalculados.
 */
public record ElectionRules(int voteMultiplier, int popularityDivisor, int maxVoteWeight,
                            boolean villagersEnabled, int minCandidates, int maxNpcCandidates,
                            int npcSupportMin, int npcSupportMax,
                            int votingTicks, int countingTicks) {

    /** Valores padrão (iguais aos padrões do TOML); usados ao carregar eleições antigas. */
    public static final ElectionRules DEFAULTS = new ElectionRules(20, 50, 5, false, 1, 0, 0, 0, 60 * 20, 15 * 20);

    public ElectionRules {
        popularityDivisor = Math.max(1, popularityDivisor);
        maxVoteWeight = Math.max(1, maxVoteWeight);
        minCandidates = Math.max(1, minCandidates);
        maxNpcCandidates = Math.max(0, maxNpcCandidates);
        npcSupportMin = Math.max(0, npcSupportMin);
        npcSupportMax = Math.max(npcSupportMin, npcSupportMax);
        votingTicks = Math.max(1, votingTicks);
        countingTicks = Math.max(1, countingTicks);
    }

    /** peso = min(peso_máximo, 1 + floor(popularidade / divisor)) */
    public int voteWeight(int voterPopularity) {
        return Math.min(maxVoteWeight, 1 + Math.max(0, voterPopularity) / popularityDivisor);
    }
}
