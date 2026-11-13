package com.tupinicraft.client;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.client.render.CaboEleitoralRenderer;
import com.tupinicraft.client.render.LeaoDoFimRenderer;
import com.tupinicraft.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Renderização GeckoLib isolada do servidor dedicado. */
@Mod(value = TupiniCraft.MOD_ID, dist = Dist.CLIENT)
public final class TupiniCraftClient {
    public TupiniCraftClient(IEventBus modBus) {
        modBus.addListener(TupiniCraftClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CABO_ELEITORAL.get(), CaboEleitoralRenderer::new);
        event.registerEntityRenderer(ModEntities.LEAO_DO_FIM.get(), LeaoDoFimRenderer::new);
    }
}
