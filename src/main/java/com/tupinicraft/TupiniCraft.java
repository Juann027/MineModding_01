package com.tupinicraft;

import com.mojang.logging.LogUtils;
import com.tupinicraft.command.BrasilCommands;
import com.tupinicraft.config.BrasilServerConfig;
import com.tupinicraft.election.ElectionManager;
import com.tupinicraft.mandate.MandateService;
import com.tupinicraft.network.ModNetwork;
import com.tupinicraft.player.PlayerEventHandler;
import com.tupinicraft.registry.ModAttachments;
import com.tupinicraft.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Ponto de entrada comum (cliente e servidor dedicado).
 * Não referencie classes do pacote {@code client} aqui.
 */
@Mod(TupiniCraft.MOD_ID)
public final class TupiniCraft {
    public static final String MOD_ID = "tupinicraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TupiniCraft(IEventBus modBus, ModContainer container) {
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        modBus.addListener(ModItems::addToCreativeTabs);
        modBus.addListener(ModNetwork::register);

        container.registerConfig(ModConfig.Type.SERVER, BrasilServerConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(BrasilCommands::register);
        NeoForge.EVENT_BUS.addListener(PlayerEventHandler::onLogin);
        NeoForge.EVENT_BUS.addListener(PlayerEventHandler::onRespawn);
        NeoForge.EVENT_BUS.addListener(PlayerEventHandler::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(TupiniCraft::onServerTick);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        ElectionManager.tick(event.getServer());
        MandateService.tick(event.getServer());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
