package com.respectcraft.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Uma recompensa de missao em JSON (Etapa 4.14.2). Tipos: {@code experience} (amount = pontos de XP),
 * {@code item} (id + amount), {@code skill} (id de uma habilidade existente das Skill Trees, entregue sem custo),
 * {@code skill_points} (amount) e {@code quest} (id de outra missao, revelada). Nada executa comandos.
 * Concedida so pelo {@code QuestService}, depois de a missao ser marcada como resgatada.
 */
public record QuestReward(String type, int amount, Optional<ResourceLocation> id) {
    public static final List<String> TYPES = List.of("experience", "item", "skill", "skill_points", "quest");

    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.<QuestReward>create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(QuestReward::type),
            Codec.intRange(0, 100_000).optionalFieldOf("amount", 1).forGetter(QuestReward::amount),
            ResourceLocation.CODEC.optionalFieldOf("id").forGetter(QuestReward::id)
    ).apply(i, QuestReward::new));

    /** Erro estrutural (sem consultar registros); null = valido. */
    public String validate() {
        if (!TYPES.contains(type)) return "unknown reward type " + type;
        return switch (type) {
            case "experience" -> amount < 1 ? "experience amount must be >= 1" : null;
            case "skill_points" -> amount < 1 || amount > 100 ? "skill_points amount must be 1..100" : null;
            case "item" -> id.isEmpty() ? "item reward needs id" : amount < 1 || amount > QuestRules.MAX_ITEM_REWARD ? "item amount must be 1.." + QuestRules.MAX_ITEM_REWARD : null;
            case "skill", "quest" -> id.isEmpty() ? type + " reward needs id" : null;
            default -> null;
        };
    }
}
