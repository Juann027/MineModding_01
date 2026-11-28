package com.respectcraft.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * Condicao de descoberta/aceitacao de uma missao (Etapa 4.14.2), definida em JSON pelo campo {@code type}.
 *
 * <p>Duas familias, que nunca se confundem:
 * <ul>
 *   <li><b>estado persistente</b> ({@link #persistent()} = true): o que o jogador <i>tem</i> agora (conquista ja
 *   obtida, outra missao concluida, dimensao atual). Vale em qualquer avaliacao, inclusive no login, entao uma
 *   conquista irrepetivel obtida antes nunca precisa ser repetida;</li>
 *   <li><b>acontecimento pontual</b>: o que <i>acabou</i> de acontecer (conquista obtida agora, morte, finalizacao,
 *   chegada). So e verdade na avaliacao disparada por aquele {@link QuestEvent}.</li>
 * </ul>
 * Composicao com {@code all}/{@code any}/{@code not}, limitada por {@link QuestRules#MAX_CONDITION_DEPTH} e
 * {@link QuestRules#MAX_CONDITION_NODES}. Tipos novos: {@link #register}.
 */
public interface QuestCondition {
    String type();

    /** {@code event} nulo = avaliacao de estado (login, reload, acao). */
    boolean test(QuestContext ctx, QuestEvent event);

    /** Gatilhos que podem mudar o resultado (para o indice do catalogo). */
    void triggers(Set<QuestTrigger> out);

    boolean persistent();

    default List<QuestCondition> children() { return List.of(); }

    /** Texto curto para o diagnostico ({@code /respectcraft quest diagnose}). */
    String describe();

    // ------------------------------------------------------------------------------------------------ registro

    Map<String, Function<Codec<QuestCondition>, MapCodec<? extends QuestCondition>>> TYPES = new LinkedHashMap<>();

    /** Registra um tipo de condicao (feito na carga da classe; mods/sistemas futuros chamam no setup comum). */
    static void register(String type, Function<Codec<QuestCondition>, MapCodec<? extends QuestCondition>> codec) {
        synchronized (TYPES) {
            Builtin.ensure();
            TYPES.put(type, codec);
        }
    }

    Codec<QuestCondition> CODEC = Codec.recursive("quest_condition", self -> Codec.STRING.partialDispatch("type",
            c -> DataResult.success(c.type()),
            t -> {
                Function<Codec<QuestCondition>, MapCodec<? extends QuestCondition>> f;
                synchronized (TYPES) {
                    Builtin.ensure();
                    f = TYPES.get(t);
                }
                return f == null ? DataResult.error(() -> "Unknown quest condition type: " + t) : DataResult.success(f.apply(self));
            }));

    /** Profundidade e quantidade de nos (limites de complexidade). */
    static int depth(QuestCondition c) {
        int d = 0;
        for (QuestCondition ch : c.children()) d = Math.max(d, depth(ch));
        return d + 1;
    }

    static int nodes(QuestCondition c) {
        int n = 1;
        for (QuestCondition ch : c.children()) n += nodes(ch);
        return n;
    }

    /** Ids de missao citados (para checar cadeias depois da carga). */
    static void quests(QuestCondition c, List<ResourceLocation> out) {
        if (c instanceof QuestIs q) out.add(q.quest());
        for (QuestCondition ch : c.children()) quests(ch, out);
    }

    // ------------------------------------------------------------------------------------------------ composicao

    record All(List<QuestCondition> conditions) implements QuestCondition {
        public String type() { return "all"; }
        public boolean test(QuestContext ctx, QuestEvent e) {
            for (QuestCondition c : conditions) if (!c.test(ctx, e)) return false;
            return true;
        }
        public void triggers(Set<QuestTrigger> out) { for (QuestCondition c : conditions) c.triggers(out); }
        public boolean persistent() {
            for (QuestCondition c : conditions) if (!c.persistent()) return false;
            return true;
        }
        public List<QuestCondition> children() { return conditions; }
        public String describe() { return "all of"; }
    }

    record Any(List<QuestCondition> conditions) implements QuestCondition {
        public String type() { return "any"; }
        public boolean test(QuestContext ctx, QuestEvent e) {
            for (QuestCondition c : conditions) if (c.test(ctx, e)) return true;
            return false;
        }
        public void triggers(Set<QuestTrigger> out) { for (QuestCondition c : conditions) c.triggers(out); }
        public boolean persistent() {
            for (QuestCondition c : conditions) if (!c.persistent()) return false;
            return true;
        }
        public List<QuestCondition> children() { return conditions; }
        public String describe() { return "any of"; }
    }

    record Not(QuestCondition condition) implements QuestCondition {
        public String type() { return "not"; }
        public boolean test(QuestContext ctx, QuestEvent e) { return !condition.test(ctx, e); }
        public void triggers(Set<QuestTrigger> out) { condition.triggers(out); }
        public boolean persistent() { return condition.persistent(); }
        public List<QuestCondition> children() { return List.of(condition); }
        public String describe() { return "not"; }
    }

    // ------------------------------------------------------------------------------------------------ estado

    /** Ja tem a conquista (obtida agora ou antes). */
    record HasAdvancement(ResourceLocation advancement) implements QuestCondition {
        public String type() { return "advancement"; }
        public boolean test(QuestContext ctx, QuestEvent e) {
            if (e != null && e.trigger() == QuestTrigger.ADVANCEMENT && advancement.equals(e.subject())) return true;
            return ctx.hasAdvancement(advancement);
        }
        public void triggers(Set<QuestTrigger> out) { out.add(QuestTrigger.ADVANCEMENT); }
        public boolean persistent() { return true; }
        public String describe() { return "has advancement " + advancement; }
    }

    /** Outra missao esta num destes estados (padrao: concluida ou resgatada). */
    record QuestIs(ResourceLocation quest, List<QuestState> states) implements QuestCondition {
        public String type() { return "quest"; }
        public boolean test(QuestContext ctx, QuestEvent e) { return states.contains(ctx.questState(quest)); }
        public void triggers(Set<QuestTrigger> out) { out.add(QuestTrigger.QUEST); }
        public boolean persistent() { return true; }
        public String describe() { return "quest " + quest + " in " + states; }
    }

    /** Esta agora na dimensao. */
    record InDimension(ResourceLocation dimension) implements QuestCondition {
        public String type() { return "in_dimension"; }
        public boolean test(QuestContext ctx, QuestEvent e) {
            if (e != null && e.trigger() == QuestTrigger.DIMENSION) return dimension.equals(e.subject());
            return dimension.equals(ctx.dimension());
        }
        public void triggers(Set<QuestTrigger> out) { out.add(QuestTrigger.DIMENSION); }
        public boolean persistent() { return true; }
        public String describe() { return "in dimension " + dimension; }
    }

    // ------------------------------------------------------------------------------------------------ acontecimentos

    /** Obteve a conquista <i>agora</i> (so na avaliacao do evento). */
    record AdvancementEarned(ResourceLocation advancement) implements QuestCondition {
        public String type() { return "advancement_earned"; }
        public boolean test(QuestContext ctx, QuestEvent e) {
            return e != null && e.trigger() == QuestTrigger.ADVANCEMENT && advancement.equals(e.subject());
        }
        public void triggers(Set<QuestTrigger> out) { out.add(QuestTrigger.ADVANCEMENT); }
        public boolean persistent() { return false; }
        public String describe() { return "just earned advancement " + advancement; }
    }

    /** Acabou de chegar a dimensao. */
    record EnteredDimension(ResourceLocation dimension) implements QuestCondition {
        public String type() { return "entered_dimension"; }
        public boolean test(QuestContext ctx, QuestEvent e) {
            return e != null && e.trigger() == QuestTrigger.DIMENSION && dimension.equals(e.subject());
        }
        public void triggers(Set<QuestTrigger> out) { out.add(QuestTrigger.DIMENSION); }
        public boolean persistent() { return false; }
        public String describe() { return "just entered " + dimension; }
    }

    /** Acabou de matar uma entidade (qualquer metodo, inclusive a execucao de uma finalizacao). */
    record Killed(QuestMatcher entity) implements QuestCondition {
        public String type() { return "kill"; }
        public boolean test(QuestContext ctx, QuestEvent e) {
            return e != null && e.trigger() == QuestTrigger.KILL && entity.matches(e.subject(), e.tags());
        }
        public void triggers(Set<QuestTrigger> out) { out.add(QuestTrigger.KILL); }
        public boolean persistent() { return false; }
        public String describe() { return "just killed " + entity.raw(); }
    }

    /** Acabou de concluir uma finalizacao com SUCCESS (morte confirmada). */
    record Finished(Optional<ResourceLocation> finisher, QuestMatcher victim, Optional<String> victimKind) implements QuestCondition {
        public String type() { return "finisher"; }
        public boolean test(QuestContext ctx, QuestEvent e) {
            return e != null && e.trigger() == QuestTrigger.FINISHER && QuestRules.finisherMatches(finisher, victim, victimKind, e);
        }
        public void triggers(Set<QuestTrigger> out) { out.add(QuestTrigger.FINISHER); }
        public boolean persistent() { return false; }
        public String describe() {
            return "just executed " + victim.raw() + finisher.map(f -> " with " + f).orElse("") + victimKind.map(k -> " (" + k + ")").orElse("");
        }
    }

    /** Acabou de derrotar um chefe com credito. */
    record BossDefeated(QuestMatcher entity, boolean finisherOnly) implements QuestCondition {
        public String type() { return "boss_defeat"; }
        public boolean test(QuestContext ctx, QuestEvent e) {
            return e != null && e.trigger() == QuestTrigger.BOSS_DEFEAT && entity.matches(e.subject(), e.tags())
                    && (!finisherOnly || "finisher".equals(e.detail()));
        }
        public void triggers(Set<QuestTrigger> out) { out.add(QuestTrigger.BOSS_DEFEAT); }
        public boolean persistent() { return false; }
        public String describe() { return "just defeated boss " + entity.raw() + (finisherOnly ? " by finisher" : ""); }
    }

    /** Tipos embutidos. */
    final class Builtin {
        private Builtin() {}

        private static boolean done;

        static void ensure() {
            if (done) return;
            done = true;
            registerAll();
        }

        private static void registerAll() {
            TYPES.put("all", self -> self.listOf().fieldOf("conditions").xmap(All::new, All::conditions));
            TYPES.put("any", self -> self.listOf().fieldOf("conditions").xmap(Any::new, Any::conditions));
            TYPES.put("not", self -> self.fieldOf("condition").xmap(Not::new, Not::condition));
            TYPES.put("advancement", self -> ResourceLocation.CODEC.fieldOf("advancement").xmap(HasAdvancement::new, HasAdvancement::advancement));
            TYPES.put("advancement_earned", self -> ResourceLocation.CODEC.fieldOf("advancement").xmap(AdvancementEarned::new, AdvancementEarned::advancement));
            TYPES.put("quest", self -> RecordCodecBuilder.<QuestIs>mapCodec(i -> i.group(
                    ResourceLocation.CODEC.fieldOf("quest").forGetter(QuestIs::quest),
                    QuestState.CODEC.listOf().optionalFieldOf("states", List.of(QuestState.COMPLETED, QuestState.CLAIMED)).forGetter(QuestIs::states)
            ).apply(i, QuestIs::new)));
            TYPES.put("in_dimension", self -> ResourceLocation.CODEC.fieldOf("dimension").xmap(InDimension::new, InDimension::dimension));
            TYPES.put("entered_dimension", self -> ResourceLocation.CODEC.fieldOf("dimension").xmap(EnteredDimension::new, EnteredDimension::dimension));
            TYPES.put("kill", self -> QuestMatcher.CODEC.optionalFieldOf("entity", QuestMatcher.ANY).xmap(Killed::new, Killed::entity));
            TYPES.put("finisher", self -> RecordCodecBuilder.<Finished>mapCodec(i -> i.group(
                    ResourceLocation.CODEC.optionalFieldOf("finisher").forGetter(Finished::finisher),
                    QuestMatcher.CODEC.optionalFieldOf("victim", QuestMatcher.ANY).forGetter(Finished::victim),
                    QuestRules.VICTIM_KIND.optionalFieldOf("victim_kind").forGetter(Finished::victimKind)
            ).apply(i, Finished::new)));
            TYPES.put("boss_defeat", self -> RecordCodecBuilder.<BossDefeated>mapCodec(i -> i.group(
                    QuestMatcher.CODEC.optionalFieldOf("entity", QuestMatcher.ANY).forGetter(BossDefeated::entity),
                    Codec.BOOL.optionalFieldOf("finisher_only", false).forGetter(BossDefeated::finisherOnly)
            ).apply(i, BossDefeated::new)));
        }
    }

    /** Linhas do diagnostico: cada no com o resultado agora (eventos aparecem como "event only"). */
    static void explain(QuestCondition c, QuestContext ctx, String indent, List<String> out) {
        String r = c.persistent() ? (c.test(ctx, null) ? "TRUE" : "false") : "event only";
        out.add(indent + c.describe() + " -> " + r);
        for (QuestCondition ch : new ArrayList<>(c.children())) explain(ch, ctx, indent + "  ", out);
    }
}
