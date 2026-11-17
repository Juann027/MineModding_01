package com.tupinicraft.finisher;

import com.tupinicraft.animation.PlayerAnimationDef;
import net.minecraft.resources.ResourceLocation;

/**
 * Finalizacao cinematografica de combate (nao explicita). So dados.
 *
 * @param id            id estavel
 * @param attacker      animacao do executor
 * @param victim        animacao da vitima quando ela e jogador (mobs ficam parados)
 * @param camera        trilha de camera dos participantes
 * @param requiresSkill habilidade que libera a finalizacao (null = todos tem)
 * @param weight        chance relativa quando o executor tem mais de uma
 */
public record FinisherDef(ResourceLocation id, PlayerAnimationDef attacker, PlayerAnimationDef victim,
                          CameraTrack camera, ResourceLocation requiresSkill, int weight) {
    public int durationTicks() { return Math.max(attacker.durationTicks(), victim.durationTicks()); }
}
