package com.respectcraft.client.render;

import com.respectcraft.RespectCraft;
import com.respectcraft.entity.AguaVivaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * So cliente (Etapa 4.9). Um renderer por tamanho: {@code agua_viva} ou {@code agua_viva_filhote} (geo, animacao,
 * textura e {@code _glowmask} com o mesmo nome). Sem sombra (flutua) e sem tombar na morte: o clip death ja anima.
 */
public class AguaVivaRenderer extends GeoEntityRenderer<AguaVivaEntity> {
    public AguaVivaRenderer(EntityRendererProvider.Context context, String name) {
        super(context, new DefaultedEntityGeoModel<>(RespectCraft.id(name), false));
        this.shadowRadius = 0.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    public static AguaVivaRenderer giant(EntityRendererProvider.Context context) {
        return new AguaVivaRenderer(context, "agua_viva");
    }

    public static AguaVivaRenderer hatchling(EntityRendererProvider.Context context) {
        return new AguaVivaRenderer(context, "agua_viva_filhote");
    }

    @Override
    protected float getDeathMaxRotation(AguaVivaEntity animatable) {
        return 0.0F;
    }
}
