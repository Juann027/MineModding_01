package com.tupinicraft.needs;

/** Variacao aplicada por consumiveis e eventos (unidades de {@link Needs}). */
public record NeedsDelta(int hydration, int energy, int bladder, int bowel, int mood) {
    public static final NeedsDelta NONE = new NeedsDelta(0, 0, 0, 0, 0);
}
