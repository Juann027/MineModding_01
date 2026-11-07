package com.tupinicraft.skilltree;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Catálogo validado de árvores e skills (puro). Arquivos inválidos são descartados com mensagem de erro, sem
 * derrubar os demais. Regras: a skill aponta para uma árvore existente; o pai existe e é da mesma árvore; cada
 * árvore tem exatamente uma raiz (skill sem pai), que é por onde a tela começa a desenhar.
 */
public record SkillCatalogData(Map<String, SkillTreeDef> trees, Map<String, SkillDef> skills) {
    public static final SkillCatalogData EMPTY = new SkillCatalogData(Map.of(), Map.of());

    public SkillCatalogData {
        trees = Map.copyOf(trees);
        skills = Map.copyOf(skills);
    }

    public static SkillCatalogData build(Map<String, JsonObject> treeFiles, Map<String, JsonObject> skillFiles, Consumer<String> errors) {
        Map<String, SkillTreeDef> trees = new LinkedHashMap<>();
        treeFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                trees.put(entry.getKey(), SkillDataParser.parseTree(entry.getKey(), entry.getValue()));
            } catch (RuntimeException e) {
                errors.accept("Árvore inválida " + entry.getKey() + ": " + e.getMessage());
            }
        });
        Map<String, SkillDef> skills = new LinkedHashMap<>();
        skillFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                String treeId = SkillDataParser.treeOf(entry.getKey(), entry.getValue());
                SkillTreeDef tree = trees.get(treeId);
                if (tree == null) {
                    throw new IllegalArgumentException("árvore inexistente \"" + treeId + "\"");
                }
                skills.put(entry.getKey(), SkillDataParser.parseSkill(entry.getKey(), entry.getValue(), tree));
            } catch (RuntimeException e) {
                errors.accept("Skill inválida " + entry.getKey() + ": " + e.getMessage());
            }
        });
        // Pais: precisam existir e ser da mesma árvore (repete até estabilizar, pois remover uma skill órfã
        // pode deixar os filhos dela órfãos também).
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SkillDef skill : List.copyOf(skills.values())) {
                Optional<String> parent = skill.parent();
                if (parent.isPresent() && (!skills.containsKey(parent.get()) || !skills.get(parent.get()).tree().equals(skill.tree()))) {
                    errors.accept("Skill inválida " + skill.id() + ": pai \"" + parent.get() + "\" não existe nesta árvore");
                    skills.remove(skill.id());
                    changed = true;
                }
            }
        }
        // Uma raiz por árvore; árvores sem skills ficam fora da tela.
        for (SkillTreeDef tree : List.copyOf(trees.values())) {
            List<SkillDef> roots = skills.values().stream().filter(s -> s.tree().equals(tree.id()) && s.parent().isEmpty()).toList();
            if (roots.size() > 1) {
                errors.accept("Árvore " + tree.id() + " tem " + roots.size() + " raízes; deve ter exatamente uma (as demais precisam de \"pai\")");
                roots.subList(1, roots.size()).forEach(extra -> removeWithDescendants(skills, extra.id()));
            }
        }
        return new SkillCatalogData(trees, skills);
    }

    private static void removeWithDescendants(Map<String, SkillDef> skills, String id) {
        skills.remove(id);
        for (SkillDef child : List.copyOf(skills.values())) {
            if (child.parent().filter(id::equals).isPresent()) {
                removeWithDescendants(skills, child.id());
            }
        }
    }

    public Optional<SkillDef> skill(String id) {
        return Optional.ofNullable(skills.get(id));
    }

    public Collection<SkillDef> allSkills() {
        return skills.values();
    }

    /** Árvores com pelo menos uma skill, na ordem das abas. */
    public List<SkillTreeDef> treesInOrder() {
        List<SkillTreeDef> list = new ArrayList<>();
        for (SkillTreeDef tree : trees.values()) {
            if (skills.values().stream().anyMatch(s -> s.tree().equals(tree.id()))) {
                list.add(tree);
            }
        }
        list.sort(Comparator.comparingInt(SkillTreeDef::order).thenComparing(SkillTreeDef::id));
        return list;
    }

    /** Filhos de uma skill, em ordem de ID (a mesma usada para desenhar). */
    public List<SkillDef> childrenOf(String id) {
        return skills.values().stream().filter(s -> s.parent().filter(id::equals).isPresent())
                .sorted(Comparator.comparing(SkillDef::id)).toList();
    }
}
