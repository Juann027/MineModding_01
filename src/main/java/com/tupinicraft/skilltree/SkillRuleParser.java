package com.tupinicraft.skilltree;

import com.google.gson.JsonObject;
import com.tupinicraft.origin.Macroregion;
import com.tupinicraft.politics.PoliticalOffice;

import java.util.Locale;
import java.util.Optional;

/**
 * Lê o bloco {@code "tupinicraft"} de um arquivo de skill do SkillLib (o SkillLib ignora campos que não conhece).
 * Exemplo:
 * <pre>
 * "tupinicraft": { "arvore": "regional", "regiao": "nordeste", "custo": 800, "xp_min": 0 }
 * "tupinicraft": { "arvore": "cargo", "cargo": "vereador", "pontos": 1 }
 * </pre>
 */
public final class SkillRuleParser {
    public static final String BLOCK = "tupinicraft";

    private SkillRuleParser() {
    }

    /** Vazio se o JSON não tem o bloco do TupiniCraft (skill de outro mod/datapack). */
    public static Optional<SkillRule> parse(String skillId, JsonObject skillJson) {
        if (!skillJson.has(BLOCK) || !skillJson.get(BLOCK).isJsonObject()) {
            return Optional.empty();
        }
        JsonObject block = skillJson.getAsJsonObject(BLOCK);
        SkillTreeKind kind = SkillTreeKind.byId(string(block, "arvore").orElseThrow(
                () -> new IllegalArgumentException(skillId + ": campo \"arvore\" obrigatório")));
        Optional<Macroregion> region = string(block, "regiao").map(value -> {
            try {
                return Macroregion.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(skillId + ": região desconhecida \"" + value
                        + "\" (norte, nordeste, centro_oeste, sudeste, sul)");
            }
        });
        Optional<String> party = string(block, "partido").map(value -> {
            if (!value.matches("[a-z0-9_]{2,32}")) {
                throw new IllegalArgumentException(skillId + ": id de partido inválido \"" + value + "\"");
            }
            return value;
        });
        Optional<PoliticalOffice> office = string(block, "cargo").map(value -> PoliticalOffice.byId(value)
                .orElseThrow(() -> new IllegalArgumentException(skillId + ": cargo desconhecido \"" + value + "\"")));
        long money = block.has("custo") ? block.get("custo").getAsLong() : 0;
        int points = block.has("pontos") ? block.get("pontos").getAsInt() : 0;
        int minXp = block.has("xp_min") ? block.get("xp_min").getAsInt() : 0;
        return Optional.of(new SkillRule(skillId, kind, region, party, office, money, points, minXp));
    }

    private static Optional<String> string(JsonObject block, String key) {
        return block.has(key) && !block.get(key).isJsonNull() ? Optional.of(block.get(key).getAsString()) : Optional.empty();
    }
}
