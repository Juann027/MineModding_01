package com.respectcraft.rig;

import com.respectcraft.RespectCraft;
import com.respectcraft.animation.PlayerAnimationDef;
import com.respectcraft.animation.PlayerAnimations;
import com.respectcraft.finisher.CameraTrack;
import com.respectcraft.finisher.FinisherDef;
import com.respectcraft.finisher.FinisherRigs;
import com.respectcraft.finisher.FinisherRole;
import com.respectcraft.finisher.FinisherTargeting;
import com.respectcraft.finisher.Finishers;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Catalogo das familias de rig e das entidades associadas (Etapa 4.12), registrado no construtor do mod nos dois lados.
 * Familia nova = manifesto {@code assets/respectcraft/rigs/<id>.rig.json} + uma linha {@link #register}; entidade
 * nova = manifesto {@code rigs/entities/<entidade>.json} + uma linha {@link #bind}. Ambos conferidos pelo validador
 * {@code tools/rigkit/validate_rig.py} e pelo {@code SelfTest}.
 */
public final class Rigs {
    /** Familia humanoide de referencia para as futuras frutas antropomorficas (o morango e a primeira fruta, Etapa 4.13.1). */
    public static final ResourceLocation FRUIT_HUMANOID_V1_ID = RespectCraft.id("fruit_humanoid_v1");

    /** Fixtures tecnicas (manequins): sem spawn natural, sem ovo, sem drop, sem progressao. */
    public static final ResourceLocation MANNEQUIN_A_ID = RespectCraft.id("dev_rig_mannequin_a");
    public static final ResourceLocation MANNEQUIN_B_ID = RespectCraft.id("dev_rig_mannequin_b");

    /** Etapa 4.13.1: primeira entidade de gameplay da familia (assets externos, binding oficial do artista). */
    public static final ResourceLocation STRAWBERRY_ID = RespectCraft.id("strawberry");

    public static RigDefinition FRUIT_HUMANOID_V1;

    /**
     * Cena de TESTE das partes (fixture, fora do catalogo de gameplay; so por {@code /respectcraft rig demo}): as
     * animacoes, camera e efeitos do Takedown, com a vitima tocando a animacao compartilhada {@code finisher.demo_peel}
     * da familia. Exige vitima da familia com maos e clip animado (mob sem rig e recusado antes da cena).
     */
    public static FinisherDef PEEL_DEMO;

    /**
     * Cena de TESTE do Finisher Scene Authoring Kit (fixture, fora do catalogo; so por
     * {@code /respectcraft rig demo <alvo> authoring}): os dois clips sairam do projeto conjunto
     * {@code scenekit/scenes/dev_player_vs_mannequin_b} pelo {@code tools/scenekit/split_scene.py} — o do jogador em
     * {@link #DEV_SCENES_FILE}, o da vitima no arquivo proprio do manequim B (override do binding). Eventos e camera
     * copiados do relatorio da separacao ({@code .events.txt}). Exige vitima da familia com clip animado: so o manequim
     * B tem esse clip (o resto e recusado antes da cena). Contra jogador, a vitima usa a reacao do Takedown.
     */
    public static FinisherDef AUTHORING_DEMO;

    /** Clips de jogador das cenas de teste do kit (gerado por tools/scenekit/make_templates.py). */
    public static final ResourceLocation DEV_SCENES_FILE = RespectCraft.id("animations/player/respectcraft_dev_scenes.animation.json");

    private static boolean bootstrapped;

    private Rigs() {}

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        FRUIT_HUMANOID_V1 = register(FRUIT_HUMANOID_V1_ID);
        bind(MANNEQUIN_A_ID);
        bind(MANNEQUIN_B_ID);
        bind(STRAWBERRY_ID);
        FinisherDef takedown = Finishers.TAKEDOWN;
        PEEL_DEMO = new FinisherDef(RespectCraft.id("dev/rig_peel_demo"), takedown.attacker(), takedown.victim(), takedown.camera(),
                null, 1, false, List.of(), takedown.fx().orElse(null), FinisherTargeting.REGULAR,
                new FinisherRigs(List.of(new FinisherRigs.Clip(FRUIT_HUMANOID_V1_ID, FinisherRole.VICTIM, "finisher.demo_peel")),
                        List.of(FinisherRigs.Requirement.rig(FinisherRole.VICTIM, FRUIT_HUMANOID_V1_ID, Set.of(RigCapabilities.HANDS)))));
        AUTHORING_DEMO = new FinisherDef(RespectCraft.id("dev/authoring_demo"),
                PlayerAnimations.register(PlayerAnimationDef
                        .builder(RespectCraft.id("dev/authoring_demo_attacker"), DEV_SCENES_FILE,
                                "animation.respectcraft.player.dev.authoring_demo_attacker", 60)
                        .priority(50).transitionTicks(2).blend(PlayerAnimationDef.Blend.FULL_BODY)
                        .event(0, PlayerAnimations.EVENT_SEQUENCE_START, "authoring_demo")
                        .event(0, PlayerAnimations.EVENT_SOUND, "respectcraft:finisher.whoosh")
                        .event(14, Finishers.EVENT_HIT, "2")
                        .event(14, PlayerAnimations.EVENT_CAMERA_SHAKE, "0.5,8")
                        .event(59, PlayerAnimations.EVENT_SEQUENCE_END, "authoring_demo").build()),
                takedown.victim(),
                CameraTrack.of(
                        new CameraTrack.Key(0, 150, 4.6, 1.7, 1.1, 1.0, 0),
                        new CameraTrack.Key(14, 75, 3.0, 1.2, 1.0, 0.95, -2),
                        new CameraTrack.Key(32, 40, 2.6, 0.9, 0.6, 0.85, -5),
                        new CameraTrack.Key(60, 20, 4.2, 2.3, 0.4, 1.0, 0)),
                null, 1, false, List.of(), null, FinisherTargeting.REGULAR,
                new FinisherRigs(List.of(), List.of(FinisherRigs.Requirement.rig(FinisherRole.VICTIM, FRUIT_HUMANOID_V1_ID, Set.of()))));
        List<String> problems = new ArrayList<>();
        for (FinisherDef def : Finishers.all()) problems.addAll(clipProblems(CustomRigRegistry.GLOBAL, def));
        problems.addAll(clipProblems(CustomRigRegistry.GLOBAL, PEEL_DEMO));
        problems.addAll(clipProblems(CustomRigRegistry.GLOBAL, AUTHORING_DEMO));
        if (!problems.isEmpty()) throw new IllegalStateException("Finisher rig clips: " + String.join("; ", problems));
        RespectCraft.LOGGER.info("RespectCraft custom rigs: {} rig(s), {} bound entity type(s)",
                CustomRigRegistry.GLOBAL.rigs().size(), CustomRigRegistry.GLOBAL.bindings().size());
    }

    /** Finalizacoes de teste (fora do catalogo) que os comandos de diagnostico conhecem. */
    public static List<FinisherDef> devFinishers() {
        return PEEL_DEMO == null ? List.of() : List.of(PEEL_DEMO, AUTHORING_DEMO);
    }

    private static RigDefinition register(ResourceLocation id) {
        return CustomRigRegistry.GLOBAL.register(CustomRigRegistry.loadRig(id));
    }

    private static RigBinding bind(ResourceLocation entityType) {
        return CustomRigRegistry.GLOBAL.bind(CustomRigRegistry.loadBinding(entityType));
    }

    /**
     * Regra pura: todo clip de familia e todo override de binding para esta finalizacao existe e tem a duracao da cena
     * (um clip errado recusaria a cena em jogo; aqui ele quebra o carregamento e o teste de fumaca).
     */
    public static List<String> clipProblems(CustomRigRegistry registry, FinisherDef def) {
        List<String> out = new ArrayList<>();
        for (FinisherRigs.Clip clip : def.rigs().clips()) {
            var rig = registry.rig(clip.rig());
            if (rig.isEmpty()) { out.add(def.id() + ": unknown rig " + clip.rig()); continue; }
            var shared = rig.get().animations().get(clip.key());
            if (shared == null) out.add(def.id() + ": rig " + clip.rig() + " has no shared animation " + clip.key());
            else if (shared.ticks() != def.durationTicks()) out.add(def.id() + ": " + clip.key() + " has " + shared.ticks()
                    + " ticks, scene has " + def.durationTicks());
        }
        for (RigBinding binding : registry.bindings()) {
            for (RigBinding.ClipOverride o : binding.clipOverrides()) {
                if (o.finisher().equals(def.id()) && o.ticks() != def.durationTicks()) {
                    out.add(binding.entityType() + ": override " + o.animation() + " has " + o.ticks() + " ticks, scene has " + def.durationTicks());
                }
            }
        }
        return out;
    }
}
