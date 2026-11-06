package com.tupinicraft.client;

import com.tupinicraft.TupiniCraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import com.tupinicraft.client.compat.SkillPanelPurchaseHook;
import com.tupinicraft.client.render.CaboEleitoralRenderer;
import com.tupinicraft.registry.ModEntities;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Ponto de entrada exclusivo do cliente (não é carregado no servidor dedicado). */
@Mod(value = TupiniCraft.MOD_ID, dist = Dist.CLIENT)
public final class TupiniCraftClient {
    public TupiniCraftClient(IEventBus modBus) {
        modBus.addListener(PoliticalHud::registerLayers);
        modBus.addListener(TupiniCraftClient::registerRenderers);
        NeoForge.EVENT_BUS.addListener(TupiniCraftClient::onLoggingOut);
        // Só mede a área das barras de chefe; quem desenha é a camada registrada em PoliticalHud.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BossBarArea::onBossEventProgress);
        // Clique numa skill bloqueada no painel do SkillLib (tecla K) = pedido de compra ao servidor.
        NeoForge.EVENT_BUS.addListener(SkillPanelPurchaseHook::onMouseClick);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CABO_ELEITORAL.get(), CaboEleitoralRenderer::new);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPoliticalCache.clear();
        ClientElectionCache.clear();
        AnnouncementOverlay.clear();
    }
}
