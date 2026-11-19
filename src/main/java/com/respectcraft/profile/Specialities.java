package com.respectcraft.profile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Catalogo de Specialities usado pelo sorteio: lista imutavel agrupada por raridade. O catalogo oficial
 * ({@link #CATALOG}) e o enum {@link Speciality} inteiro; os testes montam catalogos parciais com {@link #of} para
 * provar que uma raridade vazia nao e redistribuida. Puro.
 */
public final class Specialities {
    /** As 15 Specialities aprovadas. */
    public static final Specialities CATALOG = of(List.of(Speciality.values()));

    private final List<Speciality> all;
    private final Map<SpecialityRarity, List<Speciality>> byRarity;

    private Specialities(List<Speciality> all) {
        this.all = List.copyOf(all);
        Map<SpecialityRarity, List<Speciality>> map = new EnumMap<>(SpecialityRarity.class);
        for (SpecialityRarity rarity : SpecialityRarity.values()) map.put(rarity, new ArrayList<>());
        for (Speciality speciality : this.all) map.get(speciality.rarity()).add(speciality);
        map.replaceAll((rarity, list) -> List.copyOf(list));
        this.byRarity = map;
    }

    /** Catalogo com as Specialities dadas, na ordem dada (duplicatas sao recusadas). */
    public static Specialities of(List<Speciality> specialities) {
        if (new LinkedHashSet<>(specialities).size() != specialities.size()) {
            throw new IllegalArgumentException("duplicate speciality in catalog");
        }
        return new Specialities(specialities);
    }

    public List<Speciality> all() { return all; }
    public boolean isEmpty() { return all.isEmpty(); }
    public List<Speciality> inRarity(SpecialityRarity rarity) { return byRarity.get(rarity); }

    /** Specialities da raridade que o corpo pode receber, na ordem do catalogo. */
    public List<Speciality> eligible(SpecialityRarity rarity, BirthTrait.Body body) {
        List<Speciality> out = new ArrayList<>();
        for (Speciality speciality : inRarity(rarity)) if (speciality.eligibleFor(body)) out.add(speciality);
        return out;
    }
}
