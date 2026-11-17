package com.tupinicraft.needs;

/** Necessidades do corpo. Ids estaveis (Codec, comandos, traducoes). */
public enum Need {
    HYDRATION("hidratacao", 0xFF4FB3E8, true),
    ENERGY("energia", 0xFFF2CF4A, true),
    BLADDER("bexiga", 0xFFE8C440, false),
    BOWEL("intestino", 0xFFA37D51, false),
    MOOD("humor", 0xFF79C8D9, true);

    private final String id;
    private final int color;
    private final boolean highIsGood;

    Need(String id, int color, boolean highIsGood) {
        this.id = id;
        this.color = color;
        this.highIsGood = highIsGood;
    }

    public String id() { return id; }
    public int color() { return color; }
    /** Hidratacao, energia e humor: cheio e bom. Bexiga e intestino: cheio e urgente. */
    public boolean highIsGood() { return highIsGood; }

    public static Need byId(String id) {
        for (Need need : values()) if (need.id.equals(id)) return need;
        return null;
    }
}
