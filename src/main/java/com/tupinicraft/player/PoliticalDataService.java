package com.tupinicraft.player;

import com.tupinicraft.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Persistência legada restrita às condições/pontos das skill trees. Não sincroniza HUD político. */
public final class PoliticalDataService {
    private PoliticalDataService() {}
    public static PoliticalData get(Player player) {
        return player.getData(ModAttachments.POLITICAL_DATA);
    }
    public static void set(ServerPlayer player, PoliticalData data) {
        player.setData(ModAttachments.POLITICAL_DATA, data);
    }
}
