package com.tupinicraft.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ponte de dados para o futuro player-renderer GeckoLib / clips Blockbench.
 * A camada de teste sinaliza "test_hold" e nao altera o renderer vanilla.
 */
public final class FinisherVisualClientCache {
    public record Visual(UUID partner, String animationKey, boolean initiator, int durationTicks) {}
    private static final Map<UUID, Visual> VISUALS = new HashMap<>();

    private FinisherVisualClientCache() {}

    public static void receive(FinisherVisualSyncPayload payload) {
        try {
            UUID a = UUID.fromString(payload.attackerUuid());
            UUID b = UUID.fromString(payload.targetUuid());
            if (!payload.active()) {
                VISUALS.remove(a);
                VISUALS.remove(b);
            } else {
                VISUALS.put(a, new Visual(b, payload.animationKey(), true, payload.durationTicks()));
                VISUALS.put(b, new Visual(a, payload.animationKey(), false, payload.durationTicks()));
            }
        } catch (IllegalArgumentException ignored) {
            // UUID de pacote invalido: desconsiderar sem afetar o cliente.
        }
    }

    public static Visual get(UUID id) {
        return VISUALS.get(id);
    }

    public static void clear() {
        VISUALS.clear();
    }
}
