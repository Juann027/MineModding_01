package com.tupinicraft.election;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Gera candidatos aldeões (NPCs) para completar disputas. Puro e determinístico dado o gerador aleatório.
 * Os nomes são fictícios e não fazem referência a pessoas reais.
 */
public final class NpcCandidateFactory {
    static final String[] NAMES = {
            "Zé do Trigo", "Dona Esmeralda", "Tião da Feira", "Coronel Aldeão", "Doutor Hmmilton",
            "Dona Cenoura", "Juca Bibliotecário", "Ferreiro Raimundo", "Tia do Pão de Queijo", "Seu Batata",
            "Professora Beterraba", "Pescador Valdir", "Dona Lã", "Cartógrafo Aníbal", "Seu Lázaro Pedreiro",
            "Pastora Ovelhinha", "Flecheiro Osmar", "Açougueiro Genival", "Dona Abóbora", "Seu Hmm Júnior"
    };

    private NpcCandidateFactory() {
    }

    /**
     * Quantos NPCs devem entrar numa disputa com {@code currentCandidates} inscritos.
     * Completa até o mínimo de candidatos, sem passar do máximo de NPCs.
     */
    public static int npcsNeeded(ElectionRules rules, int currentCandidates) {
        if (!rules.villagersEnabled()) {
            return 0;
        }
        return Math.max(0, Math.min(rules.maxNpcCandidates(), rules.minCandidates() - currentCandidates));
    }

    /**
     * Cria {@code count} candidatos NPC com popularidade em [popMin, popMax] e apoio em
     * [apoio_min, apoio_max]. Evita repetir nomes já usados na eleição.
     */
    public static List<Candidate> create(int count, int popMin, int popMax, ElectionRules rules,
                                         Collection<String> namesInUse, RandomGenerator random) {
        Set<String> used = new HashSet<>(namesInUse);
        List<Candidate> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = pickName(used, random);
            used.add(name);
            int popularity = between(popMin, popMax, random);
            int support = between(rules.npcSupportMin(), rules.npcSupportMax(), random);
            UUID id = new UUID(random.nextLong(), random.nextLong());
            created.add(new Candidate(id, name, popularity, true, support));
        }
        return created;
    }

    private static String pickName(Set<String> used, RandomGenerator random) {
        List<String> free = new ArrayList<>();
        for (String name : NAMES) {
            if (!used.contains(name)) {
                free.add(name);
            }
        }
        if (!free.isEmpty()) {
            return free.get(random.nextInt(free.size()));
        }
        String base = NAMES[random.nextInt(NAMES.length)];
        int suffix = 2;
        while (used.contains(base + " " + suffix)) {
            suffix++;
        }
        return base + " " + suffix;
    }

    private static int between(int min, int max, RandomGenerator random) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return low + random.nextInt(high - low + 1);
    }
}
