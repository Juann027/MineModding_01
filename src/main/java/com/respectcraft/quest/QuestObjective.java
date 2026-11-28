package com.respectcraft.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * Um objetivo de missao em JSON (Etapa 4.14.2): {@code id} (chave do progresso, estavel), {@code type},
 * {@code count} e os parametros do tipo. O comportamento vem do {@link Type} registrado para o {@code type}, entao
 * objetivos novos sao dados, nunca subclasses de missao.
 *
 * <p>Tipos embutidos: {@code kill} (target = entidade/tag), {@code finisher} (target = vitima, {@code finisher},
 * {@code victim_kind}; so SUCCESS), {@code advancement} (target = conquista; conta a que ja foi obtida),
 * {@code enter_dimension} (target = dimensao; conta se ja estiver nela ao aceitar), {@code complete_quest}
 * (target = missao; conta a que ja foi concluida) e {@code boss_defeat} (target = chefe).
 */
public record QuestObjective(String id, String type, int count, Optional<String> target, Optional<ResourceLocation> finisher,
                             Optional<String> victimKind, Optional<Component> description) {

    /** Comportamento de um tipo de objetivo. */
    public interface Type {
        QuestTrigger trigger();

        /** null = valido; senao a mensagem do erro de carga. */
        String validate(QuestObjective o);

        boolean matches(QuestObjective o, QuestEvent e);

        /** Quanto ja esta cumprido por estado persistente (ao aceitar/no login). 0 = nada. */
        default int retroactive(QuestObjective o, QuestContext ctx) { return 0; }
    }

    public static final Map<String, Type> TYPES = new LinkedHashMap<>();

    public static void register(String id, Type type) {
        synchronized (TYPES) { TYPES.put(id, type); }
    }

    public Type behavior() {
        synchronized (TYPES) { return TYPES.get(type); }
    }

    public QuestMatcher matcher() {
        return target.map(t -> QuestMatcher.parse(t).result().orElse(null)).orElse(QuestMatcher.ANY);
    }

    public ResourceLocation targetId() { return target.map(ResourceLocation::tryParse).orElse(null); }

    public static final Codec<QuestObjective> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(QuestObjective::id),
            Codec.STRING.fieldOf("type").forGetter(QuestObjective::type),
            Codec.intRange(1, QuestRules.MAX_COUNT).optionalFieldOf("count", 1).forGetter(QuestObjective::count),
            Codec.STRING.optionalFieldOf("target").forGetter(QuestObjective::target),
            ResourceLocation.CODEC.optionalFieldOf("finisher").forGetter(QuestObjective::finisher),
            QuestRules.VICTIM_KIND.optionalFieldOf("victim_kind").forGetter(QuestObjective::victimKind),
            ComponentSerialization.CODEC.optionalFieldOf("description").forGetter(QuestObjective::description)
    ).apply(i, QuestObjective::new));

    static {
        register("kill", new Type() {
            public QuestTrigger trigger() { return QuestTrigger.KILL; }
            public String validate(QuestObjective o) { return QuestRules.validMatcher(o.target(), true); }
            public boolean matches(QuestObjective o, QuestEvent e) { return o.matcher().matches(e.subject(), e.tags()); }
        });
        register("finisher", new Type() {
            public QuestTrigger trigger() { return QuestTrigger.FINISHER; }
            public String validate(QuestObjective o) { return QuestRules.validMatcher(o.target(), false); }
            public boolean matches(QuestObjective o, QuestEvent e) {
                return QuestRules.finisherMatches(o.finisher(), o.matcher(), o.victimKind(), e);
            }
        });
        register("boss_defeat", new Type() {
            public QuestTrigger trigger() { return QuestTrigger.BOSS_DEFEAT; }
            public String validate(QuestObjective o) { return QuestRules.validMatcher(o.target(), false); }
            public boolean matches(QuestObjective o, QuestEvent e) { return o.matcher().matches(e.subject(), e.tags()); }
        });
        register("advancement", new Type() {
            public QuestTrigger trigger() { return QuestTrigger.ADVANCEMENT; }
            public String validate(QuestObjective o) { return QuestRules.validId(o, "advancement"); }
            public boolean matches(QuestObjective o, QuestEvent e) { return e.subject() != null && e.subject().equals(o.targetId()); }
            public int retroactive(QuestObjective o, QuestContext ctx) { return ctx.hasAdvancement(o.targetId()) ? o.count() : 0; }
        });
        register("enter_dimension", new Type() {
            public QuestTrigger trigger() { return QuestTrigger.DIMENSION; }
            public String validate(QuestObjective o) { return QuestRules.validId(o, "dimension"); }
            public boolean matches(QuestObjective o, QuestEvent e) { return e.subject() != null && e.subject().equals(o.targetId()); }
            public int retroactive(QuestObjective o, QuestContext ctx) { return o.targetId().equals(ctx.dimension()) ? 1 : 0; }
        });
        register("complete_quest", new Type() {
            public QuestTrigger trigger() { return QuestTrigger.QUEST; }
            public String validate(QuestObjective o) { return QuestRules.validId(o, "quest"); }
            public boolean matches(QuestObjective o, QuestEvent e) {
                return e.subject() != null && e.subject().equals(o.targetId()) && QuestState.COMPLETED.id().equals(e.detail());
            }
            public int retroactive(QuestObjective o, QuestContext ctx) { return ctx.questState(o.targetId()).succeeded() ? 1 : 0; }
        });
    }
}
