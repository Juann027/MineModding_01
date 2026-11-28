package com.respectcraft.network;

import com.respectcraft.notice.HoloNotice;
import com.respectcraft.notice.NoticeQueue;
import com.respectcraft.notice.NoticeRules;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;

/**
 * Copia do cliente dos avisos holograficos (Etapa 4.11): fila de cartoes da tela e historico da secao Alerts. Cache
 * so visual (sem estado de mundo), limpa no logout. Duplicatas pela chave sao ignoradas ({@link NoticeRules}).
 *
 * <p>Etapa 4.14.1: tambem a janela cinematografica (uma na tela + {@link NoticeQueue} com limite e prioridade),
 * duplicatas lembradas por chave (ate {@link NoticeRules#DEDUPE_MEMORY}), grupo que substitui a janela/cartao do
 * mesmo grupo em vez de empilhar, e interrupcao controlada (so por um aviso bem mais prioritario). Desligar as
 * janelas ou os cartoes na config so muda a apresentacao: o aviso continua no historico e nada do jogo depende disso.
 */
public final class NoticeClientCache {
    private NoticeClientCache() {}

    /** Aviso recebido, com a hora local de chegada. {@code shown} = o cartao ja apareceu (som tocado). */
    public static final class Entry {
        public HoloNotice notice;
        public final long arrivedMillis;
        public boolean read;
        public long shownMillis = -1L;
        /** Janela: duracao resolvida ao aparecer e, se interrompida, quando comecou a sair (-1 = nao). */
        public int cinematicMillis;
        public long exitMillis = -1L;
        /** Aviso local de exemplo (botao Preview da config): nunca entra no historico. */
        public final boolean preview;

        Entry(HoloNotice notice, long arrivedMillis, boolean preview) {
            this.notice = notice;
            this.arrivedMillis = arrivedMillis;
            this.preview = preview;
        }
    }

    private static final Deque<Entry> HISTORY = new ArrayDeque<>();
    private static final List<Entry> TOASTS = new ArrayList<>();
    private static final NoticeQueue<Entry> CINEMATIC = new NoticeQueue<>(NoticeRules.CINEMATIC_QUEUE_LIMIT,
            e -> e.notice.priority(), e -> e.notice.group());
    private static Entry current;
    private static final Map<String, Long> SEEN_KEYS = new LinkedHashMap<>();
    private static long lastSoundMillis;
    private static int revision;

    public static synchronized void receive(HoloNotice notice) {
        long now = Util.getMillis();
        String key = notice.dedupeKey();
        if (!key.isEmpty()) {
            Long last = SEEN_KEYS.get(key);
            if (last != null && NoticeRules.isDuplicate(key, key, last, now)) return;
            SEEN_KEYS.remove(key);
            SEEN_KEYS.put(key, now);
            while (SEEN_KEYS.size() > NoticeRules.DEDUPE_MEMORY) {
                Iterator<String> it = SEEN_KEYS.keySet().iterator();
                it.next();
                it.remove();
            }
        }
        Entry e = new Entry(notice, now, false);
        // historico: o mesmo grupo substitui a linha de cima em vez de empilhar
        Entry head = HISTORY.peekFirst();
        if (head != null && !notice.group().isEmpty() && notice.group().equals(head.notice.group())) HISTORY.removeFirst();
        HISTORY.addFirst(e);
        while (HISTORY.size() > NoticeRules.HISTORY_LIMIT) HISTORY.removeLast();
        if (notice.showsCard()) addToast(e);
        if (notice.showsCinematic()) offerCinematic(e, now);
        revision++;
    }

    /** Aviso de exemplo so no cliente (Preview da config): janela e/ou cartao, sem historico. */
    public static synchronized void preview(HoloNotice notice) {
        Entry e = new Entry(notice, Util.getMillis(), true);
        if (notice.showsCard()) addToast(e);
        if (notice.showsCinematic()) offerCinematic(e, e.arrivedMillis);
    }

    private static void addToast(Entry e) {
        String g = e.notice.group();
        if (!g.isEmpty()) TOASTS.removeIf(t -> g.equals(t.notice.group()));
        TOASTS.add(e);
    }

    private static void offerCinematic(Entry e, long now) {
        Entry cur = current;
        String g = e.notice.group();
        if (cur != null && cur.exitMillis < 0 && !g.isEmpty() && g.equals(cur.notice.group())) {
            // mesmo grupo na tela: troca o conteudo e recomeca a contagem sem a animacao de entrada
            cur.notice = e.notice;
            cur.shownMillis = now - 400L;
            cur.cinematicMillis = 0;
            return;
        }
        CINEMATIC.offer(e);
        if (cur != null && cur.exitMillis < 0 && cur.shownMillis >= 0) {
            float age = (now - cur.shownMillis) / 1000F;
            Entry next = CINEMATIC.peek();
            if (next != null && NoticeRules.shouldInterrupt(cur.notice.priority(), next.notice.priority(), age)) cur.exitMillis = now;
        }
    }

    /** A janela na tela agora (promove a proxima da fila quando a atual acaba). */
    public static synchronized Entry cinematic(long now) {
        if (current != null) {
            boolean done = current.shownMillis >= 0 && current.cinematicMillis > 0
                    && (now - current.shownMillis > current.cinematicMillis
                    || (current.exitMillis >= 0 && now - current.exitMillis > NoticeRules.INTERRUPT_EXIT * 1000F));
            if (!done) return current;
            current = null;
        }
        current = CINEMATIC.poll();
        return current;
    }

    /** Janelas desligadas na config: a atual e a fila viram cartoes (a informacao nao se perde). */
    public static synchronized void demoteCinematic() {
        List<Entry> all = CINEMATIC.drain();
        if (current != null) {
            all.add(0, current);
            current = null;
        }
        for (Entry e : all) if (!e.notice.showsCard()) {
            e.shownMillis = -1L;
            addToast(e);
        }
    }

    public static synchronized int cinematicQueued() { return CINEMATIC.size(); }

    /** Pode tocar o som de um aviso agora? Marca o instante se puder (sem rajadas). */
    public static synchronized boolean claimSound(long now) {
        if (!NoticeRules.soundAllowed(lastSoundMillis, now)) return false;
        lastSoundMillis = now;
        return true;
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
        CINEMATIC.clear();
        current = null;
        SEEN_KEYS.clear();
        lastSoundMillis = 0L;
        revision++;
    }
}
