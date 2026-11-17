package com.tupinicraft.client.playergeo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Primeira camada do PlayerRenderer. Quando o rig esta ativo para o jogador:
 * <ol>
 *   <li>le a pose que o vanilla acabou de calcular (andar, correr, agachar, olhar, atacar, segurar item);</li>
 *   <li>desenha o rig GeckoLib com a skin do jogador, combinando essa pose com o clip em andamento;</li>
 *   <li>devolve a pose final ao PlayerModel, para as camadas seguintes (armadura, itens nas maos, capa,
 *       elytra, cabeca customizada, Body Layers do TupiniCraft, camadas de outros mods) acompanharem o rig.</li>
 * </ol>
 * O corpo vanilla nao e desenhado nesse quadro ({@code skipDraw}, ligado em {@link PlayerRigController}).
 */
public final class PlayerRigLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final PlayerRigRenderer RENDERER = new PlayerRigRenderer();

    public PlayerRigLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (!PlayerRigController.isRigFrame(player)) return;
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        PlayerRigAnimatable rig = PlayerRigController.rig(player);
        rig.beginFrame(model);

        stack.pushPose();
        PlayerPoseBridge.vanillaToGeoSpace(stack);
        // Mesmo tipo de render do jogador vanilla (camadas externas da skin podem ter transparencia).
        RenderType type = RenderType.entityTranslucent(player.getSkin().texture());
        RENDERER.render(stack, rig, buffers, type, buffers.getBuffer(type), packedLight, partialTick);
        stack.popPose();

        PlayerRigModel rigModel = (PlayerRigModel) RENDERER.getGeoModel();
        for (PlayerPart part : PlayerPart.values()) {
            GeoBone bone = rigModel.getBone(part.boneName()).orElse(null);
            if (bone != null) PlayerPoseBridge.writeVanilla(part.vanilla(model), part.rest(), PlayerPoseBridge.boneWorld(bone));
        }
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }
}
