package com.tupinicraft.skilltree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tupinicraft.origin.Macroregion;
import com.tupinicraft.politics.PoliticalOffice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Lê os JSON das árvores e das skills (regras puras, testáveis). Exemplos:
 * <pre>
 * arvores/regional_nordeste.json:
 *   { "nome": "Nordeste", "tipo": "regional", "regiao": "nordeste", "icone": "minecraft:cactus",
 *     "fundo": "minecraft:textures/block/sand.png", "ordem": 21 }
 * skills/regional_nordeste_sertao.json:
 *   { "arvore": "tupinicraft:regional_nordeste", "nome": "Resistência do Sertão", "descricao": "+4 de vida",
 *     "icone": "minecraft:cactus", "moldura": "task", "pai": null, "custo": 800, "xp_min": 0,
 *     "efeitos": [ { "atributo": "minecraft:generic.max_health", "operacao": "add_value", "valor": 4 } ] }
 * </pre>
 * Skills de cargo usam {@code "pontos"} (pontos daquele cargo) no lugar de {@code "custo"}.
 */
public final class SkillDataParser {
    private SkillDataParser() {
    }

    public static SkillTreeDef parseTree(String id, JsonObject json) {
        SkillTreeKind kind = SkillTreeKind.byId(required(json, "tipo", id));
        Optional<Macroregion> region = string(json, "regiao").map(value -> region(id, value));
        Optional<String> party = string(json, "partido").map(value -> {
            require(value.matches("[a-z0-9_]{2,32}"), id, "id de partido inválido \"" + value + "\"");
            return value;
        });
        Optional<PoliticalOffice> office = string(json, "cargo").map(value -> PoliticalOffice.byId(value)
                .orElseThrow(() -> new IllegalArgumentException(id + ": cargo desconhecido \"" + value + "\"")));
        return new SkillTreeDef(id, kind, region, party, office, required(json, "nome", id),
                required(json, "icone", id), required(json, "fundo", id), json.has("ordem") ? json.get("ordem").getAsInt() : 0);
    }

    /** A árvore informa o tipo e o requisito; a skill informa custo, pai, aparência e efeitos. */
    public static SkillDef parseSkill(String id, JsonObject json, SkillTreeDef tree) {
        long money = json.has("custo") ? json.get("custo").getAsLong() : 0;
        int points = json.has("pontos") ? json.get("pontos").getAsInt() : 0;
        int minXp = json.has("xp_min") ? json.get("xp_min").getAsInt() : 0;
        SkillRule rule = new SkillRule(id, tree.kind(), tree.region(), tree.party(), tree.office(), money, points, minXp);
        List<SkillEffectDef> effects = new ArrayList<>();
        if (json.has("efeitos")) {
            for (JsonElement element : json.getAsJsonArray("efeitos")) {
                JsonObject effect = element.getAsJsonObject();
                try {
                    effects.add(new SkillEffectDef(required(effect, "atributo", id), required(effect, "operacao", id),
                            effect.get("valor").getAsDouble()));
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException(id + ": efeito inválido: " + e.getMessage());
                }
            }
        }
        return new SkillDef(id, tree.id(), string(json, "pai"), required(json, "nome", id),
                string(json, "descricao").orElse(""), required(json, "icone", id),
                string(json, "moldura").orElse("task"), effects, rule);
    }

    /** Lê o campo "arvore" de uma skill (para achar a árvore antes de {@link #parseSkill}). */
    public static String treeOf(String id, JsonObject json) {
        return required(json, "arvore", id);
    }

    private static Macroregion region(String id, String value) {
        try {
            return Macroregion.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(id + ": região desconhecida \"" + value
                    + "\" (norte, nordeste, centro_oeste, sudeste, sul)");
        }
    }

    static String required(JsonObject json, String key, String id) {
        return string(json, key).orElseThrow(() -> new IllegalArgumentException(id + ": campo \"" + key + "\" obrigatório"));
    }

    static Optional<String> string(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? Optional.of(json.get(key).getAsString()) : Optional.empty();
    }

    static void requireId(String value, String what) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("ID inválido para " + what + ": " + value);
        }
    }

    static void require(boolean condition, String id, String message) {
        if (!condition) {
            throw new IllegalArgumentException(id + ": " + message);
        }
    }
}
