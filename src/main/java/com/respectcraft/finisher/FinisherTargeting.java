package com.respectcraft.finisher;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Quem uma finalizacao pode EXECUTAR (Etapa 4.10.1). Regra permanente do projeto: chefes podem ser derrotados
 * normalmente (dano comum, habilidades), mas pelo Finisher Engine so a finalizacao EXCLUSIVA designada para aquele
 * chefe pode executa-lo. Finalizacoes comuns nunca executam chefes.
 *
 * <ul>
 *   <li>{@link #REGULAR} (padrao de toda {@link FinisherDef}): jogadores e mobs comuns; chefe recusado.</li>
 *   <li>{@link #exclusiveTo(ResourceLocation)}: so o chefe daquele tipo de entidade; qualquer outro alvo (outro
 *       chefe, mob comum, jogador) recusado.</li>
 * </ul>
 *
 * <p>Chefe = {@link FinisherParticipants.Kind#BOSS} (tags {@code c:bosses} ou {@code respectcraft:bosses}). So
 * regra pura: a checagem acontece no servidor, ANTES de a cena comecar ({@code FinisherTestMechanic.start}), e de
 * novo na execucao (defesa). Nenhuma finalizacao exclusiva existe ainda; elas chegam com os chefes do RespectCraft.
 *
 * @param exclusiveBoss tipo de entidade do chefe designado, ou null para finalizacao comum
 */
public record FinisherTargeting(ResourceLocation exclusiveBoss) {
    public static final FinisherTargeting REGULAR = new FinisherTargeting(null);

    /** Resultado da checagem de um alvo. */
    public enum Verdict {
        ELIGIBLE,
        /** Finalizacao comum contra chefe: recusada antes da cena. */
        BOSS_REQUIRES_EXCLUSIVE,
        /** Finalizacao exclusiva contra qualquer alvo que nao seja o chefe designado. */
        EXCLUSIVE_OTHER_TARGET;

        public boolean eligible() { return this == ELIGIBLE; }
    }

    /** Finalizacao exclusiva do chefe deste tipo de entidade. */
    public static FinisherTargeting exclusiveTo(ResourceLocation bossType) {
        return new FinisherTargeting(Objects.requireNonNull(bossType, "bossType"));
    }

    public boolean regular() { return exclusiveBoss == null; }

    /** Regra pura: pode esta finalizacao executar um alvo deste tipo? */
    public Verdict check(FinisherParticipants.Kind victimKind, ResourceLocation victimType) {
        if (regular()) return victimKind == FinisherParticipants.Kind.BOSS ? Verdict.BOSS_REQUIRES_EXCLUSIVE : Verdict.ELIGIBLE;
        return victimKind == FinisherParticipants.Kind.BOSS && exclusiveBoss.equals(victimType)
                ? Verdict.ELIGIBLE : Verdict.EXCLUSIVE_OTHER_TARGET;
    }
}
