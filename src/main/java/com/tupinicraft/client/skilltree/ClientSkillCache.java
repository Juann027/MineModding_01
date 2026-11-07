package com.tupinicraft.client.skilltree;

import com.tupinicraft.network.SkillDefinitionsPayload;
import com.tupinicraft.network.SkillStatePayload;

import java.util.List;
import java.util.Set;

/** Cópia visual (somente leitura) das árvores e do estado do jogador, recebida do servidor. Limpa ao desconectar. */
public final class ClientSkillCache {
    private static SkillDefinitionsPayload definitions;
    private static SkillStatePayload state;

    private ClientSkillCache() {
    }

    public static void setDefinitions(SkillDefinitionsPayload payload) {
        definitions = payload;
    }

    public static void setState(SkillStatePayload payload) {
        state = payload;
    }

    public static boolean ready() {
        return definitions != null && state != null;
    }

    public static List<SkillDefinitionsPayload.TreeView> visibleTrees() {
        if (!ready()) {
            return List.of();
        }
        Set<String> visible = Set.copyOf(state.visibleTrees());
        return definitions.trees().stream().filter(tree -> visible.contains(tree.id())).toList();
    }

    public static List<SkillDefinitionsPayload.SkillView> skills() {
        return definitions == null ? List.of() : definitions.skills();
    }

    public static boolean unlocked(String skillId) {
        return state != null && state.unlocked().contains(skillId);
    }

    public static boolean suspended(String skillId) {
        return state != null && state.suspended().contains(skillId);
    }

    public static void clear() {
        definitions = null;
        state = null;
    }
}
