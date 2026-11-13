package com.tupinicraft.skilltree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Reserva interna de créditos das árvores de habilidades. Mantém o formato salvo do antigo
 * attachment "wallet" para facilitar testes, mas não habilita economia geral.
 * Créditos podem ser atribuídos com o comando administrativo /skilladmin dinheiro set.
 */
public record SkillCredits(long balance, boolean starterGranted) {
    public static final SkillCredits EMPTY = new SkillCredits(0, false);
    public static final long MAX_BALANCE = 1_000_000_000_000L;

    public static final Codec<SkillCredits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("balance", 0L).forGetter(SkillCredits::balance),
            Codec.BOOL.optionalFieldOf("saldo_inicial_recebido", false).forGetter(SkillCredits::starterGranted)
    ).apply(instance, SkillCredits::new));

    public SkillCredits withBalance(long newBalance) {
        return new SkillCredits(newBalance, starterGranted);
    }

    public SkillCredits {
        balance = Math.max(0, Math.min(MAX_BALANCE, balance));
    }
}
