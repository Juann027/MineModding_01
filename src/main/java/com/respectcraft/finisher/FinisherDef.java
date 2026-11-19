package com.respectcraft.finisher;

import com.respectcraft.animation.PlayerAnimationDef;
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
 */
public record FinisherDef(ResourceLocation id, PlayerAnimationDef attacker, PlayerAnimationDef victim,
                          CameraTrack camera, ResourceLocation requiresSkill, int weight, boolean recoveryInScene,
                          List<EntityFinisherClip> entityClips) {
    public FinisherDef {
        entityClips = List.copyOf(entityClips);
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
        return new FinisherDef(id, attacker, victim, camera, requiresSkill, weight, recoveryInScene, clips);
    }
}
