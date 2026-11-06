package com.tupinicraft.election;

import com.tupinicraft.mandate.SeatKey;
import com.tupinicraft.politics.PoliticalOffice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Disputa por uma cadeira (cargo + território) dentro de uma eleição (uma eleição tem uma ou duas disputas).
 * A pontuação mínima e a faixa de popularidade dos NPCs são fixadas quando a disputa é criada.
 */
public final class ElectionRace {
    private final SeatKey seat;
    private final int minimumScore;
    private final int npcPopularityMin;
    private final int npcPopularityMax;
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<Ballot> ballots = new ArrayList<>();

    public ElectionRace(SeatKey seat, int minimumScore, int npcPopularityMin, int npcPopularityMax) {
        this.seat = seat;
        this.minimumScore = minimumScore;
        this.npcPopularityMin = Math.max(0, npcPopularityMin);
        this.npcPopularityMax = Math.max(this.npcPopularityMin, npcPopularityMax);
    }

    public SeatKey seat() {
        return seat;
    }

    public PoliticalOffice office() {
        return seat.office();
    }

    public String territoryId() {
        return seat.territoryId();
    }

    /** Pontuação mínima de vitória (popularidade mínima do cargo + margem), fixada na criação. */
    public int minimumScore() {
        return minimumScore;
    }

    public int npcPopularityMin() {
        return npcPopularityMin;
    }

    public int npcPopularityMax() {
        return npcPopularityMax;
    }

    /** Lista mutável, em ordem de inscrição. */
    public List<Candidate> candidates() {
        return candidates;
    }

    /** Lista mutável, em ordem de votação (usada também na apuração gradual). */
    public List<Ballot> ballots() {
        return ballots;
    }

    public Optional<Candidate> findCandidate(UUID id) {
        return candidates.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public Optional<Candidate> findCandidateByName(String name) {
        return candidates.stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
    }

    public boolean isCandidate(UUID id) {
        return findCandidate(id).isPresent();
    }

    public boolean hasVoted(UUID voter) {
        return ballots.stream().anyMatch(b -> b.voter().equals(voter));
    }

    public long playerCandidateCount() {
        return candidates.stream().filter(c -> !c.npc()).count();
    }
}
