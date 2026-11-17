package com.tupinicraft.client.hud;

import com.tupinicraft.needs.Need;
import com.tupinicraft.needs.Needs;

/** Instantaneo visual das necessidades (0 a 100), lido da copia sincronizada pelo servidor. */
public record HudMeters(int hydration, int energy, int bladder, int bowel, int emotion) {
    public HudMeters {
        hydration = clamp(hydration);
        energy = clamp(energy);
        bladder = clamp(bladder);
        bowel = clamp(bowel);
        emotion = clamp(emotion);
    }

    public static HudMeters of(Needs needs) {
        return new HudMeters(needs.percent(Need.HYDRATION), needs.percent(Need.ENERGY), needs.percent(Need.BLADDER),
                needs.percent(Need.BOWEL), needs.percent(Need.MOOD));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
