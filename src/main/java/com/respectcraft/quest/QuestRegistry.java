package com.respectcraft.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.respectcraft.RespectCraft;
import com.respectcraft.skill.Skills;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Carrega as missoes dos datapacks do servidor (Etapa 4.14.2): {@code data/<ns>/quest/<caminho>.json}, id =
 * {@code <ns>:<caminho>}. Recarrega com {@code /reload}. Definicao invalida (JSON, tipo desconhecido, limites,
 * item/habilidade inexistente, formato mais novo) e <b>pulada com log</b> e o resto carrega; o progresso dos
 * jogadores nessa missao continua salvo ({@link QuestLog}) e volta a valer quando a definicao for corrigida.
 *
 * <p>O catalogo ({@link QuestCatalog}) e dado do servidor, nao estado de mundo: trocado inteiro a cada reload e
 * esvaziado quando o servidor para.
 */
public final class QuestRegistry extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "quest";
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile QuestCatalog catalog = QuestCatalog.EMPTY;

    private final HolderLookup.Provider registries;

    private QuestRegistry(HolderLookup.Provider registries) {
        super(GSON, DIRECTORY);
        this.registries = registries;
    }

    public static QuestCatalog catalog() { return catalog; }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new QuestRegistry(event.getRegistryAccess()));
    }

    public static void onServerStopped(ServerStoppedEvent event) { catalog = QuestCatalog.EMPTY; }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        catalog = parse(files, registries == null ? JsonOps.INSTANCE : RegistryOps.create(JsonOps.INSTANCE, registries));
        RespectCraft.LOGGER.info("RespectCraft quests: {} loaded, {} invalid", catalog.size(), catalog.errors().size());
        for (String e : catalog.errors()) RespectCraft.LOGGER.error("RespectCraft quest definition refused: {}", e);
    }

    /** Converte e valida; publico para o {@code SelfTest} (com {@code JsonOps.INSTANCE}). */
    public static QuestCatalog parse(Map<ResourceLocation, JsonElement> files, com.mojang.serialization.DynamicOps<JsonElement> ops) {
        List<QuestDef> defs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            ResourceLocation id = e.getKey();
            if (defs.size() >= QuestRules.MAX_DEFINITIONS) {
                errors.add(id + ": more than " + QuestRules.MAX_DEFINITIONS + " quest definitions");
                continue;
            }
            var result = QuestDef.FILE_CODEC.parse(ops, e.getValue());
            if (result.error().isPresent()) {
                errors.add(id + ": " + result.error().get().message());
                continue;
            }
            QuestDef def = result.getOrThrow().withId(id);
            List<String> problems = def.validate();
            for (QuestReward r : def.rewards()) {
                if (r.id().isEmpty()) continue;
                if (r.type().equals("item") && !BuiltInRegistries.ITEM.containsKey(r.id().get())) problems.add("reward: unknown item " + r.id().get());
                if (r.type().equals("skill") && Skills.get(r.id().get()) == null) problems.add("reward: unknown skill " + r.id().get());
            }
            if (!problems.isEmpty()) {
                errors.add(id + ": " + String.join("; ", problems));
                continue;
            }
            defs.add(def);
        }
        // cadeias: referencias a missoes que nao existem viram aviso (a missao carrega, a condicao fica falsa)
        List<String> warnings = new ArrayList<>();
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        for (QuestDef d : defs) ids.add(d.id());
        for (QuestDef d : defs) {
            List<ResourceLocation> refs = new ArrayList<>();
            d.discover().ifPresent(c -> QuestCondition.quests(c, refs));
            d.requires().ifPresent(c -> QuestCondition.quests(c, refs));
            for (QuestReward r : d.rewards()) if (r.type().equals("quest")) r.id().ifPresent(refs::add);
            for (QuestObjective o : d.objectives()) if (o.type().equals("complete_quest") && o.targetId() != null) refs.add(o.targetId());
            for (ResourceLocation ref : refs) if (!ids.contains(ref)) warnings.add(d.id() + " references unknown quest " + ref);
        }
        for (String w : warnings) RespectCraft.LOGGER.warn("RespectCraft quest: {}", w);
        return new QuestCatalog(defs, errors);
    }
}
