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
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/** Catalogo das Body Layers (cliente). Para uma peca nova basta registrar uma definicao; o renderizador nao muda. */
public final class BodyLayers {
    /** Geometrias extras exportadas do mesmo .bbmodel do rig (busto; o cabelo provisorio fica sem uso). */
    public static final ResourceLocation OVERLAYS_GEO = TupiniCraft.id("geo/player/tupinicraft_player_overlays.geo.json");
    private static final ResourceLocation HAIR_TEXTURE = TupiniCraft.id("textures/entity/character/hair_white.png");
    private static final ResourceLocation FACE_SKIN_TEXTURE = TupiniCraft.id("textures/entity/character/face_skin.png");
    private static final ResourceLocation SKIN_WHITE_TEXTURE = TupiniCraft.id("textures/entity/character/skin_white.png");

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
        registerFaceAndSkin();
    }

    /**
     * Etapa 3.1: tom de pele e rosto do criador no modelo 3D (jogo e prévias do criador e do menu K). Sao Body
     * Layers comuns, por cima da skin da conta, so na frente da cabeca e nas maos; o resto da skin continua o da
     * conta. As opcoes "conta" (ultimas) desligam cada camada e mostram a skin original.
     */
    private static void registerFaceAndSkin() {
        ToIntFunction<BodyLayerDefinition.Context> skinTint = ctx -> CharacterAppearance.SKIN_ARGB[ctx.appearance().skinTone()];
        // Base da pele do rosto: precisa existir tambem quando so o rosto e personalizado (tom da conta = neutro).
        register(new BodyLayerDefinition(TupiniCraft.id("face/skin"),
                new PartGeometry(PlayerPart.HEAD, FaceMeshes.plate(-4.04F)),
                ctx -> ctx.appearance().customSkinTone() || ctx.appearance().customFace(),
                ctx -> FACE_SKIN_TEXTURE,
                ctx -> ctx.appearance().customSkinTone() ? CharacterAppearance.SKIN_ARGB[ctx.appearance().skinTone()]
                        : CharacterAppearance.SKIN_ARGB[1]));
        for (int face = 0; face < CharacterAppearance.FACE_IDS.length; face++) {
            if (face == CharacterAppearance.FACE_FROM_ACCOUNT) continue;
            int faceId = face;
            ResourceLocation texture = TupiniCraft.id("textures/entity/character/face_" + CharacterAppearance.FACE_IDS[face] + ".png");
            register(new BodyLayerDefinition(TupiniCraft.id("face/" + CharacterAppearance.FACE_IDS[face]),
                    new PartGeometry(PlayerPart.HEAD, FaceMeshes.plate(-4.08F)),
                    ctx -> ctx.appearance().customFace() && ctx.appearance().face() == faceId,
                    ctx -> texture, ctx -> -1));
        }
        for (boolean slim : new boolean[] {false, true}) {
            String model = slim ? "slim" : "classic";
            Predicate<BodyLayerDefinition.Context> visible = ctx -> ctx.appearance().customSkinTone()
                    && (ctx.player().getSkin().model() == PlayerSkin.Model.SLIM) == slim;
            register(new BodyLayerDefinition(TupiniCraft.id("skin/right_hand_" + model),
                    new PartGeometry(PlayerPart.RIGHT_ARM, FaceMeshes.hand(true, slim)), visible, ctx -> SKIN_WHITE_TEXTURE, skinTint));
            register(new BodyLayerDefinition(TupiniCraft.id("skin/left_hand_" + model),
                    new PartGeometry(PlayerPart.LEFT_ARM, FaceMeshes.hand(false, slim)), visible, ctx -> SKIN_WHITE_TEXTURE, skinTint));
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
