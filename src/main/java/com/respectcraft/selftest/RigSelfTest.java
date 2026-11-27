package com.respectcraft.selftest;

import com.respectcraft.RespectCraft;
import com.respectcraft.finisher.EntityFinisherClip;
import com.respectcraft.finisher.FinisherDef;
import com.respectcraft.finisher.FinisherEligibility;
import com.respectcraft.finisher.FinisherParticipants;
import com.respectcraft.finisher.FinisherParticipants.Kind;
import com.respectcraft.finisher.FinisherParticipants.Source;
import com.respectcraft.finisher.FinisherRigs;
import com.respectcraft.finisher.FinisherRole;
import com.respectcraft.finisher.FinisherTargeting;
import com.respectcraft.finisher.Finishers;
import com.respectcraft.fx.GeoFxKeyframes;
import com.respectcraft.rig.CustomRigRegistry;
import com.respectcraft.rig.RigBinding;
import com.respectcraft.rig.RigCapabilities;
import com.respectcraft.rig.RigDefinition;
import com.respectcraft.rig.RigSockets;
import com.respectcraft.rig.RigVisuals;
import com.respectcraft.rig.Rigs;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Etapa 4.12: Custom Rig Framework. Regras puras do registro (familias, bindings), da resolucao de clips por
 * prioridade (explicito → override do binding → familia → parado), dos requisitos de participante, da regra de chefes
 * com rigs, dos sockets e das partes. Registros de teste ficam em instancias proprias de {@link CustomRigRegistry};
 * o catalogo global so e lido.
 */
final class RigSelfTest {
    private static final ResourceLocation FAMILY = Rigs.FRUIT_HUMANOID_V1_ID;
    private static final ResourceLocation A = Rigs.MANNEQUIN_A_ID;
    private static final ResourceLocation B = Rigs.MANNEQUIN_B_ID;
    private static final ResourceLocation ZOMBIE = ResourceLocation.withDefaultNamespace("zombie");
    private static final Predicate<ResourceLocation> NO_TAGS = t -> false;

    private RigSelfTest() {}

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) f.add("rig framework: " + what);
    }

    private static boolean throwsAny(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
            return false;
        } catch (RuntimeException e) {
            return type.isInstance(e);
        }
    }

    static void run(List<String> f) {
        CustomRigRegistry g = CustomRigRegistry.GLOBAL;
        RigDefinition rig = g.rig(FAMILY).orElse(null);
        check(f, rig != null && rig.contractVersion() == 1 && rig.bones().size() == 25
                && rig.bones().stream().filter(RigDefinition.Bone::required).count() == 16
                && rig.sockets().size() == 9 && rig.parts().size() == 2 && rig.controller().equals("main"),
                "familia de referencia carregada do manifesto do jar (25 bones, 16 obrigatorios, 9 sockets, 2 partes)");
        if (rig == null) return;
        check(f, g.bindingsOf(FAMILY).size() == 3 && g.binding(A).isPresent() && g.binding(B).isPresent()
                && g.binding(Rigs.STRAWBERRY_ID).isPresent(), "dois manequins e o morango associados a mesma familia");
        check(f, g.binding(ZOMBIE).isEmpty(), "mob vanilla sem binding");
        for (FinisherDef def : Finishers.all()) check(f, Rigs.clipProblems(g, def).isEmpty(), "clips de rig em dia: " + def.id());
        check(f, Rigs.clipProblems(g, Rigs.PEEL_DEMO).isEmpty() && Finishers.get(Rigs.PEEL_DEMO.id()) == null,
                "cena de teste das partes valida e fora do catalogo");

        registry(f, rig);
        resolution(f);
        requirements(f);
        sockets(f, rig);
        parts(f);
        strawberry(f);
    }

    private static void registry(List<String> f, RigDefinition rig) {
        CustomRigRegistry r = new CustomRigRegistry();
        r.register(rig);
        check(f, throwsAny(IllegalStateException.class, () -> r.register(rig)), "familia duplicada recusada");
        RigBinding a = CustomRigRegistry.GLOBAL.binding(A).orElseThrow();
        r.bind(a);
        check(f, throwsAny(IllegalStateException.class, () -> r.bind(a)), "entidade associada duas vezes recusada");
        RigBinding orphan = copy(a, ResourceLocation.withDefaultNamespace("pig"), RespectCraft.id("no_such_rig"), 1, a.extraBones());
        check(f, throwsAny(IllegalStateException.class, () -> r.bind(orphan)), "binding para familia inexistente recusado");
        RigBinding oldVersion = copy(a, ResourceLocation.withDefaultNamespace("cow"), rig.id(), 2, a.extraBones());
        check(f, throwsAny(IllegalArgumentException.class, () -> r.bind(oldVersion)), "binding de outra versao do contrato recusado");
        RigBinding noPrefix = copy(a, ResourceLocation.withDefaultNamespace("sheep"), rig.id(), 1,
                List.of(new RigDefinition.Bone("antenna", "head", Vec3.ZERO, false, false)));
        check(f, throwsAny(IllegalArgumentException.class, () -> r.bind(noPrefix)), "bone extra sem prefixo livre recusado");
        RigBinding badParent = copy(a, ResourceLocation.withDefaultNamespace("goat"), rig.id(), 1,
                List.of(new RigDefinition.Bone("extra_x", "tail", Vec3.ZERO, false, false)));
        check(f, throwsAny(IllegalArgumentException.class, () -> r.bind(badParent)), "bone extra com pai inexistente recusado");

        // Familias invalidas: bone duplicado, pai inexistente, duas raizes, parte que esconde bone obrigatorio.
        var root = new RigDefinition.Bone("root", null, Vec3.ZERO, true, true);
        var body = new RigDefinition.Bone("body", "root", Vec3.ZERO, true, false);
        check(f, throwsAny(IllegalArgumentException.class, () -> tiny(List.of(root, body, body), List.of())), "bone duplicado recusado");
        check(f, throwsAny(IllegalArgumentException.class, () -> tiny(List.of(root,
                new RigDefinition.Bone("arm", "torso", Vec3.ZERO, false, false)), List.of())), "pai inexistente recusado");
        check(f, throwsAny(IllegalArgumentException.class, () -> tiny(List.of(root,
                new RigDefinition.Bone("other", null, Vec3.ZERO, true, false)), List.of())), "duas raizes recusadas");
        check(f, throwsAny(IllegalArgumentException.class, () -> tiny(List.of(root, body),
                List.of(new RigDefinition.Part("all", List.of("body"), true, true)))), "parte que esconde bone obrigatorio recusada");
        RigDefinition serpent = tiny(List.of(root, body, new RigDefinition.Bone("tail", "body", Vec3.ZERO, false, false)), List.of());
        check(f, serpent.bones().size() == 3 && serpent.bone("tail").isPresent(), "anatomia arbitraria (nao humanoide) aceita");
    }

    private static RigDefinition tiny(List<RigDefinition.Bone> bones, List<RigDefinition.Part> parts) {
        return new RigDefinition(RespectCraft.id("selftest_tiny"), 1, 16, 2, "main", "animation.tiny.",
                RespectCraft.id("animations/rig/tiny.animation.json"), Set.of(), List.of("extra_"), bones, List.of(), parts,
                null, Map.of("clip", new RigDefinition.SharedAnimation("clip", 50, false)), Set.of());
    }

    private static RigBinding copy(RigBinding b, ResourceLocation type, ResourceLocation rig, int version, List<RigDefinition.Bone> extra) {
        return new RigBinding(type, rig, version, b.geo(), b.texture(), b.animationFile(), b.proportion(), b.renderScale(), extra,
                b.capabilities(), b.parts(), b.sockets(), b.alignment(), List.of());
    }

    private static FinisherParticipants.Resolution res(FinisherDef def, FinisherRole role, ResourceLocation type) {
        Kind kind = type == null ? Kind.PLAYER : Kind.MOB;
        return FinisherParticipants.resolve(CustomRigRegistry.GLOBAL, def, role, kind, type, true, NO_TAGS);
    }

    private static String anim(FinisherParticipants.Resolution r) {
        return r.clip().orElse(null) instanceof FinisherParticipants.EntityClip e ? e.clip().animation() : null;
    }

    private static void resolution(List<String> f) {
        FinisherDef takedown = Finishers.TAKEDOWN;
        var b = res(takedown, FinisherRole.VICTIM, B);
        check(f, b.valid() && b.source() == Source.FAMILY && "animation.fruit_humanoid_v1.finisher.takedown_victim".equals(anim(b))
                && ((FinisherParticipants.EntityClip) b.clip().get()).clip().controller().equals("main"),
                "B: Takedown resolve pela familia (animacao compartilhada, controlador do contrato)");
        var a = res(takedown, FinisherRole.VICTIM, A);
        check(f, a.valid() && a.source() == Source.BINDING && "animation.dev_rig_mannequin_a.takedown_victim".equals(anim(a)),
                "A: override do binding vence a familia");
        var explicit = takedown.withId(RespectCraft.id("selftest/explicit")).withEntityClip(
                new EntityFinisherClip(A, FinisherRole.VICTIM, "main", "selftest_only", takedown.durationTicks()));
        var e = res(explicit, FinisherRole.VICTIM, A);
        check(f, e.source() == Source.ENTITY && "selftest_only".equals(anim(e)), "clip explicito da finalizacao vence o binding");
        check(f, res(takedown, FinisherRole.ATTACKER, B).source() == Source.HOLD, "familia sem clip no papel: parado (fallback de antes)");
        check(f, res(takedown, FinisherRole.VICTIM, ZOMBIE).source() == Source.HOLD
                && res(takedown, FinisherRole.VICTIM, null).source() == Source.PLAYER, "zumbi parado e jogador com o clip de sempre");
        for (FinisherDef def : Finishers.all()) {
            check(f, res(def, FinisherRole.VICTIM, ZOMBIE).valid() && res(def, FinisherRole.ATTACKER, null).valid(),
                    "retrocompatibilidade: " + def.id() + " continua valido para zumbi e jogador");
        }
        var noGeo = FinisherParticipants.resolve(CustomRigRegistry.GLOBAL, takedown, FinisherRole.VICTIM, Kind.MOB, B, false, NO_TAGS);
        check(f, !noGeo.valid(), "clip de familia sem GeoEntity recusado");

        // Compartilhamento real: A e B tocam a MESMA animacao da familia na cena de teste; alinhamentos diferentes.
        var pa = res(Rigs.PEEL_DEMO, FinisherRole.VICTIM, A);
        var pb = res(Rigs.PEEL_DEMO, FinisherRole.VICTIM, B);
        check(f, pa.source() == Source.FAMILY && pb.source() == Source.FAMILY && anim(pa) != null && anim(pa).equals(anim(pb)),
                "cena de teste: os dois modelos compartilham a mesma animacao");
        var player = res(Rigs.PEEL_DEMO, FinisherRole.ATTACKER, null);
        var alignB = FinisherParticipants.alignment(player.clip().get(), null, pb.clip().get(), B);
        var alignA = FinisherParticipants.alignment(player.clip().get(), null, pa.clip().get(), A);
        check(f, alignB.distance() == 1.6 && alignA.autoDistance(), "override de alinhamento do binding (B a 1,6; A automatico)");
        var holdB = FinisherParticipants.alignment(player.clip().get(), null, new FinisherParticipants.Hold(60), B);
        check(f, holdB.distance() == 1.6, "entidade de rig parada tambem usa o palco do binding");
        check(f, !res(Rigs.PEEL_DEMO, FinisherRole.VICTIM, ZOMBIE).valid(), "cena de teste recusa mob sem rig (antes de comecar)");

        // Duracao: clip de familia com outra duracao recusa a cena.
        var shortScene = new FinisherDef(RespectCraft.id("selftest/short"), Finishers.RASTEIRA.attacker(), Finishers.RASTEIRA.victim(),
                Finishers.RASTEIRA.camera(), null, 1).withRigClip(new FinisherRigs.Clip(FAMILY, FinisherRole.VICTIM, "finisher.demo_peel"));
        check(f, !res(shortScene, FinisherRole.VICTIM, B).valid() && !Rigs.clipProblems(CustomRigRegistry.GLOBAL, shortScene).isEmpty(),
                "clip de familia com duracao diferente da cena recusado");
        var missing = takedown.withId(RespectCraft.id("selftest/missing")).withRigClip(
                new FinisherRigs.Clip(RespectCraft.id("other_family"), FinisherRole.VICTIM, "x"));
        check(f, res(missing, FinisherRole.VICTIM, B).valid(), "clip de outra familia nao vale para esta");

        var bind = CustomRigRegistry.GLOBAL.binding(A).orElseThrow();
        Set<String> trig = CustomRigRegistry.GLOBAL.triggerables(bind);
        check(f, trig.contains("animation.dev_rig_mannequin_a.takedown_victim") && trig.contains("animation.fruit_humanoid_v1.finisher.demo_peel")
                && !trig.contains("animation.fruit_humanoid_v1.idle"), "gatilhos: compartilhadas (sem lacos) + overrides");
        check(f, CustomRigRegistry.GLOBAL.animationFile(bind, "animation.dev_rig_mannequin_a.takedown_victim").equals(bind.animationFile())
                && CustomRigRegistry.GLOBAL.animationFile(bind, "animation.fruit_humanoid_v1.finisher.demo_peel")
                .equals(Rigs.FRUIT_HUMANOID_V1.animationFile()), "arquivo de cada animacao: proprio ou da familia");
    }

    private static void requirements(List<String> f) {
        FinisherDef core = Finishers.TAKEDOWN.withId(RespectCraft.id("selftest/core")).withRigRequirement(
                FinisherRigs.Requirement.rig(FinisherRole.VICTIM, FAMILY, Set.of(RigCapabilities.INNER_CORE)));
        check(f, !res(core, FinisherRole.VICTIM, A).valid() && res(core, FinisherRole.VICTIM, B).valid(),
                "capacidade exigida: A (sem nucleo) recusado, B aceito");
        check(f, !res(core, FinisherRole.VICTIM, null).valid(), "requisito de rig recusa jogador vitima");
        check(f, res(core, FinisherRole.ATTACKER, null).valid(), "requisito vale so no proprio papel");
        FinisherDef animated = new FinisherDef(RespectCraft.id("selftest/animated"), Finishers.RASTEIRA.attacker(),
                Finishers.RASTEIRA.victim(), Finishers.RASTEIRA.camera(), null, 1)
                .withRigRequirement(FinisherRigs.Requirement.rig(FinisherRole.VICTIM, FAMILY, Set.of()));
        var hold = res(animated, FinisherRole.VICTIM, B);
        check(f, !hold.valid() && hold.problem().contains("hold still"), "animacao obrigatoria ausente: nunca inicia parado em silencio");
        var typed = new FinisherRigs.Requirement(FinisherRole.VICTIM, Set.of(B), Set.of(RespectCraft.id("tag_x")), null, Set.of(), false);
        check(f, typed.check(A, NO_TAGS, FAMILY, Set.of(), true).isPresent()
                && typed.check(B, NO_TAGS, FAMILY, Set.of(), true).isPresent()
                && typed.check(B, t -> t.equals(RespectCraft.id("tag_x")), FAMILY, Set.of(), true).isEmpty(),
                "requisitos por tipo de entidade e por tag");

        // Elegibilidade unica (a do sorteio e do futuro seletor): regra de chefes intacta com rigs.
        var playerP = new FinisherEligibility.Participant(Kind.PLAYER, FinisherRigs.Requirement.PLAYER_TYPE, false, NO_TAGS);
        var bP = new FinisherEligibility.Participant(Kind.MOB, B, true, NO_TAGS);
        var bossB = new FinisherEligibility.Participant(Kind.BOSS, B, true, NO_TAGS);
        check(f, FinisherEligibility.evaluate(CustomRigRegistry.GLOBAL, Rigs.PEEL_DEMO, playerP, bP).eligible(),
                "elegibilidade: jogador x manequim B na cena de teste");
        var asBoss = FinisherEligibility.evaluate(CustomRigRegistry.GLOBAL, Rigs.PEEL_DEMO, playerP, bossB);
        check(f, !asBoss.eligible() && asBoss.targeting() == FinisherTargeting.Verdict.BOSS_REQUIRES_EXCLUSIVE,
                "rig nao e classificacao: mesma rig como chefe continua exigindo exclusiva");
        check(f, !FinisherEligibility.evaluate(CustomRigRegistry.GLOBAL, Rigs.PEEL_DEMO, bP, bP).eligible(), "mob x mob continua recusado");
        var picked = Finishers.pickFor(List.of(core, Finishers.TAKEDOWN), Kind.MOB, A, s -> true,
                com.respectcraft.profile.BirthRoulette.newRandom(),
                def -> FinisherEligibility.evaluate(CustomRigRegistry.GLOBAL, def, playerP,
                        new FinisherEligibility.Participant(Kind.MOB, A, true, NO_TAGS)).eligible());
        check(f, picked.isPresent() && picked.get() == Finishers.TAKEDOWN, "sorteio nunca escolhe cena que o rig nao suporta");
    }

    private static void sockets(List<String> f, RigDefinition rig) {
        var reg = CustomRigRegistry.GLOBAL;
        var a = reg.binding(A).orElseThrow();
        var b = reg.binding(B).orElseThrow();
        Vec3 la = RigSockets.local(reg, a, "impact_point").orElseThrow();
        Vec3 lb = RigSockets.local(reg, b, "impact_point").orElseThrow();
        check(f, near(la, new Vec3(0, 18, -2.55)), "socket da familia com a proporcao do modelo (A) " + la);
        check(f, near(lb, new Vec3(0, 13 * 0.8 + 4, -4.5)), "socket com override do binding (B) " + lb);
        check(f, reg.socket(a, "no_such").isEmpty() && RigSockets.local(reg, a, "ground_contact").map(v -> near(v, Vec3.ZERO)).orElse(false),
                "socket inexistente vazio; ground_contact na origem");
        Vec3 w0 = RigSockets.toWorld(Vec3.ZERO, 0F, 1F, new Vec3(16, 16, -16));
        check(f, near(w0, new Vec3(-1, 1, 1)), "local→mundo: yaw 0 = frente +Z, direita -X " + w0);
        Vec3 w90 = RigSockets.toWorld(Vec3.ZERO, 90F, 2F, new Vec3(0, 0, -8));
        check(f, near(w90, new Vec3(-1, 0, 0)), "local→mundo: yaw 90 e escala de desenho " + w90);
    }

    private static boolean near(Vec3 a, Vec3 b) { return a.distanceTo(b) < 1e-6; }

    private static void parts(List<String> f) {
        check(f, RigVisuals.parse("rig:hide:shell").map(p -> p.part().equals("shell") && !p.visible()).orElse(false)
                && RigVisuals.parse(" rig:show:core ").map(p -> p.visible()).orElse(false)
                && RigVisuals.parse("rig:reset").map(RigVisuals.PartInstruction::reset).orElse(false)
                && RigVisuals.parse("rig:explode:shell").isEmpty() && RigVisuals.parse("rig:hide:").isEmpty()
                && RigVisuals.parse("calamidade:x").isEmpty() && RigVisuals.parse("rig:hide:Bad Part").isEmpty(),
                "instrucoes rig: hide/show/reset");
        var shell = new RigDefinition.Part("shell", List.of("torso_shell"), true, true);
        var core = new RigDefinition.Part("core", List.of("torso_core"), false, false);
        check(f, RigVisuals.visible(shell, Map.of()) && !RigVisuals.visible(core, Map.of())
                && !RigVisuals.visible(shell, Map.of("shell", false)) && RigVisuals.visible(core, Map.of("core", true)),
                "visibilidade: padrao do contrato e estado temporario");
        var b = CustomRigRegistry.GLOBAL.binding(B).orElseThrow();
        check(f, CustomRigRegistry.GLOBAL.parts(b).stream().map(RigDefinition.Part::name).toList().equals(List.of("shell", "core", "leaf"))
                && CustomRigRegistry.GLOBAL.capabilities(b).containsAll(Set.of(RigCapabilities.HANDS, RigCapabilities.DETACHABLE_PARTS)),
                "partes e capacidades efetivas = familia + binding");
        var events = RigVisuals.partEvents(Rigs.FRUIT_HUMANOID_V1.animationFile(), "animation.fruit_humanoid_v1.finisher.demo_peel");
        check(f, events.size() == 2 && events.stream().allMatch(i -> i.tick() == 22)
                && events.stream().map(GeoFxKeyframes.Instruction::text).toList().containsAll(List.of("rig:hide:shell", "rig:show:core")),
                "eventos de parte lidos do timeline da animacao compartilhada (tick 22)");
        // Finisher Scene Authoring Kit: clip da vitima separado do projeto conjunto (arquivo proprio do manequim B)
        var authored = CustomRigRegistry.GLOBAL.override(b, Rigs.AUTHORING_DEMO.id(), com.respectcraft.finisher.FinisherRole.VICTIM);
        var authoredEvents = authored.map(o -> RigVisuals.partEvents(CustomRigRegistry.GLOBAL.animationFile(b, o.animation()),
                o.animation())).orElse(List.of());
        check(f, authored.map(o -> o.ticks() == Rigs.AUTHORING_DEMO.durationTicks()).orElse(false)
                && Rigs.AUTHORING_DEMO.attacker().durationTicks() == Rigs.AUTHORING_DEMO.durationTicks()
                && CustomRigRegistry.GLOBAL.triggerables(b).contains(authored.get().animation())
                && authoredEvents.size() == 2 && authoredEvents.stream().allMatch(i -> i.tick() == 22),
                "cena do kit de autoria: override do manequim B com a duracao da cena e partes no tick 22");
    }

    // ---- Etapa 4.13.1: morango (assets externos, binding oficial do artista) ----

    /** Aplica instrucoes {@code rig:} a um estado (mesma semantica de {@link RigVisuals#set}/{@code reset}). */
    private static Map<String, Boolean> play(List<RigDefinition.Part> parts, Map<String, Boolean> start, String timeline) {
        Map<String, Boolean> state = new java.util.HashMap<>(start);
        for (String raw : timeline.split(";")) {
            if (raw.isBlank()) continue;
            var ins = RigVisuals.parse(raw).orElseThrow(() -> new IllegalArgumentException("bad instruction " + raw));
            if (ins.reset()) { state.clear(); continue; }
            var part = parts.stream().filter(p -> p.name().equals(ins.part())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown part " + ins.part()));
            if (ins.visible() == part.defaultVisible()) state.remove(part.name()); else state.put(part.name(), ins.visible());
        }
        return state;
    }

    private static boolean shown(List<RigDefinition.Part> parts, Map<String, Boolean> state, String name) {
        return parts.stream().filter(p -> p.name().equals(name)).findFirst().map(p -> RigVisuals.visible(p, state)).orElse(false);
    }

    /** Rosto coerente: rosto normal XOR variantes; no maximo um olho, uma boca; variante sempre com olho+sobrancelha+boca. */
    private static boolean faceCoherent(List<RigDefinition.Part> parts, Map<String, Boolean> s) {
        boolean normal = shown(parts, s, "face_normal");
        int eyes = (shown(parts, s, "eyes_wide") ? 1 : 0) + (shown(parts, s, "eyes_closed") ? 1 : 0);
        int mouths = (shown(parts, s, "mouth_open") ? 1 : 0) + (shown(parts, s, "mouth_scream") ? 1 : 0);
        boolean brows = shown(parts, s, "brows_panic");
        if (normal) return eyes == 0 && mouths == 0 && !brows;
        return eyes == 1 && mouths == 1 && brows;
    }

    /** Estados de expressao do {@code strawberry_visual_states.md} (convencao das cenas; nao e campo do schema). */
    static final Map<String, String> STRAWBERRY_EXPRESSIONS = Map.of(
            "shock", "rig:hide:face_normal;rig:show:eyes_wide;rig:show:brows_panic;rig:show:mouth_open;",
            "scream", "rig:hide:face_normal;rig:show:eyes_wide;rig:show:brows_panic;rig:show:mouth_scream;",
            "squeezed", "rig:hide:face_normal;rig:show:eyes_closed;rig:show:brows_panic;rig:show:mouth_scream;");

    private static void strawberry(List<String> f) {
        var reg = CustomRigRegistry.GLOBAL;
        var bind = reg.binding(Rigs.STRAWBERRY_ID).orElse(null);
        check(f, bind != null, "morango associado a familia");
        if (bind == null) return;
        var problems = CustomRigRegistry.bindingProblems(Rigs.FRUIT_HUMANOID_V1, bind);
        check(f, problems.isEmpty(), "binding oficial do morango valido: " + problems);
        var parts = reg.parts(bind);
        Map<String, Boolean> none = Map.of();
        List<String> visible = List.of("shell", "face_normal", "leaves", "stem", "top", "earrings");
        List<String> hidden = List.of("core", "eyes_wide", "eyes_closed", "brows_panic", "mouth_open", "mouth_scream",
                "damage_head", "damage_torso");
        check(f, parts.size() == visible.size() + hidden.size() && visible.stream().allMatch(n -> shown(parts, none, n))
                && hidden.stream().noneMatch(n -> shown(parts, none, n)), "morango: visibilidade padrao do binding");
        check(f, reg.capabilities(bind).containsAll(Set.of(RigCapabilities.HANDS, RigCapabilities.INNER_CORE, RigCapabilities.DETACHABLE_PARTS)),
                "morango: capacidades da familia + binding");
        check(f, faceCoherent(parts, none), "morango: rosto padrao coerente");
        for (var e : STRAWBERRY_EXPRESSIONS.entrySet()) {
            var s = play(parts, none, e.getValue());
            check(f, faceCoherent(parts, s) && shown(parts, s, "shell"), "morango: expressao " + e.getKey() + " sem sobreposicao");
        }
        // Sobreposicao detectada: variante sem esconder o rosto normal, ou dois olhos/duas bocas ao mesmo tempo.
        check(f, !faceCoherent(parts, play(parts, none, "rig:show:eyes_wide;rig:show:brows_panic;rig:show:mouth_open;"))
                && !faceCoherent(parts, play(parts, none, STRAWBERRY_EXPRESSIONS.get("shock") + "rig:show:eyes_closed;"))
                && !faceCoherent(parts, play(parts, none, STRAWBERRY_EXPRESSIONS.get("shock") + "rig:show:mouth_scream;")),
                "morango: sobreposicoes de olhos/bocas/rosto detectadas");
        // Cena de destruicao: casca fora + panico; voltar ao rosto normal com rig:reset devolveria a casca (por isso
        // a troca de expressao no meio da cena e explicita, por hide/show).
        var peeled = play(parts, none, "rig:hide:shell;rig:show:core;rig:show:damage_torso;" + STRAWBERRY_EXPRESSIONS.get("scream"));
        check(f, faceCoherent(parts, peeled) && !shown(parts, peeled, "shell") && shown(parts, peeled, "core")
                && shown(parts, peeled, "damage_torso"), "morango: descascado + rachado + grito");
        var viaReset = play(parts, peeled, "rig:reset;");
        var explicit = play(parts, peeled, "rig:hide:eyes_wide;rig:hide:brows_panic;rig:hide:mouth_scream;rig:show:face_normal;");
        check(f, shown(parts, viaReset, "shell") && !shown(parts, explicit, "shell") && shown(parts, explicit, "core")
                && faceCoherent(parts, explicit), "morango: rig:reset restaura TUDO; troca explicita preserva a destruicao");
        var bare = play(parts, none, "rig:hide:leaves;rig:hide:stem;rig:hide:top;rig:hide:earrings;");
        check(f, !shown(parts, bare, "leaves") && !shown(parts, bare, "stem") && play(parts, bare, "rig:reset;").isEmpty(),
                "morango: folhas/caule/acessorios ocultaveis e restaurados");
        // Finalizacoes: vitima pela familia (Takedown e cena de teste), sem override proprio; nunca executor.
        var t = res(Finishers.TAKEDOWN, FinisherRole.VICTIM, Rigs.STRAWBERRY_ID);
        check(f, t.valid() && t.source() == Source.FAMILY && "animation.fruit_humanoid_v1.finisher.takedown_victim".equals(anim(t)),
                "morango: Takedown pela animacao compartilhada da vitima");
        var d = res(Rigs.PEEL_DEMO, FinisherRole.VICTIM, Rigs.STRAWBERRY_ID);
        check(f, d.valid() && d.source() == Source.FAMILY, "morango: cena de teste demo_peel pela familia");
        var playerP = new FinisherEligibility.Participant(Kind.PLAYER, FinisherRigs.Requirement.PLAYER_TYPE, false, NO_TAGS);
        var sP = new FinisherEligibility.Participant(Kind.MOB, Rigs.STRAWBERRY_ID, true, NO_TAGS);
        check(f, FinisherEligibility.evaluate(reg, Finishers.TAKEDOWN, playerP, sP).eligible()
                && FinisherEligibility.evaluate(reg, Rigs.PEEL_DEMO, playerP, sP).eligible(),
                "morango: vitima elegivel (Takedown e cena de teste)");
        // Sockets sem override: pivo do contrato + deslocamento (proporcao 1).
        check(f, bind.sockets().isEmpty() && RigSockets.local(reg, bind, "impact_point").map(v -> near(v, new Vec3(0, 18, -3))).orElse(false),
                "morango: sockets do contrato sem override");
    }
}
