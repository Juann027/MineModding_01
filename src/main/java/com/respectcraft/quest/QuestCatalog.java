package com.respectcraft.quest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Definicoes carregadas e o indice por gatilho (Etapa 4.14.2), imutavel e puro: um evento so olha as missoes cujos
 * objetivos ou condicoes de descoberta dependem dele. Trocado inteiro a cada reload ({@link QuestRegistry}).
 */
public final class QuestCatalog {
    public static final QuestCatalog EMPTY = new QuestCatalog(List.of(), List.of());

    private final Map<ResourceLocation, QuestDef> byId = new LinkedHashMap<>();
    private final Map<QuestTrigger, List<QuestDef>> byObjective = new EnumMap<>(QuestTrigger.class);
    private final Map<QuestTrigger, List<QuestDef>> byDiscovery = new EnumMap<>(QuestTrigger.class);
    /** Erros de carga ("id: mensagem"), para o diagnostico. */
    private final List<String> errors;

    public QuestCatalog(Collection<QuestDef> defs, List<String> errors) {
        for (QuestTrigger t : QuestTrigger.values()) {
            byObjective.put(t, new ArrayList<>());
            byDiscovery.put(t, new ArrayList<>());
        }
        List<QuestDef> sorted = new ArrayList<>(defs);
        sorted.sort((a, b) -> a.sort() != b.sort() ? Integer.compare(a.sort(), b.sort()) : a.id().compareTo(b.id()));
        for (QuestDef d : sorted) {
            byId.put(d.id(), d);
            Set<QuestTrigger> obj = EnumSet.noneOf(QuestTrigger.class);
            for (QuestObjective o : d.objectives()) if (o.behavior() != null) obj.add(o.behavior().trigger());
            for (QuestTrigger t : obj) byObjective.get(t).add(d);
            if (d.discover().isPresent()) {
                Set<QuestTrigger> disc = EnumSet.noneOf(QuestTrigger.class);
                d.discover().get().triggers(disc);
                for (QuestTrigger t : disc) byDiscovery.get(t).add(d);
            }
        }
        this.errors = List.copyOf(errors);
    }

    public QuestDef get(ResourceLocation id) { return id == null ? null : byId.get(id); }

    public Collection<QuestDef> all() { return Collections.unmodifiableCollection(byId.values()); }

    public List<QuestDef> objectiveCandidates(QuestTrigger t) { return byObjective.get(t); }

    public List<QuestDef> discoveryCandidates(QuestTrigger t) { return byDiscovery.get(t); }

    public List<String> errors() { return errors; }

    public int size() { return byId.size(); }
}
