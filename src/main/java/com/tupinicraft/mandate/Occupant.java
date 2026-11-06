package com.tupinicraft.mandate;

import java.util.Objects;
import java.util.UUID;

/**
 * Ocupante de uma cadeira. O identificador principal é o UUID do jogador ou o ID persistente do NPC,
 * nunca o nome exibido.
 */
public record Occupant(OccupantType type, UUID id, String displayName) {
    public Occupant {
        Objects.requireNonNull(type);
        if (type == OccupantType.VACANT) {
            throw new IllegalArgumentException("Use a ausência de ocupante para cadeiras vagas.");
        }
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
    }

    public static Occupant player(UUID uuid, String name) {
        return new Occupant(OccupantType.PLAYER, uuid, name);
    }

    public static Occupant npc(UUID npcId, String name) {
        return new Occupant(OccupantType.NPC, npcId, name);
    }
}
