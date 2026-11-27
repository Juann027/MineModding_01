package com.respectcraft.finisher;

import com.respectcraft.animation.PlayerAnimationDef;
import com.respectcraft.fx.FinisherFxDef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Finalizacao cinematografica de combate (nao explicita). So dados.
 *
 * @param id            id estavel
 * @param attacker      animacao do executor quando ele e jogador (rig do jogador)
 * @param victim        animacao da vitima quando ela e jogador (rig do jogador); null (Etapa 4.13.3) = cena so PvE,
 *                      sem clip de jogador vitima: a regra de alvos tem de recusar jogadores ({@code npcExclusive})
 * @param camera        trilha de camera dos participantes
 * @param requiresSkill habilidade que libera a finalizacao (null = todos tem)
 * @param weight        chance relativa quando o executor tem mais de uma
 * @param recoveryInScene a vitima ja se levanta dentro do proprio clip: no fim nao fica caida ({@code knocked_down})
 * @param entityClips   clips por tipo de entidade e papel, no rig GeckoLib da propria entidade (Etapa 4.8). Entidade
 *                      sem clip aqui fica parada (comportamento anterior). Todos com a duracao da cena.
 * @param fxDef         efeitos visuais no Calamidade_Awakening (Etapa 4.10), ou null: sem eles a finalizacao usa as
 *                      particulas e sons vanilla de antes. Quem os desenha e {@code fx.FinisherFx}.
 * @param targeting     quem a finalizacao pode executar (Etapa 4.10.1): {@link FinisherTargeting#REGULAR} (padrao:
 *                      jogadores e mobs, nunca chefes) ou exclusiva de um chefe ({@link FinisherTargeting#exclusiveTo})
 * @param rigs          clips por familia de rig e requisitos de participante (Etapa 4.12; {@link FinisherRigs#NONE} = como
 *                      antes). A duracao dos clips de familia e conferida no registro das familias ({@code rig.Rigs}).
 * @param entityVictimEvents eventos do lado da vitima quando ela NAO e jogador (Etapa 4.13.3): quedas
 *                      ({@code Finishers.EVENT_FALL}) e o corte da musica da fruta ({@code Finishers.EVENT_SILENCE}),
 *                      disparados pelo relogio da sessao. Vazio = como antes (mob vitima nao cai).
 */
public record FinisherDef(ResourceLocation id, PlayerAnimationDef attacker, PlayerAnimationDef victim,
                          CameraTrack camera, ResourceLocation requiresSkill, int weight, boolean recoveryInScene,
                          List<EntityFinisherClip> entityClips, FinisherFxDef fxDef, FinisherTargeting targeting,
                          FinisherRigs rigs, List<PlayerAnimationDef.TimedEvent> entityVictimEvents) {
    public FinisherDef {
        entityClips = List.copyOf(entityClips);
        entityVictimEvents = entityVictimEvents == null ? List.of() : entityVictimEvents.stream()
                .sorted(java.util.Comparator.comparingInt(PlayerAnimationDef.TimedEvent::tick)).toList();
        if (targeting == null) targeting = FinisherTargeting.REGULAR;
        if (rigs == null) rigs = FinisherRigs.NONE;
        if (victim == null && targeting.category() != FinisherTargeting.Category.NPC_EXCLUSIVE) {
            throw new IllegalArgumentException(id + ": only an NPC-exclusive finisher may omit the player victim clip");
        }
        int scene = victim == null ? attacker.durationTicks() : Math.max(attacker.durationTicks(), victim.durationTicks());
        for (PlayerAnimationDef.TimedEvent e : entityVictimEvents) {
            if (e.tick() < 0 || e.tick() >= scene) throw new IllegalArgumentException(id + ": victim event outside the scene at " + e.tick());
        }
        Set<String> seen = new HashSet<>();
        for (EntityFinisherClip clip : entityClips) {
            if (clip.durationTicks() != scene) {
                throw new IllegalArgumentException("Clip de " + clip.entityType() + " em " + id + " com " + clip.durationTicks()
                        + " ticks; a cena tem " + scene);
            }
            if (!seen.add(clip.entityType() + "/" + clip.role())) {
                throw new IllegalArgumentException("Clip repetido para " + clip.entityType() + " (" + clip.role() + ") em " + id);
            }
        }
    }

    public FinisherDef(ResourceLocation id, PlayerAnimationDef attacker, PlayerAnimationDef victim,
                       CameraTrack camera, ResourceLocation requiresSkill, int weight, boolean recoveryInScene,
                       List<EntityFinisherClip> entityClips, FinisherFxDef fxDef, FinisherTargeting targeting,
                       FinisherRigs rigs) {
        this(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fxDef, targeting, rigs, List.of());
    }

    public FinisherDef(ResourceLocation id, PlayerAnimationDef attacker, PlayerAnimationDef victim,
                       CameraTrack camera, ResourceLocation requiresSkill, int weight, boolean recoveryInScene,
                       List<EntityFinisherClip> entityClips, FinisherFxDef fxDef, FinisherTargeting targeting) {
        this(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fxDef, targeting, FinisherRigs.NONE);
    }

    public FinisherDef(ResourceLocation id, PlayerAnimationDef attacker, PlayerAnimationDef victim,
                       CameraTrack camera, ResourceLocation requiresSkill, int weight, boolean recoveryInScene,
                       List<EntityFinisherClip> entityClips, FinisherFxDef fxDef) {
        this(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fxDef, FinisherTargeting.REGULAR);
    }

    public FinisherDef(ResourceLocation id, PlayerAnimationDef attacker, PlayerAnimationDef victim,
                       CameraTrack camera, ResourceLocation requiresSkill, int weight, boolean recoveryInScene,
                       List<EntityFinisherClip> entityClips) {
        this(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, null);
    }

    public FinisherDef(ResourceLocation id, PlayerAnimationDef attacker, PlayerAnimationDef victim,
                       CameraTrack camera, ResourceLocation requiresSkill, int weight, boolean recoveryInScene) {
        this(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, List.of());
    }

    /** Finalizacao classica: no fim a vitima fica caida durante a recuperacao. */
    public FinisherDef(ResourceLocation id, PlayerAnimationDef attacker, PlayerAnimationDef victim,
                       CameraTrack camera, ResourceLocation requiresSkill, int weight) {
        this(id, attacker, victim, camera, requiresSkill, weight, false);
    }

    public int durationTicks() {
        return victim == null ? attacker.durationTicks() : Math.max(attacker.durationTicks(), victim.durationTicks());
    }

    /** Eventos do lado da vitima: os do clip de jogador, ou (cena so PvE) os da vitima entidade. */
    public List<PlayerAnimationDef.TimedEvent> victimSideEvents() {
        return victim != null ? victim.events() : entityVictimEvents;
    }

    /** Animacao de jogador do papel (null: cena sem clip de jogador vitima). */
    public PlayerAnimationDef playerClip(FinisherRole role) { return role == FinisherRole.ATTACKER ? attacker : victim; }

    /** Clip do rig proprio da entidade neste papel, se a finalizacao tiver um. */
    public Optional<EntityFinisherClip> entityClip(ResourceLocation entityType, FinisherRole role) {
        for (EntityFinisherClip clip : entityClips) {
            if (clip.entityType().equals(entityType) && clip.role() == role) return Optional.of(clip);
        }
        return Optional.empty();
    }

    /** Copia com mais um clip de entidade (use antes de {@link Finishers#register}). */
    public FinisherDef withEntityClip(EntityFinisherClip clip) {
        List<EntityFinisherClip> clips = new ArrayList<>(entityClips);
        clips.add(clip);
        return new FinisherDef(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, clips, fxDef, targeting, rigs, entityVictimEvents);
    }

    /** Efeitos visuais do Calamidade, se a finalizacao tiver. */
    public Optional<FinisherFxDef> fx() { return Optional.ofNullable(fxDef); }

    /** Copia com estes efeitos (null = sem efeitos; use antes de {@link Finishers#register}). */
    public FinisherDef withFx(FinisherFxDef fx) {
        return new FinisherDef(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fx, targeting, rigs, entityVictimEvents);
    }

    /**
     * Copia com outra regra de alvos (use antes de {@link Finishers#register}). Uma finalizacao exclusiva de chefe so
     * deve ser registrada quando o chefe, a animacao e a regra de desbloqueio existirem (nada de exclusiva provisoria).
     */
    public FinisherDef withTargeting(FinisherTargeting targeting) {
        return new FinisherDef(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fxDef, targeting, rigs, entityVictimEvents);
    }

    /** Copia com outro id (fixtures e testes: a copia nunca entra no catalogo por acidente com o id do original). */
    public FinisherDef withId(ResourceLocation newId) {
        return new FinisherDef(newId, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fxDef, targeting, rigs, entityVictimEvents);
    }

    /** Copia com mais um clip de familia de rig (Etapa 4.12; use antes de {@link Finishers#register}). */
    public FinisherDef withRigClip(FinisherRigs.Clip clip) {
        return new FinisherDef(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fxDef, targeting,
                rigs.withClip(clip), entityVictimEvents);
    }

    /** Copia com mais um requisito de participante (Etapa 4.12). */
    public FinisherDef withRigRequirement(FinisherRigs.Requirement requirement) {
        return new FinisherDef(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fxDef, targeting,
                rigs.withRequirement(requirement), entityVictimEvents);
    }
}
