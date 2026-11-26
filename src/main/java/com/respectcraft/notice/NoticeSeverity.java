package com.respectcraft.notice;

/** Gravidade de um aviso: quanto tempo o cartao fica na tela e qual som toca (Etapa 4.11). */
public enum NoticeSeverity {
    INFO(5F),
    ALERT(7F),
    CRITICAL(10F);

    private final float seconds;

    NoticeSeverity(float seconds) { this.seconds = seconds; }

    /** Segundos na tela antes de sumir (o aviso continua no historico da secao Alerts). */
    public float seconds() { return seconds; }

    public static NoticeSeverity byOrdinal(int ordinal) {
        NoticeSeverity[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : INFO;
    }
}
