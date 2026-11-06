package com.tupinicraft.election;

import java.util.Optional;

/**
 * Regras puras de candidatura (sem Minecraft, testáveis).
 * <ul>
 *   <li>Não há progressão sequencial: qualquer jogador, inclusive Desempregado, pode concorrer a qualquer cargo.</li>
 *   <li>O único requisito pessoal é o XP político mínimo do cargo.</li>
 *   <li>Popularidade NÃO é requisito; ela só influencia a pontuação na apuração e o peso do voto.</li>
 *   <li>O jogador concorre no seu domicílio eleitoral (derivado da origem).</li>
 *   <li>Reeleição consecutiva (último ocupante disputando a mesma cadeira) pode ser desativada.</li>
 * </ul>
 */
public final class CandidacyRules {
    public enum Problem {
        OUTSIDE_DOMICILE,
        INSUFFICIENT_XP,
        REELECTION_DISABLED
    }

    private CandidacyRules() {
    }

    /**
     * @param inDomicile       a disputa é no domicílio eleitoral do jogador
     * @param playerXp         XP político do jogador
     * @param minXp            XP político mínimo efetivo do cargo
     * @param lastOccupant     o jogador foi o último ocupante desta cadeira
     * @param reelectionAllowed configuração {@code mandato.reeleicao}
     * @return o primeiro impedimento, ou vazio se pode concorrer
     */
    public static Optional<Problem> check(boolean inDomicile, int playerXp, int minXp,
                                          boolean lastOccupant, boolean reelectionAllowed) {
        if (!inDomicile) {
            return Optional.of(Problem.OUTSIDE_DOMICILE);
        }
        if (playerXp < minXp) {
            return Optional.of(Problem.INSUFFICIENT_XP);
        }
        if (lastOccupant && !reelectionAllowed) {
            return Optional.of(Problem.REELECTION_DISABLED);
        }
        return Optional.empty();
    }
}
