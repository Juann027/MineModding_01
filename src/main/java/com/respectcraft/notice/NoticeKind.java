package com.respectcraft.notice;

/**
 * Categoria de um aviso holografico (Etapa 4.11). Define cor, glifo, gravidade padrao e o farol do
 * Calamidade_Awakening que acende no local do evento ({@link Beacon#NONE} = sem efeito no mundo).
 *
 * <p>Os ids sao so de rede/exibicao (nada e gravado no save). Categorias novas entram no fim: o pacote usa o ordinal.
 * Varias ainda nao tem fonte no jogo (chefes proprios, portais do mod, eventos de mundo): o caminho fica pronto e
 * qualquer sistema futuro so chama {@link HoloNotices}.
 */
public enum NoticeKind {
    /** Respostas do servidor e mensagens gerais. */
    SYSTEM("system", 0xFF4FE3FF, "i", NoticeSeverity.INFO, Beacon.NONE),
    /** Evento de mundo (invasao, chuva de meteoros, festival...): nenhum existe ainda. */
    WORLD_EVENT("world_event", 0xFFFFD86B, "✦", NoticeSeverity.ALERT, Beacon.EVENT),
    /** Um chefe ({@code c:bosses}) apareceu perto. */
    BOSS_SPAWN("boss_spawn", 0xFFFF6B7A, "☠", NoticeSeverity.CRITICAL, Beacon.BOSS),
    /** Um chefe foi derrotado (via {@code BossDefeatEvent}; nenhuma recompensa e concedida). */
    BOSS_DEFEAT("boss_defeat", 0xFF6BE3A0, "✔", NoticeSeverity.ALERT, Beacon.VICTORY),
    /** Um portal se abriu (hoje: portal do Nether aceso; portais do mod usarao o mesmo caminho). */
    PORTAL("portal", 0xFFB07CFF, "◎", NoticeSeverity.ALERT, Beacon.PORTAL),
    /** Chegada a uma dimensao do mod (Inverted Realm). */
    REALM("realm", 0xFFFF5FC8, "◆", NoticeSeverity.INFO, Beacon.NONE),
    /** Caca a recompensa entre jogadores. */
    BOUNTY("bounty", 0xFFFF9A3C, "✚", NoticeSeverity.ALERT, Beacon.NONE),
    /** Etapa 4.14: missoes (descoberta, objetivo, conclusao, falha, recompensa); privado para o jogador. */
    QUEST("quest", 0xFF4FB4FF, "❖", NoticeSeverity.INFO, Beacon.NONE);

    /** Efeito do Calamidade_Awakening aceso no local do aviso (registrado em {@link NoticeFx}). */
    public enum Beacon { NONE, EVENT, BOSS, VICTORY, PORTAL }

    private final String id;
    private final int color;
    private final String glyph;
    private final NoticeSeverity severity;
    private final Beacon beacon;

    NoticeKind(String id, int color, String glyph, NoticeSeverity severity, Beacon beacon) {
        this.id = id;
        this.color = color;
        this.glyph = glyph;
        this.severity = severity;
        this.beacon = beacon;
    }

    public String id() { return id; }
    public int color() { return color; }
    public String glyph() { return glyph; }
    public NoticeSeverity defaultSeverity() { return severity; }
    public Beacon beacon() { return beacon; }
    public String translationKey() { return "notice.respectcraft.kind." + id; }

    public static NoticeKind byId(String id) {
        for (NoticeKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }

    public static NoticeKind byOrdinal(int ordinal) {
        NoticeKind[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : SYSTEM;
    }
}
