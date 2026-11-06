package com.tupinicraft.election;

import com.tupinicraft.politics.PoliticalOffice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras puras de votação e apuração (sem dependência do Minecraft, para facilitar testes).
 *
 * <pre>
 * peso              = 1 + min(4, floor(popularidade_do_eleitor / 50))
 * pontuação         = popularidade_de_candidatura + 20 × (peso_favorável − peso_contrário)
 * pontuação mínima  = popularidade_mínima_do_cargo + 30
 * </pre>
 * Nesta versão só existem votos favoráveis (peso_contrário = 0).
 */
public final class ElectionTally {
    public static final int VOTE_SCORE_MULTIPLIER = 20;
    public static final int WIN_MARGIN = 30;
    public static final int POPULARITY_PER_WEIGHT = 50;
    public static final int MAX_EXTRA_WEIGHT = 4;

    private ElectionTally() {
    }

    public static int voteWeight(int voterPopularity) {
        return 1 + Math.min(MAX_EXTRA_WEIGHT, Math.max(0, voterPopularity) / POPULARITY_PER_WEIGHT);
    }

    public static int minimumWinningScore(PoliticalOffice office) {
        return office.minPopularity() + WIN_MARGIN;
    }

    /** Pontuação de cada candidato (na ordem de inscrição) considerando os votos informados. */
    public static Map<UUID, Integer> scores(List<Candidate> candidates, List<Ballot> ballots) {
        Map<UUID, Integer> scores = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            scores.put(candidate.id(), candidate.popularity());
        }
        for (Ballot ballot : ballots) {
            scores.computeIfPresent(ballot.candidate(), (id, score) -> score + VOTE_SCORE_MULTIPLIER * ballot.weight());
        }
        return scores;
    }

    public static Result decide(PoliticalOffice office, List<Candidate> candidates, List<Ballot> ballots) {
        Map<UUID, Integer> scores = scores(candidates, ballots);
        int minimum = minimumWinningScore(office);
        if (candidates.isEmpty()) {
            return new Result(Outcome.NO_CANDIDATES, Optional.empty(), 0, minimum, scores, List.of());
        }

        if (candidates.size() == 1) {
            Candidate only = candidates.get(0);
            int score = scores.get(only.id());
            // Candidato único precisa SUPERAR a pontuação mínima.
            if (score > minimum) {
                return new Result(Outcome.WINNER, Optional.of(only), score, minimum, scores, List.of());
            }
            return new Result(Outcome.BELOW_MINIMUM, Optional.empty(), score, minimum, scores, List.of(only));
        }

        int best = candidates.stream().mapToInt(c -> scores.get(c.id())).max().orElse(0);
        if (best < minimum) {
            List<Candidate> leaders = candidates.stream().filter(c -> scores.get(c.id()) == best).toList();
            return new Result(Outcome.BELOW_MINIMUM, Optional.empty(), best, minimum, scores, leaders);
        }

        List<Candidate> leaders = new ArrayList<>(candidates.stream().filter(c -> scores.get(c.id()) == best).toList());
        if (leaders.size() == 1) {
            return new Result(Outcome.WINNER, Optional.of(leaders.get(0)), best, minimum, scores, List.of());
        }

        // Desempate: maior popularidade de candidatura.
        leaders.sort(Comparator.comparingInt(Candidate::popularity).reversed());
        int topPopularity = leaders.get(0).popularity();
        List<Candidate> stillTied = leaders.stream().filter(c -> c.popularity() == topPopularity).toList();
        if (stillTied.size() == 1) {
            return new Result(Outcome.WINNER, Optional.of(stillTied.get(0)), best, minimum, scores, List.of());
        }
        return new Result(Outcome.TIE, Optional.empty(), best, minimum, scores, stillTied);
    }

    public enum Outcome {
        WINNER,
        NO_CANDIDATES,
        BELOW_MINIMUM,
        TIE
    }

    /**
     * @param bestScore     pontuação do vencedor (ou a melhor pontuação, se não houve vencedor)
     * @param minimumScore  pontuação mínima de vitória do cargo
     * @param involved      candidatos empatados (TIE) ou líderes abaixo do mínimo (BELOW_MINIMUM)
     */
    public record Result(Outcome outcome, Optional<Candidate> winner, int bestScore, int minimumScore,
                         Map<UUID, Integer> scores, List<Candidate> involved) {
    }
}
