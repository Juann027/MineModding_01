package com.respectcraft.network;

import com.respectcraft.notice.HoloNotice;
import com.respectcraft.notice.NoticeRules;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.Util;

/**
 * Copia do cliente dos avisos holograficos (Etapa 4.11): fila de cartoes da tela e historico da secao Alerts. Cache
 * so visual (sem estado de mundo), limpa no logout. Duplicatas pela chave sao ignoradas ({@link NoticeRules}).
 */
public final class NoticeClientCache {
    private NoticeClientCache() {}

    /** Aviso recebido, com a hora local de chegada. {@code shown} = o cartao ja apareceu (som tocado). */
    public static final class Entry {
        public final HoloNotice notice;
        public final long arrivedMillis;
        public boolean read;
        public long shownMillis = -1L;

        Entry(HoloNotice notice, long arrivedMillis) {
            this.notice = notice;
            this.arrivedMillis = arrivedMillis;
        }
    }

    private static final Deque<Entry> HISTORY = new ArrayDeque<>();
    private static final List<Entry> TOASTS = new ArrayList<>();
    private static String lastKey = "";
    private static long lastKeyMillis;
    private static int revision;

    public static synchronized void receive(HoloNotice notice) {
        long now = Util.getMillis();
        if (NoticeRules.isDuplicate(notice.dedupeKey(), lastKey, lastKeyMillis, now)) return;
        if (!notice.dedupeKey().isEmpty()) {
            lastKey = notice.dedupeKey();
            lastKeyMillis = now;
        }
        Entry e = new Entry(notice, now);
        HISTORY.addFirst(e);
        while (HISTORY.size() > NoticeRules.HISTORY_LIMIT) HISTORY.removeLast();
        TOASTS.add(e);
        revision++;
    }

    /** Historico, mais novo primeiro. */
    public static synchronized List<Entry> history() { return new ArrayList<>(HISTORY); }

    /** Cartoes ainda na fila/tela (o desenho tira os que acabaram). */
    public static synchronized List<Entry> toasts() { return new ArrayList<>(TOASTS); }

    public static synchronized void dropToast(Entry e) { TOASTS.remove(e); }

    public static synchronized int unread() {
        int n = 0;
        for (Entry e : HISTORY) if (!e.read) n++;
        return n;
    }

    public static synchronized void markAllRead() { for (Entry e : HISTORY) e.read = true; }

    /** Limpa o historico (botao da secao Alerts); os cartoes na tela terminam normalmente. */
    public static synchronized void clearHistory() {
        HISTORY.clear();
        revision++;
    }

    public static synchronized int revision() { return revision; }

    public static synchronized void clear() {
        HISTORY.clear();
        TOASTS.clear();
        lastKey = "";
        lastKeyMillis = 0L;
        revision++;
    }
}
