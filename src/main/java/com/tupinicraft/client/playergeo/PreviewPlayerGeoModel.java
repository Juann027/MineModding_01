package com.tupinicraft.client.playergeo;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.character.CharacterAppearance;
import com.tupinicraft.network.CharacterAppearanceClientCache;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/** Usa a skin dinamica do jogador e os assets exportados do Blockbench, sem skin Steve fixa. */
public final class PreviewPlayerGeoModel extends GeoModel<PreviewPlayerAnimatable> {
    private static final ResourceLocation GEO = TupiniCraft.id("geo/player/tupinicraft_animated_player.geo.json");
    private static final ResourceLocation ANIM = TupiniCraft.id("animations/player/tupinicraft_animated_player.animation.json");
    private static final ResourceLocation FALLBACK = TupiniCraft.id("textures/entity/player_test/tupinicraft_test_skin_classic.png");

    @Override
    public ResourceLocation getModelResource(PreviewPlayerAnimatable animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(PreviewPlayerAnimatable animatable) {
        AbstractClientPlayer player = animatable.player();
        return player == null ? FALLBACK : player.getSkin().texture();
    }

    @Override
    public ResourceLocation getAnimationResource(PreviewPlayerAnimatable animatable) {
        return ANIM;
    }

    @Override
    public void setCustomAnimations(PreviewPlayerAnimatable animatable, long instanceId,
                                    AnimationState<PreviewPlayerAnimatable> animationState) {
        AbstractClientPlayer player = animatable.player();
        if (player == null) return;
        boolean slim = player.getSkin().model().name().equalsIgnoreCase("SLIM");
        CharacterAppearance appearance = CharacterAppearanceClientCache.get(player.getUUID());
        boolean female = appearance.created() && appearance.bodyType() == 1;

        setHidden("RightArmClassic", slim);
        setHidden("LeftArmClassic", slim);
        setHidden("RightArmLayerClassic", slim);
        setHidden("LeftArmLayerClassic", slim);
        setHidden("RightArmSlim", !slim);
        setHidden("LeftArmSlim", !slim);
        setHidden("RightArmLayerSlim", !slim);
        setHidden("LeftArmLayerSlim", !slim);
        setHidden("FemaleBust", !female);
        setHidden("FemaleBustLayer", !female);
        // A geometria provisoria de cabelo usa uma regiao da skin vanilla que pode ter pixels arbitrarios.
        // Oculta-se ate existir uma textura separada e um layer Geo com troca de textura por bone.
        setHidden("HairPlaceholder", true);
    }

    private void setHidden(String bone, boolean hidden) {
        getBone(bone).ifPresent(b -> b.setHidden(hidden));
    }
}
