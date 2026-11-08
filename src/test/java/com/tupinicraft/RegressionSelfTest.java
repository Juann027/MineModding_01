package com.tupinicraft;

import com.tupinicraft.election.Ballot;
import com.tupinicraft.election.Candidate;
import com.tupinicraft.election.ElectionTally;
import com.tupinicraft.mandate.Mandate;
import com.tupinicraft.mandate.MandateRegistry;
import com.tupinicraft.mandate.Occupant;
import com.tupinicraft.mandate.SeatKey;
import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.skilltree.EffectReconciler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Regressões que também podem rodar sem baixar Minecraft/NeoForge: scripts/regression-test.sh. */
public final class RegressionSelfTest {
    private static int passed;

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
        passed++;
        System.out.println("PASS " + description);
    }

    public static void main(String[] args) {
        passed = 0;
        tallyTests();
        mandateTests();
        effectTests();
        System.out.println("Regressões: " + passed + " verificações passaram.");
    }

    private static void tallyTests() {
        Candidate popular = Candidate.player(UUID.randomUUID(), "Popular", 1_000_000);
        Candidate other = Candidate.player(UUID.randomUUID(), "Outro", 999_999);
        List<Ballot> ballots = new ArrayList<>();
        for (int i = 0; i < 215; i++) {
            ballots.add(new Ballot(UUID.randomUUID(), popular.id(), 1000));
        }
        var result = ElectionTally.decide(100, List.of(popular, other), ballots, 10_000);
        check(result.bestScore() == 2_151_000_000L, "215 votos com configurações máximas não estouram int");
        check(result.winner().orElseThrow().id().equals(popular.id()), "maior pontuação acima de int vence");
        check(ElectionTally.decide(100, List.of(popular), ballots, 10_000).bestScore() == 2_151_000_000L,
                "candidato único também preserva pontuação long");
        Candidate npc = new Candidate(UUID.randomUUID(), "NPC", 1, true, 1000);
        check(ElectionTally.scores(List.of(npc), List.of(), 10_000).get(npc.id()) == 10_000_001L,
                "apoio simulado continua somando corretamente");
        Candidate tied = Candidate.player(UUID.randomUUID(), "Empatado", 1_000_000);
        for (int i = 0; i < 215; i++) ballots.add(new Ballot(UUID.randomUUID(), tied.id(), 1000));
        check(ElectionTally.decide(100, List.of(popular, tied), ballots, 10_000).outcome() == ElectionTally.Outcome.TIE,
                "empate acima do limite de int é preservado");
        check(ElectionTally.decide(1_000_000, List.of(popular), List.of(), 20).outcome() == ElectionTally.Outcome.BELOW_MINIMUM,
                "candidato único ainda precisa superar a pontuação mínima");
    }

    private static void mandateTests() {
        MandateRegistry registry = new MandateRegistry();
        UUID oldPlayer = UUID.randomUUID();
        UUID newPlayer = UUID.randomUUID();
        SeatKey seat = new SeatKey("tupinicraft:es", PoliticalOffice.GOVERNADOR, 1);
        SeatKey other = new SeatKey("tupinicraft:vitoria", PoliticalOffice.PREFEITO, 1);
        Mandate old = mandate(oldPlayer, 0, 100);
        registry.inaugurate(seat, old, 0, true);
        check(registry.expire(99, true).isEmpty(), "mandato não encerra antes do prazo");
        check(registry.expire(100, false).isEmpty() && registry.rawMandate(seat).isPresent(),
                "expiração desativada preserva o mandato");
        Map<SeatKey, Mandate> expired = registry.expire(100, true);
        check(expired.get(seat).equals(old) && registry.rawMandate(seat).isEmpty(),
                "vencido sai do registro e é retornado para atualizar o antigo ocupante");
        check(registry.lastOccupant(seat).orElseThrow().equals(oldPlayer), "histórico de reeleição preservado");
        check(registry.inaugurate(seat, mandate(newPlayer, 100, 200), 100, true),
                "nova posse após expiração tem cadeira livre");
        check(registry.expire(100, true).isEmpty() && registry.activeSeatOf(newPlayer, 100, true).isPresent(),
                "limpeza repetida não encerra o mandato novo");
        registry.restore(other, mandate(oldPlayer, 0, 100));
        check(registry.expire(100, true).size() == 1 && registry.activeSeatOf(newPlayer, 100, true).isPresent(),
                "limpeza de outra cadeira mantém os mandatos ainda vigentes");
        check(registry.expire(200, true).size() == 1 && registry.expire(200, true).isEmpty(),
                "cada encerramento é retornado uma única vez");
    }

    private static Mandate mandate(UUID player, long start, long end) {
        return new Mandate(Occupant.player(player, "Jogador"), start, end, Optional.empty(), Optional.empty());
    }

    // O mesmo reconciliador usado por SkillEffects; efeitos de teste substituem apenas as APIs do Minecraft.
    private record Effect(String attribute, String operation, double amount) { }

    private static void effectTests() {
        EffectReconciler<Effect> reconciler = new EffectReconciler<>();
        Map<String, List<Effect>> actual = new LinkedHashMap<>();
        List<String> calls = new ArrayList<>();
        java.util.function.BiConsumer<String, List<Effect>> remove = (id, effects) -> {
            calls.add("remove:" + id);
            check(effects.equals(actual.remove(id)), "remoção usa a definição anterior do efeito");
        };
        java.util.function.BiConsumer<String, List<Effect>> apply = (id, effects) -> {
            calls.add("apply:" + id);
            actual.put(id, effects);
        };
        Effect speed = new Effect("speed", "add", 0.1);
        Effect health = new Effect("health", "add", 4);
        var initial = Map.of("skill", List.of(speed, health));
        reconciler.reconcile(initial, remove, apply);
        check(actual.equals(initial), "login aplica todos os efeitos comprados e ativos");
        calls.clear();
        reconciler.reconcile(initial, remove, apply);
        check(calls.isEmpty(), "tick sem alteração não remove nem reaplica modificadores");
        var changed = Map.of("skill", List.of(new Effect("speed", "multiply", 0.2)));
        reconciler.reconcile(changed, remove, apply);
        check(actual.equals(changed) && calls.equals(List.of("remove:skill", "apply:skill")),
                "reload de valor/operação e redução da lista remove antigos antes de aplicar novos");
        calls.clear();
        var moved = Map.of("skill", List.of(new Effect("armor", "add", 2)));
        reconciler.reconcile(moved, remove, apply);
        check(actual.equals(moved), "troca de atributo remove modificadores do atributo antigo");
        reconciler.reconcile(Map.of(), remove, apply);
        check(actual.isEmpty(), "skill removida ou suspensa perde todos os efeitos");
        reconciler.reconcile(initial, remove, apply);
        check(actual.equals(initial), "skill que reaparece ou recupera requisito é restaurada");
        List<Effect> mutable = new ArrayList<>(List.of(speed));
        reconciler.reconcile(Map.of("skill", mutable), remove, apply);
        mutable.add(health);
        reconciler.reconcile(Map.of("skill", mutable), remove, apply);
        check(actual.get("skill").size() == 2, "cache usa cópia imutável da lista de efeitos");
        new EffectReconciler<Effect>().reconcile(initial, (id, effects) -> {
            throw new AssertionError("entidade nova não tem efeitos antigos");
        }, apply);
        check(actual.equals(initial), "entidade nova no respawn recebe seus efeitos");
    }
}
