package com.tupinicraft.election;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras puras de apuração (sem dependência do Minecraft, para facilitar testes).
 *
 * <pre>
 * pontuação         = popularidade_de_candidatura + multiplicador × (peso_dos_votos_favoráveis + apoio_simulado)
 * pontuação mínima  = popularidade_mínima_do_cargo + margem   (fixada na criação da disputa)
 * </pre>
 * Nesta versão só existem votos favoráveis. O apoio simulado só é usado por candidatos NPCs.
 */
public final class ElectionTally {
    private ElectionTally() {
    }

    /** Pontuação de cada candidato (na ordem de inscrição) considerando os votos informados. */
    public static Map<UUID, Integer> scores(List<Candidate> candidates, List<Ballot> ballots, int voteMultiplier) {
        Map<UUID, Integer> scores = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            scores.put(candidate.id(), candidate.popularity() + voteMultiplier * candidate.simulatedSupport());
        }
        for (Ballot ballot : ballots) {
            scores.computeIfPresent(ballot.candidate(), (id, score) -> score + voteMultiplier * ballot.weight());
        }
        return scores;
    }

    public static Result decide(int minimumScore, List<Candidate> candidates, List<Ballot> ballots, int voteMultiplier) {
        Map<UUID, Integer> scores = scores(candidates, ballots, voteMultiplier);
        if (candidates.isEmpty()) {
            return new Result(Outcome.NO_CANDIDATES, Optional.empty(), 0, minimumScore, scores, List.of());
        }

        if (candidates.size() == 1) {
            Candidate only = candidates.get(0);
            int score = scores.get(only.id());
            // Candidato único precisa SUPERAR a pontuação mínima.
            if (score > minimumScore) {
                return new Result(Outcome.WINNER, Optional.of(only), score, minimumScore, scores, List.of());
            }
            return new Result(Outcome.BELOW_MINIMUM, Optional.empty(), score, minimumScore, scores, List.of(only));
        }

        int best = candidates.stream().mapToInt(c -> scores.get(c.id())).max().orElse(0);
        List<Candidate> leaders = new ArrayList<>(candidates.stream().filter(c -> scores.get(c.id()) == best).toList());
        if (best < minimumScore) {
            return new Result(Outcome.BELOW_MINIMUM, Optional.empty(), best, minimumScore, scores, leaders);
        }
        if (leaders.size() == 1) {
            return new Result(Outcome.WINNER, Optional.of(leaders.get(0)), best, minimumScore, scores, List.of());
        }

        // Desempate: maior popularidade de candidatura.
        leaders.sort(Comparator.comparingInt(Candidate::popularity).reversed());
        int topPopularity = leaders.get(0).popularity();
        List<Candidate> stillTied = leaders.stream().filter(c -> c.popularity() == topPopularity).toList();
        if (stillTied.size() == 1) {
            return new Result(Outcome.WINNER, Optional.of(stillTied.get(0)), best, minimumScore, scores, List.of());
        }
        return new Result(Outcome.TIE, Optional.empty(), best, minimumScore, scores, stillTied);
    }

    public enum Outcome {
        WINNER,
        NO_CANDIDATES,
        BELOW_MINIMUM,
        TIE
    }

    /**
     * @param bestScore     pontuação do vencedor (ou a melhor pontuação, se não houve vencedor)
     * @param minimumScore  pontuação mínima de vitória da disputa
     * @param involved      candidatos empatados (TIE) ou líderes abaixo do mínimo (BELOW_MINIMUM)
     */
    public record Result(Outcome outcome, Optional<Candidate> winner, int bestScore, int minimumScore,
                         Map<UUID, Integer> scores, List<Candidate> involved) {
    }
}
