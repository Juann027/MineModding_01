package com.respectcraft.profile;

import com.respectcraft.RespectCraft;
import com.respectcraft.character.CharacterAppearanceService;
import com.respectcraft.network.GameplayNetworking;
import com.respectcraft.registry.ModAttachments;
import com.respectcraft.registry.ModSounds;
import com.respectcraft.skill.SkillService;
import java.util.random.RandomGenerator;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Unico ponto de leitura/escrita da ficha do personagem; sincroniza com o dono a cada mudanca. */
public final class ProfileService {
    /** Chave do antigo cooldown da roleta (ate 0.5.7); so e apagada do persistentData. */
    private static final String LEGACY_LAST_ROLL_KEY = "respectcraft_last_roll";
    /** Falha de teste armada pelo admin para o proximo pedido de giro (consumida no uso; Etapa 4.8.2). */
    private static final String DEBUG_FAIL_NEXT_ROLL_KEY = "respectcraft_debug_fail_next_roll";

    private ProfileService() {}

    public static CharacterProfile get(ServerPlayer player) {
        return player.getData(ModAttachments.CHARACTER_PROFILE);
    }

    public static void set(ServerPlayer player, CharacterProfile profile) {
        player.setData(ModAttachments.CHARACTER_PROFILE, profile);
        SkillService.refreshModifiers(player);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new GameplayNetworking.ProfileSync(get(player)));
    }

    /**
     * Pedido do cliente: primeiro giro, ou o novo giro enquanto houver. O resultado vem so do servidor e todo
     * pedido recebe resposta ({@link GameplayNetworking.BirthRollReply}), aceito, recusado ou com erro: a ficha atual
     * vai antes, entao a tela sempre volta ao estado real. Pedidos repetidos ou atrasados sao recusados pelo
     * {@code expectedRollCount} (ver {@link BirthRoulette#validate}).
     *
     * <p>Etapa 4.8.2: uma excecao aqui era engolida pelo NeoForge (so um log generico) e o cliente ficava sem
     * resposta ate o timeout. Agora ela e registrada com o {@code requestId} do pedido e vira uma resposta
     * {@link BirthRoulette.Outcome#ERROR} (ou {@code ROLLED}, se o giro ja tinha sido gravado:
     * {@link BirthRoulette#afterFailure}). O log so leva os campos do pedido e da roleta, nunca a ficha inteira.
     */
    public static BirthRoulette.Outcome requestRoll(ServerPlayer player, boolean reroll, int expectedRollCount, int requestId) {
        int before = -1;
        BirthRoulette.Outcome outcome;
        try {
            CharacterProfile profile = get(player);
            before = profile.rollCount();
            if (consumeInjectedFailure(player)) {
                throw new IllegalStateException("injected test failure (/respectcraft admin roulette_fail)");
            }
            outcome = BirthRoulette.validate(profile, reroll, expectedRollCount);
            if (outcome == BirthRoulette.Outcome.ROLLED) roll(player, BirthRoulette.rerollsAfter(profile));
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable e) {
            // Inclui LinkageError e afins (falhas de ambiente), nao so RuntimeException.
            int now = safeRollCount(player);
            outcome = BirthRoulette.afterFailure(before, now);
            RespectCraft.LOGGER.error("Birth roulette request #{} from {} failed on the server (reroll={}, expectedRollCount={},"
                    + " rollCount {} -> {}); replying {}", requestId, player.getGameProfile().getName(), reroll,
                    expectedRollCount, before, now, outcome, e);
        }
        reply(player, outcome, requestId);
        return outcome;
    }

    /** Ficha atual, as chances e a resposta; uma falha aqui fica no log com o {@code requestId} (nada a mais a fazer). */
    private static void reply(ServerPlayer player, BirthRoulette.Outcome outcome, int requestId) {
        try {
            sync(player);
            syncOdds(player);
            PacketDistributor.sendToPlayer(player, new GameplayNetworking.BirthRollReply(outcome, get(player).rollCount(), requestId));
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable e) {
            RespectCraft.LOGGER.error("Birth roulette request #{} from {}: could not send the reply ({})", requestId,
                    player.getGameProfile().getName(), outcome, e);
        }
    }

    private static int safeRollCount(ServerPlayer player) {
        try {
            return get(player).rollCount();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable e) {
            return -1;
        }
    }

    /**
     * Teste de diagnostico (Etapa 4.8.2): {@code /respectcraft admin roulette_fail <player>} arma uma falha para o
     * proximo pedido de giro desse jogador, que lanca uma excecao antes de qualquer sorteio. Consumida no uso.
     */
    public static void armInjectedFailure(ServerPlayer player) {
        player.getPersistentData().putBoolean(DEBUG_FAIL_NEXT_ROLL_KEY, true);
    }

    private static boolean consumeInjectedFailure(ServerPlayer player) {
        if (!player.getPersistentData().getBoolean(DEBUG_FAIL_NEXT_ROLL_KEY)) return false;
        player.getPersistentData().remove(DEBUG_FAIL_NEXT_ROLL_KEY);
        return true;
    }

    /**
     * Continuar na roleta: confirma o resultado atual e descarta o novo giro restante ({@link BirthRoulette#confirm}).
     * Pedido sobre ficha desatualizada nao muda nada. Sempre responde com a ficha atual.
     */
    public static void confirmBirth(ServerPlayer player, int expectedRollCount) {
        CharacterProfile profile = get(player);
        CharacterProfile next = BirthRoulette.confirm(profile, expectedRollCount);
        if (next != profile) set(player, next);
        else sync(player);
    }

    /**
     * Gira a roleta (tambem usado pelo comando de admin). Unico lugar que sorteia: o resultado e gravado na ficha
     * (attachment salvo) e a tela so o apresenta. Aparencia, reinicio do servidor e reabrir a tela nao o alteram.
     */
    public static BirthRoulette.Result roll(ServerPlayer player, int rerollsLeft) {
        return roll(player, rerollsLeft, null);
    }

    /**
     * Mesmo giro, com a raridade da Speciality forcada. {@code forcedRarity} nao nulo so vem do teste
     * administrativo ({@code /respectcraft admin roulette <player> <rarity>}, permissao 2); o jogador nunca escolhe.
     */
    public static BirthRoulette.Result roll(ServerPlayer player, int rerollsLeft, SpecialityRarity forcedRarity) {
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        BirthRoulette.Result result = BirthRoulette.roll(random, body(player), SpecialityConfig.odds(), forcedRarity);
        CharacterProfile next = get(player).withBirth(result, rerollsLeft);
        player.getPersistentData().remove(LEGACY_LAST_ROLL_KEY);
        set(player, next);
        celebrate(player);
        RespectCraft.LOGGER.info("Roleta de nascimento: {} nasceu {} ({}{}) {} tracos={}", player.getGameProfile().getName(),
                result.speciality().id(), result.rarity().id(), forcedRarity == null ? "" : ", raridade forcada pelo admin",
                java.util.Arrays.toString(result.attributes()), next.traits());
        return result;
    }

    /** Corpo escolhido no Character Creator (aparencia salva no servidor); sem aparencia = masculino. */
    public static BirthTrait.Body body(ServerPlayer player) {
        return BirthTrait.Body.fromBodyType(CharacterAppearanceService.get(player).bodyType());
    }

    /**
     * Chamado quando a aparencia salva muda: mantem o resultado ainda nao confirmado coerente com o corpo
     * ({@link CharacterProfile#withTraitsCoherentWith}). Resultado confirmado nao muda.
     */
    public static void onBodyChanged(ServerPlayer player) {
        CharacterProfile profile = get(player);
        CharacterProfile next = profile.withTraitsCoherentWith(body(player));
        if (next != profile) set(player, next);
    }

    /** Som e particulas de "nascimento", vistos e ouvidos por quem estiver perto. */
    public static void celebrate(ServerPlayer player) {
        player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
                40, 0.5, 0.8, 0.5, 0.3);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.ROULETTE_WIN.get(), SoundSource.PLAYERS, 0.8F, 1F);
    }

    /** Pontos por nivel de XP vanilla: 1 por nivel novo alcancado (chamado uma vez por segundo). */
    public static void rewardLevels(ServerPlayer player) {
        CharacterProfile profile = get(player);
        int level = player.experienceLevel;
        if (level <= profile.levelRewarded()) return;
        int gained = level - profile.levelRewarded();
        set(player, profile.withPoints(profile.skillPoints() + gained, level));
        player.displayClientMessage(Component.translatable("msg.respectcraft.skill_points", gained), true);
    }

    /** Chances de raridade ativas (config do servidor) para a roleta exibir; o cliente nunca as escolhe. */
    public static void syncOdds(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, GameplayNetworking.SpecialityOddsSync.of(SpecialityConfig.odds()));
    }

    /** Config das Specialities recarregada (arquivo editado com o servidor aberto): reenvia as chances a todos. */
    public static void onConfigReload(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SpecialityConfig.SPEC) return;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) syncOdds(player);
        });
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillService.refreshModifiers(player);
            syncOdds(player);
            sync(player);
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillService.refreshModifiers(player);
            player.setHealth(player.getMaxHealth());
            sync(player);
        }
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
