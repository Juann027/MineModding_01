package com.tupinicraft.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Pacotes dedicados à camada de teste. Sem dependências gráficas no servidor. */
public final class TestGameNetworking {
    private TestGameNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                CharacterAppearanceUpdatePayload.TYPE,
                CharacterAppearanceUpdatePayload.STREAM_CODEC,
                CharacterAppearanceUpdatePayload::receive
        );
        event.registrar("1").playToClient(
                CharacterAppearanceSyncPayload.TYPE,
                CharacterAppearanceSyncPayload.STREAM_CODEC,
                (payload, context) -> CharacterAppearanceClientCache.receive(payload)
        );
        event.registrar("1").playToClient(
                DefeatCountSyncPayload.TYPE,
                DefeatCountSyncPayload.STREAM_CODEC,
                (payload, context) -> DefeatCountClientCache.receive(payload)
        );
        event.registrar("1").playToClient(
                FinisherVisualSyncPayload.TYPE,
                FinisherVisualSyncPayload.STREAM_CODEC,
                (payload, context) -> FinisherVisualClientCache.receive(payload)
        );
    }
}
