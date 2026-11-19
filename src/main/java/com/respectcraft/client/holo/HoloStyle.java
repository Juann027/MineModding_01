package com.respectcraft.client.holo;

/** Paleta propria do menu holografico do RespectCraft: vidro azul-escuro, luz ciano e acentos violeta. */
final class HoloStyle {
    private HoloStyle() {}

    static final int CYAN = 0xFF4FE3FF;
    static final int CYAN_SOFT = 0xFF8FEFFF;
    static final int ICE = 0xFFE6FBFF;
    static final int VIOLET = 0xFFB07CFF;
    static final int MAGENTA = 0xFFFF5FC8;
    static final int GOLD = 0xFFFFD86B;
    static final int GREEN = 0xFF6BE3A0;
    static final int RED = 0xFFFF6B7A;

    static final int GLASS_TOP = 0xFF0E2A40;
    static final int GLASS_BOTTOM = 0xFF06121F;
    static final int GLASS_HC = 0xFF02060B;

    /** Texto principal, secundario e apagado (mais claros no alto contraste). */
    static int text(boolean hc) { return hc ? 0xFFFFFFFF : 0xFFE8F6FF; }
    static int muted(boolean hc) { return hc ? 0xFFD5E2EC : 0xFF9DB8CA; }
    static int dim(boolean hc) { return hc ? 0xFFAFC0CD : 0xFF5F7C90; }
}
