package com.tupinicraft.skilltree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Skills que o TupiniCraft desativou porque um requisito deixou de valer (ex.: fim do mandato, saída do
 * partido). Quando o requisito volta, são reativadas automaticamente. Desbloqueio e nível ficam no SkillLib.
 */
public record SkillTreeState(Set<String> suspended) {
    public static final SkillTreeState EMPTY = new SkillTreeState(Set.of());

    public static final Codec<SkillTreeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("suspended", List.of())
                    .xmap(list -> (Set<String>) new TreeSet<>(list), set -> List.copyOf(set))
                    .forGetter(SkillTreeState::suspended)
    ).apply(instance, SkillTreeState::new));

    public SkillTreeState {
        suspended = Set.copyOf(suspended);
    }

    public SkillTreeState with(String skillId, boolean isSuspended) {
        Set<String> next = new TreeSet<>(suspended);
        if (isSuspended) {
            next.add(skillId);
        } else {
            next.remove(skillId);
        }
        return new SkillTreeState(next);
    }
}
