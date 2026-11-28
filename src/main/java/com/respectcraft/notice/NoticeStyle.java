package com.respectcraft.notice;

/**
 * Estilo visual de um aviso (Etapa 4.14.1, Notification HUD V2): cor tematica, glifo, prioridade na fila e o som de
 * entrada da janela cinematografica. Estilos sao so apresentacao e nao sabem de nenhuma mecanica: quests, chefes,
 * portais e eventos futuros escolhem o estilo que combina ao chamar {@link HoloNotices}.
 *
 * <p>O pacote usa o ordinal: estilos novos entram no <b>fim</b>.
 */
public enum NoticeStyle {
    SYSTEM_INFO("system_info", 0xFF4FE3FF, "i", 1, Sound.SOFT),
    QUEST_DISCOVERED("quest_discovered", 0xFF4FB4FF, "!", 2, Sound.REVEAL),
    SECRET_DISCOVERED("secret_discovered", 0xFFB07CFF, "?", 3, Sound.REVEAL),
    OBJECTIVE_UPDATED("objective_updated", 0xFF7FD8FF, "»", 0, Sound.TICK),
    QUEST_COMPLETED("quest_completed", 0xFF6BE3A0, "✔", 3, Sound.TRIUMPH),
    QUEST_FAILED("quest_failed", 0xFFFF6B7A, "✖", 3, Sound.ALARM),
    REWARD_AVAILABLE("reward_available", 0xFFFFD86B, "✦", 2, Sound.TRIUMPH),
    WARNING("warning", 0xFFFFB347, "⚠", 4, Sound.ALARM),
    DANGER("danger", 0xFFFF3B4E, "☠", 5, Sound.ALARM),
    PORTAL_AVAILABLE("portal_available", 0xFFC58BFF, "◎", 3, Sound.REVEAL),
    MYSTERIOUS("mysterious", 0xFFC8C8D8, "∴", 2, Sound.SOFT),
    /** Etapa 4.15: "SYSTEM ERROR" (falha sem explicacao; Time Warp e fenomenos futuros). */
    SYSTEM_ERROR("system_error", 0xFFE85A6A, "×", 4, Sound.SOFT);

    /** Familia do som de entrada (o cliente escolhe o arquivo, o volume vem da config). */
    public enum Sound { NONE, TICK, SOFT, REVEAL, TRIUMPH, ALARM }

    private final String id;
    private final int color;
    private final String glyph;
    private final int priority;
    private final Sound sound;

    NoticeStyle(String id, int color, String glyph, int priority, Sound sound) {
        this.id = id;
        this.color = color;
        this.glyph = glyph;
        this.priority = priority;
        this.sound = sound;
    }

    public String id() { return id; }
    public int color() { return color; }
    public String glyph() { return glyph; }
    /** 0 = mais baixa (atualizacao de objetivo), 5 = perigo. */
    public int priority() { return priority; }
    public Sound sound() { return sound; }
    public String translationKey() { return "notice.respectcraft.style." + id; }

    public static NoticeStyle byId(String id) {
        for (NoticeStyle s : values()) if (s.id.equals(id)) return s;
        return null;
    }

    public static NoticeStyle byOrdinal(int ordinal) {
        NoticeStyle[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : SYSTEM_INFO;
    }
}
