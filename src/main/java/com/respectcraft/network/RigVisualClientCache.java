package com.respectcraft.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copia do cliente do estado visual das partes de rigs (Etapa 4.12): id da entidade → partes que diferem do padrao.
 * Cache so visual, limpo no logout e quando a entidade sai do nivel do cliente.
 */
public final class RigVisualClientCache {
    private static final Map<Integer, Map<String, Boolean>> STATES = new ConcurrentHashMap<>();

    private RigVisualClientCache() {}

    public static void receive(int entityId, Map<String, Boolean> overrides) {
        if (overrides.isEmpty()) STATES.remove(entityId);
        else STATES.put(entityId, Map.copyOf(overrides));
    }

    public static Map<String, Boolean> get(int entityId) { return STATES.getOrDefault(entityId, Map.of()); }

    public static void forget(int entityId) { STATES.remove(entityId); }

    public static void clear() { STATES.clear(); }
}
