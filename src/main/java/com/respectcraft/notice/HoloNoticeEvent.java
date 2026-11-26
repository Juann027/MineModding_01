package com.respectcraft.notice;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Postado no servidor antes de um aviso holografico sair (Etapa 4.11). Outros sistemas (ou mods) podem trocar o
 * aviso, tirar destinatarios da lista ou cancelar o envio (cancelado = nada sai e nenhum farol acende).
 */
public final class HoloNoticeEvent extends Event implements ICancellableEvent {
    private HoloNotice notice;
    private final List<ServerPlayer> audience;

    public HoloNoticeEvent(HoloNotice notice, List<ServerPlayer> audience) {
        this.notice = notice;
        this.audience = audience;
    }

    public HoloNotice getNotice() { return notice; }
    public void setNotice(HoloNotice notice) { if (notice != null) this.notice = notice; }
    /** Lista mutavel de quem recebe. */
    public List<ServerPlayer> getAudience() { return audience; }
}
