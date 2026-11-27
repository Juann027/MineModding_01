package com.respectcraft.finisher;

import java.util.Objects;
import java.util.Set;
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
 *   <li>{@link #npcExclusive(ResourceLocation...)} (Etapa 4.13.3): so mobs comuns destes tipos (pool proprio do NPC,
 *       ex.: as cinco do morango). Jogador, chefe e qualquer outro mob recusados; nunca entra no loadout de PvP.</li>
 * </ul>
 *
 * <p>Chefe = {@link FinisherParticipants.Kind#BOSS} (tags {@code c:bosses} ou {@code respectcraft:bosses}). So
 * regra pura: a checagem acontece no servidor, ANTES de a cena comecar ({@code FinisherTestMechanic.start}), e de
 * novo na execucao (defesa). Nenhuma finalizacao exclusiva existe ainda; elas chegam com os chefes do RespectCraft.
 *
 * @param exclusiveBoss tipo de entidade do chefe designado, ou null para finalizacao comum
 * @param npcTypes      tipos de mob comum do pool proprio (vazio = nao e exclusiva de NPC)
 */
public record FinisherTargeting(ResourceLocation exclusiveBoss, Set<ResourceLocation> npcTypes) {
    public static final FinisherTargeting REGULAR = new FinisherTargeting(null, Set.of());

    /** Categoria da finalizacao na politica de selecao ({@code FinisherSelection}). */
    public enum Category {
        /** Comum: jogadores (PvP, com desbloqueio) e mobs sem pool proprio. */
        GENERIC,
        /** Exclusiva de um chefe. */
        BOSS_EXCLUSIVE,
        /** Pool proprio de um ou mais tipos de NPC (sem desbloqueio pessoal). */
        NPC_EXCLUSIVE
    }

    public FinisherTargeting {
        npcTypes = npcTypes == null ? Set.of() : Set.copyOf(npcTypes);
        if (exclusiveBoss != null && !npcTypes.isEmpty()) throw new IllegalArgumentException("boss and NPC exclusive at once");
    }

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
        return new FinisherTargeting(Objects.requireNonNull(bossType, "bossType"), Set.of());
    }

    /** Pool proprio destes tipos de mob comum (Etapa 4.13.3). */
    public static FinisherTargeting npcExclusive(ResourceLocation... types) {
        if (types.length == 0) throw new IllegalArgumentException("npcExclusive needs at least one entity type");
        return new FinisherTargeting(null, Set.of(types));
    }

    /** Comum (nem exclusiva de chefe nem de NPC). */
    public boolean regular() { return exclusiveBoss == null && npcTypes.isEmpty(); }

    public Category category() {
        return exclusiveBoss != null ? Category.BOSS_EXCLUSIVE : npcTypes.isEmpty() ? Category.GENERIC : Category.NPC_EXCLUSIVE;
    }

    /** Pool proprio deste tipo de NPC? */
    public boolean ownPoolOf(ResourceLocation type) { return type != null && npcTypes.contains(type); }

    /** Regra pura: pode esta finalizacao executar um alvo deste tipo? */
    public Verdict check(FinisherParticipants.Kind victimKind, ResourceLocation victimType) {
        if (victimKind == FinisherParticipants.Kind.BOSS && exclusiveBoss == null) return Verdict.BOSS_REQUIRES_EXCLUSIVE;
        if (regular()) return Verdict.ELIGIBLE;
        if (!npcTypes.isEmpty()) {
            return victimKind == FinisherParticipants.Kind.MOB && ownPoolOf(victimType)
                    ? Verdict.ELIGIBLE : Verdict.EXCLUSIVE_OTHER_TARGET;
        }
        return victimKind == FinisherParticipants.Kind.BOSS && exclusiveBoss.equals(victimType)
                ? Verdict.ELIGIBLE : Verdict.EXCLUSIVE_OTHER_TARGET;
    }
}
