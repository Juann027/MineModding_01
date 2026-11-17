package com.tupinicraft.client.animation;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.animation.PlayerAnimationDef;
import com.tupinicraft.animation.PlayerAnimationEvent;
import com.tupinicraft.animation.PlayerAnimationState;
import com.tupinicraft.animation.PlayerAnimations;
import com.tupinicraft.animation.TimedEventCursor;
import com.tupinicraft.client.playergeo.PlayerPart;
import com.tupinicraft.network.PlayerAnimationClientCache;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.loading.object.BakedAnimations;

/**
 * Animation Manager no cliente: valida os recursos de cada animacao registrada, acompanha o estado recebido
 * do servidor e publica {@link PlayerAnimationEvent} (inicio, eventos temporizados, fim) para som, VFX e camera.
 * Nao desenha nada: quem desenha e o rig ({@code client.playergeo}).
 */
public final class ClientPlayerAnimations {
    public static final ResourceLocation RIG_GEO = TupiniCraft.id("geo/player/tupinicraft_animated_player.geo.json");

    /** Animacao pronta para tocar: clip encontrado e todos os bones existem no rig. */
    public record Clip(PlayerAnimationDef def, RawAnimation once, RawAnimation looping, Set<PlayerPart> overridden) {
        /** O servidor decide, a cada execucao, se o clip repete. */
        public RawAnimation raw(boolean loop) { return loop ? looping : once; }
    }

    private static final class Tracked {
        PlayerAnimationState state;
        final TimedEventCursor cursor = new TimedEventCursor();
    }

    private static final Map<ResourceLocation, Optional<Clip>> CLIPS = new HashMap<>();
    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();
    private static boolean reloadPending;

    private ClientPlayerAnimations() {}

    /** Recursos recarregados (F3+T, troca de resource pack): tudo e validado de novo no proximo uso. */
    public static void onResourceReload() {
        CLIPS.clear();
        reloadPending = true;
    }

    /** Clip validado, ou null se a animacao nao existe ou tem recurso invalido (erro no log, sem derrubar o jogo). */
    public static Clip clip(ResourceLocation id) {
        return CLIPS.computeIfAbsent(id, ClientPlayerAnimations::validate).orElse(null);
    }

    /** Arquivo de animacao onde esta o clip (o rig aceita clips de varios arquivos). */
    public static ResourceLocation fileOfClip(String clipName) {
        for (PlayerAnimationDef def : PlayerAnimations.all()) {
            if (def.clip().equals(clipName)) return def.animationFile();
        }
        return null;
    }

    public static boolean rigAvailable() {
        return GeckoLibCache.getBakedModels().get(RIG_GEO) != null;
    }

    private static Optional<Clip> validate(ResourceLocation id) {
        PlayerAnimationDef def = PlayerAnimations.get(id);
        if (def == null) return invalid(id, "animação não registrada neste cliente");
        BakedGeoModel rig = GeckoLibCache.getBakedModels().get(RIG_GEO);
        if (rig == null) return invalid(id, "rig do jogador não encontrado: " + RIG_GEO);
        BakedAnimations file = GeckoLibCache.getBakedAnimations().get(def.animationFile());
        if (file == null) return invalid(id, "arquivo de animação não encontrado: " + def.animationFile());
        Animation animation = file.getAnimation(def.clip());
        if (animation == null) return invalid(id, "clip '" + def.clip() + "' não existe em " + def.animationFile());

        Set<PlayerPart> animated = EnumSet.noneOf(PlayerPart.class);
        for (BoneAnimation bone : animation.boneAnimations()) {
            if (rig.getBone(bone.boneName()).isEmpty()) {
                return invalid(id, "o clip anima o bone '" + bone.boneName() + "', que não existe no rig");
            }
            PlayerPart part = PlayerPart.byBone(bone.boneName());
            if (part != null) animated.add(part);
        }
        Set<PlayerPart> overridden = switch (def.blend()) {
            case ADDITIVE -> EnumSet.noneOf(PlayerPart.class);
            case FULL_BODY -> EnumSet.allOf(PlayerPart.class);
            case OVERRIDE_ANIMATED -> {
                if (def.overrideBones().isEmpty()) yield animated;
                Set<PlayerPart> chosen = EnumSet.noneOf(PlayerPart.class);
                for (String name : def.overrideBones()) {
                    PlayerPart part = PlayerPart.byBone(name);
                    if (part == null) {
                        TupiniCraft.LOGGER.warn("Animação {}: overrideBones cita '{}', que não é um bone principal", id, name);
                    } else {
                        chosen.add(part);
                    }
                }
                yield chosen;
            }
        };
        if (Math.abs(animation.length() - def.durationTicks()) > 0.5) {
            TupiniCraft.LOGGER.warn("Animação {}: duração registrada ({} ticks) difere do clip ({} ticks)",
                    id, def.durationTicks(), animation.length());
        }
        return Optional.of(new Clip(def, RawAnimation.begin().thenPlay(def.clip()),
                RawAnimation.begin().thenLoop(def.clip()), overridden));
    }

    private static Optional<Clip> invalid(ResourceLocation id, String reason) {
        TupiniCraft.LOGGER.error("Animação de jogador {} desativada: {}", id, reason);
        return Optional.empty();
    }

    /** Estado em andamento de um jogador (recebido do servidor), ou null. */
    public static PlayerAnimationState active(UUID player) {
        PlayerAnimationState state = PlayerAnimationClientCache.get(player);
        ClientLevel level = Minecraft.getInstance().level;
        if (state == null || level == null || state.finished(level.getGameTime())) return null;
        return state;
    }

    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (reloadPending && mc.getOverlay() == null) {
            // O GeckoLib termina de carregar junto com a tela de recarga; so entao os recursos novos valem.
            CLIPS.clear();
            reloadPending = false;
        }
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = level.getGameTime();

        Iterator<Map.Entry<UUID, PlayerAnimationState>> states = PlayerAnimationClientCache.all().entrySet().iterator();
        while (states.hasNext()) {
            Map.Entry<UUID, PlayerAnimationState> entry = states.next();
            PlayerAnimationState state = entry.getValue();
            PlayerAnimationDef def = PlayerAnimations.get(state.animation());
            Player player = level.getPlayerByUUID(entry.getKey());
            Tracked tracked = TRACKED.get(entry.getKey());
            if (tracked == null || tracked.state.sequence() != state.sequence()) {
                if (tracked != null) end(level, entry.getKey(), tracked.state, PlayerAnimationEvent.EndReason.STOPPED);
                tracked = new Tracked();
                tracked.state = state;
                // Quem pega a animacao no meio nao repete os eventos que ja passaram.
                if (state.elapsed(now) > 5) tracked.cursor.skipTo(state.elapsed(now));
                TRACKED.put(entry.getKey(), tracked);
                if (def != null && player != null) NeoForge.EVENT_BUS.post(new PlayerAnimationEvent.Start(player, def, state));
            }
            if (def != null && player != null && now >= state.startGameTime()) {
                tracked.cursor.advance(def, state.loop(), state.elapsed(now),
                        timed -> NeoForge.EVENT_BUS.post(new PlayerAnimationEvent.Timed(player, def, state, timed)));
            }
            if (state.finished(now)) {
                states.remove();
                TRACKED.remove(entry.getKey());
                end(level, entry.getKey(), state, PlayerAnimationEvent.EndReason.FINISHED);
            }
        }
        // O servidor mandou parar (o estado sumiu do cache).
        TRACKED.entrySet().removeIf(entry -> {
            if (PlayerAnimationClientCache.get(entry.getKey()) != null) return false;
            end(level, entry.getKey(), entry.getValue().state, PlayerAnimationEvent.EndReason.STOPPED);
            return true;
        });
    }

    private static void end(ClientLevel level, UUID uuid, PlayerAnimationState state, PlayerAnimationEvent.EndReason reason) {
        PlayerAnimationDef def = PlayerAnimations.get(state.animation());
        Player player = level.getPlayerByUUID(uuid);
        if (def != null && player != null) NeoForge.EVENT_BUS.post(new PlayerAnimationEvent.End(player, def, state, reason));
    }

    /** Keyframe de som/particula/instrucao gravado no clip (Blockbench): vira um evento temporizado comum. */
    public static void clipKeyframe(Player player, ResourceLocation type, String data, double clipTick) {
        PlayerAnimationState state = PlayerAnimationClientCache.get(player.getUUID());
        PlayerAnimationDef def = state == null ? null : PlayerAnimations.get(state.animation());
        if (def == null) return;
        NeoForge.EVENT_BUS.post(new PlayerAnimationEvent.Timed(player, def, state,
                new PlayerAnimationDef.TimedEvent((int) clipTick, type, data)));
    }

    /** Tratador padrao do evento de som: {@code data} e o id do som. Exemplo de como plugar um sistema. */
    public static void onTimedEvent(PlayerAnimationEvent.Timed event) {
        Player player = event.getPlayer();
        if (!player.level().isClientSide() || !event.getEvent().type().equals(PlayerAnimations.EVENT_SOUND)) return;
        ResourceLocation id = ResourceLocation.tryParse(event.getEvent().data());
        if (id == null) return;
        BuiltInRegistries.SOUND_EVENT.getOptional(id).ifPresent(sound -> player.level().playLocalSound(
                player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1F, 1F, false));
    }

    public static void clear() {
        TRACKED.clear();
        PlayerAnimationClientCache.clear();
    }
}
