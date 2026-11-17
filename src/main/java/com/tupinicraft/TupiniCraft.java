package com.tupinicraft;

import com.mojang.logging.LogUtils;
import com.tupinicraft.animation.PlayerAnimationCommands;
import com.tupinicraft.animation.PlayerAnimationService;
import com.tupinicraft.animation.PlayerAnimations;
import com.tupinicraft.command.LeaoDoFimCommands;
import com.tupinicraft.command.TupiniCommands;
import com.tupinicraft.finisher.Finishers;
import com.tupinicraft.needs.NeedsService;
import com.tupinicraft.network.GameplayNetworking;
import com.tupinicraft.profile.ProfileService;
import com.tupinicraft.registry.ModSounds;
import com.tupinicraft.skill.Skills;
import com.tupinicraft.character.CharacterAppearanceService;
import com.tupinicraft.testgame.FinisherTestMechanic;
import com.tupinicraft.testgame.DefeatCounter;
import com.tupinicraft.testgame.TestGameCommands;
import com.tupinicraft.network.TestGameNetworking;
import com.tupinicraft.registry.ModAttachments;
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
        NeoForge.EVENT_BUS.addListener(PlayerAnimationCommands::register);
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
        NeoForge.EVENT_BUS.addListener(TupiniCommands::register);
        NeoForge.EVENT_BUS.addListener(ProfileService::onLogin);
        NeoForge.EVENT_BUS.addListener(ProfileService::onRespawn);
        NeoForge.EVENT_BUS.addListener(ProfileService::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(NeedsService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(NeedsService::onItemFinished);
        NeoForge.EVENT_BUS.addListener(NeedsService::onWakeUp);
        NeoForge.EVENT_BUS.addListener(NeedsService::onLogin);
        NeoForge.EVENT_BUS.addListener(NeedsService::onRespawn);
        // Biomas e dimensões ainda não foram implementados neste projeto.
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
