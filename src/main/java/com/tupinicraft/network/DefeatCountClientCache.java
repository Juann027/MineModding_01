package com.tupinicraft.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Armazenamento CLIENTE, sem classes gráficas (seguro de referenciar no registro comum). */
public final class DefeatCountClientCache {
    /** Duração do destaque visual quando o contador de alguém sobe. */
    public static final long FLASH_MILLIS = 700L;
    private static final Map<UUID, Integer> COUNTS = new HashMap<>();
    private static final Map<UUID, Long> INCREASED_AT = new HashMap<>();

    private DefeatCountClientCache() {}

    public static void receive(DefeatCountSyncPayload packet) {
        try {
            UUID uuid = UUID.fromString(packet.playerUuid());
            if (packet.count() < 0) {
                COUNTS.remove(uuid);
                INCREASED_AT.remove(uuid);
                return;
            }
            Integer previous = COUNTS.put(uuid, packet.count());
            // Só destaca um aumento real visto por este cliente (não o primeiro valor recebido nem repetições).
            if (previous != null && packet.count() > previous) INCREASED_AT.put(uuid, System.currentTimeMillis());
        } catch (IllegalArgumentException ignored) {
            // Dados inesperados não devem derrubar a renderização do cliente.
        }
    }

    public static int get(UUID uuid) {
        return COUNTS.getOrDefault(uuid, 0);
    }

    /** 1 logo após um aumento, caindo até 0 em {@link #FLASH_MILLIS}; 0 sem destaque. */
    public static float flashProgress(UUID uuid, long nowMillis) {
        Long at = INCREASED_AT.get(uuid);
        if (at == null) return 0F;
        long elapsed = nowMillis - at;
        if (elapsed >= FLASH_MILLIS || elapsed < 0) {
            INCREASED_AT.remove(uuid);
            return 0F;
        }
        return 1F - (float) elapsed / FLASH_MILLIS;
    }

    public static void clear() {
        COUNTS.clear();
        INCREASED_AT.clear();
    }
}
