package com.tupinicraft.skilltree;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Uma skill (nó da árvore). Lida de {@code data/<ns>/tupinicraft_skilltree/skills/<nome>.json}.
 *
 * @param rule   custos e requisitos (o tipo e o requisito vêm da árvore)
 * @param parent skill que precisa estar comprada antes (vazio = raiz da árvore)
 * @param frame  moldura do ícone: {@code task} (comum), {@code goal} (arredondada) ou {@code challenge} (especial)
 */
public record SkillDef(String id, String tree, Optional<String> parent, String name, String description, String icon,
                       String frame, List<SkillEffectDef> effects, SkillRule rule) {
    public static final Set<String> FRAMES = Set.of("task", "goal", "challenge");

    public SkillDef {
        SkillDataParser.requireId(id, "skill");
        SkillDataParser.requireId(icon, id + ": ícone");
        SkillDataParser.require(FRAMES.contains(frame), id, "moldura deve ser task, goal ou challenge");
        SkillDataParser.require(!name.isBlank(), id, "nome obrigatório");
        effects = List.copyOf(effects);
    }
}
