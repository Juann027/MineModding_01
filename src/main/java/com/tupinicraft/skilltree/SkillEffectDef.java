package com.tupinicraft.skilltree;

import java.util.Set;

/**
 * Efeito de uma skill: modificador de atributo aplicado enquanto ela está comprada e com o requisito valendo.
 *
 * @param attribute ID do atributo (1.21.1: {@code minecraft:generic.movement_speed}, {@code minecraft:generic.max_health}...)
 * @param operation {@code add_value}, {@code add_multiplied_base} ou {@code add_multiplied_total}
 */
public record SkillEffectDef(String attribute, String operation, double amount) {
    public static final Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");

    public SkillEffectDef {
        SkillDataParser.requireId(attribute, "atributo");
        if (!OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException("operação desconhecida \"" + operation + "\" (use " + OPERATIONS + ")");
        }
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("valor do efeito inválido: " + amount);
        }
    }
}
