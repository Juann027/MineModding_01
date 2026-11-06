package com.tupinicraft.network;

import com.tupinicraft.client.ClientPayloadHandler;
import com.tupinicraft.election.ElectionManager;
import com.tupinicraft.skilltree.SkillTreeService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registro dos payloads. Os handlers do cliente são chamados por lambdas para que a classe
 * {@link ClientPayloadHandler} só seja carregada quando um pacote chega no cliente
 * (nunca no servidor dedicado). Handlers rodam na thread principal (padrão do NeoForge 21.1).
 */
public final class ModNetwork {
    public static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToClient(PoliticalDataSyncPayload.TYPE, PoliticalDataSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handlePoliticalData(payload));
        registrar.playToClient(ElectionSyncPayload.TYPE, ElectionSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleElectionSync(payload));
        registrar.playToClient(AnnouncementPayload.TYPE, AnnouncementPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleAnnouncement(payload));
        registrar.playToClient(OpenUrnaPayload.TYPE, OpenUrnaPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleOpenUrna());
        registrar.playToClient(WalletSyncPayload.TYPE, WalletSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleWallet(payload));
        registrar.playToClient(SkillNoticePayload.TYPE, SkillNoticePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleSkillNotice(payload));

        registrar.playToServer(CastVotePayload.TYPE, CastVotePayload.STREAM_CODEC, ModNetwork::handleCastVote);
        registrar.playToServer(SkillPurchaseRequestPayload.TYPE, SkillPurchaseRequestPayload.STREAM_CODEC,
                ModNetwork::handleSkillPurchase);
    }

    private static void handleSkillPurchase(SkillPurchaseRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            SkillTreeService.purchaseFromPanel(player, payload.skillId());
        }
    }

    private static void handleCastVote(CastVotePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer voter) {
            ElectionManager.Outcome outcome = ElectionManager.castVote(voter, payload.candidate());
            voter.sendSystemMessage(outcome.message());
        }
    }
}
