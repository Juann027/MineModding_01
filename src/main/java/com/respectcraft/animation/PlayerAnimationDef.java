package com.respectcraft.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Definicao de uma animacao de jogador. E so dados: nenhuma animacao precisa de classe Java propria.
 * O servidor usa id, duracao, loop, prioridade e eventos; o cliente usa tambem o arquivo e o clip GeckoLib.
 *
 * @param id              identificador estavel (ex.: {@code respectcraft:wave})
 * @param animationFile   arquivo {@code assets/<ns>/animations/...animation.json} (so lido no cliente)
 * @param clip            nome do clip dentro do arquivo (ex.: {@code animation.respectcraft.player.wave})
 * @param durationTicks   duracao em ticks; em loop e o tamanho de um ciclo
 * @param loop            repete ate ser interrompida
 * @param priority        animacao em andamento so e trocada por outra de prioridade igual ou maior
 * @param transitionTicks ticks de mistura na entrada e na volta para a pose normal
 * @param blend           como o clip se combina com a pose vanilla (andar, agachar, olhar)
 * @param overrideBones   com {@code OVERRIDE_ANIMATED}: bones principais (Body, Head, RightArm, LeftArm, RightLeg,
 *                        LeftLeg) que seguem so o clip; vazio = todos os que o clip anima
 * @param playerTriggerable o proprio jogador pode pedir a animacao (tecla); as demais so por sistemas do servidor
 * @param stopOnHurt      interrompe quando o jogador leva dano
 * @param events          eventos em instantes fixos (som, VFX, camera...), entregues por {@link PlayerAnimationEvent.Timed}
 */
public record PlayerAnimationDef(ResourceLocation id, ResourceLocation animationFile, String clip, int durationTicks,
                                 boolean loop, int priority, int transitionTicks, Blend blend,
                                 Set<String> overrideBones, boolean playerTriggerable, boolean stopOnHurt, List<TimedEvent> events) {

    /** Combinacao do clip com a pose vanilla do jogador. */
    public enum Blend {
        /** Soma o clip a pose vanilla em todos os bones (respiracao, pequenos gestos). */
        ADDITIVE,
        /** Bones animados pelo clip seguem so o clip; os demais continuam com a pose vanilla (padrao). */
        OVERRIDE_ANIMATED,
        /** O corpo inteiro segue so o clip (cenas completas, finalizacoes). */
        FULL_BODY
    }

    /** Evento em um instante do clip. {@code type} escolhe quem trata; {@code data} e livre (id de som, intensidade...). */
    public record TimedEvent(int tick, ResourceLocation type, String data) {}

    public PlayerAnimationDef {
        if (durationTicks <= 0) throw new IllegalArgumentException("Duracao deve ser positiva: " + id);
        if (transitionTicks < 0) throw new IllegalArgumentException("Transicao negativa: " + id);
        events = List.copyOf(events);
        overrideBones = Set.copyOf(overrideBones);
        for (TimedEvent event : events) {
            if (event.tick() < 0 || event.tick() >= durationTicks) {
                throw new IllegalArgumentException("Evento fora da duracao em " + id + ": tick " + event.tick());
            }
        }
    }

    public static Builder builder(ResourceLocation id, ResourceLocation animationFile, String clip, int durationTicks) {
        return new Builder(id, animationFile, clip, durationTicks);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final ResourceLocation animationFile;
        private final String clip;
        private final int durationTicks;
        private boolean loop;
        private int priority;
        private int transitionTicks = 3;
        private Blend blend = Blend.OVERRIDE_ANIMATED;
        private Set<String> overrideBones = Set.of();
        private boolean playerTriggerable;
        private boolean stopOnHurt;
        private final List<TimedEvent> events = new ArrayList<>();

        private Builder(ResourceLocation id, ResourceLocation animationFile, String clip, int durationTicks) {
            this.id = id;
            this.animationFile = animationFile;
            this.clip = clip;
            this.durationTicks = durationTicks;
        }

        public Builder loop() { this.loop = true; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder transitionTicks(int ticks) { this.transitionTicks = ticks; return this; }
        public Builder blend(Blend blend) { this.blend = blend; return this; }
        public Builder overrideBones(String... bones) { this.overrideBones = Set.of(bones); return this; }
        public Builder playerTriggerable() { this.playerTriggerable = true; return this; }
        public Builder stopOnHurt() { this.stopOnHurt = true; return this; }
        public Builder event(int tick, ResourceLocation type, String data) {
            events.add(new TimedEvent(tick, type, data));
            return this;
        }

        public PlayerAnimationDef build() {
            return new PlayerAnimationDef(id, animationFile, clip, durationTicks, loop, priority, transitionTicks,
                    blend, overrideBones, playerTriggerable, stopOnHurt, events);
        }
    }
}
