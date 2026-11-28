package com.respectcraft.client;

import com.respectcraft.RespectCraft;
import com.respectcraft.client.character.CharacterCreatorKeybinds;
import com.respectcraft.client.animation.ClientPlayerAnimations;
import com.respectcraft.client.bodylayer.BodyLayers;
import com.respectcraft.client.bodylayer.BodyLayersRenderLayer;
import com.respectcraft.client.cinematic.CinematicCamera;
import com.respectcraft.client.dimension.InvertedRealmEffects;
import com.respectcraft.client.holo.HoloClient;
import com.respectcraft.network.ProfileClientCache;
import com.respectcraft.client.playergeo.PlayerRigController;
import com.respectcraft.client.hud.BodyStatusInventoryPanel;
import com.respectcraft.client.hud.EmotionOrbOverlay;
import com.respectcraft.client.render.AguaVivaRenderer;
import com.respectcraft.client.render.TwistedWoodRenderers;
import com.respectcraft.client.testgame.DefeatCounterNameplate;
import com.respectcraft.network.CharacterAppearanceClientCache;
import com.respectcraft.network.DefeatCountClientCache;
import com.respectcraft.network.FinisherVisualClientCache;
import com.respectcraft.registry.ModEntities;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Renderização GeckoLib isolada do servidor dedicado. */
@Mod(value = RespectCraft.MOD_ID, dist = Dist.CLIENT)
public final class RespectCraftClient {
    public RespectCraftClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(RespectCraftClient::registerRenderers);
        // Etapa 4.13.3: musica das frutas (loop por entidade, so no cliente).
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                com.respectcraft.client.fruit.FruitMusicClient.SPEC, "respectcraft-fruits-client.toml");
        NeoForge.EVENT_BUS.addListener(com.respectcraft.client.fruit.FruitMusicClient::onClientTick);
        // Etapa 4.9: placas da Madeira Retorcida.
        modBus.addListener(TwistedWoodRenderers::onClientSetup);
        modBus.addListener(TwistedWoodRenderers::registerRenderers);
        // Etapa 4.9: ceu roxo, neblina e luz do Inverted Realm (id = "effects" do dimension_type).
        modBus.addListener(InvertedRealmEffects::register);
        // Etapa 4.15: Time Warp (ceu/nuvens, neblina densa por estagio, pistas de som, silencio; limpo ao sair).
        modBus.addListener(com.respectcraft.client.dimension.TimeWarpEffects::register);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                com.respectcraft.client.dimension.TimeWarpClient.SPEC, "respectcraft-timewarp-client.toml");
        NeoForge.EVENT_BUS.addListener(com.respectcraft.client.dimension.TimeWarpClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.respectcraft.client.dimension.TimeWarpClient::onRenderFog);
        NeoForge.EVENT_BUS.addListener(com.respectcraft.client.dimension.TimeWarpClient::onFogColor);
        NeoForge.EVENT_BUS.addListener(com.respectcraft.client.dimension.TimeWarpClient::onPlaySound);
        // Etapa 4.8.1: menu holografico da tecla K (config de cliente, fundo do Rimulib, limpeza no logout).
        HoloClient.register(modBus, container);
        modBus.addListener(CharacterCreatorKeybinds::register);
        modBus.addListener(PlayerRigController::registerKeys);
        modBus.addListener(RespectCraftClient::registerPlayerLayers);
        modBus.addListener(RespectCraftClient::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(CharacterCreatorKeybinds::onTick);
        NeoForge.EVENT_BUS.addListener(PlayerRigController::onTick);
        NeoForge.EVENT_BUS.addListener(PlayerRigController::onRenderPre);
        NeoForge.EVENT_BUS.addListener(PlayerRigController::onRenderPost);
        NeoForge.EVENT_BUS.addListener(ClientPlayerAnimations::onTick);
        NeoForge.EVENT_BUS.addListener(ClientPlayerAnimations::onTimedEvent);
        // Etapa 4.10: passes de pos (shaders) dos efeitos de finalizacao no Calamidade_Awakening.
        modBus.addListener(com.respectcraft.client.fx.FinisherFxClient::onClientSetup);
        // Finisher Engine: camera dinamica, tremor e FOV dos participantes.
        NeoForge.EVENT_BUS.addListener(CinematicCamera::onClientTick);
        NeoForge.EVENT_BUS.addListener(CinematicCamera::onTimedEvent);
        NeoForge.EVENT_BUS.addListener(CinematicCamera::onComputeFov);
        modBus.addListener(RespectCraftClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(BodyStatusInventoryPanel::onInventoryForeground);
        // LOWEST: decide depois dos outros mods, respeitando quem escondeu o nick pelo RenderNameTagEvent.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, DefeatCounterNameplate::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            DefeatCountClientCache.clear();
            CharacterAppearanceClientCache.clear();
            FinisherVisualClientCache.clear();
            PlayerRigController.clear();
            ClientPlayerAnimations.clear();
            ProfileClientCache.clear();
            CinematicCamera.reset();
            com.respectcraft.network.RigVisualClientCache.clear();
            com.respectcraft.client.fruit.FruitMusicClient.clear();
            com.respectcraft.client.dimension.TimeWarpClient.onLogout();
        });
        // Etapa 4.12: partes de rig da entidade que saiu do nivel do cliente (o id pode ser reaproveitado).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) -> {
            if (event.getLevel().isClientSide()) {
                com.respectcraft.network.RigVisualClientCache.forget(event.getEntity().getId());
                com.respectcraft.client.fruit.FruitMusicClient.forget(event.getEntity().getId());
            }
        });
        // Respawn e troca de dimensao recriam o jogador local: a camera nunca fica presa na cena.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.Clone event) -> CinematicCamera.reset());
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, RespectCraft.id("emotion_orb"),
                (graphics, deltaTracker) -> EmotionOrbOverlay.render(graphics));
        // Etapa 3.1: sem grade permanente de necessidades na tela; os medidores ficam no painel Body & Traits.
        event.registerAboveAll(RespectCraft.id("cinematic_letterbox"),
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
        event.registerEntityRenderer(ModEntities.AGUA_VIVA.get(), AguaVivaRenderer::giant);
        event.registerEntityRenderer(ModEntities.AGUA_VIVA_FILHOTE.get(), AguaVivaRenderer::hatchling);
        // Etapa 4.12: fixtures do Custom Rig Framework, com o renderer generico de rig.
        event.registerEntityRenderer(ModEntities.DEV_RIG_MANNEQUIN_A.get(),
                ctx -> new com.respectcraft.client.rig.RigGeoRenderer<>(ctx, com.respectcraft.rig.Rigs.MANNEQUIN_A_ID));
        event.registerEntityRenderer(ModEntities.DEV_RIG_MANNEQUIN_B.get(),
                ctx -> new com.respectcraft.client.rig.RigGeoRenderer<>(ctx, com.respectcraft.rig.Rigs.MANNEQUIN_B_ID));
        // Etapa 4.13.1: morango, o mesmo renderer generico (modelo/textura/partes pelo binding).
        event.registerEntityRenderer(ModEntities.STRAWBERRY.get(),
                ctx -> new com.respectcraft.client.rig.RigGeoRenderer<>(ctx, com.respectcraft.rig.Rigs.STRAWBERRY_ID));
    }
}
