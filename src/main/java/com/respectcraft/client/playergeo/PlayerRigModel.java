package com.respectcraft.client.playergeo;

import com.respectcraft.RespectCraft;
import com.respectcraft.animation.PlayerAnimations;
import com.respectcraft.client.animation.ClientPlayerAnimations;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Matrix4f;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.model.GeoModel;

/**
 * Modelo GeckoLib do jogador: skin da propria conta, bracos Classic/Slim e camadas externas conforme as
 * opcoes de skin, e a pose final = pose vanilla (andar, agachar, olhar, atacar) combinada com o clip.
 */
public final class PlayerRigModel extends GeoModel<PlayerRigAnimatable> {
    private static final ResourceLocation FALLBACK =
            RespectCraft.id("textures/entity/player_test/respectcraft_test_skin_classic.png");

    @Override
    public ResourceLocation getModelResource(PlayerRigAnimatable animatable) {
        return ClientPlayerAnimations.RIG_GEO;
    }

    @Override
    public ResourceLocation getTextureResource(PlayerRigAnimatable animatable) {
        AbstractClientPlayer player = animatable.player();
        return player == null ? FALLBACK : player.getSkin().texture();
    }

    @Override
    public ResourceLocation getAnimationResource(PlayerRigAnimatable animatable) {
        return PlayerAnimations.PLAYER_FILE;
    }

    /** Cada animacao registrada diz em que arquivo esta o seu clip; o rig aceita varios arquivos. */
    @Override
    public Animation getAnimation(PlayerRigAnimatable animatable, String name) {
        ResourceLocation file = ClientPlayerAnimations.fileOfClip(name);
        BakedAnimations baked = file == null ? null : GeckoLibCache.getBakedAnimations().get(file);
        Animation animation = baked == null ? null : baked.getAnimation(name);
        return animation != null ? animation : super.getAnimation(animatable, name);
    }

    @Override
    public void handleAnimations(PlayerRigAnimatable animatable, long instanceId,
                                 AnimationState<PlayerRigAnimatable> animationState, float partialTick) {
        // O GeckoLib calcula so o clip (e pode pular o calculo num segundo desenho do mesmo quadro);
        // a combinacao com a pose vanilla e refeita sempre, a partir dos valores puros guardados.
        super.handleAnimations(animatable, instanceId, animationState, partialTick);
        compose(animatable);
    }

    @Override
    public void setCustomAnimations(PlayerRigAnimatable animatable, long instanceId,
                                    AnimationState<PlayerRigAnimatable> animationState) {
        for (GeoBone bone : getAnimationProcessor().getRegisteredBones()) {
            float[] values = animatable.clipValues.computeIfAbsent(bone.getName(), name -> new float[6]);
            values[0] = bone.getRotX();
            values[1] = bone.getRotY();
            values[2] = bone.getRotZ();
            values[3] = bone.getPosX();
            values[4] = bone.getPosY();
            values[5] = bone.getPosZ();
        }
    }

    private void compose(PlayerRigAnimatable animatable) {
        for (GeoBone bone : getAnimationProcessor().getRegisteredBones()) {
            float[] values = animatable.clipValues.get(bone.getName());
            if (values == null) continue;
            bone.setRotX(values[0]);
            bone.setRotY(values[1]);
            bone.setRotZ(values[2]);
            bone.setPosX(values[3]);
            bone.setPosY(values[4]);
            bone.setPosZ(values[5]);
        }
        applyVisibility(animatable.player());
        if (!animatable.hasVanillaPose) {
            getAnimationProcessor().getRegisteredBones().forEach(GeoBone::resetStateChanges);
            return;
        }

        Matrix4f bodyInverse = new Matrix4f(animatable.vanillaPose[PlayerPart.BODY.ordinal()]).invert();
        for (PlayerPart part : PlayerPart.values()) {
            GeoBone bone = getBone(part.boneName()).orElse(null);
            if (bone == null) continue;
            Matrix4f pose = animatable.vanillaPose[part.ordinal()];
            // Cabeca e bracos sao filhos do tronco no rig: a pose vanilla deles entra relativa ao tronco vanilla.
            Matrix4f local = part.childOfBody() ? new Matrix4f(bodyInverse).mul(pose) : pose;
            PlayerPoseBridge.addToBone(bone, local, 1F - animatable.overrideWeight[part.ordinal()]);
        }
        // Os setters do GeoBone marcam o bone como "alterado neste quadro"; sem limpar a marca, o GeckoLib
        // deixaria de recalcular os bones sem animacao e a pose vanilla seria somada de novo a cada quadro.
        getAnimationProcessor().getRegisteredBones().forEach(GeoBone::resetStateChanges);
    }

    private void applyVisibility(AbstractClientPlayer player) {
        if (player == null) return;
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        boolean sleeveRight = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        boolean sleeveLeft = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        setHidden("RightArmClassic", slim);
        setHidden("LeftArmClassic", slim);
        setHidden("RightArmSlim", !slim);
        setHidden("LeftArmSlim", !slim);
        setHidden("RightArmLayerClassic", slim || !sleeveRight);
        setHidden("LeftArmLayerClassic", slim || !sleeveLeft);
        setHidden("RightArmLayerSlim", !slim || !sleeveRight);
        setHidden("LeftArmLayerSlim", !slim || !sleeveLeft);
        setHidden("HatLayer", !player.isModelPartShown(PlayerModelPart.HAT));
        setHidden("BodyLayer", !player.isModelPartShown(PlayerModelPart.JACKET));
        setHidden("RightLegLayer", !player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG));
        setHidden("LeftLegLayer", !player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG));
        // Busto e cabelo sao Body Layers (desenhadas uma unica vez, pela camada do PlayerRenderer, nos dois modos).
        setHidden("FemaleBust", true);
        setHidden("HairPlaceholder", true);
    }

    private void setHidden(String bone, boolean hidden) {
        getBone(bone).ifPresent(b -> b.setHidden(hidden));
    }
}
