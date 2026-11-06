package com.tupinicraft.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Carteira do jogador em reais fictícios (inteiros). Persistida como Data Attachment do jogador
 * (portanto por UUID), copiada na morte e alterada só pelo servidor via {@link WalletService}.
 * <p>
 * Contrato da futura economia: esta é a ÚNICA moeda do mod. Ainda não há fontes de renda
 * (Pix, salários, Mega-Sena etc. virão depois); por enquanto só admins alteram o saldo, com
 * comandos de teste.
 */
public record Wallet(long balance) {
    public static final Wallet EMPTY = new Wallet(0);
    public static final long MAX_BALANCE = 1_000_000_000_000L;

    public static final Codec<Wallet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("balance", 0L).forGetter(Wallet::balance)
    ).apply(instance, Wallet::new));

    public Wallet {
        balance = Math.max(0, Math.min(MAX_BALANCE, balance));
    }
}
