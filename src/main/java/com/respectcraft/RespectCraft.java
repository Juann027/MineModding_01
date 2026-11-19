package com.respectcraft;

import com.mojang.logging.LogUtils;
import com.respectcraft.animation.PlayerAnimationService;
import com.respectcraft.animation.PlayerAnimations;
import com.respectcraft.command.LeaoDoFimCommands;
import com.respectcraft.command.RespectCraftCommands;
import com.respectcraft.finisher.Finishers;
import com.respectcraft.needs.NeedsService;
import com.respectcraft.network.GameplayNetworking;
import com.respectcraft.profile.ProfileService;
import com.respectcraft.registry.ModSounds;
import com.respectcraft.skill.Skills;
import com.respectcraft.character.CharacterAppearanceService;
import com.respectcraft.testgame.FinisherTestMechanic;
import com.respectcraft.testgame.DefeatCounter;
import com.respectcraft.testgame.TestGameCommands;
import com.respectcraft.network.TestGameNetworking;
import com.respectcraft.registry.ModAttachments;
import com.respectcraft.registry.ModCreativeTabs;
import com.respectcraft.registry.ModEntities;
import com.respectcraft.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Entrada comum do mod: registries, config do servidor e eventos (o cliente fica em client.RespectCraftClient). */
@Mod(RespectCraft.MOD_ID)
public final class RespectCraft {
    public static final String MOD_ID = "respectcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RespectCraft(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.respectcraft.needs.NeedsConfig.SPEC,
                "respectcraft-needs-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                com.respectcraft.profile.SpecialityConfig.SPEC, "respectcraft-speciality-server.toml");
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        PlayerAnimations.bootstrap();
        Finishers.bootstrap();
        Skills.bootstrap();
        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModItems::addToCreativeTabs);
        NeoForge.EVENT_BUS.addListener(LeaoDoFimCommands::register);
        // Contador e finisher de combate nao explicito para testes: somente eventos comuns.
        modBus.addListener(TestGameNetworking::register);
        NeoForge.EVENT_BUS.addListener(TestGameCommands::register);
        NeoForge.EVENT_BUS.addListener(CharacterAppearanceService::onLogin);
        NeoForge.EVENT_BUS.addListener(CharacterAppearanceService::onLogout);
        NeoForge.EVENT_BUS.addListener(CharacterAppearanceService::onStartTracking);
        NeoForge.EVENT_BUS.addListener(CharacterAppearanceService::onClone);
        NeoForge.EVENT_BUS.addListener(CharacterAppearanceService::onRespawn);
        NeoForge.EVENT_BUS.addListener(DefeatCounter::onLogin);
        NeoForge.EVENT_BUS.addListener(DefeatCounter::onLogout);
        NeoForge.EVENT_BUS.addListener(DefeatCounter::onStartTracking);
        NeoForge.EVENT_BUS.addListener(DefeatCounter::onClone);
        NeoForge.EVENT_BUS.addListener(DefeatCounter::onRespawn);
        NeoForge.EVENT_BUS.addListener(DefeatCounter::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::onDeath);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::onLogout);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::onClone);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::blockAttack);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::blockEntityInteract);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::blockBlockInteract);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::blockItemInteract);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::blockMining);
        // Animation Manager: estado no servidor, sincronizado com quem esta vendo o jogador.
        NeoForge.EVENT_BUS.addListener(PlayerAnimationService::onServerTick);
        NeoForge.EVENT_BUS.addListener(PlayerAnimationService::onStartTracking);
        NeoForge.EVENT_BUS.addListener(PlayerAnimationService::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(PlayerAnimationService::onRespawn);
        NeoForge.EVENT_BUS.addListener(PlayerAnimationService::onDeath);
        NeoForge.EVENT_BUS.addListener(PlayerAnimationService::onDamage);
        NeoForge.EVENT_BUS.addListener(PlayerAnimationService::onLogout);
        NeoForge.EVENT_BUS.addListener(FinisherTestMechanic::onAnimationTimed);
        // Etapa 3: ficha do personagem (roleta, atributos, habilidades) e necessidades.
        modBus.addListener(GameplayNetworking::register);
        NeoForge.EVENT_BUS.addListener(RespectCraftCommands::register);
        NeoForge.EVENT_BUS.addListener(ProfileService::onLogin);
        NeoForge.EVENT_BUS.addListener(ProfileService::onRespawn);
        NeoForge.EVENT_BUS.addListener(ProfileService::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(NeedsService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(NeedsService::onItemFinished);
        NeoForge.EVENT_BUS.addListener(NeedsService::onLogin);
        NeoForge.EVENT_BUS.addListener(NeedsService::onRespawn);
        // Biomas e dimensões ainda não foram implementados neste projeto.
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
