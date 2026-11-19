package com.respectcraft.client.hud;

import com.respectcraft.network.ProfileClientCache;

/** Ponte de leitura para o HUD: valores enviados pelo servidor (NeedsSync). Nao altera nada. */
public final class HudStatusClient {
    private HudStatusClient() {}

    public static HudMeters current() {
        return HudMeters.of(ProfileClientCache.needs());
    }
}
