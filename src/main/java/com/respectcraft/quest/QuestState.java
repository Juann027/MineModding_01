package com.respectcraft.quest;

import com.mojang.serialization.Codec;
import java.util.Locale;

/**
 * Ciclo de vida de uma missao por jogador (Etapa 4.14.2). Nem toda missao usa todos: uma comum vai de
 * {@link #AVAILABLE} a {@link #CLAIMED}; uma secreta sai de {@link #HIDDEN} direto para {@link #ACTIVE}. As
 * transicoes validas ficam em {@link QuestRules#canTransition}; o cliente nunca escolhe o estado, so pede acoes.
 * Gravado pelo nome em minusculas (id estavel).
 */
public enum QuestState {
    /** Ainda nao descoberta (secretas verdadeiras nunca saem do servidor neste estado). */
    HIDDEN,
    /** Descoberta, esperando o jogador aceitar. */
    AVAILABLE,
    /** Em andamento: os objetivos contam. */
    ACTIVE,
    /** Objetivos cumpridos; recompensa por resgatar (resgate manual). */
    COMPLETED,
    /** Recompensa entregue. Repetivel: volta a {@link #AVAILABLE} depois do intervalo. */
    CLAIMED,
    /** Falhou (ex.: morte com {@code fail_on_death}). */
    FAILED,
    /** O tempo limite acabou. */
    EXPIRED,
    /** Abandonada pelo jogador (quando a missao permite). */
    CANCELLED;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static QuestState byId(String id) {
        for (QuestState s : values()) if (s.id().equals(id)) return s;
        return null;
    }

    /** Ja passou pelo jogador (aparece no menu)? */
    public boolean discovered() { return this != HIDDEN; }

    /** Missao terminada (com ou sem sucesso). */
    public boolean finished() { return this == COMPLETED || this == CLAIMED || this == FAILED || this == EXPIRED || this == CANCELLED; }

    public boolean succeeded() { return this == COMPLETED || this == CLAIMED; }

    public static final Codec<QuestState> CODEC = Codec.STRING.comapFlatMap(s -> {
        QuestState st = byId(s);
        return st == null ? com.mojang.serialization.DataResult.error(() -> "Unknown quest state: " + s)
                : com.mojang.serialization.DataResult.success(st);
    }, QuestState::id);

    public static QuestState byOrdinal(int i) {
        QuestState[] all = values();
        return i >= 0 && i < all.length ? all[i] : HIDDEN;
    }
}
