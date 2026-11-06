package com.tupinicraft.client;

import com.tupinicraft.network.AnnouncementPayload;
import com.tupinicraft.network.ElectionSyncPayload;
import com.tupinicraft.network.PoliticalDataSyncPayload;
import net.minecraft.client.Minecraft;

/**
 * Handlers dos pacotes servidor → cliente. Só é carregada no cliente (ver ModNetwork).
 * Executa na thread principal do cliente.
 */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handlePoliticalData(PoliticalDataSyncPayload payload) {
        ClientPoliticalCache.set(payload);
    }

    public static void handleElectionSync(ElectionSyncPayload payload) {
        ClientElectionCache.update(payload);
    }

    public static void handleAnnouncement(AnnouncementPayload payload) {
        AnnouncementOverlay.push(payload.message());
    }

    public static void handleOpenUrna() {
        Minecraft.getInstance().setScreen(new UrnaScreen());
    }
}
