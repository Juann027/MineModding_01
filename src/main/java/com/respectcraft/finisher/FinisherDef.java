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
 * @param victim        animacao da vitima quando ela e jogador (rig do jogador)
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
 */
public record FinisherDef(ResourceLocation id, PlayerAnimationDef attacker, PlayerAnimationDef victim,
                          CameraTrack camera, ResourceLocation requiresSkill, int weight, boolean recoveryInScene,
                          List<EntityFinisherClip> entityClips, FinisherFxDef fxDef, FinisherTargeting targeting,
                          FinisherRigs rigs) {
    public FinisherDef {
        entityClips = List.copyOf(entityClips);
        if (targeting == null) targeting = FinisherTargeting.REGULAR;
        if (rigs == null) rigs = FinisherRigs.NONE;
        int scene = Math.max(attacker.durationTicks(), victim.durationTicks());
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

    public int durationTicks() { return Math.max(attacker.durationTicks(), victim.durationTicks()); }

    /** Animacao de jogador do papel. */
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
        return new FinisherDef(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, clips, fxDef, targeting, rigs);
    }

    /** Efeitos visuais do Calamidade, se a finalizacao tiver. */
    public Optional<FinisherFxDef> fx() { return Optional.ofNullable(fxDef); }

    /** Copia com estes efeitos (null = sem efeitos; use antes de {@link Finishers#register}). */
    public FinisherDef withFx(FinisherFxDef fx) {
        return new FinisherDef(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fx, targeting, rigs);
    }

    /**
     * Copia com outra regra de alvos (use antes de {@link Finishers#register}). Uma finalizacao exclusiva de chefe so
     * deve ser registrada quando o chefe, a animacao e a regra de desbloqueio existirem (nada de exclusiva provisoria).
     */
    public FinisherDef withTargeting(FinisherTargeting targeting) {
        return new FinisherDef(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fxDef, targeting, rigs);
    }

    /** Copia com outro id (fixtures e testes: a copia nunca entra no catalogo por acidente com o id do original). */
    public FinisherDef withId(ResourceLocation newId) {
        return new FinisherDef(newId, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fxDef, targeting, rigs);
    }

    /** Copia com mais um clip de familia de rig (Etapa 4.12; use antes de {@link Finishers#register}). */
    public FinisherDef withRigClip(FinisherRigs.Clip clip) {
        return new FinisherDef(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fxDef, targeting,
                rigs.withClip(clip));
    }

    /** Copia com mais um requisito de participante (Etapa 4.12). */
    public FinisherDef withRigRequirement(FinisherRigs.Requirement requirement) {
        return new FinisherDef(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, entityClips, fxDef, targeting,
                rigs.withRequirement(requirement));
    }
}
