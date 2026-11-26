package com.respectcraft.rig;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.respectcraft.finisher.EntityFinisherClip;
import com.respectcraft.finisher.FinisherRole;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Associacao de um tipo de entidade a uma familia de rig (Etapa 4.12). So dados, do manifesto
 * {@code assets/<ns>/rigs/entities/<path>.json}: modelo, textura e animacoes proprias, proporcao em relacao ao modelo de
 * referencia, bones/capacidades/partes/sockets extras, alinhamento e clips proprios por finalizacao (overrides).
 *
 * <p>Compatibilidade e explicita: a entidade so usa as animacoes da familia porque declarou {@code rig} aqui (e o
 * validador conferiu hierarquia e pivos); nomes de bones iguais nunca bastam.
 *
 * @param proportion  escala por eixo do modelo em relacao ao de referencia (pivos esperados = contrato × proporcao)
 * @param renderScale escala aplicada no desenho (renderer) e nos sockets do servidor
 * @param alignment   palco proprio (null = o da familia)
 */
public record RigBinding(ResourceLocation entityType, ResourceLocation rig, int contractVersion, ResourceLocation geo,
                         ResourceLocation texture, ResourceLocation animationFile, Vec3 proportion, float renderScale,
                         List<RigDefinition.Bone> extraBones, Set<ResourceLocation> capabilities,
                         List<RigDefinition.Part> parts, List<RigDefinition.Socket> sockets,
                         EntityFinisherClip.Alignment alignment, List<ClipOverride> clipOverrides) {
    public static final String FORMAT = "respectcraft:rig_binding";

    /** Clip proprio da entidade para uma finalizacao e papel (vence o clip da familia). */
    public record ClipOverride(ResourceLocation finisher, FinisherRole role, String animation, int ticks) {
        public ClipOverride {
            if (animation == null || animation.isBlank()) throw new IllegalArgumentException("Override without animation: " + finisher);
            if (ticks <= 0) throw new IllegalArgumentException("Override without duration: " + finisher);
        }
    }

    public RigBinding {
        extraBones = List.copyOf(extraBones);
        capabilities = Set.copyOf(new LinkedHashSet<>(capabilities));
        parts = List.copyOf(parts);
        sockets = List.copyOf(sockets);
        clipOverrides = List.copyOf(clipOverrides);
        if (proportion == null) proportion = new Vec3(1, 1, 1);
        if (renderScale <= 0 || renderScale > 8) throw new IllegalArgumentException("render_scale out of range: " + entityType);
        if (proportion.x <= 0 || proportion.y <= 0 || proportion.z <= 0) throw new IllegalArgumentException("proportion must be positive: " + entityType);
    }

    public Optional<ClipOverride> clipOverride(ResourceLocation finisher, FinisherRole role) {
        for (ClipOverride o : clipOverrides) if (o.finisher().equals(finisher) && o.role() == role) return Optional.of(o);
        return Optional.empty();
    }

    public static RigBinding parse(JsonObject json) {
        if (!FORMAT.equals(RigDefinition.string(json, "format", ""))) throw new IllegalArgumentException("Not a rig binding manifest (format)");
        List<RigDefinition.Bone> extra = new ArrayList<>();
        for (JsonElement e : RigDefinition.array(json, "extra_bones")) {
            JsonObject b = e.getAsJsonObject();
            extra.add(new RigDefinition.Bone(RigDefinition.string(b, "name", null), RigDefinition.string(b, "parent", null),
                    RigDefinition.vec(b, "pivot"), false, false));
        }
        List<RigDefinition.Part> parts = new ArrayList<>();
        for (JsonElement e : RigDefinition.array(json, "parts")) parts.add(RigDefinition.part(e.getAsJsonObject()));
        List<RigDefinition.Socket> sockets = new ArrayList<>();
        for (JsonElement e : RigDefinition.array(json, "sockets")) {
            JsonObject s = e.getAsJsonObject();
            sockets.add(new RigDefinition.Socket(RigDefinition.string(s, "name", null), RigDefinition.string(s, "bone", null),
                    RigDefinition.vec(s, "offset"), RigDefinition.string(s, "kind", "")));
        }
        List<ClipOverride> overrides = new ArrayList<>();
        for (JsonElement e : RigDefinition.array(json, "clip_overrides")) {
            JsonObject o = e.getAsJsonObject();
            overrides.add(new ClipOverride(ResourceLocation.parse(o.get("finisher").getAsString()),
                    FinisherRole.valueOf(o.get("role").getAsString().toUpperCase(Locale.ROOT)),
                    o.get("animation").getAsString(), o.get("ticks").getAsInt()));
        }
        String anim = RigDefinition.string(json, "animation_file", null);
        Vec3 proportion = json.has("proportion") ? RigDefinition.vec(json, "proportion") : new Vec3(1, 1, 1);
        return new RigBinding(ResourceLocation.parse(json.get("entity").getAsString()), ResourceLocation.parse(json.get("rig").getAsString()),
                json.get("contract_version").getAsInt(), ResourceLocation.parse(json.get("geo").getAsString()),
                ResourceLocation.parse(json.get("texture").getAsString()), anim == null ? null : ResourceLocation.parse(anim),
                proportion, (float) RigDefinition.number(json, "render_scale", 1.0), extra,
                RigDefinition.ids(RigDefinition.array(json, "capabilities")), parts, sockets, RigDefinition.alignment(json), overrides);
    }
}
