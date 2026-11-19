package com.respectcraft.finisher;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * Fim de uma sessao de finalizacao, postado UMA vez por sessao no {@code NeoForge.EVENT_BUS}, so no servidor
 * (Etapa 4.8). Ponto de integracao para sistemas futuros (ex.: recompensas de chefe); hoje so
 * {@link BossDefeats} escuta {@link Completed}. Nao cancelavel: a sessao ja terminou quando o evento sai.
 */
public abstract class FinisherSessionEvent extends Event {
    private final LivingEntity attacker;
    private final LivingEntity victim;
    private final FinisherDef finisher;
    private final FinisherParticipants.Interaction interaction;

    protected FinisherSessionEvent(LivingEntity attacker, LivingEntity victim, FinisherDef finisher,
                                   FinisherParticipants.Interaction interaction) {
        this.attacker = attacker;
        this.victim = victim;
        this.finisher = finisher;
        this.interaction = interaction;
    }

    public LivingEntity getAttacker() { return attacker; }
    public LivingEntity getVictim() { return victim; }
    public FinisherDef getFinisher() { return finisher; }
    public FinisherParticipants.Interaction getInteraction() { return interaction; }

    /** A cena chegou ao fim (o unico caminho que conta derrota de jogador). */
    public static final class Completed extends FinisherSessionEvent {
        public Completed(LivingEntity attacker, LivingEntity victim, FinisherDef finisher,
                         FinisherParticipants.Interaction interaction) {
            super(attacker, victim, finisher, interaction);
        }
    }

    /** A cena foi interrompida (dano, morte, saida, troca de dimensao, distancia, Shift, comando). */
    public static final class Aborted extends FinisherSessionEvent {
        private final String reason;

        public Aborted(LivingEntity attacker, LivingEntity victim, FinisherDef finisher,
                       FinisherParticipants.Interaction interaction, String reason) {
            super(attacker, victim, finisher, interaction);
            this.reason = reason;
        }

        public String getReason() { return reason; }
    }
}
