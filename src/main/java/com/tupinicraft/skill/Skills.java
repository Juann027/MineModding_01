package com.tupinicraft.skill;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.finisher.Finishers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Catalogo das habilidades (codigo, igual no cliente e no servidor). Para adicionar uma: chame {@link #register}
 * com o pai e a posicao; a tela e a compra se ajustam sozinhas. Validado por {@link #validate()} no autoteste.
 */
public final class Skills {
    private static final Map<ResourceLocation, SkillDef> REGISTRY = new LinkedHashMap<>();

    private static final ResourceLocation ATTACK = ResourceLocation.withDefaultNamespace("generic.attack_damage");
    private static final ResourceLocation ATTACK_SPEED = ResourceLocation.withDefaultNamespace("generic.attack_speed");
    private static final ResourceLocation ARMOR = ResourceLocation.withDefaultNamespace("generic.armor");
    private static final ResourceLocation HEALTH = ResourceLocation.withDefaultNamespace("generic.max_health");
    private static final ResourceLocation SPEED = ResourceLocation.withDefaultNamespace("generic.movement_speed");
    private static final ResourceLocation LUCK = ResourceLocation.withDefaultNamespace("generic.luck");
    private static final ResourceLocation KNOCKBACK_RES = ResourceLocation.withDefaultNamespace("generic.knockback_resistance");

    // ------------------------------------------------------------- Combate
    public static final SkillDef PUNHOS = register("combate/punhos_de_aco", SkillBranch.COMBATE, 1, 0, 0, "mao_em_l",
            List.of(), SkillEffect.add(ATTACK, 1));
    public static final SkillDef PELE_GROSSA = register("combate/pele_grossa", SkillBranch.COMBATE, 1, 1, -1, "caveira",
            List.of(PUNHOS), SkillEffect.add(ARMOR, 2));
    public static final SkillDef FOLEGO = register("combate/folego", SkillBranch.COMBATE, 1, 1, 1, "picanha",
            List.of(PUNHOS), SkillEffect.add(HEALTH, 4));
    public static final SkillDef RASTEIRA = register("combate/rasteira", SkillBranch.COMBATE, 2, 2, -1, "jumento",
            List.of(PELE_GROSSA), SkillEffect.finisher(Finishers.RASTEIRA_ID), SkillEffect.add(KNOCKBACK_RES, 0.1));
    public static final SkillDef RACA = register("combate/raca", SkillBranch.COMBATE, 2, 2, 1, "vira_lata",
            List.of(FOLEGO), SkillEffect.multiply(ATTACK_SPEED, 0.1));
    public static final SkillDef MITO = register("combate/mito", SkillBranch.COMBATE, 3, 3, 0, "mito",
            List.of(RASTEIRA, RACA), SkillEffect.add(ATTACK, 2), SkillEffect.add(HEALTH, 4));

    // ------------------------------------------------------- Sobrevivencia
    public static final SkillDef CANTIL = register("sobrevivencia/cantil", SkillBranch.SOBREVIVENCIA, 1, 0, 0, "chimarrao",
            List.of(), SkillEffect.needRate("hidratacao", 0.8));
    public static final SkillDef REDE = register("sobrevivencia/rede", SkillBranch.SOBREVIVENCIA, 1, 1, -1, "sertao",
            List.of(CANTIL), SkillEffect.needRate("energia", 0.8));
    public static final SkillDef BEXIGA = register("sobrevivencia/bexiga_de_aco", SkillBranch.SOBREVIVENCIA, 1, 1, 1, "comprimido",
            List.of(CANTIL), SkillEffect.needRate("bexiga", 0.7));
    public static final SkillDef VACINADO = register("sobrevivencia/vacinado", SkillBranch.SOBREVIVENCIA, 2, 2, -1, "vacina",
            List.of(REDE), SkillEffect.add(HEALTH, 4));
    public static final SkillDef SUS = register("sobrevivencia/intestino_preso", SkillBranch.SOBREVIVENCIA, 2, 2, 1, "cus",
            List.of(BEXIGA), SkillEffect.needRate("intestino", 0.7));
    public static final SkillDef SERTANEJO = register("sobrevivencia/sertanejo", SkillBranch.SOBREVIVENCIA, 3, 3, 0, "montanha",
            List.of(VACINADO, SUS), SkillEffect.needRate("hidratacao", 0.85), SkillEffect.needRate("energia", 0.85),
            SkillEffect.multiply(SPEED, 0.05));

    // ---------------------------------------------------------- Malandragem
    public static final SkillDef JEITINHO = register("malandragem/jeitinho", SkillBranch.MALANDRAGEM, 1, 0, 0, "pix",
            List.of(), SkillEffect.add(LUCK, 1));
    public static final SkillDef GINGA = register("malandragem/ginga", SkillBranch.MALANDRAGEM, 1, 1, -1, "estadio",
            List.of(JEITINHO), SkillEffect.multiply(SPEED, 0.05));
    public static final SkillDef LABIA = register("malandragem/labia", SkillBranch.MALANDRAGEM, 1, 1, 1, "painho",
            List.of(JEITINHO), SkillEffect.mood(800));
    public static final SkillDef ROLEZINHO = register("malandragem/rolezinho", SkillBranch.MALANDRAGEM, 2, 2, -1, "favela",
            List.of(GINGA), SkillEffect.multiply(SPEED, 0.05), SkillEffect.needRate("energia", 0.9));
    public static final SkillDef TIGRINHO = register("malandragem/tigrinho", SkillBranch.MALANDRAGEM, 2, 2, 1, "tigrinho",
            List.of(LABIA), SkillEffect.add(LUCK, 2));
    public static final SkillDef NIOBIO = register("malandragem/niobio", SkillBranch.MALANDRAGEM, 3, 3, 0, "niobio",
            List.of(ROLEZINHO, TIGRINHO), SkillEffect.add(LUCK, 1), SkillEffect.add(HEALTH, 2), SkillEffect.mood(500));

    private Skills() {}

    public static synchronized SkillDef register(SkillDef def) {
        if (REGISTRY.putIfAbsent(def.id(), def) != null) throw new IllegalStateException("Habilidade duplicada: " + def.id());
        return def;
    }

    private static SkillDef register(String path, SkillBranch branch, int cost, int x, int y, String icon,
                                     List<SkillDef> parents, SkillEffect... effects) {
        List<ResourceLocation> parentIds = new ArrayList<>();
        for (SkillDef parent : parents) parentIds.add(parent.id());
        return register(new SkillDef(TupiniCraft.id(path), branch, parentIds, cost, x, y,
                SkillDef.iconTexture(icon), List.of(effects)));
    }

    public static SkillDef get(ResourceLocation id) { return REGISTRY.get(id); }

    public static SkillDef get(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed == null ? null : REGISTRY.get(parsed);
    }

    public static Collection<SkillDef> all() { return Collections.unmodifiableCollection(REGISTRY.values()); }

    public static List<SkillDef> of(SkillBranch branch) {
        return REGISTRY.values().stream().filter(def -> def.branch() == branch).toList();
    }

    /** Motivo pelo qual a compra nao pode acontecer, ou null se pode. Puro: usado no servidor e na tela. */
    public static String purchaseBlocker(SkillDef def, Set<String> owned, int points, boolean born) {
        if (!born) return "nascimento";
        if (owned.contains(def.id().toString())) return "comprada";
        for (ResourceLocation parent : def.parents()) {
            if (!owned.contains(parent.toString())) return "requisito";
        }
        if (points < def.cost()) return "pontos";
        return null;
    }

    /** Problemas do catalogo (pai inexistente, de outra aba, posicao repetida, aba sem raiz). Vazio = ok. */
    public static List<String> validate() {
        List<String> problems = new ArrayList<>();
        java.util.Set<String> positions = new java.util.HashSet<>();
        for (SkillDef def : REGISTRY.values()) {
            for (ResourceLocation parent : def.parents()) {
                SkillDef p = REGISTRY.get(parent);
                if (p == null) problems.add(def.id() + ": pai inexistente " + parent);
                else if (p.branch() != def.branch()) problems.add(def.id() + ": pai de outra aba " + parent);
                else if (p.x() >= def.x()) problems.add(def.id() + ": pai deve ficar a esquerda");
            }
            if (!positions.add(def.branch() + "@" + def.x() + "," + def.y())) problems.add(def.id() + ": posicao repetida");
        }
        for (SkillBranch branch : SkillBranch.values()) {
            if (of(branch).stream().noneMatch(SkillDef::root)) problems.add(branch.id() + ": aba sem raiz");
        }
        return problems;
    }

    public static void bootstrap() {}
}
