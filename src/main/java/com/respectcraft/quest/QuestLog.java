package com.respectcraft.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Todas as missoes de um jogador (Etapa 4.14.2): attachment {@code respectcraft:quests}, salvo com o jogador e
 * copiado na morte. Mapa id-da-missao -> {@link QuestProgress}. Ids sem definicao carregada (missao removida,
 * datapack desligado, id errado) <b>ficam</b> no registro e voltam a valer se a definicao voltar: nada e apagado em
 * silencio. {@code version} = versao do formato salvo ({@link QuestRules#SAVE_VERSION}).
 */
public record QuestLog(int version, Map<String, QuestProgress> quests) {
    public static final QuestLog EMPTY = new QuestLog(QuestRules.SAVE_VERSION, Map.of());

    public QuestLog {
        quests = Map.copyOf(quests);
    }

    public static final Codec<QuestLog> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("version", QuestRules.SAVE_VERSION).forGetter(QuestLog::version),
            Codec.unboundedMap(Codec.STRING, QuestProgress.CODEC).optionalFieldOf("quests", Map.of()).forGetter(QuestLog::quests)
    ).apply(i, QuestLog::new));

    public QuestProgress get(ResourceLocation id) {
        QuestProgress p = quests.get(id.toString());
        return p == null ? QuestProgress.NEW : p;
    }

    public boolean has(ResourceLocation id) { return quests.containsKey(id.toString()); }

    public QuestState state(ResourceLocation id) { return get(id).state(); }

    public QuestLog with(ResourceLocation id, QuestProgress p) {
        Map<String, QuestProgress> m = new LinkedHashMap<>(quests);
        m.put(id.toString(), p);
        return new QuestLog(QuestRules.SAVE_VERSION, m);
    }

    public QuestLog without(ResourceLocation id) {
        Map<String, QuestProgress> m = new LinkedHashMap<>(quests);
        m.remove(id.toString());
        return new QuestLog(QuestRules.SAVE_VERSION, m);
    }

    public int trackedCount() {
        int n = 0;
        for (QuestProgress p : quests.values()) if (p.tracked()) n++;
        return n;
    }
}
