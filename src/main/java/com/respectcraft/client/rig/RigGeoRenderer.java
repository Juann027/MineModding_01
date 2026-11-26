package com.respectcraft.client.rig;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * So cliente (Etapa 4.12). Renderer generico de entidades com rig customizada: {@link RigGeoModel} + a
 * {@code render_scale} do manifesto. Mob novo de uma familia registra so isto em {@code RespectCraftClient}.
 */
public class RigGeoRenderer<T extends LivingEntity & GeoEntity> extends GeoEntityRenderer<T> {
    public RigGeoRenderer(EntityRendererProvider.Context context, ResourceLocation entityType) {
        this(context, new RigGeoModel<>(entityType));
    }

    private final RigGeoModel<T> rigModel;

    private RigGeoRenderer(EntityRendererProvider.Context context, RigGeoModel<T> model) {
        super(context, model);
        this.rigModel = model;
        withScale(model.binding().renderScale());
        this.shadowRadius = 0.4F * model.binding().renderScale();
    }

    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          int colour) {
        if (!isReRender) rigModel.applyVisualState(animatable);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
