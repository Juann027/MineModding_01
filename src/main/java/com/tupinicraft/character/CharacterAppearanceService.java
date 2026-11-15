package com.tupinicraft.character;

import com.tupinicraft.network.CharacterAppearanceSyncPayload;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dados cosméticos novos, independentes do contador de derrotas e dos demais sistemas salvos. */
public final class CharacterAppearanceService {
    private static final String DATA_KEY = "tupinicraft_character_appearance_v1";

    private CharacterAppearanceService() {}

    public static CharacterAppearance get(Player player) {
        if (!player.getPersistentData().contains(DATA_KEY, Tag.TAG_INT)) return CharacterAppearance.defaults();
        CharacterAppearance stored = CharacterAppearance.fromPacked(player.getPersistentData().getInt(DATA_KEY));
        return stored == null ? CharacterAppearance.defaults() : stored;
    }

    /** Só aceita personalização visual; uma tela de criação nunca sorteia ou altera atributos. */
    public static void apply(ServerPlayer player, int packed) {
        CharacterAppearance requested = CharacterAppearance.fromPacked(packed);
        if (requested == null || !requested.created()) return;
        player.getPersistentData().putInt(DATA_KEY, requested.pack());
        broadcast(player);
    }

    private static CharacterAppearanceSyncPayload snapshot(ServerPlayer player) {
        return new CharacterAppearanceSyncPayload(player.getUUID().toString(), get(player).pack());
    }

    private static void broadcast(ServerPlayer player) {
        PacketDistributor.sendToAllPlayers(snapshot(player));
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joining)) return;
        for (ServerPlayer online : joining.server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(joining, snapshot(online));
        }
        broadcast(joining);
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(observer, snapshot(target));
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer leaving)) return;
        CharacterAppearanceSyncPayload clear = new CharacterAppearanceSyncPayload(leaving.getUUID().toString(), -1);
        for (ServerPlayer online : leaving.server.getPlayerList().getPlayers()) {
            if (online != leaving) PacketDistributor.sendToPlayer(online, clear);
        }
    }

    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getOriginal().getPersistentData().contains(DATA_KEY, Tag.TAG_INT)) {
            event.getEntity().getPersistentData().putInt(DATA_KEY,
                    event.getOriginal().getPersistentData().getInt(DATA_KEY));
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) broadcast(player);
    }
}
