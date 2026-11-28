package com.respectcraft.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Motor de missoes (Etapa 4.14.2), <b>puro</b>: recebe o registro do jogador, o catalogo, o acontecimento e o
 * contexto e devolve o registro novo + a lista de mudancas. Nao envia pacote, nao concede nada, nao toca no mundo:
 * quem aplica e o {@code QuestService} (servidor). Por isso tudo aqui e testado no {@code SelfTest}.
 *
 * <p>Ordem de um acontecimento: (1) objetivos das missoes que ja estavam ativas; (2) descoberta das ainda ocultas;
 * (3) cadeia: missao que mudou de estado vira um evento {@link QuestTrigger#QUEST} (no maximo
 * {@link QuestRules#MAX_CHAIN} niveis). Por (1) vir antes de (2), o acontecimento que revela uma missao nunca conta
 * de novo nos objetivos dela.
 */
public final class QuestEngine {
    private QuestEngine() {}

    public enum ChangeKind { DISCOVERED, ACCEPTED, PROGRESS, COMPLETED, CLAIMED, FAILED, EXPIRED, CANCELLED, REOPENED, TRACKED, MIGRATED, RESET }

    /** Uma mudanca para o servico aplicar (aviso, recompensa, pacote incremental). */
    public record Change(ChangeKind kind, ResourceLocation quest, String objective, int value, int count) {
        static Change of(ChangeKind k, ResourceLocation q) { return new Change(k, q, "", 0, 0); }
    }

    /** Resultado de uma operacao; {@code error} = motivo da recusa (chave de traducao) ou null. */
    public static final class Result {
        public QuestLog log;
        public final List<Change> changes = new ArrayList<>();
        public String error;

        Result(QuestLog log) { this.log = log; }

        public boolean changed() { return !changes.isEmpty(); }
    }

    /** Contexto que enxerga o registro em montagem (para condicoes {@code quest} e objetivos {@code complete_quest}). */
    private static QuestContext live(QuestContext base, Result r) {
        return new QuestContext() {
            public boolean hasAdvancement(ResourceLocation id) { return base.hasAdvancement(id); }
            public ResourceLocation dimension() { return base.dimension(); }
            public long now() { return base.now(); }
            public QuestState questState(ResourceLocation id) { return r.log.state(id); }
        };
    }

    // ================================================================================================ acontecimentos

    public static Result handle(QuestLog log, QuestCatalog cat, QuestEvent event, QuestContext base) {
        Result r = new Result(log);
        handle(r, cat, event, live(base, r), 0);
        return r;
    }

    private static void handle(Result r, QuestCatalog cat, QuestEvent event, QuestContext ctx, int depth) {
        List<QuestEvent> moved = new ArrayList<>();
        // (1) objetivos das ativas
        for (QuestDef def : cat.objectiveCandidates(event.trigger())) {
            QuestProgress p = r.log.get(def.id());
            if (p.state() != QuestState.ACTIVE) continue;
            Map<String, Integer> map = new HashMap<>(p.progress());
            boolean changed = false;
            for (QuestObjective o : def.objectives()) {
                QuestObjective.Type t = o.behavior();
                if (t == null || t.trigger() != event.trigger() || !t.matches(o, event)) continue;
                int cur = map.getOrDefault(o.id(), 0);
                if (cur >= o.count()) continue;
                map.put(o.id(), cur + 1);
                changed = true;
                r.changes.add(new Change(ChangeKind.PROGRESS, def.id(), o.id(), cur + 1, o.count()));
            }
            if (!changed) continue;
            r.log = r.log.with(def.id(), p.withProgress(map));
            if (def.complete(map)) complete(r, def, ctx, moved);
        }
        // (2) descoberta das ocultas
        for (QuestDef def : cat.discoveryCandidates(event.trigger())) {
            if (r.log.state(def.id()) != QuestState.HIDDEN) continue;
            if (!def.discover().get().test(ctx, event)) continue;
            reveal(r, def, ctx, moved);
        }
        // (3) cadeias
        if (depth < QuestRules.MAX_CHAIN) {
            for (QuestEvent ev : moved) handle(r, cat, ev, ctx, depth + 1);
        }
    }

    /** Descobre (ou abre direto, com aceitacao automatica) ignorando a condicao de descoberta. */
    private static void reveal(Result r, QuestDef def, QuestContext ctx, List<QuestEvent> moved) {
        QuestProgress p = r.log.get(def.id()).discovered(ctx.now());
        r.log = r.log.with(def.id(), p);
        r.changes.add(Change.of(ChangeKind.DISCOVERED, def.id()));
        if (def.flags().autoAccept() && def.requires().map(c -> c.test(ctx, null)).orElse(true)) activate(r, def, ctx, moved);
    }

    private static void activate(Result r, QuestDef def, QuestContext ctx, List<QuestEvent> moved) {
        Map<String, Integer> start = new HashMap<>();
        for (QuestObjective o : def.objectives()) {
            QuestObjective.Type t = o.behavior();
            int v = t == null ? 0 : Math.min(o.count(), t.retroactive(o, ctx));
            if (v > 0) start.put(o.id(), v);
        }
        QuestProgress p = r.log.get(def.id()).accepted(start, ctx.now(), def.signature());
        r.log = r.log.with(def.id(), p);
        r.changes.add(Change.of(ChangeKind.ACCEPTED, def.id()));
        if (def.complete(start)) complete(r, def, ctx, moved);
    }

    private static void complete(Result r, QuestDef def, QuestContext ctx, List<QuestEvent> moved) {
        r.log = r.log.with(def.id(), r.log.get(def.id()).completed(ctx.now()));
        r.changes.add(Change.of(ChangeKind.COMPLETED, def.id()));
        moved.add(QuestEvent.quest(def.id(), QuestState.COMPLETED));
        if (def.flags().autoClaim()) claimInternal(r, def, ctx);
    }

    private static void claimInternal(Result r, QuestDef def, QuestContext ctx) {
        long cooldown = def.flags().repeatable() ? ctx.now() + def.flags().cooldownTicks() : 0L;
        r.log = r.log.with(def.id(), r.log.get(def.id()).claimed(cooldown));
        r.changes.add(Change.of(ChangeKind.CLAIMED, def.id()));
    }

    // ================================================================================================ reconciliacao

    /**
     * Login, reload de datapacks e comandos: descobre o que o estado persistente ja permite (inclusive missoes sem
     * condicao), atualiza objetivos retroativos (conquista obtida enquanto a missao estava ativa, mas cujo evento se
     * perdeu), migra definicoes alteradas e faz a varredura de tempo. Limitado ao catalogo e ao registro do jogador.
     */
    public static Result reconcile(QuestLog log, QuestCatalog cat, QuestContext base) {
        Result r = new Result(log);
        QuestContext ctx = live(base, r);
        List<QuestEvent> moved = new ArrayList<>();
        for (QuestDef def : cat.all()) {
            QuestProgress p = r.log.get(def.id());
            if (p.state() == QuestState.HIDDEN) {
                if (def.discover().map(c -> c.test(ctx, null)).orElse(true)) reveal(r, def, ctx, moved);
                continue;
            }
            if (p.state() != QuestState.ACTIVE) continue;
            Map<String, Integer> map = new HashMap<>(p.progress());
            boolean changed = false;
            boolean migrated = p.signature() != 0 && p.signature() != def.signature();
            for (QuestObjective o : def.objectives()) {
                int cur = map.getOrDefault(o.id(), 0);
                int clamped = Math.min(cur, o.count());
                QuestObjective.Type t = o.behavior();
                int retro = t == null ? 0 : Math.min(o.count(), t.retroactive(o, ctx));
                int v = Math.max(clamped, retro);
                if (v != cur) {
                    map.put(o.id(), v);
                    changed = true;
                    if (v > cur) r.changes.add(new Change(ChangeKind.PROGRESS, def.id(), o.id(), v, o.count()));
                }
            }
            if (migrated) {
                QuestProgress np = p.withProgress(map);
                np = new QuestProgress(np.state(), np.progress(), np.discoveredAt(), np.acceptedAt(), np.finishedAt(), np.cooldownUntil(),
                        np.completions(), np.tracked(), def.signature());
                r.log = r.log.with(def.id(), np);
                r.changes.add(Change.of(ChangeKind.MIGRATED, def.id()));
            } else if (changed) {
                r.log = r.log.with(def.id(), p.withProgress(map));
            }
            if ((changed || migrated) && def.complete(map)) complete(r, def, ctx, moved);
        }
        for (QuestEvent ev : moved) handle(r, cat, ev, ctx, 1);
        sweepInto(r, cat, ctx);
        return r;
    }

    /** Varredura de tempo: limite das ativas e reabertura das repetiveis. So olha o registro do jogador. */
    public static Result sweep(QuestLog log, QuestCatalog cat, QuestContext base) {
        Result r = new Result(log);
        sweepInto(r, cat, live(base, r));
        return r;
    }

    private static void sweepInto(Result r, QuestCatalog cat, QuestContext ctx) {
        List<QuestEvent> moved = new ArrayList<>();
        for (Map.Entry<String, QuestProgress> e : new ArrayList<>(r.log.quests().entrySet())) {
            QuestDef def = cat.get(ResourceLocation.tryParse(e.getKey()));
            if (def == null) continue;
            QuestProgress p = e.getValue();
            if (p.state() == QuestState.ACTIVE && QuestRules.expired(p.acceptedAt(), def.flags().timeLimitTicks(), ctx.now())) {
                r.log = r.log.with(def.id(), p.ended(QuestState.EXPIRED, ctx.now()));
                r.changes.add(Change.of(ChangeKind.EXPIRED, def.id()));
                moved.add(QuestEvent.quest(def.id(), QuestState.EXPIRED));
            } else if (QuestRules.reopens(p.state(), def.flags(), p.cooldownUntil(), ctx.now())) {
                r.log = r.log.with(def.id(), new QuestProgress(QuestState.AVAILABLE, Map.of(), p.discoveredAt(), p.acceptedAt(),
                        p.finishedAt(), 0L, p.completions(), false, p.signature()));
                r.changes.add(Change.of(ChangeKind.REOPENED, def.id()));
                if (def.flags().autoAccept() && def.requires().map(c -> c.test(ctx, null)).orElse(true)) activate(r, def, ctx, moved);
            }
        }
        for (QuestEvent ev : moved) handle(r, cat, ev, ctx, 1);
    }

    // ================================================================================================ acoes

    public static Result accept(QuestLog log, QuestCatalog cat, ResourceLocation id, QuestContext base) {
        Result r = new Result(log);
        QuestContext ctx = live(base, r);
        QuestDef def = cat.get(id);
        QuestProgress p = log.get(id);
        if (def == null || !p.state().discovered()) return refuse(r, "unknown");
        if (!QuestRules.canAccept(p.state(), def.flags())) return refuse(r, "not_available");
        if (def.requires().isPresent() && !def.requires().get().test(ctx, null)) return refuse(r, "requirements");
        List<QuestEvent> moved = new ArrayList<>();
        activate(r, def, ctx, moved);
        for (QuestEvent ev : moved) handle(r, cat, ev, ctx, 1);
        return r;
    }

    public static Result abandon(QuestLog log, QuestCatalog cat, ResourceLocation id, QuestContext base) {
        Result r = new Result(log);
        QuestDef def = cat.get(id);
        QuestProgress p = log.get(id);
        if (def == null || p.state() != QuestState.ACTIVE) return refuse(r, "not_active");
        if (!def.flags().abandonable()) return refuse(r, "cannot_abandon");
        r.log = log.with(id, p.ended(QuestState.CANCELLED, base.now()));
        r.changes.add(Change.of(ChangeKind.CANCELLED, id));
        return r;
    }

    /** Resgate manual: so de COMPLETED; pedido repetido encontra CLAIMED e e recusado (sem segunda recompensa). */
    public static Result claim(QuestLog log, QuestCatalog cat, ResourceLocation id, QuestContext base) {
        Result r = new Result(log);
        QuestContext ctx = live(base, r);
        QuestDef def = cat.get(id);
        QuestProgress p = log.get(id);
        if (def == null || p.state() != QuestState.COMPLETED) return refuse(r, "nothing_to_claim");
        claimInternal(r, def, ctx);
        handle(r, cat, QuestEvent.quest(id, QuestState.CLAIMED), ctx, 1);
        return r;
    }

    public static Result track(QuestLog log, QuestCatalog cat, ResourceLocation id, boolean on) {
        Result r = new Result(log);
        QuestDef def = cat.get(id);
        QuestProgress p = log.get(id);
        if (def == null || !p.state().discovered()) return refuse(r, "unknown");
        if (p.tracked() == on) return r;
        if (on && (p.state() != QuestState.ACTIVE && p.state() != QuestState.AVAILABLE)) return refuse(r, "cannot_track");
        if (on && log.trackedCount() >= QuestRules.MAX_TRACKED) return refuse(r, "track_limit");
        r.log = log.with(id, p.withTracked(on));
        r.changes.add(Change.of(ChangeKind.TRACKED, id));
        return r;
    }

    /** Morte do jogador: falham as ativas com {@code fail_on_death}. */
    public static Result death(QuestLog log, QuestCatalog cat, QuestContext base) {
        Result r = new Result(log);
        QuestContext ctx = live(base, r);
        List<QuestEvent> moved = new ArrayList<>();
        for (Map.Entry<String, QuestProgress> e : new ArrayList<>(log.quests().entrySet())) {
            QuestDef def = cat.get(ResourceLocation.tryParse(e.getKey()));
            if (def == null || e.getValue().state() != QuestState.ACTIVE || !def.flags().failOnDeath()) continue;
            r.log = r.log.with(def.id(), e.getValue().ended(QuestState.FAILED, ctx.now()));
            r.changes.add(Change.of(ChangeKind.FAILED, def.id()));
            moved.add(QuestEvent.quest(def.id(), QuestState.FAILED));
        }
        for (QuestEvent ev : moved) handle(r, cat, ev, ctx, 1);
        return r;
    }

    // ================================================================================================ administracao

    /** Revela ignorando a condicao (recompensa {@code quest} e {@code /respectcraft quest discover}). */
    public static Result forceDiscover(QuestLog log, QuestCatalog cat, ResourceLocation id, QuestContext base) {
        Result r = new Result(log);
        QuestContext ctx = live(base, r);
        QuestDef def = cat.get(id);
        if (def == null) return refuse(r, "unknown");
        if (log.state(id) != QuestState.HIDDEN) return refuse(r, "already_discovered");
        List<QuestEvent> moved = new ArrayList<>();
        reveal(r, def, ctx, moved);
        for (QuestEvent ev : moved) handle(r, cat, ev, ctx, 1);
        return r;
    }

    /** Volta a missao ao comeco (oculta, sem progresso). So por comando administrativo. */
    public static Result reset(QuestLog log, ResourceLocation id) {
        Result r = new Result(log);
        if (!log.has(id)) return refuse(r, "unknown");
        r.log = log.without(id);
        r.changes.add(Change.of(ChangeKind.RESET, id));
        return r;
    }

    private static Result refuse(Result r, String why) {
        r.error = why;
        return r;
    }
}
