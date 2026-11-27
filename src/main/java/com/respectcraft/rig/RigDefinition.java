package com.respectcraft.rig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.respectcraft.finisher.EntityFinisherClip;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Familia de rig (Etapa 4.12): o contrato que varios modelos GeckoLib compativeis seguem. So dados, lidos do manifesto
 * {@code assets/<ns>/rigs/<path>.rig.json} dentro do jar (o mesmo arquivo que o validador {@code tools/rigkit} confere),
 * nos dois lados. Nao impoe anatomia: humanoide, quadrupede ou serpente sao so listas diferentes de bones.
 *
 * <p>Coordenadas: pixels do Blockbench (16 px = 1 bloco), frente para -Z, +Y para cima, +X = direita do modelo. O
 * {@code .geo.json} exportado guarda X invertido (convencao Bedrock/GeckoLib); quem compara pivos desinverte.
 *
 * @param id                 id da familia ({@code respectcraft:fruit_humanoid_v1})
 * @param contractVersion    versao do contrato; muda quando uma alteracao quebra animacoes compartilhadas
 * @param referenceHeight    altura do modelo de referencia (px)
 * @param pivotTolerance     desvio de pivo (px, apos a proporcao do modelo) aceito sem aviso
 * @param controller         controlador GeckoLib onde as cenas da familia sao disparadas ({@code triggerAnim})
 * @param animationPrefix    prefixo dos nomes das animacoes compartilhadas
 * @param animationFile      arquivo das animacoes compartilhadas (ex.: {@code respectcraft:animations/rig/x.animation.json})
 * @param capabilities       capacidades de todo modelo da familia
 * @param optionalPrefixes   prefixos de bones livres (extras de cada modelo, sockets, expressoes)
 * @param bones              hierarquia esperada, na ordem do arquivo
 * @param sockets            pontos semanticos (bone + deslocamento em px)
 * @param parts              grupos de geometria manipulaveis (ocultar/expor)
 * @param alignment          palco padrao das cenas da familia
 * @param animations         animacoes compartilhadas: chave → duracao/laco
 * @param sharedBones        bones que animacoes compartilhadas podem animar
 */
public record RigDefinition(ResourceLocation id, int contractVersion, double referenceHeight, double pivotTolerance,
                            String controller, String animationPrefix, ResourceLocation animationFile,
                            Set<ResourceLocation> capabilities, List<String> optionalPrefixes, List<Bone> bones,
                            List<Socket> sockets, List<Part> parts, EntityFinisherClip.Alignment alignment,
                            Map<String, SharedAnimation> animations, Set<String> sharedBones) {
    public static final String FORMAT = "respectcraft:rig";
    public static final int FORMAT_VERSION = 1;

    /** Bone do contrato. {@code strict} = pivo exato (sem tolerancia nem proporcao). */
    public record Bone(String name, String parent, Vec3 pivot, boolean required, boolean strict) {}

    /** Ponto semantico: posicao = pivo do bone + deslocamento (px, coordenadas do Blockbench). */
    public record Socket(String name, String bone, Vec3 offset, String kind) {
        public String boneName() { return "socket_" + name; }
    }

    /** Grupo de geometria: os bones (e filhos) somem/aparecem juntos. */
    public record Part(String name, List<String> bones, boolean defaultVisible, boolean detachable) {}

    public record SharedAnimation(String key, int ticks, boolean loop) {}

    public RigDefinition {
        capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities));
        optionalPrefixes = List.copyOf(optionalPrefixes);
        bones = List.copyOf(bones);
        sockets = List.copyOf(sockets);
        parts = List.copyOf(parts);
        animations = Collections.unmodifiableMap(new LinkedHashMap<>(animations));
        sharedBones = Collections.unmodifiableSet(new LinkedHashSet<>(sharedBones));
        if (alignment == null) alignment = EntityFinisherClip.Alignment.AUTO;
        List<String> problems = problems(id, controller, bones, sockets, parts, animations, sharedBones);
        if (!problems.isEmpty()) throw new IllegalArgumentException("Invalid rig " + id + ": " + String.join("; ", problems));
    }

    /** Regras de consistencia (puras): nomes unicos, pais existentes, raiz unica, sockets/partes em bones existentes. */
    static List<String> problems(ResourceLocation id, String controller, List<Bone> bones, List<Socket> sockets,
                                 List<Part> parts, Map<String, SharedAnimation> animations, Set<String> sharedBones) {
        List<String> out = new ArrayList<>();
        if (id == null) out.add("missing id");
        if (controller == null || controller.isBlank()) out.add("missing controller");
        if (bones.isEmpty()) out.add("no bones");
        Map<String, Bone> byName = new HashMap<>();
        int roots = 0;
        for (Bone bone : bones) {
            if (bone.name() == null || bone.name().isBlank()) out.add("bone without name");
            else if (byName.putIfAbsent(bone.name(), bone) != null) out.add("duplicate bone " + bone.name());
            if (bone.parent() == null) roots++;
        }
        if (roots != 1) out.add("expected exactly one root bone, found " + roots);
        for (Bone bone : bones) {
            if (bone.parent() == null) continue;
            Bone parent = byName.get(bone.parent());
            if (parent == null) out.add("bone " + bone.name() + " has unknown parent " + bone.parent());
            else if (bone.required() && !parent.required()) out.add("required bone " + bone.name() + " under optional " + parent.name());
        }
        for (Bone bone : bones) { // ciclos
            Set<String> seen = new HashSet<>();
            for (Bone b = bone; b != null && b.parent() != null; b = byName.get(b.parent())) {
                if (!seen.add(b.name())) { out.add("cycle at bone " + bone.name()); break; }
            }
        }
        Set<String> socketNames = new HashSet<>();
        for (Socket s : sockets) {
            if (!socketNames.add(s.name())) out.add("duplicate socket " + s.name());
            if (!byName.containsKey(s.bone())) out.add("socket " + s.name() + " on unknown bone " + s.bone());
        }
        Set<String> partNames = new HashSet<>();
        for (Part p : parts) {
            if (!partNames.add(p.name())) out.add("duplicate part " + p.name());
            for (String b : p.bones()) {
                Bone bone = byName.get(b);
                if (bone == null) out.add("part " + p.name() + " uses unknown bone " + b);
                else if (bone.required()) out.add("part " + p.name() + " would hide required bone " + b);
            }
        }
        for (SharedAnimation a : animations.values()) if (a.ticks() <= 0) out.add("animation " + a.key() + " without duration");
        for (String b : sharedBones) if (!byName.containsKey(b)) out.add("shared animation bone " + b + " is not in the rig");
        return out;
    }

    public Optional<Bone> bone(String name) {
        for (Bone b : bones) if (b.name().equals(name)) return Optional.of(b);
        return Optional.empty();
    }

    public Optional<Socket> socket(String name) {
        for (Socket s : sockets) if (s.name().equals(name)) return Optional.of(s);
        return Optional.empty();
    }

    public Optional<Part> part(String name) {
        for (Part p : parts) if (p.name().equals(name)) return Optional.of(p);
        return Optional.empty();
    }

    /** Nome GeckoLib da animacao compartilhada desta chave. */
    public String animationName(String key) { return animationPrefix + key; }

    // ------------------------------------------------------------------ manifesto JSON

    public static RigDefinition parse(JsonObject json) {
        if (!FORMAT.equals(string(json, "format", ""))) throw new IllegalArgumentException("Not a rig manifest (format)");
        if (json.has("format_version") && json.get("format_version").getAsInt() > FORMAT_VERSION) {
            throw new IllegalArgumentException("Rig manifest format_version " + json.get("format_version") + " is newer than " + FORMAT_VERSION);
        }
        List<Bone> bones = new ArrayList<>();
        for (JsonElement e : array(json, "bones")) {
            JsonObject b = e.getAsJsonObject();
            bones.add(new Bone(string(b, "name", null), b.has("parent") && !b.get("parent").isJsonNull()
                    ? b.get("parent").getAsString() : null, vec(b, "pivot"), bool(b, "required", false), bool(b, "strict", false)));
        }
        List<Socket> sockets = new ArrayList<>();
        for (JsonElement e : array(json, "sockets")) {
            JsonObject s = e.getAsJsonObject();
            sockets.add(new Socket(string(s, "name", null), string(s, "bone", null), vec(s, "offset"), string(s, "kind", "")));
        }
        List<Part> parts = new ArrayList<>();
        for (JsonElement e : array(json, "parts")) parts.add(part(e.getAsJsonObject()));
        Map<String, SharedAnimation> animations = new LinkedHashMap<>();
        if (json.has("animations")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("animations").entrySet()) {
                JsonObject a = e.getValue().getAsJsonObject();
                animations.put(e.getKey(), new SharedAnimation(e.getKey(), a.get("ticks").getAsInt(), bool(a, "loop", false)));
            }
        }
        return new RigDefinition(ResourceLocation.parse(string(json, "id", "")), json.get("contract_version").getAsInt(),
                number(json, "reference_height", 32), number(json, "pivot_tolerance", 2), string(json, "controller", null),
                string(json, "animation_prefix", ""), ResourceLocation.parse(string(json, "animation_file", "")),
                ids(array(json, "capabilities")), strings(array(json, "optional_prefixes")), bones, sockets, parts,
                alignment(json), animations, new LinkedHashSet<>(strings(array(json, "shared_animation_bones"))));
    }

    static Part part(JsonObject p) {
        return new Part(string(p, "name", null), strings(array(p, "bones")), bool(p, "default_visible", true), bool(p, "detachable", false));
    }

    static EntityFinisherClip.Alignment alignment(JsonObject json) {
        if (!json.has("alignment") || json.get("alignment").isJsonNull()) return null;
        JsonObject a = json.getAsJsonObject("alignment");
        double distance = a.has("distance") && !a.get("distance").isJsonNull() ? a.get("distance").getAsDouble() : Double.NaN;
        return new EntityFinisherClip.Alignment(distance, number(a, "lateral", 0), (float) number(a, "yaw_offset", 0));
    }

    static JsonArray array(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonArray() ? json.getAsJsonArray(key) : new JsonArray();
    }

    static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsBoolean() : fallback;
    }

    static double number(JsonObject json, String key, double fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble() : fallback;
    }

    static Vec3 vec(JsonObject json, String key) {
        if (!json.has(key)) return Vec3.ZERO;
        JsonArray a = json.getAsJsonArray(key);
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : array) out.add(e.getAsString());
        return out;
    }

    static Set<ResourceLocation> ids(JsonArray array) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (JsonElement e : array) out.add(ResourceLocation.parse(e.getAsString()));
        return out;
    }
}
