package com.tupinicraft.client;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.client.character.CharacterCreatorKeybinds;
import com.tupinicraft.client.animation.ClientPlayerAnimations;
import com.tupinicraft.client.bodylayer.BodyLayers;
import com.tupinicraft.client.bodylayer.BodyLayersRenderLayer;
import com.tupinicraft.client.cinematic.CinematicCamera;
import com.tupinicraft.network.ProfileClientCache;
import com.tupinicraft.client.playergeo.PlayerRigController;
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
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Renderização GeckoLib isolada do servidor dedicado. */
@Mod(value = TupiniCraft.MOD_ID, dist = Dist.CLIENT)
public final class TupiniCraftClient {
    public TupiniCraftClient(IEventBus modBus) {
        modBus.addListener(TupiniCraftClient::registerRenderers);
        modBus.addListener(CharacterCreatorKeybinds::register);
        modBus.addListener(PlayerRigController::registerKeys);
        modBus.addListener(TupiniCraftClient::registerPlayerLayers);
        modBus.addListener(TupiniCraftClient::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(CharacterCreatorKeybinds::onTick);
        NeoForge.EVENT_BUS.addListener(PlayerRigController::onTick);
        NeoForge.EVENT_BUS.addListener(PlayerRigController::onRenderPre);
        NeoForge.EVENT_BUS.addListener(PlayerRigController::onRenderPost);
        NeoForge.EVENT_BUS.addListener(ClientPlayerAnimations::onTick);
        NeoForge.EVENT_BUS.addListener(ClientPlayerAnimations::onTimedEvent);
        // Finisher Engine: camera dinamica, tremor e FOV dos participantes.
        NeoForge.EVENT_BUS.addListener(CinematicCamera::onClientTick);
        NeoForge.EVENT_BUS.addListener(CinematicCamera::onTimedEvent);
        NeoForge.EVENT_BUS.addListener(CinematicCamera::onComputeFov);
        modBus.addListener(TupiniCraftClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(BodyStatusInventoryPanel::onInventoryForeground);
        NeoForge.EVENT_BUS.addListener(DefeatCounterNameplate::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            DefeatCountClientCache.clear();
            CharacterAppearanceClientCache.clear();
            FinisherVisualClientCache.clear();
            PlayerRigController.clear();
            ClientPlayerAnimations.clear();
            ProfileClientCache.clear();
            CinematicCamera.reset();
        });
        // Respawn e troca de dimensao recriam o jogador local: a camera nunca fica presa na cena.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.Clone event) -> CinematicCamera.reset());
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, TupiniCraft.id("emotion_orb"),
                (graphics, deltaTracker) -> EmotionOrbOverlay.render(graphics));
        // Etapa 3.1: sem grade permanente de necessidades na tela; os medidores ficam no painel Body & Traits.
        event.registerAboveAll(TupiniCraft.id("cinematic_letterbox"),
                (graphics, deltaTracker) -> CinematicCamera.renderLetterbox(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
    }

    private static void registerPlayerLayers(EntityRenderersEvent.AddLayers event) {
        BodyLayers.registerDefaults();
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) {
                // O rig entra como primeira camada; as Body Layers (busto, cabelo...) por ultimo.
                PlayerRigController.addLayer(renderer);
                renderer.addLayer(new BodyLayersRenderLayer(renderer));
            }
        }
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            ClientPlayerAnimations.onResourceReload();
            BodyLayers.onResourceReload();
        });
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CABO_ELEITORAL.get(), CaboEleitoralRenderer::new);
        event.registerEntityRenderer(ModEntities.LEAO_DO_FIM.get(), LeaoDoFimRenderer::new);
    }
}
