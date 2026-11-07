package com.tupinicraft.skilltree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Skills do jogador (attachment {@code tupinicraft:skill_tree_state}, copiado na morte).
 *
 * @param unlocked  skills compradas (nunca são apagadas: perder o requisito só suspende)
 * @param suspended compradas cujo requisito não vale agora (sem efeito até o requisito voltar)
 */
public record SkillTreeState(Set<String> unlocked, Set<String> suspended) {
    public static final SkillTreeState EMPTY = new SkillTreeState(Set.of(), Set.of());

    private static final Codec<Set<String>> SET = Codec.STRING.listOf()
            .xmap(list -> (Set<String>) new TreeSet<>(list), set -> List.copyOf(set));

    // "suspended" vem da v0.3.0/v0.4.0 (na época as compras ficavam no SkillLib e se perderam com ele); "desbloqueadas" é novo.
    public static final Codec<SkillTreeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SET.optionalFieldOf("desbloqueadas", Set.of()).forGetter(SkillTreeState::unlocked),
            SET.optionalFieldOf("suspended", Set.of()).forGetter(SkillTreeState::suspended)
    ).apply(instance, SkillTreeState::new));

    public SkillTreeState {
        unlocked = Set.copyOf(unlocked);
        suspended = Set.copyOf(suspended);
    }

    public boolean isUnlocked(String skillId) {
        return unlocked.contains(skillId);
    }

    public SkillTreeState withUnlocked(String skillId) {
        Set<String> next = new TreeSet<>(unlocked);
        next.add(skillId);
        return new SkillTreeState(next, suspended);
    }

    public SkillTreeState withSuspended(String skillId, boolean isSuspended) {
        Set<String> next = new TreeSet<>(suspended);
        if (isSuspended) {
            next.add(skillId);
        } else {
            next.remove(skillId);
        }
        return new SkillTreeState(unlocked, next);
    }
}
