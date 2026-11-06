package com.tupinicraft.client.render;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.entity.CaboEleitoralEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renderer GeckoLib (só cliente). O modelo padrão procura, pelo nome "cabo_eleitoral":
 * geo/entity/cabo_eleitoral.geo.json, animations/entity/cabo_eleitoral.animation.json e
 * textures/entity/cabo_eleitoral.png em assets/tupinicraft. O "true" faz o osso "head" seguir o olhar.
 */
public class CaboEleitoralRenderer extends GeoEntityRenderer<CaboEleitoralEntity> {
    public CaboEleitoralRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(TupiniCraft.id("cabo_eleitoral"), true));
    }
}
