package com.tupinicraft.mandate;

import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.territory.Territories;
import com.tupinicraft.territory.Territory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * API de consulta de mandatos (somente leitura, dados estruturados, sem texto pronto nem interface).
 * Mandatos expirados aparecem como cadeiras vagas, mesmo antes da limpeza periódica do servidor.
 * <p>
 * Criada sob demanda com o registro autoritativo, o instante atual e a regra de expiração.
 */
public final class MandateQueries {
    private final MandateRegistry registry;
    private final long now;
    private final boolean expirationEnabled;

    public MandateQueries(MandateRegistry registry, long now, boolean expirationEnabled) {
        this.registry = registry;
        this.now = now;
        this.expirationEnabled = expirationEnabled;
    }

    public Optional<Territory> getTerritory(String territoryId) {
        return Territories.get(territoryId);
    }

    /** Todas as cadeiras previstas para o território (ocupadas e vagas), em ordem de cargo. */
    public List<SeatView> getSeats(String territoryId) {
        List<SeatView> seats = new ArrayList<>();
        Optional<Territory> territory = Territories.get(territoryId);
        if (territory.isEmpty()) {
            return seats;
        }
        for (PoliticalOffice office : PoliticalOffice.values()) {
            if (office.isElective() && office.jurisdiction() == territory.get().type()) {
                for (int seat = 1; seat <= office.seatsPerTerritory(); seat++) {
                    seats.add(view(new SeatKey(territory.get().id(), office, seat)));
                }
            }
        }
        return seats;
    }

    public Optional<SeatView> getSeat(String territoryId, PoliticalOffice office, int seat) {
        Optional<Territory> territory = Territories.get(territoryId);
        if (territory.isEmpty() || !office.isElective() || office.jurisdiction() != territory.get().type()
                || seat < 1 || seat > office.seatsPerTerritory()) {
            return Optional.empty();
        }
        return Optional.of(view(new SeatKey(territory.get().id(), office, seat)));
    }

    public SeatView view(SeatKey key) {
        return registry.activeMandate(key, now, expirationEnabled)
                .map(mandate -> SeatView.of(key, mandate, now))
                .orElseGet(() -> SeatView.vacant(key));
    }

    public Optional<Occupant> getCurrentOccupant(SeatKey key) {
        return registry.activeMandate(key, now, expirationEnabled).map(Mandate::occupant);
    }

    /** Ticks restantes do mandato vigente; vazio se a cadeira está vaga. */
    public OptionalLong getRemainingTerm(SeatKey key) {
        return registry.activeMandate(key, now, expirationEnabled)
                .map(mandate -> OptionalLong.of(mandate.remainingTicks(now)))
                .orElse(OptionalLong.empty());
    }

    /** Quem governa o território: cadeiras ocupadas do território. */
    public List<SeatView> getOccupiedSeats(String territoryId) {
        return getSeats(territoryId).stream().filter(Predicate.not(SeatView::isVacant)).toList();
    }

    public List<SeatView> getVacantSeats(String territoryId) {
        return getSeats(territoryId).stream().filter(SeatView::isVacant).toList();
    }

    /** Todos os mandatos vigentes ocupados por jogadores. */
    public List<SeatView> getPlayerMandates() {
        return activeWhere(mandate -> mandate.occupant().type() == OccupantType.PLAYER);
    }

    /** Todos os mandatos vigentes ocupados por aldeões (NPCs). */
    public List<SeatView> getNpcMandates() {
        return activeWhere(mandate -> mandate.occupant().type() == OccupantType.NPC);
    }

    /** Mandatos vigentes de um jogador específico (por UUID). */
    public List<SeatView> getMandatesOf(UUID playerId) {
        return activeWhere(mandate -> mandate.occupant().type() == OccupantType.PLAYER
                && mandate.occupant().id().equals(playerId));
    }

    /** Snapshot para a futura interface do mapa, limitado aos territórios pedidos. */
    public PoliticalMapSnapshot snapshot(Collection<Territory> territories) {
        List<SeatView> seats = new ArrayList<>();
        for (Territory territory : territories) {
            seats.addAll(getSeats(territory.id()));
        }
        return new PoliticalMapSnapshot(now, List.copyOf(territories), seats);
    }

    private List<SeatView> activeWhere(Predicate<Mandate> filter) {
        List<SeatView> result = new ArrayList<>();
        registry.mandates().forEach((key, mandate) -> {
            if (mandate.isActive(now, expirationEnabled) && filter.test(mandate)) {
                result.add(SeatView.of(key, mandate, now));
            }
        });
        return result;
    }
}
