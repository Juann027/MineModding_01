package com.tupinicraft.player;

import com.tupinicraft.election.ElectionManager;
import com.tupinicraft.mandate.MandateService;
import com.tupinicraft.origin.OriginService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Eventos de jogador no servidor (eventos do NeoForge.EVENT_BUS). */
public final class PlayerEventHandler {
    private PlayerEventHandler() {
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OriginService.assignIfMissing(player);
            OriginService.applyOriginEffects(player);
            ElectionManager.onPlayerLogin(player);
            MandateService.onPlayerLogin(player);
            PoliticalDataService.sync(player);
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OriginService.applyOriginEffects(player);
            PoliticalDataService.sync(player);
        }
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PoliticalDataService.sync(player);
        }
    }
}
