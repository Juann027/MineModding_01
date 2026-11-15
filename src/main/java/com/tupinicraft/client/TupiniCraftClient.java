package com.tupinicraft.client;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.client.character.CharacterCreatorKeybinds;
import com.tupinicraft.client.character.PlayerHairLayer;
import com.tupinicraft.client.hud.BodyStatusInventoryPanel;
import com.tupinicraft.client.hud.EmotionOrbOverlay;
import com.tupinicraft.client.render.CaboEleitoralRenderer;
import com.tupinicraft.client.render.LeaoDoFimRenderer;
import com.tupinicraft.client.testgame.DefeatCounterNameplate;
import com.tupinicraft.network.CharacterAppearanceClientCache;
import com.tupinicraft.network.DefeatCountClientCache;
import com.tupinicraft.network.FinisherVisualClientCache;
import com.tupinicraft.registry.ModEntities;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Renderização GeckoLib isolada do servidor dedicado. */
@Mod(value = TupiniCraft.MOD_ID, dist = Dist.CLIENT)
public final class TupiniCraftClient {
    public TupiniCraftClient(IEventBus modBus) {
        modBus.addListener(TupiniCraftClient::registerRenderers);
        modBus.addListener(CharacterCreatorKeybinds::register);
        modBus.addListener(TupiniCraftClient::registerHairLayers);
        NeoForge.EVENT_BUS.addListener(CharacterCreatorKeybinds::onTick);
        modBus.addListener(TupiniCraftClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(BodyStatusInventoryPanel::onInventoryForeground);
        NeoForge.EVENT_BUS.addListener(DefeatCounterNameplate::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            DefeatCountClientCache.clear();
            CharacterAppearanceClientCache.clear();
            FinisherVisualClientCache.clear();
        });
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, TupiniCraft.id("emotion_orb"),
                (graphics, deltaTracker) -> EmotionOrbOverlay.render(graphics));
    }

    private static void registerHairLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new PlayerHairLayer(renderer));
            }
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CABO_ELEITORAL.get(), CaboEleitoralRenderer::new);
        event.registerEntityRenderer(ModEntities.LEAO_DO_FIM.get(), LeaoDoFimRenderer::new);
    }
}
