package com.respectcraft.needs;

/**
 * Necessidades do corpo. Ids estaveis (Codec, comandos, traducoes).
 *
 * <p>As quatro necessidades ativas do RespectCraft sao Coom, Bexiga, Intestino e Emocao ({@link #ACTIVE}).
 * Hidratacao e Energia (Etapa 3) sao <b>dados legados inativos</b> desde a Etapa 4.5: continuam no enum e no
 * save para nao quebrar mundos antigos, mas nao mudam com o tempo, nao recebem efeitos de consumiveis, nao
 * afetam a Emocao, nao dao avisos nem penalidades e nao aparecem na interface.
 */
public enum Need {
    HYDRATION("hidratacao", 0xFF4FB3E8, true, false),
    ENERGY("energia", 0xFFF2CF4A, true, false),
    BLADDER("bexiga", 0xFFE8C440, false, true),
    BOWEL("intestino", 0xFFA37D51, false, true),
    MOOD("humor", 0xFF79C8D9, true, true),
    /** Medidor Coom: recurso abstrato que acumula com o tempo (cheio = critico). Etapa 3.1. */
    COOM("coom", 0xFFE06AA8, false, true);

    /** Necessidades ativas, na ordem em que a interface as mostra. */
    public static final Need[] ACTIVE = {COOM, BLADDER, BOWEL, MOOD};

    private final String id;
    private final int color;
    private final boolean highIsGood;
    private final boolean active;

    Need(String id, int color, boolean highIsGood, boolean active) {
        this.id = id;
        this.color = color;
        this.highIsGood = highIsGood;
        this.active = active;
    }

    public String id() { return id; }
    public int color() { return color; }
    /** Emocao (e as legadas hidratacao/energia): cheio e bom. Bexiga, intestino e coom: cheio e urgente. */
    public boolean highIsGood() { return highIsGood; }
    /** Falso para Hidratacao e Energia (dados legados inativos). */
    public boolean active() { return active; }

    public static Need byId(String id) {
        for (Need need : values()) if (need.id.equals(id)) return need;
        return null;
    }
}
