package com.tupinicraft.economy;

import com.tupinicraft.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

/** Único ponto de leitura/escrita do saldo (servidor). Débitos nunca deixam saldo negativo. */
public final class WalletService {
    private WalletService() {
    }

    public static long balance(ServerPlayer player) {
        return player.getData(ModAttachments.WALLET).balance();
    }

    public static void set(ServerPlayer player, long balance) {
        player.setData(ModAttachments.WALLET, new Wallet(balance));
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
