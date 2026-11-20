package com.respectcraft.client.holo;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Registro do menu holografico no cliente: config propria, estagio do Rimulib, limpeza no logout e (Etapa 4.8.4-A)
 * os eventos do modo mundo: superficies antes do quadro, passada no mundo, ciclo de vida e a tela da 4.8.1 trocada
 * pelos paineis no mundo.
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
    }
}
