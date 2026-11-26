package com.respectcraft.boss;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Ficha de um chefe personalizado do RespectCraft (Etapa 4.11). So dados; registrada em {@link Bosses}.
 *
 * <p>Identidade: o tipo de entidade. Ser chefe continua sendo decidido pelas tags ({@code c:bosses} ou
 * {@code respectcraft:bosses}, {@code FinisherParticipants.kindOf}); a ficha so acrescenta o que e proprio do chefe —
 * quais finalizacoes EXCLUSIVAS o executam e as condicoes extras delas. Chefe com tag e sem ficha (Wither, Dragao do
 * Fim, chefes de outros mods) continua como antes: derrota comum normal e nenhuma finalizacao.
 *
 * @param entityType          tipo de entidade do chefe (id estavel)
 * @param exclusiveFinishers  ids das finalizacoes exclusivas deste chefe; cada uma precisa estar no catalogo com
 *                            {@code FinisherTargeting.exclusiveTo(entityType)} ({@link Bosses#validate}). Vazio = chefe
 *                            que so pode ser derrotado normalmente
 * @param executionConditions condicoes extras para as exclusivas comecarem (todas precisam passar)
 */
public record BossProfile(ResourceLocation entityType, List<ResourceLocation> exclusiveFinishers,
                          List<BossCondition> executionConditions) {
    public BossProfile {
        Objects.requireNonNull(entityType, "entityType");
        exclusiveFinishers = List.copyOf(exclusiveFinishers);
        executionConditions = List.copyOf(executionConditions);
    }

    public static BossProfile of(ResourceLocation entityType) {
        return new BossProfile(entityType, List.of(), List.of());
    }

    public BossProfile withExclusiveFinisher(ResourceLocation finisher) {
        List<ResourceLocation> list = new java.util.ArrayList<>(exclusiveFinishers);
        list.add(finisher);
        return new BossProfile(entityType, list, executionConditions);
    }

    public BossProfile withCondition(BossCondition condition) {
        List<BossCondition> list = new java.util.ArrayList<>(executionConditions);
        list.add(condition);
        return new BossProfile(entityType, exclusiveFinishers, list);
    }
}
