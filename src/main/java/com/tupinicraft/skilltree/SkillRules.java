package com.tupinicraft.skilltree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.tupinicraft.TupiniCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Regras do TupiniCraft lidas dos mesmos arquivos de skill do SkillLib ({@code data/<ns>/skilllib/skills/*.json},
 * bloco {@code "tupinicraft"}), recarregadas com {@code /reload}. Assim cada skill tem um único arquivo.
 * Funciona mesmo sem o SkillLib instalado (as regras existem, mas não há árvore para aplicá-las).
 * <p>
 * É dado de datapack do servidor (como receitas), não estado do mundo; por isso fica num holder estático.
 */
public final class SkillRules extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<String, SkillRule> rules = Map.of();

    private SkillRules() {
        super(GSON, "skilllib/skills");
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SkillRules());
    }

    public static Optional<SkillRule> get(String skillId) {
        return Optional.ofNullable(rules.get(skillId));
    }

    public static Collection<SkillRule> all() {
        return rules.values();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, SkillRule> loaded = new LinkedHashMap<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String skillId = entry.getKey().toString();
            try {
                if (entry.getValue().isJsonObject()) {
                    SkillRuleParser.parse(skillId, entry.getValue().getAsJsonObject()).ifPresent(rule -> loaded.put(skillId, rule));
                }
            } catch (RuntimeException e) {
                TupiniCraft.LOGGER.error("Regra de skill inválida em {}: {}", skillId, e.getMessage());
            }
        });
        rules = Collections.unmodifiableMap(loaded);
        TupiniCraft.LOGGER.info("TupiniCraft: {} regras de skill carregadas: {}", loaded.size(), loaded.keySet());
    }
}
