package com.tupinicraft.needs;

/** Variacao aplicada por consumiveis e eventos (unidades de {@link Needs}). */
public record NeedsDelta(int hydration, int energy, int bladder, int bowel, int mood, int coom) {
    public static final NeedsDelta NONE = new NeedsDelta(0, 0, 0, 0, 0, 0);

    /** Formato da Etapa 3 (sem coom). */
    public NeedsDelta(int hydration, int energy, int bladder, int bowel, int mood) {
        this(hydration, energy, bladder, bowel, mood, 0);
    }

    public static NeedsDelta of(Need need, int value) {
        return NONE.with(need, value);
    }

    public int get(Need need) {
        return switch (need) {
            case HYDRATION -> hydration;
            case ENERGY -> energy;
            case BLADDER -> bladder;
            case BOWEL -> bowel;
            case MOOD -> mood;
            case COOM -> coom;
        };
    }

    public NeedsDelta with(Need need, int value) {
        return switch (need) {
            case HYDRATION -> new NeedsDelta(value, energy, bladder, bowel, mood, coom);
            case ENERGY -> new NeedsDelta(hydration, value, bladder, bowel, mood, coom);
            case BLADDER -> new NeedsDelta(hydration, energy, value, bowel, mood, coom);
            case BOWEL -> new NeedsDelta(hydration, energy, bladder, value, mood, coom);
            case MOOD -> new NeedsDelta(hydration, energy, bladder, bowel, value, coom);
            case COOM -> new NeedsDelta(hydration, energy, bladder, bowel, mood, value);
        };
    }

    public NeedsDelta plus(NeedsDelta o) {
        return new NeedsDelta(hydration + o.hydration, energy + o.energy, bladder + o.bladder, bowel + o.bowel,
                mood + o.mood, coom + o.coom);
    }

    /** Fracao {@code num/den} de cada campo (efeito gradual dividido por segundo). */
    public NeedsDelta scaled(int num, int den) {
        if (den <= 0) return NONE;
        return new NeedsDelta(hydration * num / den, energy * num / den, bladder * num / den, bowel * num / den,
                mood * num / den, coom * num / den);
    }

    public boolean isZero() { return equals(NONE); }
}
