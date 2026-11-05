package com.tupinicraft;

import com.tupinicraft.election.*;
import com.tupinicraft.origin.*;
import com.tupinicraft.politics.*;
import java.util.*;

/**
 * Testes das regras puras (origens, cargos, peso do voto e apuração), sem dependências externas.
 * Executado por `./gradlew rulesTest` (e automaticamente em `./gradlew build`).
 */
public class RulesSelfTest {
    static int failures = 0, passed = 0;
    static void check(boolean cond, String name) {
        if (cond) { passed++; System.out.println("PASS " + name); }
        else { failures++; System.out.println("FAIL " + name); }
    }
    static Candidate cand(String n, int pop) { return new Candidate(UUID.nameUUIDFromBytes(n.getBytes()), n, pop); }
    static Ballot vote(String voter, Candidate c, int voterPop) { return new Ballot(UUID.nameUUIDFromBytes(voter.getBytes()), c.id(), ElectionTally.voteWeight(voterPop)); }

    public static void main(String[] a) {
        // Origens
        check(RegionalOrigin.values().length == 27, "27 UFs");
        check(RegionalOrigin.MA.demonym().equals("Maranhense"), "MA Maranhense");
        check(RegionalOrigin.ES.demonym().equals("Capixaba"), "ES Capixaba");
        check(RegionalOrigin.RJ.demonym().equals("Carioca"), "RJ Carioca");
        check(RegionalOrigin.SP.demonym().equals("Paulista"), "SP Paulista");
        check(RegionalOrigin.RS.demonym().equals("Gaúcho"), "RS Gaucho");
        check(RegionalOrigin.DF.demonym().equals("Brasiliense"), "DF Brasiliense");
        check(RegionalOrigin.byCode("ma").orElseThrow() == RegionalOrigin.MA, "byCode case-insensitive");
        check(RegionalOrigin.byCode("XX").isEmpty(), "byCode invalid");
        Map<Macroregion, Integer> perRegion = new EnumMap<>(Macroregion.class);
        for (RegionalOrigin o : RegionalOrigin.values()) perRegion.merge(o.macroregion(), 1, Integer::sum);
        check(perRegion.get(Macroregion.NORTE) == 7 && perRegion.get(Macroregion.NORDESTE) == 9
              && perRegion.get(Macroregion.CENTRO_OESTE) == 4 && perRegion.get(Macroregion.SUDESTE) == 4
              && perRegion.get(Macroregion.SUL) == 3, "macrorregioes 7/9/4/4/3");

        // Cargos
        PoliticalOffice[] order = PoliticalOffice.values();
        String[] expected = {"Desempregado","Vereador","Deputado Estadual","Prefeito","Deputado Federal","Senador","Governador","Presidente da República"};
        boolean ok = order.length == 8;
        for (int i = 0; ok && i < 8; i++) ok = order[i].displayName().equals(expected[i]);
        check(ok, "ordem dos cargos");
        int[][] req = {{0,10},{50,30},{150,60},{300,100},{500,160},{800,240},{1200,350}};
        ok = true;
        for (int i = 0; i < 7; i++) ok &= order[i+1].minPoliticalXp()==req[i][0] && order[i+1].minPopularity()==req[i][1];
        check(ok, "requisitos dos cargos");
        check(PoliticalOffice.PRESIDENTE.next().isEmpty(), "presidente sem proximo");
        check(PoliticalOffice.DESEMPREGADO.next().orElseThrow()==PoliticalOffice.VEREADOR, "desempregado->vereador");
        check(PoliticalOffice.byId("DEPUTADO_ESTADUAL").orElseThrow()==PoliticalOffice.DEPUTADO_ESTADUAL, "byId");
        check(!PoliticalOffice.PREFEITO.meetsRequirements(149, 60) && !PoliticalOffice.PREFEITO.meetsRequirements(150, 59)
              && PoliticalOffice.PREFEITO.meetsRequirements(150, 60), "requisitos minimos");

        // Peso do voto
        int[][] w = {{0,1},{49,1},{50,2},{99,2},{100,3},{149,3},{150,4},{199,4},{200,5},{10000,5},{-5,1}};
        ok = true;
        for (int[] p : w) ok &= ElectionTally.voteWeight(p[0]) == p[1];
        check(ok, "tabela de peso do voto");
        check(ElectionTally.minimumWinningScore(PoliticalOffice.PREFEITO) == 90, "minimo prefeito = 90");

        // Candidato unico
        Candidate solo = cand("Solo", 60);
        var r = ElectionTally.decide(PoliticalOffice.PREFEITO, List.of(solo), List.of());
        check(r.outcome()==ElectionTally.Outcome.BELOW_MINIMUM && r.bestScore()==60, "unico sem votos perde (60 < 90)");
        r = ElectionTally.decide(PoliticalOffice.PREFEITO, List.of(cand("Solo", 70)), List.of(vote("v1", cand("Solo",70), 0)));
        check(r.outcome()==ElectionTally.Outcome.BELOW_MINIMUM && r.bestScore()==90, "unico com 90 nao supera 90");
        r = ElectionTally.decide(PoliticalOffice.PREFEITO, List.of(solo), List.of(vote("v1", solo, 0), vote("v2", solo, 0)));
        check(r.outcome()==ElectionTally.Outcome.WINNER && r.bestScore()==100, "unico com 2 votos vence (100)");

        // Dois candidatos, peso influencia
        Candidate a1 = cand("A", 100), b1 = cand("B", 80);
        r = ElectionTally.decide(PoliticalOffice.PREFEITO, List.of(a1, b1),
                List.of(vote("v1", b1, 0), vote("v2", b1, 0)));
        check(r.outcome()==ElectionTally.Outcome.WINNER && r.winner().get().name().equals("B") && r.bestScore()==120, "votos favoraveis viram eleicao (B 120 x A 100)");
        r = ElectionTally.decide(PoliticalOffice.PREFEITO, List.of(a1, b1),
                List.of(vote("v1", b1, 0), vote("rico", a1, 200)));
        check(r.winner().get().name().equals("A") && r.bestScore()==200, "eleitor popular (peso 5) decide: A 200");
        // Exemplo do enunciado: 175 pontos
        Candidate y = cand("PlayerY", 75);
        r = ElectionTally.decide(PoliticalOffice.PREFEITO, List.of(y, cand("PlayerX", 60)), List.of(vote("v", y, 200)));
        check(r.winner().get().name().equals("PlayerY") && r.bestScore()==175, "PlayerY 175 pontos");
        // Dois abaixo do minimo
        r = ElectionTally.decide(PoliticalOffice.PREFEITO, List.of(cand("A",60), cand("B",61)), List.of());
        check(r.outcome()==ElectionTally.Outcome.BELOW_MINIMUM && r.bestScore()==61, "dois abaixo do minimo");
        // Multiplos: alcancar (>=) basta
        r = ElectionTally.decide(PoliticalOffice.PREFEITO, List.of(cand("A",90), cand("B",61)), List.of());
        check(r.outcome()==ElectionTally.Outcome.WINNER && r.bestScore()==90, "multiplos: 90 alcanca minimo 90");
        // Empate desfeito pela popularidade
        Candidate ta = cand("TA", 100), tb = cand("TB", 80);
        r = ElectionTally.decide(PoliticalOffice.PREFEITO, List.of(ta, tb), List.of(vote("v1", tb, 0)));
        check(r.outcome()==ElectionTally.Outcome.WINNER && r.winner().get().name().equals("TA"), "empate 100x100 -> maior popularidade");
        // Empate total
        Candidate e1 = cand("E1", 100), e2 = cand("E2", 100);
        r = ElectionTally.decide(PoliticalOffice.PREFEITO, List.of(e1, e2), List.of(vote("v1", e1, 0), vote("v2", e2, 0)));
        check(r.outcome()==ElectionTally.Outcome.TIE && r.involved().size()==2, "empate total sem vencedor");
        // Sem candidatos
        check(ElectionTally.decide(PoliticalOffice.VEREADOR, List.of(), List.of()).outcome()==ElectionTally.Outcome.NO_CANDIDATES, "sem candidatos");
        // Voto para candidato inexistente nao pontua
        var sc = ElectionTally.scores(List.of(a1), List.of(new Ballot(UUID.randomUUID(), UUID.randomUUID(), 5)));
        check(sc.size()==1 && sc.get(a1.id())==100, "voto em candidato inexistente ignorado no calculo");

        // ElectionRace: voto unico
        ElectionRace race = new ElectionRace(PoliticalOffice.VEREADOR);
        race.candidates().add(a1);
        UUID voter = UUID.randomUUID();
        check(!race.hasVoted(voter), "ainda nao votou");
        race.ballots().add(new Ballot(voter, a1.id(), 1));
        check(race.hasVoted(voter), "votou -> hasVoted");
        check(race.findCandidateByName("a").isPresent(), "busca por nome case-insensitive");

        System.out.println(passed + " passed, " + failures + " failed");
        System.exit(failures == 0 ? 0 : 1);
    }
}
