package com.respectcraft.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.respectcraft.notice.NoticeStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * Definicao de uma missao (Etapa 4.14.2), carregada de {@code data/<ns>/quest/<caminho>.json} pelos datapacks do
 * servidor ({@link QuestRegistry}). O id e o caminho do arquivo ({@code respectcraft:journey/a_journey_begins}) e e
 * o id persistente do progresso. Formato em {@code docs/QUESTS.md}; {@code format} e a versao do formato
 * ({@link QuestRules#FORMAT}).
 *
 * <p>Separacao: {@code discover} (condicao de descoberta), {@code requires} (condicao de aceitacao),
 * {@code objectives}, {@code rewards} e {@link Flags} (tipo da missao). O progresso de cada jogador fica em
 * {@link QuestProgress}; nada aqui e estado.
 */
public record QuestDef(ResourceLocation id, int format, String category, int sort, Component title, Component description,
                       Optional<Component> hint, Optional<QuestCondition> discover, Optional<QuestCondition> requires,
                       List<Component> requirementsText, Flags flags, List<QuestObjective> objectives,
                       List<QuestReward> rewards, Notices notices) {

    /** Tipo da missao: secreta, unica/repetivel, aceitacao e resgate automaticos ou manuais, tempo, falha. */
    public record Flags(boolean secret, boolean autoAccept, boolean autoClaim, boolean repeatable, long cooldownTicks,
                        long timeLimitTicks, boolean failOnDeath, boolean abandonable, boolean retry) {
        public static final Flags DEFAULT = new Flags(false, false, false, false, 0L, 0L, false, true, true);

        public static final MapCodec<Flags> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("secret", false).forGetter(Flags::secret),
                QuestRules.MODE.optionalFieldOf("accept", false).forGetter(Flags::autoAccept),
                QuestRules.MODE.optionalFieldOf("claim", false).forGetter(Flags::autoClaim),
                Codec.BOOL.optionalFieldOf("repeatable", false).forGetter(Flags::repeatable),
                Codec.LONG.optionalFieldOf("cooldown_ticks", 0L).forGetter(Flags::cooldownTicks),
                Codec.LONG.optionalFieldOf("time_limit_ticks", 0L).forGetter(Flags::timeLimitTicks),
                Codec.BOOL.optionalFieldOf("fail_on_death", false).forGetter(Flags::failOnDeath),
                Codec.BOOL.optionalFieldOf("abandonable", true).forGetter(Flags::abandonable),
                Codec.BOOL.optionalFieldOf("retry", true).forGetter(Flags::retry)
        ).apply(i, Flags::new));
    }

    /** Estilos das janelas de cada momento (padroes quando ausentes). */
    public record Notices(Optional<NoticeStyle> discover, Optional<NoticeStyle> complete, Optional<NoticeStyle> fail,
                          Optional<Component> discoverTitle) {
        public static final Notices DEFAULT = new Notices(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        public static final Codec<Notices> CODEC = RecordCodecBuilder.create(i -> i.group(
                QuestRules.STYLE.optionalFieldOf("discover").forGetter(Notices::discover),
                QuestRules.STYLE.optionalFieldOf("complete").forGetter(Notices::complete),
                QuestRules.STYLE.optionalFieldOf("fail").forGetter(Notices::fail),
                ComponentSerialization.CODEC.optionalFieldOf("discover_title").forGetter(Notices::discoverTitle)
        ).apply(i, Notices::new));
    }

    /** Codec do arquivo (sem o id, que vem do caminho). */
    public static final Codec<QuestDef> FILE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format", QuestRules.FORMAT).forGetter(QuestDef::format),
            Codec.STRING.optionalFieldOf("category", "general").forGetter(QuestDef::category),
            Codec.INT.optionalFieldOf("sort", 0).forGetter(QuestDef::sort),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestDef::title),
            ComponentSerialization.CODEC.fieldOf("description").forGetter(QuestDef::description),
            ComponentSerialization.CODEC.optionalFieldOf("hint").forGetter(QuestDef::hint),
            QuestCondition.CODEC.optionalFieldOf("discover").forGetter(QuestDef::discover),
            QuestCondition.CODEC.optionalFieldOf("requires").forGetter(QuestDef::requires),
            ComponentSerialization.CODEC.listOf().optionalFieldOf("requirements_text", List.of()).forGetter(QuestDef::requirementsText),
            Flags.MAP_CODEC.forGetter(QuestDef::flags),
            QuestObjective.CODEC.listOf().fieldOf("objectives").forGetter(QuestDef::objectives),
            QuestReward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(QuestDef::rewards),
            Notices.CODEC.optionalFieldOf("notices", Notices.DEFAULT).forGetter(QuestDef::notices)
    ).apply(i, (format, category, sort, title, description, hint, discover, requires, reqText, flags, objectives, rewards, notices) ->
            new QuestDef(null, format, category, sort, title, description, hint, discover, requires, reqText, flags, objectives, rewards, notices)));

    public QuestDef withId(ResourceLocation newId) {
        return new QuestDef(newId, format, category, sort, title, description, hint, discover, requires, requirementsText, flags,
                objectives, rewards, notices);
    }

    public boolean secret() { return flags.secret(); }

    /** Todos os objetivos cumpridos com este progresso? */
    public boolean complete(java.util.Map<String, Integer> progress) {
        for (QuestObjective o : objectives) if (progress.getOrDefault(o.id(), 0) < o.count()) return false;
        return true;
    }

    /**
     * Assinatura dos objetivos (ids, tipos, alvos, quantidades): muda quando a definicao muda os objetivos. O
     * progresso guarda a da aceitacao para detectar definicoes alteradas sem apagar nada.
     */
    public int signature() {
        int h = 17;
        for (QuestObjective o : objectives) h = h * 31 + (o.id() + "|" + o.type() + "|" + o.target().orElse("") + "|" + o.count()).hashCode();
        return h;
    }

    /** Erros estruturais (limites, ids repetidos, tipos desconhecidos). Lista vazia = valida. */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (format < 1 || format > QuestRules.FORMAT) errors.add("unsupported format " + format + " (this version reads 1.." + QuestRules.FORMAT + ")");
        if (objectives.isEmpty()) errors.add("needs at least one objective");
        if (objectives.size() > QuestRules.MAX_OBJECTIVES) errors.add("too many objectives (max " + QuestRules.MAX_OBJECTIVES + ")");
        if (rewards.size() > QuestRules.MAX_REWARDS) errors.add("too many rewards (max " + QuestRules.MAX_REWARDS + ")");
        if (category.length() > 32) errors.add("category longer than 32 characters");
        Set<String> ids = new HashSet<>();
        for (QuestObjective o : objectives) {
            if (!QuestRules.validKey(o.id())) errors.add("objective id '" + o.id() + "' must be 1-32 chars of a-z 0-9 _ -");
            if (!ids.add(o.id())) errors.add("duplicate objective id " + o.id());
            QuestObjective.Type t = o.behavior();
            if (t == null) errors.add("objective " + o.id() + ": unknown type " + o.type());
            else {
                String e = t.validate(o);
                if (e != null) errors.add("objective " + o.id() + ": " + e);
            }
        }
        for (QuestReward r : rewards) {
            String e = r.validate();
            if (e != null) errors.add("reward: " + e);
        }
        discover.ifPresent(c -> QuestRules.checkComplexity("discover", c, errors));
        requires.ifPresent(c -> QuestRules.checkComplexity("requires", c, errors));
        if (flags.cooldownTicks() < 0 || flags.timeLimitTicks() < 0) errors.add("negative cooldown/time limit");
        if (requires.isPresent() && !requires.get().persistent()) errors.add("requires must only use persistent (state) conditions");
        return errors;
    }
}
