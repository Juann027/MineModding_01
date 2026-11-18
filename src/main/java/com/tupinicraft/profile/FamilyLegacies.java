package com.tupinicraft.profile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catalogo do Family Legacy (Etapa 4.7). <b>Ainda nao finalizado</b>: nenhuma familia esta cadastrada e
 * {@link #CATALOG_FINALIZED} e {@code false}, entao {@link #isLive()} e falso e a roleta de nascimento continua
 * exatamente como antes (sem raridade, sem campo novo na ficha). Nao invente familias: cadastre aqui so as
 * aprovadas e so depois ligue a flag.
 *
 * <p>Um catalogo e imutavel e validado (ids unicos). Os testes montam catalogos proprios com {@link #of}. Puro.
 */
public final class FamilyLegacies {
    /** Liga o sorteio na roleta ao vivo. So mude para {@code true} quando o catalogo aprovado estiver completo. */
    public static final boolean CATALOG_FINALIZED = false;

    /** Familias aprovadas (nenhuma ainda). */
    public static final FamilyLegacies CATALOG = of(List.of());

    private final List<FamilyLegacy> all;
    private final Map<FamilyLegacyRarity, List<FamilyLegacy>> byRarity;

    private FamilyLegacies(List<FamilyLegacy> all) {
        this.all = List.copyOf(all);
        Map<FamilyLegacyRarity, List<FamilyLegacy>> map = new EnumMap<>(FamilyLegacyRarity.class);
        for (FamilyLegacyRarity rarity : FamilyLegacyRarity.values()) map.put(rarity, new ArrayList<>());
        for (FamilyLegacy family : this.all) map.get(family.rarity()).add(family);
        map.replaceAll((rarity, list) -> List.copyOf(list));
        this.byRarity = map;
    }

    public static FamilyLegacies of(List<FamilyLegacy> families) {
        Set<String> ids = new HashSet<>();
        for (FamilyLegacy family : families) {
            if (!ids.add(family.id())) throw new IllegalArgumentException("duplicate family legacy id: " + family.id());
        }
        return new FamilyLegacies(families);
    }

    /** O sorteio so vale na roleta quando o catalogo foi finalizado e tem familias. */
    public static boolean isLive() {
        return CATALOG_FINALIZED && !CATALOG.isEmpty();
    }

    public List<FamilyLegacy> all() { return all; }
    public boolean isEmpty() { return all.isEmpty(); }
    public List<FamilyLegacy> inRarity(FamilyLegacyRarity rarity) { return byRarity.get(rarity); }

    /** Familias da raridade que o corpo pode receber, na ordem do cadastro. */
    public List<FamilyLegacy> eligible(FamilyLegacyRarity rarity, BirthTrait.Body body) {
        List<FamilyLegacy> out = new ArrayList<>();
        for (FamilyLegacy family : inRarity(rarity)) if (family.eligibleFor(body)) out.add(family);
        return out;
    }

    /** Null se o id nao existe. */
    public FamilyLegacy byId(String id) {
        for (FamilyLegacy family : all) if (family.id().equals(id)) return family;
        return null;
    }
}
