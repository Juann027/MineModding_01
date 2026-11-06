package com.tupinicraft;

import com.tupinicraft.config.*;
import com.tupinicraft.client.AnnouncementLayout;
import com.tupinicraft.client.AnnouncementLayout.Rect;
import com.tupinicraft.election.*;
import com.tupinicraft.mandate.*;
import com.tupinicraft.origin.*;
import com.tupinicraft.politics.*;
import com.tupinicraft.territory.*;
import java.util.*;
import com.tupinicraft.player.OfficeProgress;
import com.tupinicraft.player.SkillProgression;

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
    static Candidate cand(String n, int pop) { return Candidate.player(UUID.nameUUIDFromBytes(n.getBytes()), n, pop); }
    static Ballot vote(String voter, Candidate c, int voterPop, ElectionRules rules) { return new Ballot(UUID.nameUUIDFromBytes(voter.getBytes()), c.id(), rules.voteWeight(voterPop)); }

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
        for (int i = 0; i < 7; i++) ok &= order[i+1].defaultMinPoliticalXp()==req[i][0] && order[i+1].defaultReferencePopularity()==req[i][1];
        check(ok, "XP minimo e popularidade de referencia padrao dos cargos");
        check(PoliticalOffice.PRESIDENTE.next().isEmpty(), "presidente sem proximo");
        check(PoliticalOffice.DESEMPREGADO.next().orElseThrow()==PoliticalOffice.VEREADOR, "desempregado->vereador");
        check(PoliticalOffice.byId("DEPUTADO_ESTADUAL").orElseThrow()==PoliticalOffice.DEPUTADO_ESTADUAL, "byId");
        check(PoliticalOffice.SENADOR.defaultTermMinutes()==45 && PoliticalOffice.PREFEITO.defaultTermMinutes()==30, "mandato padrao 30/45 (senador)");
        check(PoliticalOffice.PRESIDENTE.jurisdiction()==TerritoryType.COUNTRY && PoliticalOffice.GOVERNADOR.jurisdiction()==TerritoryType.STATE
              && PoliticalOffice.VEREADOR.jurisdiction()==TerritoryType.MUNICIPALITY, "jurisdicao dos cargos");
        check(PoliticalOffice.SENADOR.isGeneral() && PoliticalOffice.PREFEITO.isMunicipal() && !PoliticalOffice.PREFEITO.isGeneral(), "calendario geral x municipal");

        // Configuracoes (definicoes e validacao)
        check(ConfigKeys.byPath("mandato.senador").orElseThrow().defaultValue()==45, "config mandato.senador = 45");
        check(ConfigKeys.byPath("mandato.vereador").orElseThrow().defaultValue()==30, "config mandato.vereador = 30");
        check(ConfigKeys.byPath("eleicao.intervalo_gerais").isPresent() && ConfigKeys.byPath("skills.minutos_por_ponto").isPresent(), "chaves dos exemplos existem");
        check(ConfigKeys.byPath("progressao.xp_min.prefeito").orElseThrow().defaultValue()==150, "xp_min.prefeito = 150");
        check(ConfigKeys.byPath("progressao.popularidade_min.prefeito").isEmpty()
              && ConfigKeys.byPath("eleicao.popularidade_referencia.prefeito").orElseThrow().defaultValue()==60,
              "popularidade nao e requisito de candidatura (so referencia da pontuacao minima)");
        check(ConfigKeys.byPath("nao.existe").isEmpty(), "chave inexistente");
        Set<String> paths = new HashSet<>();
        ok = true;
        for (ConfigKey k : ConfigKeys.all()) ok &= paths.add(k.path()) && k.isValid(k.defaultValue()) && k.path().matches("[a-z_]+(\\.[a-z_]+)+");
        check(ok && paths.size() >= 50, "chaves unicas, padroes validos (" + paths.size() + " chaves)");
        ConfigKey senador = ConfigKeys.byPath("mandato.senador").orElseThrow();
        check(senador.parse("60").getAsInt()==60, "parse valido");
        check(senador.parse("0").isEmpty() && senador.parse("-5").isEmpty() && senador.parse("abc").isEmpty()
              && senador.parse("99999999").isEmpty() && senador.parse("").isEmpty(), "valores invalidos rejeitados");
        ConfigKey auto = ConfigKeys.AUTO_ELECTIONS;
        check(auto.parse("false").getAsInt()==0 && auto.parse("sim").getAsInt()==1 && auto.parse("talvez").isEmpty(), "parse booleano");
        check(ConfigKeys.pairedMax(ConfigKeys.NPC_SUPPORT_MIN).orElseThrow()==ConfigKeys.NPC_SUPPORT_MAX
              && ConfigKeys.pairedMin(ConfigKeys.NPC_POPULARITY_MAX.get(PoliticalOffice.PREFEITO)).orElseThrow()==ConfigKeys.NPC_POPULARITY_MIN.get(PoliticalOffice.PREFEITO), "pares min/max");

        // Territorios
        check(Territories.all().size()==1+27+27, "brasil + 27 UFs + 27 capitais");
        Set<String> ids = new HashSet<>();
        ok = true;
        for (Territory t : Territories.all()) ok &= ids.add(t.id()) && t.id().matches("tupinicraft:[a-z_]+");
        check(ok, "IDs de territorio unicos e no formato estavel");
        check(Territories.get("tupinicraft:es").orElseThrow().name().equals("Espírito Santo"), "tupinicraft:es");
        check(Territories.get("vitoria").orElseThrow().parent().orElseThrow().equals("tupinicraft:es"), "vitoria -> es");
        check(Territories.get("tupinicraft:cuiaba").orElseThrow().uf().orElseThrow()==RegionalOrigin.MT, "cuiaba -> MT");
        check(Territories.brasil().type()==TerritoryType.COUNTRY && Territories.brasil().parent().isEmpty(), "brasil e pais raiz");
        ok = true;
        for (Territory t : Territories.ofType(TerritoryType.STATE)) ok &= Territories.children(t.id()).size()==1;
        check(ok, "cada estado tem 1 municipio cadastrado");
        check(Territories.domicile(PoliticalOffice.PREFEITO, RegionalOrigin.SP).id().equals("tupinicraft:sao_paulo")
              && Territories.domicile(PoliticalOffice.GOVERNADOR, RegionalOrigin.SP).id().equals("tupinicraft:sp")
              && Territories.domicile(PoliticalOffice.PRESIDENTE, RegionalOrigin.SP).id().equals("tupinicraft:brasil"), "domicilio eleitoral");

        // Peso do voto (regras padrao)
        ElectionRules rules = new ElectionRules(20, 50, 5, true, 2, 2, 0, 3, 1200, 300);
        int[][] w = {{0,1},{49,1},{50,2},{99,2},{100,3},{149,3},{150,4},{199,4},{200,5},{10000,5},{-5,1}};
        ok = true;
        for (int[] p : w) ok &= rules.voteWeight(p[0]) == p[1];
        check(ok, "tabela de peso do voto");
        ElectionRules custom = new ElectionRules(10, 100, 3, true, 2, 2, 0, 3, 1200, 300);
        check(custom.voteWeight(250)==3 && custom.voteWeight(150)==2 && custom.voteWeight(10000)==3, "divisor e peso maximo configuraveis");
        int min = 60 + 30;

        // Candidato unico
        Candidate solo = cand("Solo", 60);
        var r = ElectionTally.decide(min, List.of(solo), List.of(), 20);
        check(r.outcome()==ElectionTally.Outcome.BELOW_MINIMUM && r.bestScore()==60, "unico sem votos perde (60 < 90)");
        Candidate solo70 = cand("Solo", 70);
        r = ElectionTally.decide(min, List.of(solo70), List.of(vote("v1", solo70, 0, rules)), 20);
        check(r.outcome()==ElectionTally.Outcome.BELOW_MINIMUM && r.bestScore()==90, "unico com 90 nao supera 90");
        r = ElectionTally.decide(min, List.of(solo), List.of(vote("v1", solo, 0, rules), vote("v2", solo, 0, rules)), 20);
        check(r.outcome()==ElectionTally.Outcome.WINNER && r.bestScore()==100, "unico com 2 votos vence (100)");

        // Dois candidatos, peso influencia
        Candidate a1 = cand("A", 100), b1 = cand("B", 80);
        r = ElectionTally.decide(min, List.of(a1, b1), List.of(vote("v1", b1, 0, rules), vote("v2", b1, 0, rules)), 20);
        check(r.outcome()==ElectionTally.Outcome.WINNER && r.winner().get().name().equals("B") && r.bestScore()==120, "votos favoraveis viram eleicao (B 120 x A 100)");
        r = ElectionTally.decide(min, List.of(a1, b1), List.of(vote("v1", b1, 0, rules), vote("rico", a1, 200, rules)), 20);
        check(r.winner().get().name().equals("A") && r.bestScore()==200, "eleitor popular (peso 5) decide: A 200");
        Candidate y = cand("PlayerY", 75);
        r = ElectionTally.decide(min, List.of(y, cand("PlayerX", 60)), List.of(vote("v", y, 200, rules)), 20);
        check(r.winner().get().name().equals("PlayerY") && r.bestScore()==175, "PlayerY 175 pontos");
        r = ElectionTally.decide(min, List.of(y, cand("PlayerX", 60)), List.of(vote("v", y, 200, rules)), 10);
        check(r.bestScore()==125, "multiplicador configuravel (10 -> 125)");
        r = ElectionTally.decide(min, List.of(cand("A",60), cand("B",61)), List.of(), 20);
        check(r.outcome()==ElectionTally.Outcome.BELOW_MINIMUM && r.bestScore()==61, "dois abaixo do minimo");
        r = ElectionTally.decide(min, List.of(cand("A",90), cand("B",61)), List.of(), 20);
        check(r.outcome()==ElectionTally.Outcome.WINNER && r.bestScore()==90, "multiplos: 90 alcanca minimo 90");
        Candidate ta = cand("TA", 100), tb = cand("TB", 80);
        r = ElectionTally.decide(min, List.of(ta, tb), List.of(vote("v1", tb, 0, rules)), 20);
        check(r.outcome()==ElectionTally.Outcome.WINNER && r.winner().get().name().equals("TA"), "empate 100x100 -> maior popularidade");
        Candidate e1 = cand("E1", 100), e2 = cand("E2", 100);
        r = ElectionTally.decide(min, List.of(e1, e2), List.of(vote("v1", e1, 0, rules), vote("v2", e2, 0, rules)), 20);
        check(r.outcome()==ElectionTally.Outcome.TIE && r.involved().size()==2, "empate total sem vencedor");
        check(ElectionTally.decide(40, List.of(), List.of(), 20).outcome()==ElectionTally.Outcome.NO_CANDIDATES, "sem candidatos");
        var sc = ElectionTally.scores(List.of(a1), List.of(new Ballot(UUID.randomUUID(), UUID.randomUUID(), 5)), 20);
        check(sc.size()==1 && sc.get(a1.id())==100, "voto em candidato inexistente ignorado no calculo");

        // NPCs
        check(NpcCandidateFactory.npcsNeeded(rules, 1)==1 && NpcCandidateFactory.npcsNeeded(rules, 0)==2
              && NpcCandidateFactory.npcsNeeded(rules, 3)==0, "quantidade de NPCs para completar o minimo");
        ElectionRules noVillagers = new ElectionRules(20, 50, 5, false, 2, 2, 0, 3, 1200, 300);
        check(NpcCandidateFactory.npcsNeeded(noVillagers, 0)==0, "aldeoes desativados -> nenhum NPC");
        ElectionRules maxOne = new ElectionRules(20, 50, 5, true, 4, 1, 0, 3, 1200, 300);
        check(NpcCandidateFactory.npcsNeeded(maxOne, 0)==1, "respeita maximo de NPCs por disputa");
        List<Candidate> npcs = NpcCandidateFactory.create(5, 60, 140, rules, List.of("Zé do Trigo"), new Random(42));
        ok = npcs.size()==5;
        Set<String> names = new HashSet<>();
        for (Candidate c : npcs) ok &= c.npc() && c.popularity()>=60 && c.popularity()<=140 && c.simulatedSupport()>=0
                && c.simulatedSupport()<=3 && names.add(c.name()) && !c.name().equals("Zé do Trigo");
        check(ok, "NPCs gerados dentro das faixas, nomes unicos");
        Candidate npc = new Candidate(UUID.randomUUID(), "NPC", 70, true, 2);
        check(ElectionTally.scores(List.of(npc), List.of(), 20).get(npc.id())==110, "apoio simulado do NPC soma 20 x 2");

        // ElectionRace
        ElectionRace race = new ElectionRace(new SeatKey("tupinicraft:sao_paulo", PoliticalOffice.VEREADOR, 1), 40, 10, 40);
        race.candidates().add(a1);
        UUID voter = UUID.randomUUID();
        check(!race.hasVoted(voter), "ainda nao votou");
        race.ballots().add(new Ballot(voter, a1.id(), 1));
        check(race.hasVoted(voter), "votou -> hasVoted");
        check(race.findCandidateByName("a").isPresent(), "busca por nome case-insensitive");

        // ---------------------------------------------------------------- candidatura direta (sem progressao)
        int presidentXp = ConfigKeys.MIN_POLITICAL_XP.get(PoliticalOffice.PRESIDENTE).defaultValue();
        check(presidentXp == 1200, "XP minimo padrao de Presidente = 1200");
        // Jogador Desempregado, popularidade 0, XP suficiente: pode concorrer DIRETAMENTE a Presidente.
        check(CandidacyRules.check(true, 1200, presidentXp, false, true).isEmpty(),
              "Desempregado com XP 1200 e popularidade 0 pode se candidatar direto a Presidente");
        check(CandidacyRules.check(true, 1199, presidentXp, false, true).orElseThrow()==CandidacyRules.Problem.INSUFFICIENT_XP,
              "Desempregado com XP 1199 nao pode se candidatar a Presidente (XP e o unico requisito)");
        ok = true;
        for (PoliticalOffice office : PoliticalOffice.values()) {
            if (!office.isElective()) continue;
            int minXp = ConfigKeys.MIN_POLITICAL_XP.get(office).defaultValue();
            ok &= CandidacyRules.check(true, minXp, minXp, false, true).isEmpty();
        }
        check(ok, "qualquer cargo e acessivel diretamente com o XP minimo (sem cargo anterior)");
        check(CandidacyRules.check(false, 5000, presidentXp, false, true).orElseThrow()==CandidacyRules.Problem.OUTSIDE_DOMICILE,
              "fora do domicilio eleitoral e recusado");
        check(CandidacyRules.check(true, 5000, presidentXp, true, false).orElseThrow()==CandidacyRules.Problem.REELECTION_DISABLED
              && CandidacyRules.check(true, 5000, presidentXp, true, true).isEmpty(), "reeleicao consecutiva configuravel");
        // Desempregado (popularidade 0) vence a Presidencia contra um NPC so com votos: popularidade so pesa no resultado.
        int presMin = ConfigKeys.REFERENCE_POPULARITY.get(PoliticalOffice.PRESIDENTE).defaultValue()
                + ConfigKeys.WIN_MARGIN.defaultValue();
        Candidate desempregado = cand("Desempregado", 0);
        Candidate npcPres = new Candidate(UUID.randomUUID(), "NPC", 350, true, 0);
        List<Ballot> manyVotes = new ArrayList<>();
        for (int i = 0; i < 4; i++) manyVotes.add(vote("eleitor" + i, desempregado, 200, rules));
        r = ElectionTally.decide(presMin, List.of(desempregado, npcPres), manyVotes, 20);
        check(r.outcome()==ElectionTally.Outcome.WINNER && r.winner().get().name().equals("Desempregado") && r.bestScore()==400,
              "Desempregado com popularidade 0 vence Presidente pelos votos (400 x 350)");
        r = ElectionTally.decide(presMin, List.of(desempregado, npcPres), List.of(), 20);
        check(r.outcome()==ElectionTally.Outcome.BELOW_MINIMUM, "sem votos, popularidade baixa nao alcanca o minimo (popularidade influencia o resultado)");

        // ---------------------------------------------------------------- habilidades por cargo
        Map<PoliticalOffice, OfficeProgress> progress = Map.of();
        for (int i = 0; i < 120; i++) progress = SkillProgression.addOnlineSecond(progress, PoliticalOffice.VEREADOR, 60).progress();
        check(SkillProgression.skillPoints(progress, PoliticalOffice.VEREADOR)==2, "2 min de mandato de Vereador = 2 pontos");
        for (int i = 0; i < 90; i++) progress = SkillProgression.addOnlineSecond(progress, PoliticalOffice.PREFEITO, 60).progress();
        check(SkillProgression.skillPoints(progress, PoliticalOffice.PREFEITO)==1 && progress.get(PoliticalOffice.PREFEITO).onlineSeconds()==30,
              "pontos de Prefeito sao separados dos de Vereador");
        check(SkillProgression.skillPoints(progress, PoliticalOffice.VEREADOR)==2, "pontos de Vereador mantidos apos mandato em outro cargo");
        for (int i = 0; i < 60; i++) progress = SkillProgression.addOnlineSecond(progress, PoliticalOffice.VEREADOR, 60).progress();
        check(SkillProgression.skillPoints(progress, PoliticalOffice.VEREADOR)==3, "ao voltar a Vereador os pontos anteriores continuam e somam");
        SkillProgression.Tick tick = SkillProgression.addOnlineSecond(Map.of(), PoliticalOffice.SENADOR, 1);
        check(tick.gainedPoint() && tick.progress().get(PoliticalOffice.SENADOR).skillPoints()==1, "tick sinaliza ponto ganho");

        // ---------------------------------------------------------------- NPCs obedecem a configuracao
        ok = true;
        Random rnd = new Random(7);
        ElectionRules npcRules = new ElectionRules(20, 50, 5, true, 2, 2, 1, 4, 1200, 300);
        for (int i = 0; i < 500; i++) {
            for (Candidate c : NpcCandidateFactory.create(2, 60, 140, npcRules, List.of(), rnd)) {
                ok &= c.npc() && c.popularity() >= 60 && c.popularity() <= 140 && c.simulatedSupport() >= 1 && c.simulatedSupport() <= 4;
            }
        }
        check(ok, "1000 NPCs: popularidade e apoio sempre dentro das faixas configuradas");
        Candidate fixed = NpcCandidateFactory.create(1, 77, 77, new ElectionRules(20, 50, 5, true, 2, 2, 3, 3, 1200, 300), List.of(), rnd).get(0);
        check(fixed.popularity()==77 && fixed.simulatedSupport()==3, "faixa fixa (min = max) respeitada exatamente");
        ElectionRace npcRace = new ElectionRace(new SeatKey("tupinicraft:sp", PoliticalOffice.GOVERNADOR, 1), 270, 500, 100);
        check(npcRace.npcPopularityMin()==500 && npcRace.npcPopularityMax()==500, "faixa invertida e normalizada (max >= min)");

        // Mandatos
        MandateRegistry registry = new MandateRegistry();
        SeatKey gov = new SeatKey("tupinicraft:es", PoliticalOffice.GOVERNADOR, 1);
        UUID alice = UUID.randomUUID();
        Mandate m1 = new Mandate(Occupant.player(alice, "Alice"), 0, 1000, Optional.empty(), Optional.of(UUID.randomUUID()));
        check(registry.inaugurate(gov, m1, 0, true), "posse em cadeira vaga");
        Mandate m2 = new Mandate(Occupant.npc(UUID.randomUUID(), "Zé"), 10, 2000, Optional.empty(), Optional.empty());
        check(!registry.inaugurate(gov, m2, 10, true), "cadeira nao aceita dois ocupantes simultaneos");
        MandateQueries q = new MandateQueries(registry, 500, true);
        check(q.getCurrentOccupant(gov).orElseThrow().id().equals(alice) && q.getRemainingTerm(gov).getAsLong()==500, "ocupante e tempo restante");
        check(q.getPlayerMandates().size()==1 && q.getNpcMandates().isEmpty(), "consulta de mandatos de jogadores");
        check(q.getSeats("tupinicraft:es").size()==4 && q.getVacantSeats("es").size()==3 && q.getOccupiedSeats("es").size()==1, "cadeiras do estado (4 cargos)");
        check(q.getSeats("tupinicraft:brasil").size()==1 && q.getSeats("tupinicraft:vitoria").size()==2, "cadeiras do pais e do municipio");
        check(q.getSeat("tupinicraft:es", PoliticalOffice.PREFEITO, 1).isEmpty(), "prefeito nao existe em estado");
        MandateQueries expired = new MandateQueries(registry, 1000, true);
        check(expired.getCurrentOccupant(gov).isEmpty() && expired.view(gov).isVacant() && expired.getRemainingTerm(gov).isEmpty(), "mandato expirado aparece vago");
        MandateQueries noExpiry = new MandateQueries(registry, 5000, false);
        check(noExpiry.getCurrentOccupant(gov).isPresent(), "sem expiracao automatica o mandato continua");
        check(registry.inaugurate(gov, m2, 1000, true) && registry.lastOccupant(gov).orElseThrow().equals(m2.occupant().id()), "posse apos expiracao");
        MandateQueries q2 = new MandateQueries(registry, 1500, true);
        check(q2.getNpcMandates().size()==1 && q2.view(gov).occupantType()==OccupantType.NPC && q2.view(gov).npcId().isPresent()
              && q2.view(gov).playerId().isEmpty(), "NPC consultado como ocupante");
        registry.vacate(gov);
        check(registry.lastOccupant(gov).orElseThrow().equals(m2.occupant().id()) && q2.view(gov).isVacant(), "vacancia guarda ultimo ocupante");
        PoliticalMapSnapshot snap = q2.snapshot(List.of(Territories.brasil(), Territories.get("es").orElseThrow()));
        check(snap.territories().size()==2 && snap.seats().size()==5, "snapshot do mapa");
        boolean immutable;
        try { snap.seats().clear(); immutable = false; } catch (UnsupportedOperationException ex) { immutable = true; }
        check(immutable, "snapshot somente leitura");
        boolean rejected;
        try { new SeatKey("tupinicraft:es", PoliticalOffice.DESEMPREGADO, 1); rejected = false; } catch (IllegalArgumentException ex) { rejected = true; }
        check(rejected, "Desempregado nao tem cadeira");

        // Uma cadeira vigente por jogador
        MandateRegistry one = new MandateRegistry();
        UUID bob = UUID.randomUUID();
        SeatKey depPi = new SeatKey("tupinicraft:pi", PoliticalOffice.DEPUTADO_ESTADUAL, 1);
        SeatKey prefTer = new SeatKey("tupinicraft:teresina", PoliticalOffice.PREFEITO, 1);
        check(one.inaugurate(depPi, new Mandate(Occupant.player(bob, "Bob"), 0, 1000, Optional.empty(), Optional.empty()), 0, true),
              "Bob toma posse como Deputado Estadual");
        check(!one.inaugurate(prefTer, new Mandate(Occupant.player(bob, "Bob"), 10, 1000, Optional.empty(), Optional.empty()), 10, true),
              "Bob nao pode ocupar duas cadeiras vigentes ao mesmo tempo");
        check(one.activeSeatOf(bob, 10, true).orElseThrow().equals(depPi), "cadeira vigente de Bob");
        one.vacate(depPi);
        check(one.inaugurate(prefTer, new Mandate(Occupant.player(bob, "Bob"), 20, 1000, Optional.empty(), Optional.empty()), 20, true),
              "apos encerrar a anterior, Bob pode assumir outra cadeira");
        check(one.inaugurate(depPi, new Mandate(Occupant.npc(UUID.randomUUID(), "NPC"), 30, 1000, Optional.empty(), Optional.empty()), 30, true),
              "NPCs nao sao limitados pela regra de cadeira por jogador");
        check(one.activeSeatOf(bob, 2000, true).isEmpty(), "mandato expirado nao conta como cadeira vigente");

        // ---------------------------------------------------------------- dados da v0.1.0 preservados
        // NBT exatamente como a v0.1.0 gravava o attachment tupinicraft:political_data.
        net.minecraft.nbt.CompoundTag legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putString("origin", "MA");
        legacy.putString("office", "vereador");
        legacy.putInt("political_xp", 60);
        legacy.putInt("popularity", 75);
        com.tupinicraft.player.PoliticalData loaded = com.tupinicraft.player.PoliticalData.CODEC
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, legacy).getOrThrow();
        check(loaded.origin().orElseThrow()==RegionalOrigin.MA && loaded.office()==PoliticalOffice.VEREADOR
              && loaded.politicalXp()==60 && loaded.popularity()==75 && loaded.officeProgress().isEmpty(),
              "dados da v0.1.0 (origem, cargo, XP, popularidade) carregam sem perda");
        net.minecraft.nbt.Tag saved = com.tupinicraft.player.PoliticalData.CODEC
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, loaded.withOfficeProgress(Map.of(PoliticalOffice.PREFEITO, new OfficeProgress(3, 42))))
                .getOrThrow();
        com.tupinicraft.player.PoliticalData roundTrip = com.tupinicraft.player.PoliticalData.CODEC
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow();
        net.minecraft.nbt.CompoundTag savedTag = (net.minecraft.nbt.CompoundTag) saved;
        check(roundTrip.skillPoints(PoliticalOffice.PREFEITO)==3 && roundTrip.officeProgress().get(PoliticalOffice.PREFEITO).onlineSeconds()==42
              && savedTag.getString("office").equals("vereador") && savedTag.getInt("political_xp")==60,
              "novo formato salva/carrega progresso por cargo e mantem os campos da v0.1.0");
        net.minecraft.nbt.CompoundTag weird = legacy.copy();
        weird.putString("office", "imperador");
        weird.putString("origin", "XX");
        com.tupinicraft.player.PoliticalData tolerant = com.tupinicraft.player.PoliticalData.CODEC
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, weird).getOrThrow();
        check(tolerant.office()==PoliticalOffice.DESEMPREGADO && tolerant.origin().isEmpty() && tolerant.politicalXp()==60,
              "valores desconhecidos nao apagam o resto dos dados");
        layoutTests();

        System.out.println(passed + " passed, " + failures + " failed");
        System.exit(failures == 0 ? 0 : 1);
    }

    // Faixa de avisos: fonte simulada de 6 px por caractere, 9 px por linha, padding 5.
    static AnnouncementLayout.Measure text(int chars) {
        return maxWidth -> {
            int perLine = Math.max(1, maxWidth / 6);
            int lines = (chars + perLine - 1) / perLine;
            return new int[]{Math.min(chars, perLine) * 6 + 10, lines * 9 + 10};
        };
    }
    static boolean clear(Rect box, List<Rect> obstacles) {
        for (Rect o : obstacles) if (box.intersects(o, 0)) return false;
        return true;
    }

    static void layoutTests() {
        // 1280x720 com guiScale 2 -> GUI 640x360; aviso longo (~56 caracteres).
        AnnouncementLayout.Measure longText = text(56);
        Rect none = AnnouncementLayout.place(640, 360, 0, List.of(), 280, 5, longText).box();
        check(none.top() == 60 && Math.abs((none.left() + none.right()) - 640) <= 1, "faixa centralizada sem obstaculos");

        List<Rect> panels = List.of(new Rect(478, 2, 638, 32), new Rect(450, 34, 638, 80));
        AnnouncementLayout.Placement p = AnnouncementLayout.place(640, 360, 0, panels, 280, 5, longText);
        check(clear(p.box(), panels), "faixa nao cobre o painel da eleicao");
        check(p.box().top() == 60 && p.box().right() <= 450 - AnnouncementLayout.GAP, "faixa vai para a esquerda do painel");
        check(p.maxTextWidth() == 280, "largura cheia mantida quando cabe ao lado");

        Rect boss = AnnouncementLayout.place(640, 360, 74, List.of(), 280, 5, longText).box();
        check(boss.top() >= 74 + AnnouncementLayout.GAP, "faixa abaixo das barras de chefe");

        // Tela estreita: nao sobra largura legivel ao lado do painel -> desce para baixo dele.
        List<Rect> wide = List.of(new Rect(120, 2, 318, 90));
        Rect below = AnnouncementLayout.place(320, 240, 0, wide, 280, 5, longText).box();
        check(clear(below, wide) && below.top() >= 90 + AnnouncementLayout.GAP, "faixa abaixo do painel sem espaco lateral");

        // Aleatorio: nunca cobre painel algum e nunca sai da tela na horizontal.
        Random random = new Random(42);
        boolean ok = true;
        for (int i = 0; i < 2000 && ok; i++) {
            int gw = 200 + random.nextInt(800), gh = 150 + random.nextInt(400);
            List<Rect> obs = new ArrayList<>();
            int y = 2;
            for (int k = random.nextInt(3); k > 0; k--) {
                int w = 40 + random.nextInt(gw / 2), h = 12 + random.nextInt(gh / 3);
                obs.add(new Rect(gw - 2 - w, y, gw - 2, y + h));
                y += h + 2;
            }
            Rect b = AnnouncementLayout.place(gw, gh, random.nextInt(3) * 19, obs, 280, 5, text(5 + random.nextInt(120))).box();
            ok = clear(b, obs) && b.left() >= 0 && b.right() <= gw;
            if (!ok) System.out.println("  caso " + i + ": " + gw + "x" + gh + " " + obs + " -> " + b);
        }
        check(ok, "faixa nunca sobrepoe paineis (aleatorio)");
    }
}
