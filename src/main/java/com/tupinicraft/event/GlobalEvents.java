package com.tupinicraft.event;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.network.AnnouncementPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Call on the server thread to notify all connected players, in every dimension. */
public final class GlobalEvents {
    private GlobalEvents() {}
    public static void broadcast(String category, Component title, Component body, int accent, int durationMillis) {
        TupiniCraft.LOGGER.info("[{}] {}", category, body.getString());
        PacketDistributor.sendToAllPlayers(new AnnouncementPayload(category, title, body, accent, durationMillis));
    }
}
