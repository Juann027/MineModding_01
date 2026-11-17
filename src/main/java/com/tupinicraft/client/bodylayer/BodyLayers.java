package com.tupinicraft.client.bodylayer;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.character.CharacterAppearance;
import com.tupinicraft.client.bodylayer.BodyLayerDefinition.GeoGeometry;
import com.tupinicraft.client.bodylayer.BodyLayerDefinition.PartGeometry;
import com.tupinicraft.client.playergeo.PlayerPart;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/** Catalogo das Body Layers (cliente). Para uma peca nova basta registrar uma definicao; o renderizador nao muda. */
public final class BodyLayers {
    /** Geometrias extras exportadas do mesmo .bbmodel do rig (busto; o cabelo provisorio fica sem uso). */
    public static final ResourceLocation OVERLAYS_GEO = TupiniCraft.id("geo/player/tupinicraft_player_overlays.geo.json");
    private static final ResourceLocation HAIR_TEXTURE = TupiniCraft.id("textures/entity/character/hair_white.png");

    /** Bone de um modelo Blockbench ja localizado e a parte do corpo que ele acompanha. */
    record ResolvedBone(GeoBone bone, PlayerPart part) {}

    private static final Map<ResourceLocation, BodyLayerDefinition> REGISTRY = new LinkedHashMap<>();
    private static final Map<ResourceLocation, List<ResolvedBone>> RESOLVED = new HashMap<>();
    private static boolean defaultsRegistered;

    private BodyLayers() {}

    public static synchronized BodyLayerDefinition register(BodyLayerDefinition definition) {
        if (REGISTRY.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("Body Layer duplicada: " + definition.id());
        }
        return definition;
    }

    public static Collection<BodyLayerDefinition> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** Pecas do proprio TupiniCraft: busto da variante feminina e os seis penteados. */
    public static synchronized void registerDefaults() {
        if (defaultsRegistered) return;
        defaultsRegistered = true;
        register(new BodyLayerDefinition(TupiniCraft.id("female_bust"),
                new GeoGeometry(OVERLAYS_GEO, Map.of("FemaleBust", PlayerPart.BODY)),
                ctx -> ctx.appearance().created() && ctx.appearance().bodyType() == 1,
                ctx -> ctx.player().getSkin().texture(),
                ctx -> -1));
        for (int style = 1; style < CharacterAppearance.HAIR_IDS.length; style++) {
            int hairStyle = style;
            register(new BodyLayerDefinition(TupiniCraft.id("hair/" + CharacterAppearance.HAIR_IDS[style]),
                    new PartGeometry(PlayerPart.HEAD, HairMeshes.build(style)),
                    ctx -> ctx.appearance().created() && ctx.appearance().hairStyle() == hairStyle,
                    ctx -> HAIR_TEXTURE,
                    ctx -> CharacterAppearance.COLOR_ARGB[ctx.appearance().hairColor()]));
        }
    }

    /** Recursos recarregados: os modelos Blockbench sao localizados e validados de novo. */
    public static synchronized void onResourceReload() {
        RESOLVED.clear();
    }

    /** Bones do modelo Blockbench de uma peca; lista vazia (e erro no log, uma vez) se o recurso for invalido. */
    static synchronized List<ResolvedBone> resolve(BodyLayerDefinition definition, GeoGeometry geometry) {
        List<ResolvedBone> cached = RESOLVED.get(definition.id());
        if (cached != null) return cached;
        List<ResolvedBone> bones = new ArrayList<>();
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(geometry.model());
        if (model == null) {
            TupiniCraft.LOGGER.error("Body Layer {} desativada: modelo {} não encontrado", definition.id(), geometry.model());
        } else {
            geometry.bones().forEach((name, part) -> model.getBone(name).ifPresentOrElse(
                    bone -> bones.add(new ResolvedBone(bone, part)),
                    () -> TupiniCraft.LOGGER.error("Body Layer {}: bone '{}' não existe em {}",
                            definition.id(), name, geometry.model())));
        }
        RESOLVED.put(definition.id(), List.copyOf(bones));
        return bones;
    }
}
