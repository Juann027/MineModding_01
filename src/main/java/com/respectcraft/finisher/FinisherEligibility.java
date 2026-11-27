package com.respectcraft.finisher;

import com.respectcraft.rig.CustomRigRegistry;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * Elegibilidade de uma finalizacao para um par de participantes, sem tocar no mundo (Etapa 4.12). Junta as regras que
 * ja existiam — combinacao com jogador ({@link FinisherParticipants#interaction}), regra de chefes
 * ({@link FinisherTargeting}) — com a resolucao de clip por rig e os requisitos de participante ({@link FinisherRigs}).
 * E a mesma consulta que o {@code FinisherTestMechanic} faz antes de comecar e que o sorteio usa para filtrar; o futuro
 * seletor de finalizacoes chama esta classe (e o {@code FinisherTestMechanic.check} para ficha de chefe e PvP) em vez de
 * duplicar regras.
 */
public final class FinisherEligibility {
    private FinisherEligibility() {}

    /** O que a regra precisa saber de um participante. */
    public record Participant(FinisherParticipants.Kind kind, ResourceLocation type, boolean geckoRig,
                              Predicate<ResourceLocation> hasTag) {
        public static Participant of(LivingEntity entity) {
            EntityType<?> type = entity.getType();
            return new Participant(FinisherParticipants.kindOf(entity), FinisherParticipants.typeId(entity), entity instanceof GeoEntity,
                    tag -> type.is(TagKey.create(Registries.ENTITY_TYPE, tag)));
        }
    }

    /** Resultado: elegivel, ou o primeiro motivo de recusa. As resolucoes valem para o diagnostico. */
    public record Verdict(String problem, FinisherTargeting.Verdict targeting,
                          FinisherParticipants.Resolution attacker, FinisherParticipants.Resolution victim) {
        public boolean eligible() { return problem == null; }
    }

    public static Verdict evaluate(FinisherDef def, LivingEntity attacker, LivingEntity victim) {
        return evaluate(CustomRigRegistry.GLOBAL, def, Participant.of(attacker), Participant.of(victim));
    }

    public static Verdict evaluate(CustomRigRegistry registry, FinisherDef def, Participant attacker, Participant victim) {
        Optional<FinisherParticipants.Interaction> interaction = FinisherParticipants.interaction(attacker.kind(), victim.kind());
        if (interaction.isEmpty()) return new Verdict("a finisher needs a player participant", null, null, null);
        FinisherTargeting.Verdict targeting = def.targeting().check(victim.kind(), victim.type());
        if (!targeting.eligible()) return new Verdict(targeting.name(), targeting, null, null);
        var a = FinisherParticipants.resolve(registry, def, FinisherRole.ATTACKER, attacker.kind(), attacker.type(),
                attacker.geckoRig(), attacker.hasTag());
        var v = FinisherParticipants.resolve(registry, def, FinisherRole.VICTIM, victim.kind(), victim.type(),
                victim.geckoRig(), victim.hasTag());
        String problem = !a.valid() ? a.problem() : !v.valid() ? v.problem() : null;
        return new Verdict(problem, targeting, a, v);
    }
}
