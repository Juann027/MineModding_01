package com.tupinicraft.client.hud;

import com.tupinicraft.network.ProfileClientCache;

/** Ponte de leitura para o HUD: valores enviados pelo servidor (NeedsSync). Nao altera nada. */
public final class HudStatusClient {
    private HudStatusClient() {}

    public static HudMeters current() {
        return HudMeters.of(ProfileClientCache.needs());
    }
}
