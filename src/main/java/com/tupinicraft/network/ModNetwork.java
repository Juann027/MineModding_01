package com.tupinicraft.network;

import com.tupinicraft.client.ClientPayloadHandler;
import com.tupinicraft.skilltree.SkillTreeService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Rede exclusiva das árvores de habilidades. */
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "5";
    private ModNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(SkillDefinitionsPayload.TYPE, SkillDefinitionsPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleSkillDefinitions(payload));
        registrar.playToClient(SkillStatePayload.TYPE, SkillStatePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleSkillState(payload));
        registrar.playToClient(SkillNoticePayload.TYPE, SkillNoticePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleSkillNotice(payload));
        registrar.playToServer(SkillPurchaseRequestPayload.TYPE, SkillPurchaseRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player)
                        SkillTreeService.purchaseFromScreen(player, payload.skillId());
                });
    }
}
