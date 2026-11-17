package com.tupinicraft.selftest;

import com.tupinicraft.animation.PlayerAnimationDef;
import com.tupinicraft.animation.PlayerAnimations;
import com.tupinicraft.finisher.CameraTrack;
import com.tupinicraft.finisher.FinisherDef;
import com.tupinicraft.finisher.Finishers;
import com.tupinicraft.needs.Needs;
import com.tupinicraft.needs.NeedsRules;
import com.tupinicraft.profile.BirthOrigin;
import com.tupinicraft.profile.BirthRoulette;
import com.tupinicraft.profile.CharacterAttribute;
import com.tupinicraft.profile.CharacterProfile;
import com.tupinicraft.skill.SkillDef;
import com.tupinicraft.skill.Skills;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * Autoteste das regras puras (roleta, necessidades, habilidades, trilhas de camera, Codecs), rodado pelo comando
 * {@code /tupini selftest} no servidor dedicado do CI. Substitui um runner de testes separado.
 */
public final class SelfTest {
    private SelfTest() {}

    public static List<String> run() {
        List<String> f = new ArrayList<>();
        try {
            roulette(f);
            needs(f);
            coomAndEffects(f);
            skills(f);
            finishers(f);
            codecs(f);
        } catch (RuntimeException e) {
            f.add("exceção: " + e);
        }
        return f;
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) f.add(what);
    }

    private static void roulette(List<String> f) {
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        Map<BirthOrigin, Integer> counts = new EnumMap<>(BirthOrigin.class);
        for (int i = 0; i < 20_000; i++) {
            BirthRoulette.Result r = BirthRoulette.roll(random);
            counts.merge(r.origin(), 1, Integer::sum);
            int sum = 0;
            for (int v : r.attributes()) {
                if (v < CharacterAttribute.MIN || v > CharacterAttribute.MAX) {
                    f.add("roleta: atributo fora da faixa " + v);
                    return;
                }
                sum += v;
            }
            int bonus = 0;
            for (CharacterAttribute a : CharacterAttribute.values()) bonus += r.origin().bonus(a);
            int expected = CharacterAttribute.BASE * 6 + BirthRoulette.EXTRA_POINTS + bonus;
            if (Math.abs(sum - expected) > 3) { // so o clamp pode tirar pontos
                f.add("roleta: soma inesperada " + sum + " (esperado ~" + expected + ")");
                return;
            }
        }
        int total = BirthOrigin.totalWeight();
        for (BirthOrigin origin : BirthOrigin.values()) {
            double expected = 20_000.0 * origin.weight() / total;
            int got = counts.getOrDefault(origin, 0);
            check(f, Math.abs(got - expected) < expected * 0.25 + 30, "roleta: frequência de " + origin.id() + " = " + got + " (esperado ~" + (int) expected + ")");
        }
        check(f, BirthRoulette.startingSkillPoints(3) == 2 && BirthRoulette.startingSkillPoints(9) == 5, "roleta: pontos iniciais");
    }

    private static void needs(List<String> f) {
        Needs n = Needs.DEFAULT;
        NeedsRules.Context neutral = NeedsRules.Context.neutral();
        for (int i = 0; i < 60; i++) n = NeedsRules.tickSecond(n, neutral);
        check(f, n.hydration() == Needs.MAX - 60 * NeedsRules.HYDRATION_PER_SECOND, "necessidades: hidratação em 60 s = " + n.hydration());
        check(f, n.bladder() == 60 * NeedsRules.BLADDER_PER_SECOND, "necessidades: bexiga em 60 s = " + n.bladder());
        Needs sleeping = NeedsRules.tickSecond(new Needs(5_000, 1_000, 0, 0, 5_000),
                new NeedsRules.Context(false, true, false, true, false, 3, 3, 1, 1, 1, 1, 0));
        check(f, sleeping.energy() > 1_000, "necessidades: dormir não recuperou energia");
        Needs sprint = NeedsRules.tickSecond(Needs.DEFAULT, new NeedsRules.Context(true, false, true, false, false, 3, 3, 1, 1, 1, 1, 0));
        check(f, Needs.MAX - sprint.hydration() == 3 * NeedsRules.HYDRATION_PER_SECOND, "necessidades: correr no calor");
        Needs skilled = NeedsRules.tickSecond(Needs.DEFAULT, new NeedsRules.Context(false, false, false, false, false, 3, 3, 0.5F, 1, 1, 1, 0));
        check(f, Needs.MAX - skilled.hydration() == NeedsRules.HYDRATION_PER_SECOND / 2, "necessidades: multiplicador de habilidade");
        Needs sad = new Needs(0, 0, 9_000, 9_000, 6_000);
        check(f, NeedsRules.moodTarget(sad, neutral) == 0, "necessidades: alvo do humor com tudo ruim");
        check(f, NeedsRules.tickSecond(sad, neutral).mood() < 6_000, "necessidades: humor deveria cair");
        check(f, NeedsRules.accident(Needs.MAX) && !NeedsRules.accident(Needs.MAX - 1), "necessidades: acidente");
        check(f, new Needs(-5, 99_999, 0, 0, 0).hydration() == 0 && new Needs(0, 99_999, 0, 0, 0).energy() == Needs.MAX, "necessidades: clamp");
    }

    /** Etapa 3.1: medidor Coom, efeitos de consumiveis e regra do unico novo giro. */
    private static void coomAndEffects(List<String> f) {
        NeedsRules.Context neutral = NeedsRules.Context.neutral();
        Needs n = Needs.DEFAULT;
        for (int i = 0; i < 60; i++) n = NeedsRules.tickSecond(n, neutral);
        check(f, n.coom() == 60 * NeedsRules.COOM_PER_SECOND, "coom: acúmulo em 60 s = " + n.coom());
        Needs off = NeedsRules.tickSecond(Needs.DEFAULT, new NeedsRules.Context(false, false, false, false, false, 3, 3, 1, 1, 1, 1, 0, 0F));
        check(f, off.coom() == 0, "coom: taxa 0 desliga");
        check(f, NeedsRules.coomAttributeMultiplier(8, 3) > 1F && NeedsRules.coomAttributeMultiplier(3, 8) < 1F, "coom: atributos");
        check(f, NeedsRules.moodTarget(Needs.DEFAULT.with(com.tupinicraft.needs.Need.COOM, 9_000), neutral)
                == NeedsRules.moodTarget(Needs.DEFAULT, neutral) - NeedsRules.COOM_MOOD_PENALTY, "coom: humor");
        // Save da Etapa 3 (sem o campo coom) carrega com coom 0.
        CompoundTag old = new CompoundTag();
        old.putInt("hidratacao", 5_000);
        Needs loaded = Needs.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        check(f, loaded.coom() == 0 && loaded.hydration() == 5_000 && loaded.mood() == 6_000, "coom: save antigo");

        for (com.tupinicraft.needs.NeedEffectDef def : com.tupinicraft.needs.NeedEffects.all()) {
            if (def.durationSeconds() > 0 && !def.gradual().isZero()) {
                com.tupinicraft.needs.NeedsDelta sum = com.tupinicraft.needs.NeedsDelta.NONE;
                for (int s = -2; s < def.durationSeconds() + 5; s++) sum = sum.plus(com.tupinicraft.needs.NeedEffectTimeline.gradualStep(def, s));
                check(f, sum.equals(def.gradual()), "efeitos: gradual não fecha o total " + def.id());
            }
            int fired = 0;
            for (int s = 0; s < def.lifetimeSeconds() + 5; s++) if (com.tupinicraft.needs.NeedEffectTimeline.delayedFires(def, s)) fired++;
            check(f, fired == (def.delaySeconds() > 0 ? 1 : 0), "efeitos: estágio atrasado " + def.id());
            check(f, com.tupinicraft.needs.NeedEffectTimeline.expired(def, def.lifetimeSeconds()), "efeitos: não expira " + def.id());
        }
        var lax = com.tupinicraft.needs.NeedEffects.LAXANTE_INDUSTRIAL;
        check(f, lax.immediate().isZero() && lax.delayed().bowel() > 0 && lax.delaySeconds() > 0, "efeitos: laxante atrasado");
        var calm = com.tupinicraft.needs.NeedEffects.CHA_CALMANTE;
        check(f, calm.immediate().coom() < 0 && calm.rate(com.tupinicraft.needs.Need.COOM) < 1F, "efeitos: chá calmante");
        check(f, com.tupinicraft.needs.NeedEffectTimeline.combinedRate(List.of(com.tupinicraft.needs.NeedEffects.AFRODISIACO_MISTERIOSO),
                List.of(0), com.tupinicraft.needs.Need.COOM) == 3F, "efeitos: ritmo do afrodisíaco");
        check(f, com.tupinicraft.needs.NeedEffectTimeline.combinedRate(List.of(com.tupinicraft.needs.NeedEffects.AFRODISIACO_MISTERIOSO),
                List.of(500), com.tupinicraft.needs.Need.COOM) == 1F, "efeitos: ritmo expira");
        var active = com.tupinicraft.needs.ActiveNeedEffects.EMPTY.start("cha_calmante").start("cha_calmante");
        check(f, active.entries().size() == 1, "efeitos: repetir reinicia em vez de acumular");
        Tag at = com.tupinicraft.needs.ActiveNeedEffects.CODEC.encodeStart(NbtOps.INSTANCE, active).getOrThrow();
        check(f, com.tupinicraft.needs.ActiveNeedEffects.CODEC.parse(NbtOps.INSTANCE, at).getOrThrow().equals(active), "efeitos: codec");

        // Roleta: um unico novo giro, so depois do nascimento; fichas antigas com mais giros sao corrigidas.
        CharacterProfile born = CharacterProfile.DEFAULT.withBirth(BirthRoulette.roll(RandomGenerator.of("L64X128MixRandom")), BirthRoulette.FREE_REROLLS);
        check(f, born.canReroll() && born.rerolls() == 1, "roleta: um novo giro após nascer");
        CharacterProfile afterReroll = born.withBirth(BirthRoulette.roll(RandomGenerator.of("L64X128MixRandom")), born.rerolls() - 1);
        check(f, !afterReroll.canReroll(), "roleta: segundo novo giro bloqueado");
        check(f, new CharacterProfile(true, "sertao", List.of(), 5, 1, 0, 0, List.of()).rerolls() == 1, "roleta: ficha antiga com 5 giros");
        check(f, !CharacterProfile.DEFAULT.canReroll(), "roleta: sem nascimento não há novo giro");
    }

    private static void skills(List<String> f) {
        for (String problem : Skills.validate()) f.add("habilidades: " + problem);
        check(f, Skills.all().size() >= 18, "habilidades: catálogo incompleto");
        SkillDef mito = Skills.MITO;
        check(f, "requisito".equals(Skills.purchaseBlocker(mito, Set.of(Skills.PUNHOS.id().toString()), 99, true)), "habilidades: pai obrigatório");
        check(f, "nascimento".equals(Skills.purchaseBlocker(Skills.PUNHOS, Set.of(), 99, false)), "habilidades: exige nascimento");
        check(f, "pontos".equals(Skills.purchaseBlocker(Skills.PUNHOS, Set.of(), 0, true)), "habilidades: pontos");
        check(f, Skills.purchaseBlocker(Skills.PUNHOS, Set.of(), 1, true) == null, "habilidades: raiz comprável");
        check(f, "comprada".equals(Skills.purchaseBlocker(Skills.PUNHOS, Set.of(Skills.PUNHOS.id().toString()), 5, true)), "habilidades: repetida");
    }

    private static void finishers(List<String> f) {
        check(f, Finishers.all().size() >= 2, "finalizações: catálogo");
        for (FinisherDef def : Finishers.all()) {
            check(f, PlayerAnimations.get(def.attacker().id()) == def.attacker(), "finalizações: animação do executor não registrada " + def.id());
            check(f, PlayerAnimations.get(def.victim().id()) == def.victim(), "finalizações: animação da vítima não registrada " + def.id());
            check(f, def.attacker().blend() == PlayerAnimationDef.Blend.FULL_BODY, "finalizações: executor deve ser FULL_BODY");
            check(f, Finishers.byAnimation(def.victim().id()) == def, "finalizações: byAnimation");
            check(f, def.attacker().events().stream().anyMatch(e -> e.type().equals(Finishers.EVENT_HIT)), "finalizações: sem impacto " + def.id());
            CameraTrack.Sample start = def.camera().sample(-5);
            CameraTrack.Sample end = def.camera().sample(10_000);
            check(f, start.distance() > 1 && end.distance() > 1, "finalizações: câmera perto demais " + def.id());
        }
        CameraTrack track = CameraTrack.of(new CameraTrack.Key(0, 0, 2, 1, 1, 1, 0), new CameraTrack.Key(10, 90, 4, 1, 1, 1, 0));
        CameraTrack.Sample mid = track.sample(5);
        check(f, Math.abs(mid.orbit() - 45) < 1e-6 && Math.abs(mid.distance() - 3) < 1e-6, "câmera: interpolação no meio");
        double[] o = CameraTrack.offset(track.sample(0), 0); // executor olhando para +Z: orbit 0 = a frente (+Z)
        check(f, Math.abs(o[0]) < 1e-6 && Math.abs(o[2] - 2) < 1e-6, "câmera: offset frontal");
        double[] side = CameraTrack.offset(track.sample(10), 0);
        check(f, Math.abs(side[0] + 4) < 1e-6, "câmera: offset lateral");
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        for (int i = 0; i < 32; i++) {
            check(f, Finishers.pick(s -> false, random).requiresSkill() == null, "finalizações: sem habilidade só as livres");
        }
        // Coreografia da Etapa 4: dois clips do mesmo tamanho, impactos no executor, queda e recuperação na vítima.
        FinisherDef combo = Finishers.COMBO;
        check(f, combo.attacker().durationTicks() == combo.victim().durationTicks(), "combo: clips com a mesma duração");
        check(f, combo.recoveryInScene() && combo.victim().blend() == PlayerAnimationDef.Blend.FULL_BODY, "combo: vítima levanta no clip");
        check(f, combo.attacker().events().stream().filter(e -> e.type().equals(Finishers.EVENT_HIT)).count() == 2, "combo: dois impactos");
        check(f, combo.victim().events().stream().anyMatch(e -> e.type().equals(Finishers.EVENT_FALL)), "combo: evento de queda");
        for (PlayerAnimationDef def : List.of(PlayerAnimations.COMBAT_IDLE, PlayerAnimations.PUNCH, PlayerAnimations.KICK,
                PlayerAnimations.HIT_REACTION, PlayerAnimations.STAGGER, PlayerAnimations.KNOCKDOWN, PlayerAnimations.KNEEL,
                PlayerAnimations.HANDS_AND_KNEES, PlayerAnimations.CRAWL, PlayerAnimations.GET_UP)) {
            check(f, PlayerAnimations.get(def.id()) == def && def.animationFile().equals(PlayerAnimations.COMBAT_FILE),
                    "biblioteca: animação não registrada " + def.id());
        }
    }

    private static void codecs(List<String> f) {
        CharacterProfile p = CharacterProfile.DEFAULT.withBirth(new BirthRoulette.Result(BirthOrigin.SERTAO,
                new int[] {5, 4, 9, 3, 6, 2}), 1).withSkills(List.of(Skills.PUNHOS.id().toString()), 3);
        Tag tag = CharacterProfile.CODEC.encodeStart(NbtOps.INSTANCE, p).getOrThrow();
        CharacterProfile back = CharacterProfile.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        check(f, back.equals(p), "codec: ficha ida e volta");
        CharacterProfile empty = CharacterProfile.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        check(f, empty.equals(CharacterProfile.DEFAULT), "codec: ficha vazia = padrão");
        Needs n = new Needs(1, 2, 3, 4, 5);
        check(f, Needs.CODEC.parse(NbtOps.INSTANCE, Needs.CODEC.encodeStart(NbtOps.INSTANCE, n).getOrThrow()).getOrThrow().equals(n), "codec: necessidades");
        CharacterProfile broken = new CharacterProfile(true, "x", List.of(99, -4), -1, 0, -3, -2, List.of("a", "a"));
        check(f, broken.attributes().size() == 6 && broken.attribute(CharacterAttribute.FORCA) == 10
                && broken.skills().size() == 1 && broken.skillPoints() == 0, "ficha: normalização de dados inválidos");
    }
}
