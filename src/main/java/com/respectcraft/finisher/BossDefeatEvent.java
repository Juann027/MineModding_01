package com.respectcraft.finisher;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * Derrota de um chefe ({@code c:bosses}), postada no maximo UMA vez por entidade chefe, so no servidor (Etapa 4.8).
 * PREPARACAO: nenhum ouvinte concede recompensa, habilidade, conquista ou drop nesta etapa. Uma etapa futura
 * registra um ouvinte aqui com a lista de chefes e as regras de recompensa.
 */
public final class BossDefeatEvent extends Event {
    /** Como o chefe foi derrotado. */
    public enum Method {
        /** Morte comum (dano, sem finalizacao recente). */
        NORMAL,
        /** Morte dentro da janela de credito de uma finalizacao concluida contra este chefe. */
        FINISHER
    }

    private final LivingEntity boss;
    private final Method method;
    private final ServerPlayer credited;
    private final ResourceLocation finisher;

    public BossDefeatEvent(LivingEntity boss, Method method, ServerPlayer credited, ResourceLocation finisher) {
        this.boss = boss;
        this.method = method;
        this.credited = credited;
        this.finisher = finisher;
    }

    public LivingEntity getBoss() { return boss; }
    public Method getMethod() { return method; }
    /** Jogador creditado: o executor da finalizacao (FINISHER) ou quem recebe o credito da morte (NORMAL). */
    public Optional<ServerPlayer> getCredited() { return Optional.ofNullable(credited); }
    /** Finalizacao qualificante (so em FINISHER). */
    public Optional<ResourceLocation> getFinisher() { return Optional.ofNullable(finisher); }
}
