package com.tupinicraft.testgame;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.tupinicraft.network.DefeatCountSyncPayload;

/**
 * Contador de derrotas por finalização (exibido acima do nick por {@code DefeatCounterNameplate}). Só o servidor
 * altera: +1 para o ALVO em {@code FinisherTestMechanic.complete} (uma vez por sessão; cancelamentos e interrupções
 * passam por {@code abort} e não contam) e pelos comandos de admin {@code /tupini... defeats add|set}. Nunca por
 * dano, morte comum ou por carregar alguém. Persistido no persistentData do jogador (chave estável, não renomear).
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
        return set(player, next(get(player)));
    }

    /** Regra pura do incremento (+1, sem passar do máximo); coberta pelo {@code SelfTest}. */
    public static int next(int current) {
        return current >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, current) + 1;
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

    /**
     * Morte e volta do End recriam o ServerPlayer: copia sempre (não só na morte), porque o persistentData da
     * NeoForge não é copiado no {@code restoreFrom}. Troca comum de dimensão mantém o mesmo objeto.
     */
    public static void onClone(PlayerEvent.Clone event) {
        event.getEntity().getPersistentData().putInt(DATA_KEY, get(event.getOriginal()));
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) broadcast(player);
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) broadcast(player);
    }
}
