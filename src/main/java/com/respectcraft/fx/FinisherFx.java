package com.respectcraft.fx;

import com.respectcraft.RespectCraft;
import com.respectcraft.finisher.FinisherDef;
import com.respectcraft.finisher.FinisherRole;
import com.respectcraft.finisher.Finishers;
import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.EffectSource;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/**
 * Ponte unica do Finisher Engine com o Calamidade_Awakening (Etapa 4.10): o <b>unico dono</b> dos efeitos de
 * finalizacao. Registra os efeitos no construtor do mod (lado comum, o servidor dedicado tambem os conhece), comeca o
 * efeito HERO da cena no inicio da sessao, dispara golpes e quedas nos marcadores da cena e encerra tudo no fim
 * ({@code release}) ou no cancelamento ({@code stop}).
 *
 * <p>Regras: fonte dos efeitos = o jogador participante ({@link FinisherFxRules#sourceRole}); sem {@code film} nem
 * conjurador (a camera continua sendo a {@code CinematicCamera}) e sem {@code shake} (o tremor e so dela); pontos
 * pela ancora da cena e deslocamentos no referencial orientado, nunca por ossos do GeckoLib. Shaders e
 * {@code AddonLayers} ficam em {@code client.fx.FinisherFxClient}.
 *
 * <p>O estado de cada cena ({@link Scene}) vive na sessao do {@code FinisherTestMechanic}, nao em estaticos.
 */
public final class FinisherFx {
    /** Duracao dos efeitos de golpe e de queda (curtos: acabam antes da cena e nao gastam o limite por fonte). */
    public static final int HIT_TICKS = 16;
    public static final int FALL_TICKS = 24;
    /** Atmosfera de chefe: entrada 0..20, laco 20..40, saida 40..50 depois do {@code release}. */
    public static final int BOSS_TICKS = 50;
    /** Altura do peito usada para pontos da cena (referencial orientado, sem ossos). */
    public static final double CHEST = 1.1;
    /** Raio da cupula do COMBO, em blocos. */
    public static final float DOME_RADIUS = 2.3F;

    private static boolean registered;

    private FinisherFx() {}

    /**
     * Estado dos efeitos de uma sessao. Guardado na propria sessao; nada aqui sobrevive a ela.
     */
    public static final class Scene {
        final FinisherDef def;
        final FinisherFxDef fx;
        final LivingEntity attacker;
        final LivingEntity target;
        final EffectSource source;
        final List<FinisherFxRules.Cue> cues;
        final List<Effects.Handle> handles = new ArrayList<>();
        int lastTick = 0;
        boolean ended;

        Scene(FinisherDef def, FinisherFxDef fx, LivingEntity attacker, LivingEntity target, EffectSource source) {
            this.def = def;
            this.fx = fx;
            this.attacker = attacker;
            this.target = target;
            this.source = source;
            this.cues = FinisherFxRules.forSession(FinisherFxRules.cues(def), target instanceof ServerPlayer);
        }

        /** Instancias desta cena ainda vivas no servidor (diagnostico e teste). */
        public int alive() {
            int n = 0;
            for (Effects.Handle h : handles) if (h.alive()) n++;
            return n;
        }
    }

    // ------------------------------------------------------------------------------------------------ registro

    /** Construtor do mod, depois de {@code Finishers.bootstrap()}: efeitos de todas as finalizacoes com fx. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        for (FinisherFxDef.Tier tier : FinisherFxDef.Tier.values()) {
            for (int strength = 1; strength <= 3; strength++) registerHit(tier, strength);
            registerFall(tier);
        }
        registerBossAtmosphere();
        for (FinisherDef def : Finishers.all()) def.fx().ifPresent(fx -> registerScene(def, fx));
        RespectCraft.LOGGER.info("RespectCraft finisher FX registered on Calamidade_Awakening");
    }

    /**
     * Roteiro da cena: fase {@code scene} do tamanho da finalizacao, um marcador e uma fase curta por golpe/queda
     * (nos ticks dos eventos das animacoes), fase {@code impact_frame} nos golpes de forca 3 (a cena cede o passe ao
     * impact frame), trilha {@code pulse} (impulso que decai a cada momento) e {@code energy} (sobe com os golpes).
     */
    public static Timeline sceneTimeline(FinisherDef def) {
        int duration = def.durationTicks();
        List<FinisherFxRules.Cue> cues = FinisherFxRules.cues(def);
        Timeline.Builder b = Timeline.builder(duration).phase("scene", 0, duration, 6, Math.min(12, duration / 4));
        int hits = 0;
        for (FinisherFxRules.Cue cue : cues) if (cue.kind() == FinisherFxRules.CueKind.HIT) hits++;
        for (FinisherFxRules.Cue cue : cues) {
            b.marker(cue.marker(), cue.tick());
            int end = Math.min(duration, cue.tick() + 10);
            b.phase(cue.marker(), cue.tick(), end, 0, Math.min(8, end - cue.tick()));
            if (cue.kind() == FinisherFxRules.CueKind.HIT && cue.strength() >= 3) {
                b.phase("impact_frame_" + cue.marker(), cue.tick(), Math.min(duration, cue.tick() + 7), 0, 0);
            }
        }
        // Trilhas amostradas a cada 2 ticks (lineares entre si): impulso e energia acumulada.
        List<Timeline.Keyframe> pulse = new ArrayList<>(), energy = new ArrayList<>(), sweep = new ArrayList<>();
        List<Integer> samples = new ArrayList<>();
        for (int t = 0; t < duration; t += 2) samples.add(t);
        samples.add(duration);
        for (int t : samples) {
            float p = 0, e;
            int passed = 0;
            for (FinisherFxRules.Cue cue : cues) {
                if (t < cue.tick()) continue;
                float age = t - cue.tick();
                p = Math.max(p, cue.strength() / 3F * (float) Math.exp(-age / 4.0));
                if (cue.kind() == FinisherFxRules.CueKind.HIT) passed++;
            }
            e = hits == 0 ? 0.3F : 0.2F + 0.8F * passed / hits;
            float fadeOut = Math.min(1F, (duration - t) / 12F);
            float fadeIn = Math.min(1F, t / 8F);
            pulse.add(keyframe(t, p));
            energy.add(keyframe(t, e * fadeOut * fadeIn));
        }
        b.track("pulse", pulse.toArray(Timeline.Keyframe[]::new));
        b.track("energy", energy.toArray(Timeline.Keyframe[]::new));
        // Varredura (RASTEIRA): 0 → 1 nos 8 ticks antes do primeiro golpe; depois some em 14 ticks.
        int first = cues.stream().filter(c -> c.kind() == FinisherFxRules.CueKind.HIT).mapToInt(FinisherFxRules.Cue::tick)
                .findFirst().orElse(Math.min(duration - 1, 10));
        int sweepStart = Math.max(0, first - 8);
        if (sweepStart > 0) sweep.add(keyframe(0, 0F));
        sweep.add(keyframe(sweepStart, 0F));
        sweep.add(keyframe(Math.max(sweepStart + 1, first), 1F, Timeline.Easing.EASE_OUT));
        b.track("sweep", sweep.toArray(Timeline.Keyframe[]::new));
        List<Timeline.Keyframe> fade = new ArrayList<>();
        fade.add(keyframe(0, 0F));
        if (sweepStart > 0) fade.add(keyframe(sweepStart, 0F));
        fade.add(keyframe(Math.max(sweepStart + 1, Math.min(duration - 2, first)), 1F, Timeline.Easing.EASE_OUT));
        int fadeEnd = Math.min(duration, Math.max(sweepStart + 2, first) + 14);
        if (fadeEnd > fade.get(fade.size() - 1).tick()) fade.add(keyframe(fadeEnd, 0F, Timeline.Easing.SMOOTH));
        b.track("sweep_fade", fade.toArray(Timeline.Keyframe[]::new));
        return b.build();
    }

    private static void registerScene(FinisherDef def, FinisherFxDef fx) {
        ResourceLocation id = fx.scene();
        int duration = def.durationTicks();
        float k = fx.tier().strength();
        int color = switch (fx.look()) {
            case TAKEDOWN_FOCUS -> 0xffffd29a;
            case RASTEIRA_SWEEP -> 0xff58c8ff;
            case COMBO_PRESSURE -> 0xffff4a2e;
        };
        // HERO: a cena gira em torno deste efeito; nunca e cortado pelo limite de desenhados.
        Effects.register(id, new Effects.Definition(duration, 6, color, Effects.Shape.NONE, Effects.Material.NONE,
                null, null).withRange(96).withPriority(Effects.Priority.HERO));
        Effects.timeline(id, sceneTimeline(def));
        switch (fx.look()) {
            case TAKEDOWN_FOCUS -> Effects.lights(id,
                    // Luz quente e baixa entre os dois, que acende a cada golpe.
                    Light.point(Point.facing(0, CHEST, 0.2)).color(color).radius(4.5F).falloff(1.6F)
                            .intensity(1.1F * k).track(Light.Param.WEIGHT, "pulse").weightPhase("scene").build(),
                    Light.disc(Point.facing(0, 0.1, 0.55), 1.1F).color(0xffffb070).intensity(0.6F * k).height(1.5F)
                            .track(Light.Param.WEIGHT, "energy").weightPhase("scene").build());
            case RASTEIRA_SWEEP -> {
                // Arco rente ao chao em volta das pernas da vitima, do lado do executor, revelado em sequencia.
                List<Beam> arc = new ArrayList<>();
                int segments = 5;
                double cx = 0, cz = 0.58, r = 0.85;
                for (int s = 0; s < segments; s++) {
                    double a0 = Math.toRadians(-95 + 190.0 * s / segments);
                    double a1 = Math.toRadians(-95 + 190.0 * (s + 1) / segments);
                    arc.add(Beam.builder(
                                    Point.facing(cx + r * Math.sin(a0), 0.12, cz - r * Math.cos(a0)),
                                    Point.facing(cx + r * Math.sin(a1), 0.12, cz - r * Math.cos(a1)))
                            .width(0.05F, 0.5F * k).color(0xffe8fbff, color).intensity(1.3F * k).noise(0.015F)
                            .track(Beam.Param.WEIGHT, "sweep_fade").weightPhase("scene").build());
                }
                Effects.beams(id, arc.toArray(Beam[]::new));
                Effects.lights(id,
                        Light.along(arc.get(segments / 2)).radius(2.2F).intensity(1.2F * k).build(),
                        Light.disc(Point.facing(0, 0.1, 0.58), 1.4F).color(color).intensity(0.7F * k).height(1.2F)
                                .track(Light.Param.WEIGHT, "pulse").weightPhase("scene").build());
            }
            case COMBO_PRESSURE -> {
                // Octogono de feixes no chao, na borda da cupula, aceso pela energia acumulada dos golpes.
                List<Beam> ring = new ArrayList<>();
                for (int s = 0; s < 8; s++) {
                    double a0 = Math.toRadians(22.5 + 45.0 * s), a1 = Math.toRadians(22.5 + 45.0 * (s + 1));
                    ring.add(Beam.builder(
                                    Point.facing(DOME_RADIUS * Math.sin(a0), 0.08, DOME_RADIUS * Math.cos(a0)),
                                    Point.facing(DOME_RADIUS * Math.sin(a1), 0.08, DOME_RADIUS * Math.cos(a1)))
                            .width(0.04F, 0.35F).color(0xffffe0d0, color).intensity(1.1F * k)
                            .pulses(1, 0.12F, 0.5F).track(Beam.Param.WEIGHT, "energy").weightPhase("scene").build());
                }
                Effects.beams(id, ring.toArray(Beam[]::new));
                Effects.lights(id,
                        Light.point(Point.facing(0, CHEST, 0.3)).color(color).radius(5.5F).falloff(1.4F)
                                .intensity(1.8F * k).track(Light.Param.WEIGHT, "pulse").weightPhase("scene").build(),
                        Light.disc(Point.facing(0, 0.1, 0), DOME_RADIUS).color(0xffff7a50).intensity(0.55F * k)
                                .height(1.5F).track(Light.Param.WEIGHT, "energy").weightPhase("scene").build());
            }
        }
    }

    /** Golpe: luz breve; anel no chao a partir da forca 2; impact frame (passe no cliente) so na forca 3. */
    private static void registerHit(FinisherFxDef.Tier tier, int strength) {
        ResourceLocation id = FinisherFxDef.hitEffect(tier, strength);
        var layers = FinisherFxRules.layersFor(strength);
        float k = tier.strength() * (0.55F + 0.15F * strength);
        Effects.Definition definition = new Effects.Definition(HIT_TICKS, 3.5, 0xfffff0dc, Effects.Shape.NONE,
                Effects.Material.NONE, null, null).withRange(96);
        // O golpe final (impact frame) e HERO: o passe nunca e cortado pelo limite de desenhados.
        if (layers.contains(FinisherFxRules.HitLayer.IMPACT_FRAME)) definition = definition.withPriority(Effects.Priority.HERO);
        Effects.register(id, definition);
        Effects.timeline(id, Timeline.builder(HIT_TICKS)
                .phase("flash", 0, 6, 0, 5)
                .phase("ring", 0, HIT_TICKS, 0, 8)
                .phase("frame", 0, 6, 0, 2)
                .track("ring_r", keyframe(0, 0.3F), keyframe(HIT_TICKS, 1.2F + 1.1F * strength * tier.strength(),
                        Timeline.Easing.EASE_OUT))
                .marker("impact", 0)
                .build());
        List<Light> lights = new ArrayList<>();
        lights.add(Light.point(Point.facing(0, CHEST, -0.2)).color(0xfffff0dc).radius(2.5F + strength)
                .intensity(1.4F * k).falloff(1.8F).weightPhase("flash").build());
        if (layers.contains(FinisherFxRules.HitLayer.RING)) {
            lights.add(Light.ring(Point.facing(0, 0.1, 0), 0.3F, 0.45F).color(0xffffc890).intensity(1.6F * k)
                    .height(1.2F).track(Light.Param.RADIUS, "ring_r").weightPhase("ring").build());
        }
        Effects.lights(id, lights.toArray(Light[]::new));
    }

    /** Queda: onda de poeira (passe no cliente) e luz no chao onde o corpo bate. */
    private static void registerFall(FinisherFxDef.Tier tier) {
        ResourceLocation id = FinisherFxDef.fallEffect(tier);
        float k = tier.strength();
        Effects.register(id, new Effects.Definition(FALL_TICKS, 4, 0xffc8a47a, Effects.Shape.NONE,
                Effects.Material.NONE, null, null).withRange(96));
        Effects.timeline(id, Timeline.builder(FALL_TICKS)
                .phase("dust", 0, FALL_TICKS, 0, 12)
                .phase("glow", 0, 10, 0, 8)
                .track("ring_r", keyframe(0, 0.2F), keyframe(FALL_TICKS, 1.6F + 1.6F * k, Timeline.Easing.EASE_OUT))
                .marker("impact", 0)
                .build());
        Effects.lights(id,
                Light.disc(Point.facing(0, 0.1, 0), 0.9F).color(0xffffc08a).intensity(0.9F * k).height(1F)
                        .weightPhase("glow").build(),
                Light.ring(Point.facing(0, 0.1, 0), 0.2F, 0.5F).color(0xffffd8a8).intensity(0.8F * k).height(1F)
                        .track(Light.Param.RADIUS, "ring_r").weightPhase("dust").build());
    }

    /** Atmosfera de chefe (sustain): ceu escurecido e avermelhado, nevoa quente; soltada no fim da cena. */
    private static void registerBossAtmosphere() {
        ResourceLocation id = FinisherFxDef.BOSS_ATMOSPHERE;
        Effects.register(id, new Effects.Definition(BOSS_TICKS, 12, 0xff8a1020, Effects.Shape.NONE,
                Effects.Material.NONE, null, null).withRange(128));
        Effects.timeline(id, Timeline.builder(BOSS_TICKS)
                .track("w", keyframe(0, 0F), keyframe(20, 1F, Timeline.Easing.SMOOTH), keyframe(40, 1F),
                        keyframe(BOSS_TICKS, 0F, Timeline.Easing.SMOOTH))
                .sustain(20, 40)
                .release(40)
                .build());
        Effects.atmosphere(id, Atmosphere.builder()
                .sky(0xff1a0410, 0xff6a1020, 0.55F)
                .fog(0xff2a0810, 0.3F, 16, 120)
                .exposure(0.88F)
                .ambient(0xffff4a40, 0.12F)
                .track(Atmosphere.Param.WEIGHT, "w")
                .build());
    }

    // ------------------------------------------------------------------------------------------------ sessao

    /**
     * Comeca os efeitos de uma sessao: o HERO da cena no ponto da cena e, contra chefe, a atmosfera. Devolve null
     * quando a finalizacao nao tem {@link FinisherFxDef} (comportamento antigo) ou nao ha jogador para ser a fonte.
     *
     * @param center ponto da cena (meio dos dois, na altura do chao mais baixo)
     * @param yaw    yaw da ancora da cena (o do executor ao montar o palco)
     * @param boss   algum participante e chefe ({@code c:bosses})
     */
    public static Scene start(LivingEntity attacker, LivingEntity target, FinisherDef def, Vec3 center, float yaw,
                              boolean boss) {
        if (!registered || def.fx().isEmpty() || !(attacker.level() instanceof ServerLevel level)) return null;
        FinisherRole role = FinisherFxRules.sourceRole(attacker instanceof ServerPlayer, target instanceof ServerPlayer);
        if (role == null) return null;
        ServerPlayer player = (ServerPlayer) (role == FinisherRole.ATTACKER ? attacker : target);
        FinisherFxDef fx = def.fx().get();
        Scene scene = new Scene(def, fx, attacker, target, EffectSource.player(player));
        Placement placement = Placement.at(center);
        Vec3 from = new Vec3(attacker.getX(), center.y, attacker.getZ());
        placement = fx.orientation() == FinisherFxDef.Orientation.ARRIVING_FROM_ATTACKER && from.distanceToSqr(center) > 1e-4
                ? placement.arrivingFrom(from) : placement.withYaw(yaw);
        track(scene, Effects.start(fx.scene(), level, placement.withSource(scene.source)), "scene");
        if (boss) track(scene, Effects.start(FinisherFxDef.BOSS_ATMOSPHERE, level,
                Placement.at(center).withYaw(yaw).withSource(scene.source)), "boss atmosphere");
        return scene;
    }

    /** Tick da sessao (servidor, uma vez por tick): dispara os golpes e quedas cujo marcador ja chegou. */
    public static void tick(Scene scene, int sceneTick) {
        if (scene == null || scene.ended || sceneTick <= scene.lastTick) return;
        List<FinisherFxRules.Cue> due = FinisherFxRules.due(scene.cues, scene.lastTick, sceneTick);
        scene.lastTick = sceneTick;
        if (!(scene.target.level() instanceof ServerLevel level)) return;
        for (FinisherFxRules.Cue cue : due) {
            if (cue.kind() == FinisherFxRules.CueKind.HIT) {
                Vec3 at = scene.target.position();
                Vec3 from = new Vec3(scene.attacker.getX(), at.y, scene.attacker.getZ());
                Placement p = from.distanceToSqr(at) > 1e-4 ? Placement.at(at).arrivingFrom(from)
                        : Placement.at(at).withYaw(scene.attacker.getYRot());
                track(scene, Effects.start(scene.fx.hitEffect(cue.strength()), level, p.withSource(scene.source)), cue.marker());
            } else {
                // Onde o corpo bate: recuo para tras da vitima, como a poeira vanilla de antes.
                Vec3 behind = Vec3.directionFromRotation(0, scene.target.getYRot()).scale(-cue.back());
                Vec3 at = scene.target.position().add(behind.x, 0, behind.z);
                track(scene, Effects.start(scene.fx.fallEffect(), level,
                        Placement.at(at).withYaw(scene.target.getYRot()).withSource(scene.source)), cue.marker());
            }
        }
    }

    /** Fim ou cancelamento da sessao: nada desta cena continua ativo (release no fim, stop no cancelamento). */
    public static void end(Scene scene, FinisherFxRules.EndReason reason) {
        if (scene == null || scene.ended) return;
        scene.ended = true;
        FinisherFxRules.EndAction action = FinisherFxRules.endAction(reason);
        for (Effects.Handle handle : scene.handles) {
            if (!handle.alive()) continue;
            // release so tem efeito em sustain (atmosfera de chefe: saida de 10 ticks); o resto para agora.
            if (action == FinisherFxRules.EndAction.STOP || !handle.release()) handle.stop();
        }
        scene.handles.clear();
    }

    private static void track(Scene scene, Optional<Effects.Handle> handle, String what) {
        if (handle.isPresent()) {
            scene.handles.add(handle.get());
        } else {
            // Limite por fonte/servidor do Calamidade: recusado sem erro (a cena continua, so sem esse efeito).
            RespectCraft.LOGGER.debug("Finisher FX {} ({}) refused by Calamidade limits", scene.def.id(), what);
        }
    }
}
