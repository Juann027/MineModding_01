package com.tupinicraft.testgame;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.tupinicraft.network.DefeatCountSyncPayload;

/**
 * Contador de derrotas especiais de TESTE. Nunca incrementa por dano comum ou
 * por carregar alguém. Um futuro duelo consentido poderá registrar seu resultado aqui.
 */
public final class DefeatCounter {
    private static final String DATA_KEY = "tupinicraft_special_defeats";

    private DefeatCounter() {}

    public static int get(Player player) {
        return Math.max(0, player.getPersistentData().getInt(DATA_KEY));
    }

    /** Operação exclusiva do servidor. */
    public static int set(ServerPlayer player, int amount) {
        int value = Math.max(0, amount);
        player.getPersistentData().putInt(DATA_KEY, value);
        broadcast(player);
        return value;
    }

    public static int increment(ServerPlayer player) {
        return set(player, get(player) == Integer.MAX_VALUE ? Integer.MAX_VALUE : get(player) + 1);
    }

    private static void broadcast(ServerPlayer player) {
        PacketDistributor.sendToAllPlayers(new DefeatCountSyncPayload(player.getUUID().toString(), get(player)));
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joining)) return;
        // Inclui jogadores já conectados e o próprio jogador: o cliente conhece todos
        // os contadores, inclusive depois de um respawn/troca de dimensão.
        for (ServerPlayer online : joining.server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(joining, new DefeatCountSyncPayload(online.getUUID().toString(), get(online)));
        }
        broadcast(joining);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer leaving)) return;
        DefeatCountSyncPayload clear = new DefeatCountSyncPayload(leaving.getUUID().toString(), -1);
        for (ServerPlayer online : leaving.server.getPlayerList().getPlayers()) {
            if (online != leaving) PacketDistributor.sendToPlayer(online, clear);
        }
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(observer, new DefeatCountSyncPayload(target.getUUID().toString(), get(target)));
        }
    }

    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            int old = get(event.getOriginal());
            event.getEntity().getPersistentData().putInt(DATA_KEY, old);
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) broadcast(player);
    }
}
