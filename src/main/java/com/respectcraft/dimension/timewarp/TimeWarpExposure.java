package com.respectcraft.dimension.timewarp;

import com.respectcraft.RespectCraft;
import com.respectcraft.dimension.timewarp.TimeWarpRules.Stage;
import com.respectcraft.network.TimeWarpNetworking;
import com.respectcraft.network.TimeWarpNetworking.Cue;
import com.respectcraft.notice.HoloNotice;
import com.respectcraft.notice.HoloNotices;
import com.respectcraft.notice.NoticeKind;
import com.respectcraft.notice.NoticeStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Exposicao e atmosfera do Time Warp (Etapa 4.15.4), so no servidor e por jogador. Conta os ticks em que o jogador
 * esta de fato no Time Warp (vivo, nao espectador, online); fora dele a exposicao cai {@code recoveryRate} por tick.
 * Gravada no persistentData (logout/reconexao/morte nao zeram). Estagios em {@link TimeWarpRules#stage}; a mudanca
 * de estagio manda o estado ao cliente e um aviso do "Sistema" ({@link HoloNotices}, privado). Nunca mata ninguem.
 *
 * <p><b>Acontecimentos</b> (raros, imprevisiveis, mais frequentes com o estagio; nunca combate nem susto repetido):
 * <ul>
 *   <li>individuais (so o jogador ouve/ve): porta distante na neblina, passos sem origem, passos atras, pulso de
 *   neblina, silencio repentino, mensagens do Sistema;</li>
 *   <li>compartilhados (todo jogador do Time Warp perto ouve o mesmo): ruido dentro de uma construcao proxima (bau,
 *   porta, passos no assoalho), no ponto da construcao.</li>
 * </ul>
 * Nenhum bloco e mudado e nenhuma entidade e criada.
 */
public final class TimeWarpExposure {
    private TimeWarpExposure() {}

    public static final ResourceLocation LINGERED = RespectCraft.id("time_warp/lingered");
    static final double SHARED_RADIUS = 48.0;

    /** Acontecimentos (ids de log e do comando de teste). */
    public enum Happening {
        DISTANT_DOOR, DISTANT_STEPS, HOUSE_NOISE, CAVE_SOUND, FOG_PULSE, STEPS_BEHIND, SILENCE, SYSTEM_MESSAGE;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public static Happening byId(String id) {
            for (Happening h : values()) if (h.id().equals(id)) return h;
            return null;
        }
    }

    /** Mensagens do Sistema (chaves de idioma) por estagio minimo. */
    static final String[] SIGNAL_MESSAGES = {"source", "region", "time_sync", "last_save", "unstable"};
    static final String[] CRITICAL_MESSAGES = {"player_count", "chunk_session", "return_advised"};

    // ================================================================================================ tick

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        TimeWarpSession s = TimeWarpTravel.session(player);
        boolean inWarp = TimeWarp.isIn(player);
        int stageSeconds = TimeWarpConfig.stageSeconds();
        int exposure = TimeWarp.exposure(player);
        if (inWarp && player.isAlive() && !player.isSpectator()) {
            exposure = TimeWarpRules.cap(exposure + 1, stageSeconds);
            TimeWarpTravel.tickDrift(player, s);
        } else if (exposure > 0) {
            exposure = TimeWarpRules.recover(exposure, TimeWarpConfig.recoveryRate());
        }
        TimeWarp.setExposure(player, exposure);
        if (player.tickCount % 20 != 0) return;
        Stage stage = TimeWarpRules.stage(exposure, stageSeconds);
        int lastStage = TimeWarp.data(player).getInt("stage");
        if (inWarp && stage.ordinal() > lastStage) onStageUp(player, stage);
        if (stage.ordinal() != lastStage) TimeWarp.data(player).putInt("stage", stage.ordinal());
        sync(player, false);
        if (inWarp && TimeWarpConfig.events()) maybeHappen(player, stage);
    }

    /** Manda o estado ao cliente se mudou (ou sempre, com {@code force}). */
    public static void sync(ServerPlayer player, boolean force) {
        TimeWarpSession s = TimeWarpTravel.session(player);
        boolean inWarp = TimeWarp.isIn(player);
        int stage = TimeWarpRules.stage(TimeWarp.exposure(player), TimeWarpConfig.stageSeconds()).ordinal();
        if (!force && s.sentInWarp == inWarp && s.sentStage == stage) return;
        s.sentInWarp = inWarp;
        s.sentStage = stage;
        TimeWarpNetworking.send(player, new TimeWarpNetworking.State(inWarp, stage));
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sync(player, true);
        if (TimeWarp.isIn(player)) {
            notice(player, NoticeStyle.SYSTEM_INFO, "reconnected", false);
            if (!TimeWarp.anchor(player).contains("dim")) TimeWarpLog.debug(player, "no entry anchor (recovery falls back to world spawn)");
        }
    }

    /** Renasceu fora do Time Warp (morte la dentro): o ponto de entrada nao serve mais; a exposicao continua e cai. */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TimeWarp.isIn(player)) TimeWarp.clearAnchor(player);
        sync(player, true);
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        TimeWarpSession s = TimeWarpTravel.session(player);
        s.resetCharge();
        s.driftTicks = 0;
        s.driftHinted = false;
        if (event.getTo() == TimeWarp.LEVEL) {
            // Entrada por qualquer caminho (relogio, comando, /tp): sem ponto de entrada, guarda o de origem.
            if (!TimeWarp.anchor(player).contains("dim") && event.getFrom() != TimeWarp.LEVEL) {
                TimeWarpLog.debug(player, "entered without the clock; recovery anchor = world spawn");
            }
            TimeWarp.data(player).putLong("entered_at", TimeWarpTravel.now(player));
            notice(player, NoticeStyle.SYSTEM_INFO, "entered", true);
        } else if (event.getFrom() == TimeWarp.LEVEL) {
            TimeWarp.clearAnchor(player);
            notice(player, NoticeStyle.SYSTEM_INFO, "left", false);
        }
        sync(player, true);
    }

    // ================================================================================================ avisos

    private static void onStageUp(ServerPlayer player, Stage stage) {
        RespectCraft.LOGGER.info("RespectCraft Time Warp: {} exposure stage -> {}", player.getGameProfile().getName(), stage.id());
        switch (stage) {
            case ANOMALY -> notice(player, NoticeStyle.MYSTERIOUS, "anomaly", false);
            case INSTABILITY -> notice(player, NoticeStyle.WARNING, "instability", false);
            case CRITICAL -> {
                notice(player, NoticeStyle.WARNING, "critical", true);
                award(player, LINGERED);
            }
            default -> {}
        }
    }

    /**
     * Aviso do "Sistema" para um jogador (Notification HUD V2). {@code cinematic} = janela no meio da visao; senao
     * cartao. Os modificadores {@code time_warp} e {@code phenomenon:*} sao os ganchos do futuro Holographic UI
     * Phenomena (o cliente de hoje ignora ids que nao conhece).
     */
    static void notice(ServerPlayer player, NoticeStyle style, String key, boolean cinematic) {
        if (!TimeWarpConfig.notices()) return;
        Component title = Component.translatable("notice.respectcraft.time_warp.header." + style.id());
        Component body = Component.translatable("notice.respectcraft.time_warp." + key);
        HoloNotice n = cinematic ? HoloNotice.cinematic(NoticeKind.TIME_WARP, style, title, body)
                : HoloNotice.of(NoticeKind.TIME_WARP, title, body).style(style);
        n = n.group("time_warp").dedupe("time_warp:" + key)
                .modifiers(List.of("time_warp", style == NoticeStyle.SYSTEM_ERROR ? "phenomenon:static" : "phenomenon:none"));
        HoloNotices.toPlayer(player, n);
    }

    private static void award(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder h = player.server.getAdvancements().get(id);
        if (h == null) return;
        for (String criterion : player.getAdvancements().getOrStartProgress(h).getRemainingCriteria()) {
            player.getAdvancements().award(h, criterion);
        }
    }

    // ================================================================================================ acontecimentos

    private static void maybeHappen(ServerPlayer player, Stage stage) {
        long now = TimeWarpTravel.now(player);
        long last = TimeWarp.data(player).contains("last_event") ? TimeWarp.data(player).getLong("last_event") : -1;
        RandomSource r = player.getRandom();
        if (!TimeWarpRules.eventDue(stage, last, now, r.nextInt(1000), TimeWarpConfig.eventFrequency())) return;
        List<Happening> pool = new ArrayList<>(List.of(Happening.DISTANT_DOOR, Happening.HOUSE_NOISE));
        if (stage.ordinal() >= Stage.ANOMALY.ordinal()) pool.addAll(List.of(Happening.DISTANT_STEPS, Happening.CAVE_SOUND, Happening.FOG_PULSE));
        if (stage.ordinal() >= Stage.INSTABILITY.ordinal()) pool.addAll(List.of(Happening.STEPS_BEHIND, Happening.SYSTEM_MESSAGE, Happening.FOG_PULSE));
        if (stage == Stage.CRITICAL) pool.addAll(List.of(Happening.SILENCE, Happening.STEPS_BEHIND, Happening.SYSTEM_MESSAGE));
        Happening h = pool.get(r.nextInt(pool.size()));
        if (happen(player, h, stage)) TimeWarp.data(player).putLong("last_event", now);
    }

    /** Executa um acontecimento; false = nao coube agora (ex.: ruido de casa sem casa por perto). */
    public static boolean happen(ServerPlayer player, Happening h, Stage stage) {
        RandomSource r = player.getRandom();
        Vec3 eye = player.getEyePosition();
        boolean done = switch (h) {
            case DISTANT_DOOR -> {
                Vec3 at = around(player, 26 + r.nextInt(16), r);
                SoundEvent door = r.nextBoolean() ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE;
                cue(player, sound(door, at, 0.9F, 0.85F + r.nextFloat() * 0.1F));
                yield true;
            }
            case DISTANT_STEPS -> {
                Vec3 at = around(player, 18 + r.nextInt(10), r);
                Vec3 dir = new Vec3(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5).normalize().scale(0.8);
                cue(player, steps(SoundEvents.GRASS_STEP, at, dir, 5 + r.nextInt(4), 7, 0.55F));
                yield true;
            }
            case HOUSE_NOISE -> houseNoise(player, r);
            case CAVE_SOUND -> {
                cue(player, sound(SoundEvents.AMBIENT_CAVE.value(), around(player, 10 + r.nextInt(8), r), 0.7F, 0.9F + r.nextFloat() * 0.2F));
                yield true;
            }
            case FOG_PULSE -> {
                float strength = stage == Stage.CRITICAL ? 0.55F : stage == Stage.INSTABILITY ? 0.42F : 0.28F;
                cue(player, new Cue(TimeWarpNetworking.CUE_FOG, SoundEvents.AMBIENT_CAVE.value().getLocation(), eye.x, eye.y, eye.z,
                        0F, 1F, 0F, 0F, 0, 100 + r.nextInt(80), strength));
                yield true;
            }
            case STEPS_BEHIND -> {
                // Passos que se aproximam por tras e param a poucos blocos. Nada aparece.
                Vec3 back = player.getLookAngle().multiply(-1, 0, -1).normalize();
                Vec3 start = player.position().add(back.scale(12 + r.nextInt(4)));
                SoundEvent step = player.getBlockStateOn().getSoundType().getStepSound();
                cue(player, steps(step, start, back.scale(-1.2), 6 + r.nextInt(3), 6, 0.7F));
                yield true;
            }
            case SILENCE -> {
                cue(player, new Cue(TimeWarpNetworking.CUE_SILENCE, SoundEvents.AMBIENT_CAVE.value().getLocation(), eye.x, eye.y, eye.z,
                        0F, 1F, 0F, 0F, 0, 160 + r.nextInt(120), 1F));
                yield true;
            }
            case SYSTEM_MESSAGE -> {
                String[] pool = stage == Stage.CRITICAL && r.nextBoolean() ? CRITICAL_MESSAGES : SIGNAL_MESSAGES;
                notice(player, NoticeStyle.SYSTEM_ERROR, "signal." + pool[r.nextInt(pool.length)], stage == Stage.CRITICAL && r.nextInt(3) == 0);
                yield true;
            }
        };
        if (done) TimeWarpLog.debug(player, "happening " + h.id() + " (stage " + stage.id() + ")");
        return done;
    }

    /** Ruido compartilhado dentro de uma construcao proxima (todos os jogadores perto ouvem). */
    private static boolean houseNoise(ServerPlayer player, RandomSource r) {
        ServerLevel level = player.serverLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<StructurePiece> pieces = new ArrayList<>();
        for (var key : List.of(TimeWarpStructures.HOUSE, TimeWarpStructures.SETTLEMENT)) {
            Structure structure = registry.get(key);
            if (structure == null) continue;
            for (int[] o : new int[][] {{0, 0}, {24, 0}, {-24, 0}, {0, 24}, {0, -24}}) {
                StructureStart start = level.structureManager().getStructureAt(player.blockPosition().offset(o[0], 0, o[1]), structure);
                if (!start.isValid()) continue;
                for (StructurePiece p : start.getPieces()) if (p instanceof TimeWarpPiece tp && tp.kind() != TimeWarpPiece.Kind.DOCK) pieces.add(p);
            }
        }
        if (pieces.isEmpty()) return false;
        StructurePiece piece = pieces.get(r.nextInt(pieces.size()));
        BlockPos c = piece.getBoundingBox().getCenter();
        Vec3 at = new Vec3(c.getX() + 0.5, piece.getBoundingBox().minY() + 1.5, c.getZ() + 0.5);
        if (at.distanceTo(player.position()) < 6) return false; // nunca colado no jogador
        SoundEvent[] noises = {SoundEvents.CHEST_CLOSE, SoundEvents.WOODEN_DOOR_CLOSE, SoundEvents.WOOD_STEP, SoundEvents.FURNACE_FIRE_CRACKLE};
        SoundEvent noise = noises[r.nextInt(noises.length)];
        Cue cue = noise == SoundEvents.WOOD_STEP ? steps(noise, at, new Vec3(0.6, 0, 0.2), 3, 9, 0.6F) : sound(noise, at, 0.8F, 0.9F);
        for (ServerPlayer other : level.players()) {
            if (other.position().distanceTo(at) <= SHARED_RADIUS) cue(other, cue);
        }
        return true;
    }

    private static Vec3 around(ServerPlayer player, double distance, RandomSource r) {
        double a = r.nextDouble() * Math.PI * 2;
        return player.position().add(Math.cos(a) * distance, 1.0 + r.nextDouble() * 2, Math.sin(a) * distance);
    }

    static Cue sound(SoundEvent event, Vec3 at, float volume, float pitch) {
        return new Cue(TimeWarpNetworking.CUE_SOUND, event.getLocation(), at.x, at.y, at.z, volume, pitch, 0F, 0F, 1, 0, 0F);
    }

    static Cue steps(SoundEvent event, Vec3 start, Vec3 step, int count, int interval, float volume) {
        return new Cue(TimeWarpNetworking.CUE_STEPS, event.getLocation(), start.x, start.y, start.z, volume, 1F,
                (float) step.x, (float) step.z, count, interval, 0F);
    }

    private static void cue(ServerPlayer player, Cue cue) {
        TimeWarpNetworking.send(player, cue);
    }

    /** Log de diagnostico (so com {@code -Drespectcraft.timewarp.debug=true}). */
    static final class TimeWarpLog {
        static final boolean DEBUG = Boolean.getBoolean("respectcraft.timewarp.debug");

        static void debug(ServerPlayer player, String what) {
            if (DEBUG) RespectCraft.LOGGER.info("RespectCraft Time Warp [{}]: {}", player.getGameProfile().getName(), what);
        }
    }
}
