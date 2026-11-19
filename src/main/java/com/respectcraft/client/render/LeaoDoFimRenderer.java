package com.respectcraft.client.render;

import com.respectcraft.RespectCraft;
import com.respectcraft.entity.LeaoDoFimEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Client only. Retains the authored head animation instead of overriding head rotation. */
public class LeaoDoFimRenderer extends GeoEntityRenderer<LeaoDoFimEntity> {
    public LeaoDoFimRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(RespectCraft.id("leao_do_fim"), false));
        this.shadowRadius = 0.8F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
