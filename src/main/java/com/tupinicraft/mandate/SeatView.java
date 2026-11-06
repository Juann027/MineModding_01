package com.tupinicraft.mandate;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Visão somente leitura de uma cadeira num instante. Base do snapshot do futuro mapa político.
 *
 * @param playerId        UUID do jogador ocupante, quando {@code occupantType == PLAYER}
 * @param npcId           ID persistente do NPC ocupante, quando {@code occupantType == NPC}
 * @param remainingTicks  ticks restantes do mandato (vazio se vaga)
 */
public record SeatView(SeatKey key, OccupantType occupantType, Optional<UUID> playerId, Optional<UUID> npcId,
                       Optional<String> displayName, OptionalLong termStart, OptionalLong termEnd,
                       OptionalLong remainingTicks, Optional<String> party, Optional<UUID> electionId) {

    public static SeatView vacant(SeatKey key) {
        return new SeatView(key, OccupantType.VACANT, Optional.empty(), Optional.empty(), Optional.empty(),
                OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(), Optional.empty(), Optional.empty());
    }

    public static SeatView of(SeatKey key, Mandate mandate, long now) {
        Occupant occupant = mandate.occupant();
        boolean player = occupant.type() == OccupantType.PLAYER;
        return new SeatView(key, occupant.type(),
                player ? Optional.of(occupant.id()) : Optional.empty(),
                player ? Optional.empty() : Optional.of(occupant.id()),
                Optional.of(occupant.displayName()),
                OptionalLong.of(mandate.termStart()), OptionalLong.of(mandate.termEnd()),
                OptionalLong.of(mandate.remainingTicks(now)), mandate.party(), mandate.electionId());
    }

    public boolean isVacant() {
        return occupantType == OccupantType.VACANT;
    }
}
