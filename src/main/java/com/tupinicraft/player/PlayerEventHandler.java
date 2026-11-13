package com.tupinicraft.player;

import com.tupinicraft.origin.OriginService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Apenas a origem regional usada por determinadas skill trees. */
public final class PlayerEventHandler {
    private PlayerEventHandler() {}
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OriginService.assignIfMissing(player);
        }
    }
}
