package com.tupinicraft.client.render;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.entity.LeaoDoFimEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Client only. Retains the authored head animation instead of overriding head rotation. */
public class LeaoDoFimRenderer extends GeoEntityRenderer<LeaoDoFimEntity> {
    public LeaoDoFimRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(TupiniCraft.id("leao_do_fim"), false));
        this.shadowRadius = 0.8F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
