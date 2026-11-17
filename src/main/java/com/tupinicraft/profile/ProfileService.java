package com.tupinicraft.profile;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.network.GameplayNetworking;
import com.tupinicraft.registry.ModAttachments;
import com.tupinicraft.registry.ModSounds;
import com.tupinicraft.skill.SkillService;
import java.util.random.RandomGenerator;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Unico ponto de leitura/escrita da ficha do personagem; sincroniza com o dono a cada mudanca. */
public final class ProfileService {
    /** Intervalo minimo entre pedidos de giro (anti-spam). */
    private static final int ROLL_COOLDOWN_TICKS = 40;
    private static final String LAST_ROLL_KEY = "tupinicraft_last_roll";

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

    /** Pedido do cliente: primeiro giro, ou nova tentativa enquanto houver. O resultado vem so do servidor. */
    public static void requestRoll(ServerPlayer player, boolean reroll) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(LAST_ROLL_KEY);
        if (now >= last && now - last < ROLL_COOLDOWN_TICKS) return;
        CharacterProfile profile = get(player);
        int rerolls;
        if (!profile.born()) {
            if (reroll) return;
            rerolls = BirthRoulette.FREE_REROLLS;
        } else {
            if (!reroll || !profile.canReroll()) {
                sync(player); // a tela volta ao estado real
                return;
            }
            rerolls = profile.rerolls() - 1;
        }
        player.getPersistentData().putLong(LAST_ROLL_KEY, now);
        roll(player, rerolls);
    }

    /**
     * Gira a roleta (tambem usado pelo comando de admin). Unico lugar que sorteia: o resultado e gravado na ficha
     * (attachment salvo) e a tela so o apresenta. Aparencia, reinicio do servidor e reabrir a tela nao o alteram.
     */
    public static BirthRoulette.Result roll(ServerPlayer player, int rerollsLeft) {
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        BirthRoulette.Result result = BirthRoulette.roll(random);
        CharacterProfile next = get(player).withBirth(result, rerollsLeft);
        set(player, next);
        celebrate(player);
        TupiniCraft.LOGGER.info("Roleta de nascimento: {} nasceu em {} {}", player.getGameProfile().getName(),
                result.origin().id(), java.util.Arrays.toString(result.attributes()));
        return result;
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
        player.displayClientMessage(Component.translatable("msg.tupinicraft.skill_points", gained), true);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillService.refreshModifiers(player);
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
