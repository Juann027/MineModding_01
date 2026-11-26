package com.respectcraft;

import com.mojang.logging.LogUtils;
import com.respectcraft.animation.PlayerAnimationService;
import com.respectcraft.animation.PlayerAnimations;
import com.respectcraft.block.wood.TwistedWood;
import com.respectcraft.registry.ModBlocks;
import com.respectcraft.command.RespectCraftCommands;
import com.respectcraft.finisher.BossDefeats;
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
import cyou.rimuru.rimulib.mc1211.RimuMod;
import cyou.rimuru.rimulib.mc1211.api.RimulibApi;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
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
    /** Versao minima do Rimulib aceita em tempo de execucao (igual ao neoforge.mods.toml). */
    public static final String RIMULIB_MINIMUM = "1.3.0";
    /**
     * Conexao com o Rimulib (Etapa 4.8), feita uma vez na classe principal como pede a documentacao oficial. So usa a
     * API comum ({@code cyou.rimuru.rimulib.mc1211.api}), segura no servidor dedicado. Efeitos de tela
     * ({@code mc1211.client.api.FxApi}) ficam so em {@code client}. Recusa Rimulib mais antigo que o minimo.
     */
    public static final RimuMod RIMULIB = RimulibApi.connect(MOD_ID, RespectCraft.class, RIMULIB_MINIMUM);

    public RespectCraft(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.respectcraft.needs.NeedsConfig.SPEC,
                "respectcraft-needs-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                com.respectcraft.profile.SpecialityConfig.SPEC, "respectcraft-speciality-server.toml");
        LOGGER.info("RespectCraft connected to Rimulib {} (API {}, loader {})",
                RimulibApi.version(), RimulibApi.apiVersion(), RimulibApi.loader());
        // Etapa 4.9: Madeira Retorcida (blocos em ModBlocks, itens em ModItems) e a dimensao Inverted Realm (dados).
        TwistedWood.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        PlayerAnimations.bootstrap();
        Finishers.bootstrap();
        // Etapa 4.10: efeitos das finalizacoes no Calamidade_Awakening (lado comum: o servidor tambem os conhece).
        com.respectcraft.fx.FinisherFx.register();
        NeoForge.EVENT_BUS.addListener(com.respectcraft.fx.GeoFxKeyframes::onEntityTick);
        // Etapa 4.11: farois dos avisos holograficos no Calamidade_Awakening (lado comum).
        com.respectcraft.notice.NoticeFx.register();
        Skills.bootstrap();
        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModEntities::registerSpawnPlacements);
        modBus.addListener(ModItems::addToCreativeTabs);
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
        // Etapa 4.8: caminho preparado para recompensas de chefe (so anuncia BossDefeatEvent; nada e concedido).
        NeoForge.EVENT_BUS.addListener(BossDefeats::onFinisherCompleted);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BossDefeats::onDeath);
        // Etapa 3: ficha do personagem (roleta, atributos, habilidades) e necessidades.
        modBus.addListener(GameplayNetworking::register);
        modBus.addListener(ProfileService::onConfigReload);
        NeoForge.EVENT_BUS.addListener(RespectCraftCommands::register);
        NeoForge.EVENT_BUS.addListener(ProfileService::onLogin);
        NeoForge.EVENT_BUS.addListener(ProfileService::onRespawn);
        NeoForge.EVENT_BUS.addListener(ProfileService::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(NeedsService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(NeedsService::onItemFinished);
        NeoForge.EVENT_BUS.addListener(NeedsService::onLogin);
        NeoForge.EVENT_BUS.addListener(NeedsService::onRespawn);
        // Etapa 4.9: Inverted Realm (dimensao e bioma em dados; aqui so o registro de volta do /respectcraft realm).
        NeoForge.EVENT_BUS.addListener(com.respectcraft.dimension.InvertedRealm::onClone);
        // Etapa 4.11: avisos holograficos automaticos e caca a recompensa entre jogadores.
        modBus.addListener(com.respectcraft.network.HoloNetworking::register);
        NeoForge.EVENT_BUS.addListener(com.respectcraft.notice.HoloNotices::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(com.respectcraft.notice.HoloNotices::onBossDefeat);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, com.respectcraft.notice.HoloNotices::onPortalSpawn);
        NeoForge.EVENT_BUS.addListener(com.respectcraft.notice.HoloNotices::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, com.respectcraft.bounty.BountyService::onDeath);
        NeoForge.EVENT_BUS.addListener(com.respectcraft.bounty.BountyService::onFinisherCompleted);
        NeoForge.EVENT_BUS.addListener(com.respectcraft.bounty.BountyService::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.respectcraft.bounty.BountyService::onLogin);
        NeoForge.EVENT_BUS.addListener(com.respectcraft.bounty.BountyService::onLogout);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
