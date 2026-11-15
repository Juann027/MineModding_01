package com.tupinicraft.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Armazenamento CLIENTE, sem classes gráficas (seguro de referenciar no registro comum). */
public final class DefeatCountClientCache {
    private static final Map<UUID, Integer> COUNTS = new HashMap<>();

    private DefeatCountClientCache() {}

    public static void receive(DefeatCountSyncPayload packet) {
        try {
            UUID uuid = UUID.fromString(packet.playerUuid());
            if (packet.count() < 0) COUNTS.remove(uuid);
            else COUNTS.put(uuid, packet.count());
        } catch (IllegalArgumentException ignored) {
            // Dados inesperados não devem derrubar a renderização do cliente.
        }
    }

    public static int get(UUID uuid) {
        return COUNTS.getOrDefault(uuid, 0);
    }

    public static void clear() {
        COUNTS.clear();
    }
}
