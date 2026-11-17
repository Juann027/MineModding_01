package com.tupinicraft.client.bodylayer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tupinicraft.client.bodylayer.BodyLayerDefinition.GeoGeometry;
import com.tupinicraft.client.bodylayer.BodyLayerDefinition.PartGeometry;
import com.tupinicraft.network.CharacterAppearanceClientCache;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.joml.Vector3f;
import com.tupinicraft.client.playergeo.PlayerPoseBridge;

/**
 * Unica camada que desenha as Body Layers. Usa a pose do PlayerModel, que e a vanilla no jogo normal e a do
 * rig GeckoLib durante as animacoes (o {@code PlayerRigLayer} a escreve antes). Por isso cada peca e desenhada
 * uma so vez nos dois modos e nunca fica presa na pose anterior.
 */
public final class BodyLayersRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float PX = 1F / 16F;

    public BodyLayersRenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        BodyLayerDefinition.Context context = new BodyLayerDefinition.Context(player,
                CharacterAppearanceClientCache.get(player.getUUID()));
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0F);

        for (BodyLayerDefinition definition : BodyLayers.all()) {
            if (!definition.visible().test(context)) continue;
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(definition.texture().apply(context)));
            int tint = definition.tint().applyAsInt(context);
            switch (definition.geometry()) {
                case PartGeometry geometry -> {
                    stack.pushPose();
                    geometry.attach().vanilla(model).translateAndRotate(stack);
                    geometry.mesh().render(stack, buffer, packedLight, overlay, tint);
                    stack.popPose();
                }
                case GeoGeometry geometry -> {
                    for (BodyLayers.ResolvedBone resolved : BodyLayers.resolve(definition, geometry)) {
                        ModelPart part = resolved.part().vanilla(model);
                        Vector3f rest = resolved.part().rest();
                        stack.pushPose();
                        // Pose atual da parte, desfeita a posicao de repouso: sobra o deslocamento repouso -> atual,
                        // aplicado a geometria que esta nas coordenadas do rig.
                        part.translateAndRotate(stack);
                        stack.translate(-rest.x * PX, -rest.y * PX, -rest.z * PX);
                        PlayerPoseBridge.vanillaToGeoSpace(stack);
                        GeoMeshRenderer.renderBone(stack, resolved.bone(), buffer, packedLight, overlay, tint);
                        stack.popPose();
                    }
                }
            }
        }
    }
}
