package com.respectcraft.network;

import com.respectcraft.network.QuestNetworking.QuestView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Copia do cliente das missoes descobertas do proprio jogador (Etapa 4.14.2). Cache so visual, limpa no logout;
 * nunca decide nada. Guarda a hora local de chegada de cada linha para contar o tempo restante sem pacote por tick,
 * e quais mudaram desde a ultima visita a secao Quests (o ponto aceso do chip).
 */
public final class QuestClientCache {
    private QuestClientCache() {}

    public record Entry(QuestView view, long receivedMillis) {
        /** Ticks restantes agora (contagem local a partir do valor do servidor); -1 = sem tempo. */
        public long remainingTicks() {
            if (view.remainingTicks() < 0) return -1;
            long elapsed = (Util.getMillis() - receivedMillis) / 50L;
            return Math.max(0L, view.remainingTicks() - elapsed);
        }
    }

    private static final Map<ResourceLocation, Entry> QUESTS = new LinkedHashMap<>();
    private static final List<Component> HINTS = new ArrayList<>();
    private static final Set<ResourceLocation> UNSEEN = new HashSet<>();
    private static boolean received;
    private static int revision;

    public static synchronized void snapshot(List<QuestView> list, List<Component> hints) {
        long now = Util.getMillis();
        Map<ResourceLocation, Entry> old = new LinkedHashMap<>(QUESTS);
        QUESTS.clear();
        for (QuestView v : list) {
            QUESTS.put(v.id(), new Entry(v, now));
            Entry o = old.get(v.id());
            if (received && (o == null || o.view().state() != v.state())) UNSEEN.add(v.id());
        }
        UNSEEN.retainAll(QUESTS.keySet());
        HINTS.clear();
        HINTS.addAll(hints);
        received = true;
        revision++;
    }

    public static synchronized void update(QuestView v) {
        Entry o = QUESTS.get(v.id());
        QUESTS.put(v.id(), new Entry(v, Util.getMillis()));
        if (o == null || o.view().state() != v.state()) UNSEEN.add(v.id());
        revision++;
    }

    public static synchronized List<Entry> all() { return new ArrayList<>(QUESTS.values()); }

    public static synchronized Entry get(ResourceLocation id) { return QUESTS.get(id); }

    public static synchronized List<Component> hints() { return new ArrayList<>(HINTS); }

    public static synchronized List<Entry> tracked() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : QUESTS.values()) if (e.view().has(QuestView.TRACKED)) out.add(e);
        return out;
    }

    public static synchronized boolean unseen(ResourceLocation id) { return UNSEEN.contains(id); }

    public static synchronized int unseenCount() { return UNSEEN.size(); }

    public static synchronized void markSeen() { UNSEEN.clear(); }

    public static synchronized boolean received() { return received; }

    public static synchronized int revision() { return revision; }

    public static synchronized void clear() {
        QUESTS.clear();
        HINTS.clear();
        UNSEEN.clear();
        received = false;
        revision++;
    }
}
