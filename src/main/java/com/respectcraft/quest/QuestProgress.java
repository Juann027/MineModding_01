package com.respectcraft.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;

/**
 * Progresso de um jogador em uma missao (Etapa 4.14.2), gravado no attachment {@code respectcraft:quests}. Nomes de
 * campos estaveis. Tempos em ticks do relogio do Overworld; {@code signature} e a dos objetivos quando a missao foi
 * aceita ({@link QuestDef#signature()}), para detectar definicoes alteradas sem apagar progresso.
 */
public record QuestProgress(QuestState state, Map<String, Integer> progress, long discoveredAt, long acceptedAt,
                            long finishedAt, long cooldownUntil, int completions, boolean tracked, int signature) {
    public static final QuestProgress NEW = new QuestProgress(QuestState.HIDDEN, Map.of(), 0L, 0L, 0L, 0L, 0, false, 0);

    public QuestProgress {
        progress = Map.copyOf(progress);
    }

    public static final Codec<QuestProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            QuestState.CODEC.fieldOf("state").forGetter(QuestProgress::state),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("progress", Map.of()).forGetter(QuestProgress::progress),
            Codec.LONG.optionalFieldOf("discovered_at", 0L).forGetter(QuestProgress::discoveredAt),
            Codec.LONG.optionalFieldOf("accepted_at", 0L).forGetter(QuestProgress::acceptedAt),
            Codec.LONG.optionalFieldOf("finished_at", 0L).forGetter(QuestProgress::finishedAt),
            Codec.LONG.optionalFieldOf("cooldown_until", 0L).forGetter(QuestProgress::cooldownUntil),
            Codec.INT.optionalFieldOf("completions", 0).forGetter(QuestProgress::completions),
            Codec.BOOL.optionalFieldOf("tracked", false).forGetter(QuestProgress::tracked),
            Codec.INT.optionalFieldOf("signature", 0).forGetter(QuestProgress::signature)
    ).apply(i, QuestProgress::new));

    public QuestProgress withState(QuestState s) {
        return new QuestProgress(s, progress, discoveredAt, acceptedAt, finishedAt, cooldownUntil, completions, tracked, signature);
    }

    public QuestProgress withProgress(Map<String, Integer> p) {
        return new QuestProgress(state, p, discoveredAt, acceptedAt, finishedAt, cooldownUntil, completions, tracked, signature);
    }

    public QuestProgress withTracked(boolean t) {
        return new QuestProgress(state, progress, discoveredAt, acceptedAt, finishedAt, cooldownUntil, completions, t, signature);
    }

    public QuestProgress discovered(long now) {
        return new QuestProgress(QuestState.AVAILABLE, progress, now, acceptedAt, finishedAt, cooldownUntil, completions, tracked, signature);
    }

    public QuestProgress accepted(Map<String, Integer> start, long now, int sig) {
        return new QuestProgress(QuestState.ACTIVE, start, discoveredAt == 0L ? now : discoveredAt, now, 0L, cooldownUntil, completions,
                tracked, sig);
    }

    public QuestProgress completed(long now) {
        return new QuestProgress(QuestState.COMPLETED, progress, discoveredAt, acceptedAt, now, cooldownUntil, completions + 1, tracked, signature);
    }

    public QuestProgress ended(QuestState s, long now) {
        return new QuestProgress(s, progress, discoveredAt, acceptedAt, now, cooldownUntil, completions, false, signature);
    }

    public QuestProgress claimed(long cooldownUntil) {
        return new QuestProgress(QuestState.CLAIMED, progress, discoveredAt, acceptedAt, finishedAt, cooldownUntil, completions, false, signature);
    }

    public int value(String objective) { return progress.getOrDefault(objective, 0); }
}
