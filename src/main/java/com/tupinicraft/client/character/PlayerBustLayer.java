package com.tupinicraft.client.character;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tupinicraft.character.CharacterAppearance;
import com.tupinicraft.network.CharacterAppearanceClientCache;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Teste de silhueta feminina vestido: copia a regiao superior do torso da skin atual
 * para um pequeno volume voxel sobre o modelo vanilla.
 *
 * Este e um prototipo, NAO altera a skin nem os dados do jogador. O volume foi adaptado
 * do bone FemaleBust do BBMODEL fornecido; as UVs podem ficar esticadas com skins reais.
 * TODO: implementar o overlay exportado completo do GeckoLib apos validar as UVs.
 */
public final class PlayerBustLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final ModelPart bust;

    public PlayerBustLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        MeshDefinition mesh = new MeshDefinition();
        // Coordenadas locais ao bone body do modelo vanilla: Y=-12..0; frente em Z=-2.
        // Prototipo vestido com uma pequena saliencia na frente do tronco.
        mesh.getRoot().addOrReplaceChild("bust",
                CubeListBuilder.create().texOffs(20, 22)
                        .addBox(-3.75F, -9.5F, -3.5F, 7.5F, 3.5F, 1.6F),
                PartPose.ZERO);
        this.bust = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("bust");
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        CharacterAppearance appearance = CharacterAppearanceClientCache.get(player.getUUID());
        if (!appearance.created() || appearance.bodyType() != 1) return;

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        VertexConsumer vertex = buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkin().texture()));
        bust.render(poseStack, vertex, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
