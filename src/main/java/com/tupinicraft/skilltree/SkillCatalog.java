package com.tupinicraft.skilltree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tupinicraft.TupiniCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Carrega árvores e skills do datapack ({@code data/<ns>/tupinicraft_skilltree/arvores|skills/*.json}),
 * recarregadas com {@code /reload}. É dado do servidor (como receitas), não estado do mundo; por isso fica
 * num holder estático. Depois do carregamento, {@code OnDatapackSyncEvent} envia as definições aos clientes.
 */
public final class SkillCatalog extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "tupinicraft_skilltree";
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile SkillCatalogData current = SkillCatalogData.EMPTY;

    private SkillCatalog() {
        super(GSON, DIRECTORY);
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SkillCatalog());
    }

    public static SkillCatalogData get() {
        return current;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, JsonObject> trees = new HashMap<>();
        Map<String, JsonObject> skills = new HashMap<>();
        files.forEach((file, json) -> {
            if (!json.isJsonObject()) {
                return;
            }
            String path = file.getPath();
            if (path.startsWith("arvores/")) {
                trees.put(file.getNamespace() + ":" + path.substring("arvores/".length()), json.getAsJsonObject());
            } else if (path.startsWith("skills/")) {
                skills.put(file.getNamespace() + ":" + path.substring("skills/".length()), json.getAsJsonObject());
            }
        });
        SkillCatalogData data = SkillCatalogData.build(trees, skills, TupiniCraft.LOGGER::error);
        // Ícones e atributos precisam existir no jogo (o parser puro não conhece os registros).
        Map<String, SkillDef> validSkills = new HashMap<>(data.skills());
        data.skills().values().forEach(skill -> {
            String problem = !itemExists(skill.icon()) ? "ícone desconhecido " + skill.icon()
                    : skill.effects().stream().map(SkillEffectDef::attribute)
                    .filter(id -> BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(id)).isEmpty())
                    .findFirst().map(id -> "atributo desconhecido " + id).orElse(null);
            if (problem != null) {
                TupiniCraft.LOGGER.error("Skill inválida {}: {}", skill.id(), problem);
                validSkills.remove(skill.id());
            }
        });
        current = validSkills.size() == data.skills().size() ? data : SkillCatalogData.build(trees,
                filter(skills, validSkills.keySet()), message -> { });
        TupiniCraft.LOGGER.info("TupiniCraft: {} árvores e {} skills carregadas: {}", current.treesInOrder().size(),
                current.skills().size(), current.skills().keySet().stream().sorted().toList());
        SkillTreeService.onCatalogReloaded();
    }

    private static Map<String, JsonObject> filter(Map<String, JsonObject> skills, java.util.Set<String> keep) {
        Map<String, JsonObject> out = new HashMap<>(skills);
        out.keySet().retainAll(keep);
        return out;
    }

    private static boolean itemExists(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.ITEM.containsKey(location);
    }
}
