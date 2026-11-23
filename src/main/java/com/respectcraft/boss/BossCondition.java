package com.respectcraft.boss;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Condicao extra para a finalizacao EXCLUSIVA de um chefe poder comecar (Etapa 4.11). So dados e regra pura (coberta
 * pelo {@code SelfTest}); quem avalia e {@link Bosses#checkExecution}, no servidor, antes de qualquer palco, animacao,
 * trava ou efeito. As travas comuns (distancia, vida abaixo de 25%, cooldown, sessao ativa) continuam valendo antes.
 *
 * <p>Nenhum chefe real usa condicoes ainda: elas chegam quando o dono aprovar cada chefe.
 */
public sealed interface BossCondition {
    /**
     * O que o servidor sabe no momento do pedido.
     *
     * @param healthFraction vida do chefe / vida maxima (0..1)
     * @param hasSkill       o executor tem esta habilidade ({@code Skills}, gravada na ficha)
     * @param progress       o executor tem esta marca de progressao. Ponto de integracao: hoje NAO existe progressao de
     *                       chefes, entao o servidor responde sempre "nao" e {@link RequiresProgress} sempre recusa
     */
    record Context(float healthFraction, Predicate<ResourceLocation> hasSkill, Predicate<ResourceLocation> progress) {}

    /** Null/vazio = condicao satisfeita; senao o motivo da recusa (ingles, para o jogador). */
    Optional<String> denial(Context context);

    /** Vida do chefe abaixo desta fracao (0 &lt; fracao &lt;= 1). */
    record HealthBelow(float fraction) implements BossCondition {
        public HealthBelow {
            if (!(fraction > 0F && fraction <= 1F)) throw new IllegalArgumentException("fraction fora de (0,1]: " + fraction);
        }

        @Override
        public Optional<String> denial(Context context) {
            return context.healthFraction() < fraction ? Optional.empty()
                    : Optional.of("The boss must be below " + Math.round(fraction * 100F) + "% health.");
        }
    }

    /** O executor precisa ter esta habilidade (integracao com as Skill Trees). */
    record RequiresSkill(ResourceLocation skill) implements BossCondition {
        @Override
        public Optional<String> denial(Context context) {
            return context.hasSkill().test(skill) ? Optional.empty()
                    : Optional.of("You have not unlocked the skill required for this execution.");
        }
    }

    /** O executor precisa desta marca de progressao (integracao futura; sem sistema de progressao, sempre recusa). */
    record RequiresProgress(ResourceLocation key) implements BossCondition {
        @Override
        public Optional<String> denial(Context context) {
            return context.progress().test(key) ? Optional.empty()
                    : Optional.of("You have not made the progress required for this execution.");
        }
    }
}
