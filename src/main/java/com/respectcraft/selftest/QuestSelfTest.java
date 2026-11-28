package com.respectcraft.selftest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.respectcraft.quest.QuestCatalog;
import com.respectcraft.quest.QuestContext;
import com.respectcraft.quest.QuestEngine;
import com.respectcraft.quest.QuestEvent;
import com.respectcraft.quest.QuestLog;
import com.respectcraft.quest.QuestRegistry;
import com.respectcraft.quest.QuestRules;
import com.respectcraft.quest.QuestState;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Etapa 4.14.2: regras puras do Quest Engine no {@code /respectcraft selftest}: carga e recusa de definicoes,
 * descoberta por conquista (estado e evento), secreta por finalizacao sem contar o evento revelador, condicoes
 * compostas, aceitacao manual/automatica, objetivos incrementais, cadeia, repeticao com intervalo, resgate unico,
 * falha na morte, tempo limite, dimensao, persistencia (Codec) e migracao de objetivos.
 */
final class QuestSelfTest {
    private QuestSelfTest() {}

    /** Contexto de teste: conquistas, dimensao e relogio mudaveis. */
    static final class Ctx implements QuestContext {
        final Set<ResourceLocation> advancements = new HashSet<>();
        ResourceLocation dimension = ResourceLocation.withDefaultNamespace("overworld");
        long now = 1_000L;
        public boolean hasAdvancement(ResourceLocation id) { return advancements.contains(id); }
        public ResourceLocation dimension() { return dimension; }
        public long now() { return now; }
    }

    static ResourceLocation rl(String s) { return ResourceLocation.parse(s); }

    static QuestCatalog catalog(Map<String, String> json) {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : json.entrySet()) files.put(rl(e.getKey()), JsonParser.parseString(e.getValue()));
        return QuestRegistry.parse(files, JsonOps.INSTANCE);
    }

    static void run(List<String> f) {
        Map<String, String> defs = new LinkedHashMap<>();
        defs.put("t:first", """
                {"format":1,"title":"First","description":"d","discover":{"type":"advancement","advancement":"minecraft:story/root"},
                 "objectives":[{"id":"stone","type":"advancement","target":"minecraft:story/mine_stone"},
                               {"id":"undead","type":"kill","target":"#minecraft:undead","count":3}],
                 "rewards":[{"type":"experience","amount":50},{"type":"item","id":"minecraft:bread","amount":6},{"type":"quest","id":"t:chain"}]}""");
        defs.put("t:secret", """
                {"title":"S","description":"d","secret":true,"accept":"auto","claim":"auto",
                 "discover":{"type":"finisher","victim":"respectcraft:strawberry"},
                 "objectives":[{"id":"more","type":"finisher","target":"respectcraft:strawberry","count":5}],
                 "rewards":[{"type":"experience","amount":150}]}""");
        defs.put("t:chain", """
                {"title":"C","description":"d","hint":{"text":"something beyond"},
                 "discover":{"type":"all","conditions":[{"type":"quest","quest":"t:first","states":["claimed"]},
                    {"type":"any","conditions":[{"type":"advancement","advancement":"minecraft:story/mine_stone"},{"type":"advancement","advancement":"x:none"}]},
                    {"type":"not","condition":{"type":"in_dimension","dimension":"respectcraft:inverted_realm"}}]},
                 "objectives":[{"id":"enter","type":"enter_dimension","target":"respectcraft:inverted_realm"},
                               {"id":"first","type":"complete_quest","target":"t:first"}]}""");
        defs.put("t:repeat", """
                {"title":"R","description":"d","repeatable":true,"cooldown_ticks":200,"accept":"auto","claim":"auto",
                 "objectives":[{"id":"z","type":"kill","target":"minecraft:zombie"}],"rewards":[{"type":"experience","amount":5}]}""");
        defs.put("t:timed", """
                {"title":"T","description":"d","time_limit_ticks":100,"fail_on_death":true,"retry":false,
                 "objectives":[{"id":"z","type":"kill","target":"minecraft:zombie","count":2}]}""");
        // invalidas: tipo desconhecido, sem objetivos, formato futuro, item inexistente, complexidade, JSON ruim
        defs.put("t:bad_type", "{\"title\":\"B\",\"description\":\"d\",\"objectives\":[{\"id\":\"a\",\"type\":\"fly\"}]}");
        defs.put("t:bad_empty", "{\"title\":\"B\",\"description\":\"d\",\"objectives\":[]}");
        defs.put("t:bad_format", "{\"format\":99,\"title\":\"B\",\"description\":\"d\",\"objectives\":[{\"id\":\"a\",\"type\":\"kill\",\"target\":\"*\"}]}");
        defs.put("t:bad_item", "{\"title\":\"B\",\"description\":\"d\",\"objectives\":[{\"id\":\"a\",\"type\":\"kill\",\"target\":\"*\"}],\"rewards\":[{\"type\":\"item\",\"id\":\"x:nope\"}]}");
        defs.put("t:bad_cond", "{\"title\":\"B\",\"description\":\"d\",\"discover\":{\"type\":\"teleport\"},\"objectives\":[{\"id\":\"a\",\"type\":\"kill\",\"target\":\"*\"}]}");
        StringBuilder deep = new StringBuilder("{\"type\":\"advancement\",\"advancement\":\"a:b\"}");
        for (int i = 0; i < QuestRules.MAX_CONDITION_DEPTH + 1; i++) deep = new StringBuilder("{\"type\":\"not\",\"condition\":" + deep + "}");
        defs.put("t:bad_deep", "{\"title\":\"B\",\"description\":\"d\",\"discover\":" + deep + ",\"objectives\":[{\"id\":\"a\",\"type\":\"kill\",\"target\":\"*\"}]}");
        defs.put("t:bad_requires", "{\"title\":\"B\",\"description\":\"d\",\"requires\":{\"type\":\"kill\"},\"objectives\":[{\"id\":\"a\",\"type\":\"kill\",\"target\":\"*\"}]}");
        QuestCatalog cat = catalog(defs);
        check(f, cat.size() == 5, "quests: definicoes validas carregadas (" + cat.size() + ")");
        check(f, cat.errors().size() == 7, "quests: definicoes invalidas recusadas com motivo (" + cat.errors().size() + ")");
        check(f, cat.get(rl("t:bad_type")) == null && cat.get(rl("t:bad_format")) == null && cat.get(rl("t:bad_deep")) == null,
                "quests: invalida nao entra no catalogo");
        check(f, cat.discoveryCandidates(com.respectcraft.quest.QuestTrigger.FINISHER).size() == 1
                && cat.objectiveCandidates(com.respectcraft.quest.QuestTrigger.KILL).size() == 3, "quests: indice por gatilho");

        Ctx ctx = new Ctx();
        ResourceLocation first = rl("t:first"), secret = rl("t:secret"), chain = rl("t:chain"), repeat = rl("t:repeat"), timed = rl("t:timed");
        Predicate undead = t -> t.equals(rl("minecraft:undead"));

        // ---- login: missoes sem condicao aparecem; com condicao de estado, so com a conquista
        QuestEngine.Result r = QuestEngine.reconcile(QuestLog.EMPTY, cat, ctx);
        QuestLog log = r.log;
        check(f, log.state(first) == QuestState.HIDDEN && log.state(timed) == QuestState.AVAILABLE && log.state(repeat) == QuestState.ACTIVE
                && log.state(secret) == QuestState.HIDDEN, "quests: login descobre so o que o estado permite (auto-aceita vira ativa)");
        // ---- descoberta por conquista (evento) e por estado (conquista antiga, sem repetir)
        log = QuestEngine.handle(log, cat, QuestEvent.advancement(rl("minecraft:story/root")), ctx).log;
        check(f, log.state(first) == QuestState.AVAILABLE, "quests: descoberta pela conquista obtida agora");
        Ctx old = new Ctx();
        old.advancements.add(rl("minecraft:story/root"));
        check(f, QuestEngine.reconcile(QuestLog.EMPTY, cat, old).log.state(first) == QuestState.AVAILABLE,
                "quests: conquista obtida antes descobre no login (sem repetir)");
        // ---- aceitacao manual: objetivos retroativos (conquista ja obtida conta)
        ctx.advancements.add(rl("minecraft:story/mine_stone"));
        r = QuestEngine.accept(log, cat, first, ctx);
        log = r.log;
        check(f, r.error == null && log.state(first) == QuestState.ACTIVE && log.get(first).value("stone") == 1,
                "quests: aceitar manual + objetivo retroativo de conquista");
        check(f, QuestEngine.accept(log, cat, first, ctx).error != null, "quests: aceitar de novo e recusado");
        check(f, QuestEngine.accept(log, cat, secret, ctx).error != null, "quests: secreta nao descoberta nao pode ser aceita pelo cliente");
        // ---- objetivos incrementais por tag
        for (int i = 0; i < 2; i++) log = QuestEngine.handle(log, cat, QuestEvent.kill(rl("minecraft:zombie"), undead), ctx).log;
        check(f, log.get(first).value("undead") == 2 && log.state(first) == QuestState.ACTIVE, "quests: progresso incremental (2/3)");
        log = QuestEngine.handle(log, cat, QuestEvent.kill(rl("minecraft:pig"), t -> false), ctx).log;
        check(f, log.get(first).value("undead") == 2, "quests: alvo errado nao conta");
        r = QuestEngine.handle(log, cat, QuestEvent.kill(rl("minecraft:skeleton"), undead), ctx);
        log = r.log;
        check(f, log.state(first) == QuestState.COMPLETED && has(r, QuestEngine.ChangeKind.COMPLETED), "quests: conclusao ao cumprir tudo");
        log = QuestEngine.handle(log, cat, QuestEvent.kill(rl("minecraft:skeleton"), undead), ctx).log;
        check(f, log.get(first).value("undead") == 3, "quests: missao concluida nao conta mais");
        // ---- resgate unico: segundo pedido (pacote repetido) e recusado
        r = QuestEngine.claim(log, cat, first, ctx);
        check(f, r.error == null && r.log.state(first) == QuestState.CLAIMED && count(r, QuestEngine.ChangeKind.CLAIMED) == 1, "quests: resgate");
        log = r.log;
        // cadeia: a condicao composta (quest claimed AND any(advancement) AND not(in_dimension)) revela a seguinte
        check(f, log.state(chain) == QuestState.AVAILABLE, "quests: cadeia por condicao composta ao resgatar");
        QuestEngine.Result again = QuestEngine.claim(log, cat, first, ctx);
        check(f, again.error != null && !again.changed(), "quests: resgate repetido nao concede de novo");
        // ---- objetivo complete_quest retroativo + dimensao
        log = QuestEngine.accept(log, cat, chain, ctx).log;
        check(f, log.get(chain).value("first") == 1 && log.get(chain).value("enter") == 0, "quests: objetivo de missao concluida retroativo");
        ctx.dimension = rl("respectcraft:inverted_realm");
        r = QuestEngine.handle(log, cat, QuestEvent.dimension(ctx.dimension), ctx);
        log = r.log;
        check(f, log.state(chain) == QuestState.COMPLETED, "quests: objetivo de dimensao (troca de dimensao)");
        ctx.dimension = rl("minecraft:overworld");
        // ---- secreta: descoberta pela 1a finalizacao, que nao conta nos 5 objetivos
        QuestEvent straw = QuestEvent.finisher(rl("respectcraft:strawberry"), t -> false, rl("respectcraft:strawberry/shut_up"), "mob");
        r = QuestEngine.handle(log, cat, straw, ctx);
        log = r.log;
        check(f, log.state(secret) == QuestState.ACTIVE && log.get(secret).value("more") == 0, "quests: secreta descoberta e o evento revelador nao conta");
        for (int i = 0; i < 4; i++) log = QuestEngine.handle(log, cat, straw, ctx).log;
        check(f, log.get(secret).value("more") == 4 && log.state(secret) == QuestState.ACTIVE, "quests: finalizacoes contam uma a uma (4/5)");
        log = QuestEngine.handle(log, cat, QuestEvent.kill(rl("respectcraft:strawberry"), t -> false), ctx).log;
        check(f, log.get(secret).value("more") == 4, "quests: morte comum nao conta como finalizacao");
        r = QuestEngine.handle(log, cat, straw, ctx);
        log = r.log;
        check(f, log.state(secret) == QuestState.CLAIMED && count(r, QuestEngine.ChangeKind.CLAIMED) == 1, "quests: 5a finalizacao conclui e resgata sozinha");
        check(f, count(QuestEngine.handle(log, cat, straw, ctx), QuestEngine.ChangeKind.CLAIMED) == 0, "quests: secreta unica nao reabre");
        // ---- repetivel: conclui, espera o intervalo e reabre
        r = QuestEngine.handle(log, cat, QuestEvent.kill(rl("minecraft:zombie"), undead), ctx);
        log = r.log;
        check(f, log.state(repeat) == QuestState.CLAIMED && log.get(repeat).completions() == 1, "quests: repetivel concluida e resgatada");
        check(f, QuestEngine.sweep(log, cat, ctx).log.state(repeat) == QuestState.CLAIMED, "quests: repetivel espera o intervalo");
        ctx.now += 200;
        log = QuestEngine.sweep(log, cat, ctx).log;
        check(f, log.state(repeat) == QuestState.ACTIVE && log.get(repeat).value("z") == 0, "quests: repetivel reabre zerada depois do intervalo");
        log = QuestEngine.handle(log, cat, QuestEvent.kill(rl("minecraft:zombie"), undead), ctx).log;
        check(f, log.get(repeat).completions() == 2, "quests: repetivel conta de novo");
        // ---- tempo limite, falha na morte, sem nova tentativa
        log = QuestEngine.accept(log, cat, timed, ctx).log;
        QuestLog dying = QuestEngine.death(log, cat, ctx).log;
        check(f, dying.state(timed) == QuestState.FAILED && QuestEngine.accept(dying, cat, timed, ctx).error != null,
                "quests: falha na morte (sem nova tentativa)");
        ctx.now += 100;
        check(f, QuestEngine.sweep(log, cat, ctx).log.state(timed) == QuestState.EXPIRED, "quests: tempo limite expira");
        // ---- abandonar, rastrear com limite
        check(f, QuestEngine.abandon(log, cat, timed, ctx).log.state(timed) == QuestState.CANCELLED, "quests: abandonar");
        QuestLog tr = QuestEngine.track(log, cat, timed, true).log;
        check(f, tr.get(timed).tracked() && QuestEngine.track(tr, cat, first, true).error != null, "quests: rastrear (resgatada nao pode)");
        // ---- transicoes
        var flags = com.respectcraft.quest.QuestDef.Flags.DEFAULT;
        check(f, QuestRules.canTransition(QuestState.HIDDEN, QuestState.AVAILABLE, flags) && !QuestRules.canTransition(QuestState.HIDDEN, QuestState.CLAIMED, flags)
                && !QuestRules.canTransition(QuestState.AVAILABLE, QuestState.COMPLETED, flags) && !QuestRules.canTransition(QuestState.CLAIMED, QuestState.AVAILABLE, flags),
                "quests: transicoes arbitrarias recusadas");
        // ---- persistencia: Codec NBT ida e volta, ids desconhecidos mantidos
        QuestLog withUnknown = log.with(rl("t:removed"), log.get(first));
        Tag nbt = QuestLog.CODEC.encodeStart(NbtOps.INSTANCE, withUnknown).getOrThrow();
        QuestLog back = QuestLog.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        check(f, back.equals(withUnknown), "quests: registro salvo e lido igual (logout/reinicio)");
        QuestLog afterReconcile = QuestEngine.reconcile(back, cat, ctx).log;
        check(f, afterReconcile.has(rl("t:removed")), "quests: missao sem definicao nao e apagada");
        // ---- migracao: objetivo com meta menor apos reload conclui sem apagar
        Map<String, String> changed = new LinkedHashMap<>();
        changed.put("t:timed", "{\"title\":\"T\",\"description\":\"d\",\"objectives\":[{\"id\":\"z\",\"type\":\"kill\",\"target\":\"minecraft:zombie\",\"count\":1}]}");
        QuestLog progressed = QuestEngine.handle(QuestEngine.accept(QuestEngine.reconcile(QuestLog.EMPTY, cat, ctx).log, cat, timed, ctx).log, cat,
                QuestEvent.kill(rl("minecraft:zombie"), undead), ctx).log;
        r = QuestEngine.reconcile(progressed, catalog(changed), ctx);
        check(f, has(r, QuestEngine.ChangeKind.MIGRATED) && r.log.state(timed) == QuestState.COMPLETED, "quests: reload com objetivo alterado migra o progresso");
    }

    interface Predicate extends java.util.function.Predicate<ResourceLocation> {}

    static boolean has(QuestEngine.Result r, QuestEngine.ChangeKind k) { return count(r, k) > 0; }

    static int count(QuestEngine.Result r, QuestEngine.ChangeKind k) {
        int n = 0;
        for (QuestEngine.Change c : r.changes) if (c.kind() == k) n++;
        return n;
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) f.add(what);
    }
}
