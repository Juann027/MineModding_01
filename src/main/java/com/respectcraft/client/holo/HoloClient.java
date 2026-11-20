package com.respectcraft.client.holo;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import com.respectcraft.RespectCraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Registro do menu holografico no cliente: config propria, estagio do Rimulib, limpeza no logout e (Etapa 4.8.4-A)
 * os eventos do modo mundo: superficies antes do quadro, passada no mundo, ciclo de vida e a tela da 4.8.1 trocada
 * pelos paineis no mundo. Etapa 4.8.4-B: entrada nos paineis ({@link HoloWorldInput}) e a dica ao lado da mira.
 */
public final class HoloClient {
    private HoloClient() {}

    public static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, HoloConfig.SPEC, "respectcraft-holomenu-client.toml");
        // O estagio do FxApi e criado uma vez, na thread principal, depois que o Rimulib carregou.
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(HoloBackdrop::init));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> HoloBackdrop.clear());
        NeoForge.EVENT_BUS.addListener(HoloWorld::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(HoloWorld::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(HoloWorld::onClientTick);
        NeoForge.EVENT_BUS.addListener(HoloWorld::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(HoloWorld::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(HoloWorld::onClone);
        NeoForge.EVENT_BUS.addListener(HoloWorld::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(HoloWorld::onScreenOpened);
        NeoForge.EVENT_BUS.addListener(HoloWorldInput::onMouseButton);
        NeoForge.EVENT_BUS.addListener(HoloWorldInput::onScroll);
        NeoForge.EVENT_BUS.addListener(HoloWorldInput::onInteraction);
        NeoForge.EVENT_BUS.addListener(HoloWorldInput::onClientTick);
        NeoForge.EVENT_BUS.addListener(HoloWorldInput::onLoggingOut);
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                RespectCraft.id("holo_tooltip"), (graphics, deltaTracker) -> HoloWorld.renderTooltip(graphics)));
    }
}
