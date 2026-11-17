package com.tupinicraft.network;

import com.tupinicraft.animation.PlayerAnimationState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cache apenas de dados (estado visual recebido do servidor); nao acessa classes do cliente. */
public final class PlayerAnimationClientCache {
    private static final Map<UUID, PlayerAnimationState> STATES = new HashMap<>();
    private PlayerAnimationClientCache() {}

    public static void receive(PlayerAnimationSyncPayload payload) {
        if (payload.state().isPresent()) STATES.put(payload.player(), payload.state().get());
        else STATES.remove(payload.player());
    }

    public static PlayerAnimationState get(UUID player) { return STATES.get(player); }

    public static Map<UUID, PlayerAnimationState> all() { return STATES; }

    public static void clear() { STATES.clear(); }
}
