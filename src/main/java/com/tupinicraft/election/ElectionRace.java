package com.tupinicraft.election;

import com.tupinicraft.politics.PoliticalOffice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Disputa por um cargo dentro de uma eleição (uma eleição tem uma ou duas disputas). */
public final class ElectionRace {
    private final PoliticalOffice office;
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<Ballot> ballots = new ArrayList<>();

    public ElectionRace(PoliticalOffice office) {
        this.office = office;
    }

    public PoliticalOffice office() {
        return office;
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
}
