package com.tupinicraft.network;

import com.tupinicraft.character.CharacterAppearance;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cache apenas de dados; não acessa classes do MinecraftClient no servidor dedicado. */
public final class CharacterAppearanceClientCache {
    private static final Map<UUID, CharacterAppearance> APPEARANCES = new HashMap<>();
    private CharacterAppearanceClientCache() {}

    public static void receive(CharacterAppearanceSyncPayload payload) {
        try {
            UUID uuid = UUID.fromString(payload.uuid());
            if (payload.appearance() == -1) {
                APPEARANCES.remove(uuid);
                return;
            }
            CharacterAppearance appearance = CharacterAppearance.fromPacked(payload.appearance());
            if (appearance != null) APPEARANCES.put(uuid, appearance);
        } catch (IllegalArgumentException ignored) {
            // Rede inválida não derruba renderização.
        }
    }

    public static CharacterAppearance get(UUID uuid) {
        return APPEARANCES.getOrDefault(uuid, CharacterAppearance.defaults());
    }

    public static void clear() { APPEARANCES.clear(); }
}
