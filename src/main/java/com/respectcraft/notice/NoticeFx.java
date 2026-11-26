package com.respectcraft.notice;

import com.respectcraft.RespectCraft;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.EffectSource;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Ponte dos avisos holograficos com o Calamidade_Awakening (Etapa 4.11): o <b>unico dono</b> dos faroes de aviso.
 * Cada {@link NoticeKind.Beacon} e um efeito curto (pilar de luz + anel no chao + luz) aceso no local do evento, visto
 * por quem estiver no alcance. Registro no construtor do mod (lado comum); comeca so no servidor, por
 * {@link HoloNotices}. Nada de {@code film}, conjurador ou {@code shake}: o farol nunca mexe na camera.
 *
 * <p>Efeitos de finalizacao continuam so no {@code fx.FinisherFx}; este arquivo nao toca neles.
 */
public final class NoticeFx {
    /** Duracao de um farol (ticks). */
    public static final int TICKS = 80;
    /** Altura do pilar (blocos). */
    public static final double PILLAR = 14.0;

    private static boolean registered;

    private NoticeFx() {}

    public static ResourceLocation effectId(NoticeKind.Beacon beacon) {
        return RespectCraft.id("notice/beacon_" + beacon.name().toLowerCase(java.util.Locale.ROOT));
    }

    static int color(NoticeKind.Beacon beacon) {
        return switch (beacon) {
            case BOSS -> 0xffff3a4a;
            case VICTORY -> 0xff6be3a0;
            case PORTAL -> 0xffb07cff;
            case EVENT, NONE -> 0xffffd86b;
        };
    }

    /** Construtor do mod: um efeito por farol. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        for (NoticeKind.Beacon beacon : NoticeKind.Beacon.values()) {
            if (beacon == NoticeKind.Beacon.NONE) continue;
            ResourceLocation id = effectId(beacon);
            int color = color(beacon);
            float k = beacon == NoticeKind.Beacon.BOSS ? 1.3F : 1F;
            Effects.register(id, new Effects.Definition(TICKS, 3, color, Effects.Shape.NONE, Effects.Material.NONE,
                    null, null).withRange(Math.max(NoticeRules.BOSS_RADIUS, 128)));
            Effects.timeline(id, Timeline.builder(TICKS)
                    .phase("beacon", 0, TICKS, 8, 24)
                    .track("w", keyframe(0, 0F), keyframe(10, 1F, Timeline.Easing.EASE_OUT), keyframe(TICKS - 24, 0.8F),
                            keyframe(TICKS, 0F, Timeline.Easing.SMOOTH))
                    .track("ring_r", keyframe(0, 0.4F), keyframe(TICKS, 4.5F * k, Timeline.Easing.EASE_OUT))
                    .marker("flash", 0)
                    .build());
            Effects.beams(id, Beam.builder(Point.facing(0, 0, 0), Point.facing(0, PILLAR, 0))
                    .width(0.06F, 0.55F * k).color(0xfff4fbff, color).intensity(1.2F * k).noise(0.02F)
                    .pulses(2, 0.15F, 0.6F).track(Beam.Param.WEIGHT, "w").weightPhase("beacon").build());
            Effects.lights(id,
                    Light.point(Point.facing(0, 2.0, 0)).color(color).radius(6F * k).falloff(1.5F).intensity(1.2F * k)
                            .track(Light.Param.WEIGHT, "w").weightPhase("beacon").build(),
                    Light.ring(Point.facing(0, 0.1, 0), 0.4F, 0.5F).color(color).intensity(1.1F * k).height(1.2F)
                            .track(Light.Param.RADIUS, "ring_r").weightPhase("beacon").build());
        }
        RespectCraft.LOGGER.info("RespectCraft notice FX registered on Calamidade_Awakening");
    }

    /**
     * Acende o farol do aviso no ponto (servidor). {@code anchor} (ex.: o chefe) vira a fonte do efeito; sem ele a
     * fonte e o bloco. Recusado pelos limites do Calamidade = so sem o farol (o aviso sai do mesmo jeito).
     */
    public static Optional<Effects.Handle> play(ServerLevel level, NoticeKind.Beacon beacon, BlockPos pos, Entity anchor) {
        if (!registered || beacon == NoticeKind.Beacon.NONE) return Optional.empty();
        EffectSource source = anchor != null ? EffectSource.entity(anchor) : EffectSource.block(level, pos);
        Vec3 at = Vec3.atBottomCenterOf(pos);
        Optional<Effects.Handle> handle = Effects.start(effectId(beacon), level, Placement.at(at).withSource(source));
        if (handle.isEmpty()) RespectCraft.LOGGER.debug("Notice beacon {} at {} refused by Calamidade limits", beacon, pos);
        return handle;
    }
}
