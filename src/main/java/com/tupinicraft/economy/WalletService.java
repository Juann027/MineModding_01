package com.tupinicraft.economy;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.config.ConfigKeys;
import com.tupinicraft.config.ServerSettings;
import com.tupinicraft.network.WalletSyncPayload;
import com.tupinicraft.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Único ponto de leitura/escrita do saldo (servidor). Débitos nunca deixam saldo negativo. Toda mudança sincroniza o HUD. */
public final class WalletService {
    private WalletService() {
    }

    public static long balance(ServerPlayer player) {
        return player.getData(ModAttachments.WALLET).balance();
    }

    public static void set(ServerPlayer player, long balance) {
        player.setData(ModAttachments.WALLET, player.getData(ModAttachments.WALLET).withBalance(balance));
        sync(player);
    }

    /** Envia o saldo ao HUD do próprio jogador. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new WalletSyncPayload(balance(player)));
    }

    /** No login: concede o saldo inicial configurado, uma única vez por jogador, e sincroniza. */
    public static void onPlayerLogin(ServerPlayer player) {
        Wallet wallet = player.getData(ModAttachments.WALLET);
        if (!wallet.starterGranted()) {
            long starter = ServerSettings.getInt(player.server, ConfigKeys.STARTING_BALANCE);
            player.setData(ModAttachments.WALLET, new Wallet(wallet.balance() + starter, true));
            if (starter > 0) {
                TupiniCraft.LOGGER.info("{} recebeu o saldo inicial de {}", player.getGameProfile().getName(), Money.format(starter));
            }
        }
        sync(player);
    }

    public static void credit(ServerPlayer player, long amount) {
        set(player, balance(player) + Math.max(0, amount));
    }

    /** Debita se houver saldo; retorna false sem alterar nada se não houver. */
    public static boolean debit(ServerPlayer player, long amount) {
        long current = balance(player);
        if (amount < 0 || current < amount) {
            return false;
        }
        set(player, current - amount);
        return true;
    }
}
