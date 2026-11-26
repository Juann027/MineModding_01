package com.respectcraft.rig;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.respectcraft.finisher.FinisherRole;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Registro das familias de rig e das entidades associadas a elas (Etapa 4.12). Catalogo de dados imutaveis do jar
 * (como {@code Finishers}): preenchido no construtor do mod por {@link Rigs}, igual nos dois lados, nunca estado de
 * mundo. {@link #GLOBAL} e o do jogo; o {@code SelfTest} cria registros proprios para nao sujar o catalogo.
 */
public final class CustomRigRegistry {
    public static final CustomRigRegistry GLOBAL = new CustomRigRegistry();

    private final Map<ResourceLocation, RigDefinition> rigs = new LinkedHashMap<>();
    private final Map<ResourceLocation, RigBinding> bindings = new LinkedHashMap<>();

    public synchronized RigDefinition register(RigDefinition rig) {
        if (rigs.putIfAbsent(rig.id(), rig) != null) throw new IllegalStateException("Duplicate rig: " + rig.id());
        return rig;
    }

    /** Associa um tipo de entidade a uma familia ja registrada; recusa binding incoerente com o contrato. */
    public synchronized RigBinding bind(RigBinding binding) {
        RigDefinition rig = rigs.get(binding.rig());
        if (rig == null) throw new IllegalStateException("Binding of " + binding.entityType() + " to unknown rig " + binding.rig());
        List<String> problems = bindingProblems(rig, binding);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Invalid rig binding " + binding.entityType() + ": " + String.join("; ", problems));
        }
        if (bindings.putIfAbsent(binding.entityType(), binding) != null) {
            throw new IllegalStateException("Entity type already bound to a rig: " + binding.entityType());
        }
        return binding;
    }

    /** Regras puras do binding contra o contrato (versao, extras com prefixo livre e pai existente, partes, sockets). */
    public static List<String> bindingProblems(RigDefinition rig, RigBinding binding) {
        List<String> out = new ArrayList<>();
        if (binding.contractVersion() != rig.contractVersion()) {
            out.add("contract_version " + binding.contractVersion() + " but rig " + rig.id() + " is " + rig.contractVersion());
        }
        Set<String> names = new HashSet<>();
        for (RigDefinition.Bone b : rig.bones()) names.add(b.name());
        for (RigDefinition.Bone b : binding.extraBones()) {
            if (!names.add(b.name())) out.add("extra bone " + b.name() + " duplicates a bone");
            if (rig.optionalPrefixes().stream().noneMatch(p -> b.name().startsWith(p))) {
                out.add("extra bone " + b.name() + " must start with one of " + rig.optionalPrefixes());
            }
        }
        for (RigDefinition.Bone b : binding.extraBones()) {
            if (b.parent() == null || !names.contains(b.parent())) out.add("extra bone " + b.name() + " has unknown parent " + b.parent());
        }
        Set<String> partNames = new HashSet<>();
        for (RigDefinition.Part p : rig.parts()) partNames.add(p.name());
        for (RigDefinition.Part p : binding.parts()) {
            if (!partNames.add(p.name())) out.add("part " + p.name() + " already exists in the rig");
            for (String b : p.bones()) if (!names.contains(b)) out.add("part " + p.name() + " uses unknown bone " + b);
            for (String b : p.bones()) if (rig.bone(b).map(RigDefinition.Bone::required).orElse(false)) out.add("part " + p.name() + " would hide required bone " + b);
        }
        for (RigDefinition.Socket s : binding.sockets()) {
            if (!names.contains(s.bone())) out.add("socket " + s.name() + " on unknown bone " + s.bone());
        }
        Set<String> overrides = new HashSet<>();
        for (RigBinding.ClipOverride o : binding.clipOverrides()) {
            if (!overrides.add(o.finisher() + "/" + o.role())) out.add("repeated clip override " + o.finisher() + " (" + o.role() + ")");
        }
        return out;
    }

    public Optional<RigDefinition> rig(ResourceLocation id) { return Optional.ofNullable(rigs.get(id)); }

    public Optional<RigBinding> binding(ResourceLocation entityType) {
        return entityType == null ? Optional.empty() : Optional.ofNullable(bindings.get(entityType));
    }

    public Collection<RigDefinition> rigs() { return Collections.unmodifiableCollection(rigs.values()); }

    public Collection<RigBinding> bindings() { return Collections.unmodifiableCollection(bindings.values()); }

    public List<RigBinding> bindingsOf(ResourceLocation rig) {
        return bindings.values().stream().filter(b -> b.rig().equals(rig)).toList();
    }

    /** Capacidades efetivas de uma entidade associada: as da familia + as do binding. */
    public Set<ResourceLocation> capabilities(RigBinding binding) {
        Set<ResourceLocation> out = new LinkedHashSet<>(rig(binding.rig()).map(RigDefinition::capabilities).orElse(Set.of()));
        out.addAll(binding.capabilities());
        return out;
    }

    /** Partes efetivas: as da familia + as do binding. */
    public List<RigDefinition.Part> parts(RigBinding binding) {
        List<RigDefinition.Part> out = new ArrayList<>(rig(binding.rig()).map(RigDefinition::parts).orElse(List.of()));
        out.addAll(binding.parts());
        return out;
    }

    public Optional<RigDefinition.Part> part(RigBinding binding, String name) {
        for (RigDefinition.Part p : parts(binding)) if (p.name().equals(name)) return Optional.of(p);
        return Optional.empty();
    }

    /** Socket efetivo: o do binding (override) ou o da familia. */
    public Optional<RigDefinition.Socket> socket(RigBinding binding, String name) {
        for (RigDefinition.Socket s : binding.sockets()) if (s.name().equals(name)) return Optional.of(s);
        return rig(binding.rig()).flatMap(r -> r.socket(name));
    }

    /** Pivo de um bone no modelo desta entidade (contrato × proporcao; extras como declarados). */
    public Optional<net.minecraft.world.phys.Vec3> pivot(RigBinding binding, String bone) {
        for (RigDefinition.Bone b : binding.extraBones()) if (b.name().equals(bone)) return Optional.of(b.pivot());
        return rig(binding.rig()).flatMap(r -> r.bone(bone)).map(b -> b.strict() ? b.pivot()
                : b.pivot().multiply(binding.proportion()));
    }

    /**
     * Nomes de animacao que o controlador da familia precisa aceitar como gatilho nesta entidade: as compartilhadas da
     * familia (nao em laco) e os overrides do binding. A entidade os registra em {@code triggerableAnim}.
     */
    public Set<String> triggerables(RigBinding binding) {
        Set<String> out = new LinkedHashSet<>();
        rig(binding.rig()).ifPresent(r -> r.animations().values().stream().filter(a -> !a.loop())
                .forEach(a -> out.add(r.animationName(a.key()))));
        for (RigBinding.ClipOverride o : binding.clipOverrides()) out.add(o.animation());
        return out;
    }

    /** Arquivo onde esta animacao mora para esta entidade: o proprio (override) ou o da familia. */
    public ResourceLocation animationFile(RigBinding binding, String animation) {
        for (RigBinding.ClipOverride o : binding.clipOverrides()) {
            if (o.animation().equals(animation) && binding.animationFile() != null) return binding.animationFile();
        }
        return rig(binding.rig()).map(RigDefinition::animationFile).orElse(binding.animationFile());
    }

    /** Override do binding para esta finalizacao e papel. */
    public Optional<RigBinding.ClipOverride> override(RigBinding binding, ResourceLocation finisher, FinisherRole role) {
        return binding.clipOverride(finisher, role);
    }

    // ------------------------------------------------------------------ manifestos do jar

    /** Le {@code assets/<ns>/rigs/<path>.rig.json} do jar (servidor e cliente). */
    public static RigDefinition loadRig(ResourceLocation id) {
        RigDefinition rig = RigDefinition.parse(read("/assets/" + id.getNamespace() + "/rigs/" + id.getPath() + ".rig.json"));
        if (!rig.id().equals(id)) throw new IllegalArgumentException("Rig manifest " + id + " declares id " + rig.id());
        return rig;
    }

    /** Le {@code assets/<ns>/rigs/entities/<path>.json} do jar. */
    public static RigBinding loadBinding(ResourceLocation entityType) {
        RigBinding binding = RigBinding.parse(read("/assets/" + entityType.getNamespace() + "/rigs/entities/" + entityType.getPath() + ".json"));
        if (!binding.entityType().equals(entityType)) {
            throw new IllegalArgumentException("Binding manifest " + entityType + " declares entity " + binding.entityType());
        }
        return binding;
    }

    private static JsonObject read(String path) {
        try (InputStream in = CustomRigRegistry.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Rig manifest not found in the jar: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read rig manifest " + path, e);
        }
    }
}
