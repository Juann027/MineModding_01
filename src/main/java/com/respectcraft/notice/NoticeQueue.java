package com.respectcraft.notice;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Fila com limite da janela cinematografica (Etapa 4.14.1), pura (coberta pelo {@code SelfTest}): ordem por
 * prioridade (maior primeiro) e chegada; mesmo grupo substitui no lugar (atualizacoes frequentes nao empilham);
 * acima do limite sai o item de menor prioridade mais novo (o recem-chegado, se for ele o menor).
 */
public final class NoticeQueue<T> {
    public enum Offer { QUEUED, MERGED, DROPPED, REPLACED_LOWER }

    private final int limit;
    private final ToIntFunction<T> priority;
    private final Function<T, String> group;
    private final List<T> items = new ArrayList<>();

    public NoticeQueue(int limit, ToIntFunction<T> priority, Function<T, String> group) {
        this.limit = Math.max(1, limit);
        this.priority = priority;
        this.group = group;
    }

    public Offer offer(T item) {
        String g = group.apply(item);
        if (g != null && !g.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                if (g.equals(group.apply(items.get(i)))) {
                    items.remove(i);
                    insert(item);
                    return Offer.MERGED;
                }
            }
        }
        if (items.size() >= limit) {
            int lowest = lowestIndex();
            if (priority.applyAsInt(items.get(lowest)) >= priority.applyAsInt(item)) return Offer.DROPPED;
            items.remove(lowest);
            insert(item);
            return Offer.REPLACED_LOWER;
        }
        insert(item);
        return Offer.QUEUED;
    }

    /** Insere depois de todos com prioridade maior ou igual (FIFO dentro da mesma prioridade). */
    private void insert(T item) {
        int p = priority.applyAsInt(item);
        int at = items.size();
        for (int i = 0; i < items.size(); i++) {
            if (priority.applyAsInt(items.get(i)) < p) {
                at = i;
                break;
            }
        }
        items.add(at, item);
    }

    /** Menor prioridade; entre iguais, o mais novo (ultimo). */
    private int lowestIndex() {
        int idx = 0;
        for (int i = 1; i < items.size(); i++) {
            if (priority.applyAsInt(items.get(i)) <= priority.applyAsInt(items.get(idx))) idx = i;
        }
        return idx;
    }

    public T peek() { return items.isEmpty() ? null : items.get(0); }
    public T poll() { return items.isEmpty() ? null : items.remove(0); }
    public int size() { return items.size(); }
    public boolean isEmpty() { return items.isEmpty(); }
    public List<T> snapshot() { return List.copyOf(items); }
    public void clear() { items.clear(); }

    /** Tira e devolve tudo (ex.: janelas desligadas viram cartoes). */
    public List<T> drain() {
        List<T> all = new ArrayList<>(items);
        items.clear();
        return all;
    }
}
