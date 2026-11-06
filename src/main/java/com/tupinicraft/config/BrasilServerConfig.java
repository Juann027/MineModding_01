package com.tupinicraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Arquivo de configuração SERVER ({@code serverconfig/tupinicraft-server.toml} de cada mundo), gerado a partir
 * de {@link ConfigKeys}. Contém os <b>valores padrão</b>; overrides administrativos ficam no mundo.
 * Não leia daqui diretamente no gameplay: use {@link ServerSettings}.
 */
public final class BrasilServerConfig {
    public static final ModConfigSpec SPEC;
    private static final Map<ConfigKey, ModConfigSpec.ConfigValue<?>> VALUES = new LinkedHashMap<>();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        for (ConfigKey key : ConfigKeys.all()) {
            builder.comment(key.isBool() ? key.comment() + " (" + key.describeRange() + ")" : key.comment());
            if (key.isBool()) {
                VALUES.put(key, builder.define(key.path(), key.defaultValue() != 0));
            } else {
                VALUES.put(key, builder.defineInRange(key.path(), key.defaultValue(), key.min(), key.max()));
            }
        }
        SPEC = builder.build();
    }

    private BrasilServerConfig() {
    }

    /** Valor do TOML (já validado pelo NeoForge dentro dos limites). */
    static int tomlValue(ConfigKey key) {
        Object value = VALUES.get(key).get();
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return ((Number) value).intValue();
    }
}
