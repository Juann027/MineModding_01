package com.respectcraft.client.fx;

import com.respectcraft.finisher.FinisherDef;
import com.respectcraft.finisher.Finishers;
import com.respectcraft.fx.FinisherFx;
import com.respectcraft.fx.FinisherFxDef;
import com.respectcraft.fx.FinisherFxRules;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.client.AddonLayers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Camadas de cliente dos efeitos de finalizacao (Etapa 4.10): os passes de pos-processamento dos shaders em
 * {@code assets/respectcraft/shaders/fx}. Os efeitos em si (definicoes, timelines, luzes, feixes, atmosfera) sao
 * registrados no lado comum por {@link FinisherFx}.
 *
 * <p>Acessibilidade do Calamidade: todo passe proprio multiplica a forca por {@code post.screenEffects()}; o impact
 * frame cai para 35% e nao inverte (nao pisca) com "ocultar clarões"; as linhas de foco so se mexem com efeitos de
 * movimento ligados. Nada aqui usa {@code post.shake} (o tremor e so da {@code CinematicCamera}) nem {@code film}.
 */
public final class FinisherFxClient {
    private FinisherFxClient() {}

    /** {@code FMLClientSetupEvent} (barramento do mod). */
    public static void onClientSetup(FMLClientSetupEvent event) {
        for (FinisherDef def : Finishers.all()) {
            def.fx().ifPresent(fx -> {
                List<String> heavy = new ArrayList<>();
                List<FinisherFxRules.Cue> cues = FinisherFxRules.cues(def);
                for (FinisherFxRules.Cue cue : cues) {
                    if (cue.kind() == FinisherFxRules.CueKind.HIT && cue.strength() >= 3) heavy.add("impact_frame_" + cue.marker());
                }
                AddonLayers.post(fx.scene(), post -> scene(post, fx, cues, heavy));
            });
        }
        for (FinisherFxDef.Tier tier : FinisherFxDef.Tier.values()) {
            for (int strength = 1; strength <= 2; strength++) {
                int s = strength;
                AddonLayers.post(FinisherFxDef.hitEffect(tier, s), post -> hitAccent(post, tier, s));
            }
            AddonLayers.post(FinisherFxDef.hitEffect(tier, 3), post -> impactFrame(post, tier));
            AddonLayers.post(FinisherFxDef.fallEffect(tier), post -> dust(post, tier));
        }
    }

    private static void scene(AddonLayers.Post post, FinisherFxDef fx, List<FinisherFxRules.Cue> cues, List<String> heavy) {
        Timeline.Sampler t = post.timeline();
        float scene = t.weight("scene");
        if (scene <= 0.001F) return;
        float k = fx.tier().strength();
        float pulse = t.value("pulse");
        float energy = t.value("energy");
        // No quadro de impacto (forca 3) a cena cede o passe ao impact frame do golpe.
        boolean yield = false;
        for (String phase : heavy) yield |= t.weight(phase) > 0;
        float screen = post.screenEffects();
        switch (fx.look()) {
            case TAKEDOWN_FOCUS -> {
                float strength = FinisherFxRules.screenStrength(scene * (0.2F + 0.8F * pulse), fx.tier(), screen, true);
                if (!yield && strength > 0.002F && post.passesLeft() > 0) {
                    Vec3 focus = post.center().add(post.facing(0, FinisherFx.CHEST, 0.35));
                    post.pass(fx.look().shader(), u -> {
                        u.v3("FocusCenter", focus);
                        u.f4("FocusState", strength, (float) post.age(), post.time().seed(), post.motionEffects() ? 1F : 0F);
                        u.f3("FocusColor", 1.0F, 0.93F, 0.82F);
                    });
                }
                post.vignette(0xff160606, scene * energy * 0.22F * k);
            }
            case RASTEIRA_SWEEP -> {
                float fade = t.value("sweep_fade");
                float strength = FinisherFxRules.screenStrength(scene * fade, fx.tier(), screen, true);
                if (!yield && strength > 0.002F && post.passesLeft() > 0) {
                    Vec3 feet = post.center().add(post.facing(0, 0.02, 0.58));
                    Vec3 fwd = post.facing(0, 0, 1), right = post.facing(1, 0, 0);
                    float sweep = t.value("sweep");
                    post.pass(fx.look().shader(), u -> {
                        u.v3("SweepCenter", feet);
                        u.v3("SweepFwd", fwd);
                        u.v3("SweepRight", right);
                        u.f4("SweepState", strength, sweep, (float) post.age(), 0.85F);
                        u.f3("SweepColor", 0.35F, 0.78F, 1.0F);
                    });
                }
                post.bloom(scene * pulse * 0.15F * k);
            }
            case COMBO_PRESSURE -> {
                float strength = FinisherFxRules.screenStrength(scene * (0.3F + 0.7F * energy), fx.tier(), screen, true);
                // Onda no chao do momento mais recente (golpe ou queda), pelo relogio da instancia.
                float since = -1F, wave = 0F;
                for (FinisherFxRules.Cue cue : cues) {
                    double s = t.since(cue.marker());
                    if (s >= 0 && (since < 0 || s < since)) {
                        since = (float) s;
                        wave = cue.kind() == FinisherFxRules.CueKind.HIT ? cue.strength() / 3F : 0.8F;
                    }
                }
                float lastSince = since, lastWave = post.flashAllowed() ? wave : wave * 0.6F;
                if (!yield && strength > 0.002F && post.passesLeft() > 0) {
                    Vec3 center = post.center();
                    post.pass(fx.look().shader(), u -> {
                        u.v3("DomeCenter", center);
                        u.f4("DomeState", strength, pulse, FinisherFx.DOME_RADIUS, (float) post.age());
                        u.f4("DomeHit", lastSince, 0.32F, lastWave, 0F);
                        u.f3("DomeColor", 1.0F, 0.36F, 0.22F);
                    });
                }
                post.bloom(scene * pulse * 0.25F * k);
                post.aberration(scene * pulse * 0.12F * k);
            }
        }
    }

    /** Golpes de forca 1 e 2: so realce de tela (bloom; aberracao na intensidade maxima). Luz e anel sao luzes. */
    private static void hitAccent(AddonLayers.Post post, FinisherFxDef.Tier tier, int strength) {
        float flash = post.timeline().weight("flash");
        if (flash <= 0) return;
        post.bloom(flash * 0.07F * strength * tier.strength());
        if (strength >= 2 && tier == FinisherFxDef.Tier.INTENSE) post.aberration(flash * 0.1F);
    }

    /** Forca 3: impact frame de duas cores (6 ticks), com o holofote na vitima. */
    private static void impactFrame(AddonLayers.Post post, FinisherFxDef.Tier tier) {
        Timeline.Sampler t = post.timeline();
        float frame = t.weight("frame");
        if (frame <= 0.001F) return;
        float strength = FinisherFxRules.screenStrength(frame, tier, post.screenEffects(), post.flashAllowed());
        boolean invert = FinisherFxRules.impactFrameInverts(post.flashAllowed(), t.since("impact"));
        if (strength > 0.002F && post.passesLeft() > 0) {
            Vec3 victim = post.center().add(post.facing(0, FinisherFx.CHEST, 0));
            post.pass("respectcraft:finisher_impact_frame", u -> {
                u.v3("ImpactCenter", victim);
                u.f3("ImpactLight", 1.0F, 0.94F, 0.86F);
                u.f3("ImpactDark", 0.09F, 0.02F, 0.05F);
                u.f4("ImpactState", strength, invert ? 1F : 0F, 0.36F, 0F);
            });
        }
        post.bloom(t.weight("flash") * 0.2F * tier.strength());
    }

    /** Queda: onda de poeira no chao (o anel e o disco de luz sao luzes do proprio efeito). */
    private static void dust(AddonLayers.Post post, FinisherFxDef.Tier tier) {
        Timeline.Sampler t = post.timeline();
        float weight = t.weight("dust");
        float strength = FinisherFxRules.screenStrength(weight, tier, post.screenEffects(), true);
        if (strength <= 0.002F || post.passesLeft() <= 0) return;
        Vec3 center = post.center();
        float since = (float) t.since("impact");
        float seed = post.time().seed();
        post.pass("respectcraft:finisher_dust_wave", u -> {
            u.v3("DustCenter", center);
            u.f4("DustState", since, 0.16F, strength, 1.6F + 1.6F * tier.strength());
            u.f4("DustColor", 0.62F, 0.53F, 0.42F, seed);
        });
    }
}
