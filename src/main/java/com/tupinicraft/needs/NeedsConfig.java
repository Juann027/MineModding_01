package com.tupinicraft.needs;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuracao do servidor para as necessidades ({@code config/tupinicraft-needs-server.toml}, por mundo).
 * Lida so no servidor; antes de carregar, valem os padroes.
 */
public final class NeedsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue COOM_RATE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Necessidades do RespectCraft").push("necessidades");
        COOM_RATE = b.comment("Multiplicador do acumulo do medidor Coom (1.0 = ~21 min de 0 a 100%; 0 desliga o acumulo).")
                .defineInRange("coom_taxa", 1.0, 0.0, 10.0);
        b.pop();
        SPEC = b.build();
    }

    private NeedsConfig() {}

    public static float coomRate() {
        return SPEC.isLoaded() ? COOM_RATE.get().floatValue() : 1F;
    }
}
