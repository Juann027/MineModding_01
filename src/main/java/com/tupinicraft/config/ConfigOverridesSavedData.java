package com.tupinicraft.config;

import com.tupinicraft.TupiniCraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Overrides administrativos do mundo (data/tupinicraft_config_overrides.dat): só as chaves alteradas por
 * {@code /brasil admin config set}. Chaves desconhecidas ou fora dos limites são descartadas ao carregar.
 */
public final class ConfigOverridesSavedData extends SavedData {
    private static final String FILE_NAME = TupiniCraft.MOD_ID + "_config_overrides";

    private final Map<String, Integer> overrides = new LinkedHashMap<>();

    static ConfigOverridesSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ConfigOverridesSavedData::new, ConfigOverridesSavedData::load, null), FILE_NAME);
    }

    Optional<Integer> get(ConfigKey key) {
        return Optional.ofNullable(overrides.get(key.path()));
    }

    void set(ConfigKey key, int value) {
        overrides.put(key.path(), value);
        setDirty();
    }

    boolean remove(ConfigKey key) {
        boolean removed = overrides.remove(key.path()) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    Map<String, Integer> all() {
        return Collections.unmodifiableMap(overrides);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag values = new CompoundTag();
        overrides.forEach(values::putInt);
        tag.put("overrides", values);
        return tag;
    }

    private static ConfigOverridesSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ConfigOverridesSavedData data = new ConfigOverridesSavedData();
        CompoundTag values = tag.getCompound("overrides");
        for (String path : values.getAllKeys()) {
            Optional<ConfigKey> key = ConfigKeys.byPath(path);
            int value = values.getInt(path);
            if (key.isEmpty()) {
                TupiniCraft.LOGGER.warn("Override de configuração desconhecido ignorado: {}", path);
            } else if (!key.get().isValid(value)) {
                TupiniCraft.LOGGER.warn("Override fora dos limites ignorado: {} = {} ({})", path, value, key.get().describeRange());
            } else {
                data.overrides.put(path, value);
            }
        }
        return data;
    }
}
