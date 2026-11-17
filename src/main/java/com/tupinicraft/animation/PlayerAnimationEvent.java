package com.tupinicraft.animation;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/**
 * Pontos de integracao do Animation Manager, publicados em {@code NeoForge.EVENT_BUS} nos DOIS lados:
 * no servidor (regras: dano, efeitos, fim de sequencia) e no cliente (som, VFX, particulas, camera, FOV).
 * Use {@code player.level().isClientSide()} para saber o lado. Os ouvintes nao conhecem o renderizador.
 */
public abstract class PlayerAnimationEvent extends Event {
    private final Player player;
    private final PlayerAnimationDef definition;
    private final PlayerAnimationState state;

    protected PlayerAnimationEvent(Player player, PlayerAnimationDef definition, PlayerAnimationState state) {
        this.player = player;
        this.definition = definition;
        this.state = state;
    }

    public Player getPlayer() { return player; }
    public PlayerAnimationDef getDefinition() { return definition; }
    public PlayerAnimationState getState() { return state; }

    /** Motivo do fim de uma animacao. */
    public enum EndReason { FINISHED, STOPPED, REPLACED, HURT, DEATH, DISCONNECT }

    /** A animacao comecou (ou recomecou) para este jogador. */
    public static final class Start extends PlayerAnimationEvent {
        public Start(Player player, PlayerAnimationDef definition, PlayerAnimationState state) {
            super(player, definition, state);
        }
    }

    /** Um evento temporizado chegou ao seu instante. */
    public static final class Timed extends PlayerAnimationEvent {
        private final PlayerAnimationDef.TimedEvent event;

        public Timed(Player player, PlayerAnimationDef definition, PlayerAnimationState state,
                     PlayerAnimationDef.TimedEvent event) {
            super(player, definition, state);
            this.event = event;
        }

        public PlayerAnimationDef.TimedEvent getEvent() { return event; }
    }

    /** A animacao terminou ou foi interrompida. No cliente o motivo e FINISHED ou STOPPED. */
    public static final class End extends PlayerAnimationEvent {
        private final EndReason reason;

        public End(Player player, PlayerAnimationDef definition, PlayerAnimationState state, EndReason reason) {
            super(player, definition, state);
            this.reason = reason;
        }

        public EndReason getReason() { return reason; }
    }
}
