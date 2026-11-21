package com.respectcraft.finisher;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * Fim de uma sessao de finalizacao, postado UMA vez por sessao no {@code NeoForge.EVENT_BUS}, so no servidor
 * (Etapa 4.8). Ponto de integracao para sistemas futuros (ex.: recompensas de chefe); hoje ninguem o escuta. Nao
 * cancelavel: a sessao ja terminou quando o evento sai.
 *
 * <p>Etapa 4.10.1: todo evento tem um {@link FinisherExecution.Outcome}. {@link Aborted} = CANCELLED (nenhuma
 * execucao); {@link Completed} = a CENA terminou e o golpe final foi tentado uma vez: SUCCESS so com a morte
 * confirmada, FAILED se ela foi impedida (totem, invulnerabilidade, PvP, protecao, erro). Cena concluida nao e morte.
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
    public abstract FinisherExecution.Outcome getOutcome();

    /** A cena chegou ao fim (o unico caminho que conta derrota de jogador) e o golpe final foi tentado. */
    public static final class Completed extends FinisherSessionEvent {
        private final FinisherExecution.Result result;

        public Completed(LivingEntity attacker, LivingEntity victim, FinisherDef finisher,
                         FinisherParticipants.Interaction interaction, FinisherExecution.Result result) {
            super(attacker, victim, finisher, interaction);
            this.result = result;
        }

        public FinisherExecution.Result getResult() { return result; }
        @Override public FinisherExecution.Outcome getOutcome() { return result.outcome(); }
        /** A vitima morreu pelo golpe final desta sessao (confirmado pelo servidor). */
        public boolean killConfirmed() { return result.killConfirmed(); }
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
        @Override public FinisherExecution.Outcome getOutcome() { return FinisherExecution.Outcome.CANCELLED; }
    }
}
