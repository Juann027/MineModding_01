package com.tupinicraft;

import com.mojang.logging.LogUtils;
import com.tupinicraft.command.LeaoDoFimCommands;
import com.tupinicraft.registry.ModCreativeTabs;
import com.tupinicraft.registry.ModEntities;
import com.tupinicraft.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Nova base de desenvolvimento: mobs/NPCs, biomas e dimensões. */
@Mod(TupiniCraft.MOD_ID)
public final class TupiniCraft {
    public static final String MOD_ID = "tupinicraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TupiniCraft(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModItems::addToCreativeTabs);
        NeoForge.EVENT_BUS.addListener(LeaoDoFimCommands::register);
        // Biomas e dimensões ainda não foram implementados neste projeto.
        com.tupinicraft.registry.ModAttachments.ATTACHMENT_TYPES.register(modBus);
        modBus.addListener(com.tupinicraft.network.ModNetwork::register);
        NeoForge.EVENT_BUS.addListener(com.tupinicraft.player.PlayerEventHandler::onLogin);
        NeoForge.EVENT_BUS.addListener(com.tupinicraft.skilltree.SkillCatalog::onAddReloadListener);
        NeoForge.EVENT_BUS.addListener(com.tupinicraft.skilltree.SkillTreeService::onDatapackSync);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
                com.tupinicraft.skilltree.SkillTreeService.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) -> {
            event.getDispatcher().register(com.tupinicraft.command.SkillCommands.player());
            event.getDispatcher().register(com.tupinicraft.command.SkillCommands.adminTest()
                    .requires(source -> source.hasPermission(2)));
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
