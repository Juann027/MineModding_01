package com.tupinicraft.selftest;

import com.tupinicraft.animation.AnimationSequence;
import com.tupinicraft.animation.PlayerAnimationCommands;
import com.tupinicraft.animation.PlayerAnimationDef;
import com.tupinicraft.animation.PlayerAnimationState;
import com.tupinicraft.animation.PlayerAnimations;
import com.tupinicraft.finisher.CameraTrack;
import com.tupinicraft.finisher.FinisherDef;
import com.tupinicraft.finisher.Finishers;
import com.tupinicraft.needs.Needs;
import com.tupinicraft.needs.NeedsRules;
import com.tupinicraft.profile.BirthOrigin;
import com.tupinicraft.profile.BirthRoulette;
import com.tupinicraft.profile.BirthTrait;
import com.tupinicraft.profile.CharacterAttribute;
import com.tupinicraft.profile.CharacterProfile;
import com.tupinicraft.profile.FamilyLegacies;
import com.tupinicraft.profile.FamilyLegacy;
import com.tupinicraft.profile.FamilyLegacyOdds;
import com.tupinicraft.profile.FamilyLegacyRarity;
import com.tupinicraft.profile.FamilyLegacyRoll;
import com.tupinicraft.skill.SkillDef;
import com.tupinicraft.skill.Skills;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
            sequences(f);
            defeats(f);
            familyLegacy(f);
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
            BirthRoulette.Result r = BirthRoulette.roll(random, i % 2 == 0 ? BirthTrait.Body.MALE : BirthTrait.Body.FEMALE);
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
        // Etapa 4.5: hidratacao e energia sao legadas e inativas (nao mudam, nao afetam a emocao).
        check(f, n.hydration() == Needs.DEFAULT.hydration() && n.energy() == Needs.DEFAULT.energy(),
                "necessidades: hidratação/energia legadas mudaram = " + n.hydration() + "/" + n.energy());
        check(f, n.bladder() == 60 * NeedsRules.BLADDER_PER_SECOND, "necessidades: bexiga em 60 s = " + n.bladder());
        Needs sprint = NeedsRules.tickSecond(new Needs(0, 0, 0, 0, 6_000), new NeedsRules.Context(true, false, true, false, false, 3, 3, 1, 1, 1, 1, 0));
        check(f, sprint.hydration() == 0 && sprint.energy() == 0, "necessidades: legadas zeradas continuam paradas");
        check(f, NeedsRules.moodTarget(new Needs(0, 0, 0, 0, 6_000), neutral) == NeedsRules.moodTarget(Needs.DEFAULT, neutral),
                "necessidades: hidratação/energia zeradas não podem derrubar a emoção");
        check(f, new com.tupinicraft.needs.NeedsDelta(4_000, 3_000, 100, 200, 300, 400).activeOnly()
                .equals(new com.tupinicraft.needs.NeedsDelta(0, 0, 100, 200, 300, 400)), "necessidades: consumíveis ignoram as legadas");
        check(f, com.tupinicraft.needs.Need.ACTIVE.length == 4 && !com.tupinicraft.needs.Need.HYDRATION.active()
                && !com.tupinicraft.needs.Need.ENERGY.active(), "necessidades: quatro ativas");
        Needs sleeping = NeedsRules.tickSecond(Needs.DEFAULT, new NeedsRules.Context(false, true, false, true, false, 3, 3, 1, 1, 1, 1, 0));
        check(f, sleeping.bladder() == Math.round(NeedsRules.BLADDER_PER_SECOND * 0.3F), "necessidades: bexiga dormindo");
        Needs sad = new Needs(0, 0, 9_000, 9_000, 6_000, 9_000);
        check(f, NeedsRules.moodTarget(sad, neutral) == 6_000 - 3_000 - NeedsRules.COOM_MOOD_PENALTY, "necessidades: alvo do humor com tudo ruim");
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
        CharacterProfile born = CharacterProfile.DEFAULT.withBirth(BirthRoulette.roll(RandomGenerator.of("L64X128MixRandom"), BirthTrait.Body.MALE), BirthRoulette.FREE_REROLLS);
        check(f, born.canReroll() && born.rerolls() == 1, "roleta: um novo giro após nascer");
        CharacterProfile afterReroll = born.withBirth(BirthRoulette.roll(RandomGenerator.of("L64X128MixRandom"), BirthTrait.Body.MALE), born.rerolls() - 1);
        check(f, !afterReroll.canReroll(), "roleta: segundo novo giro bloqueado");
        check(f, new CharacterProfile(true, "sertao", List.of(), 5, 1, 0, 0, List.of(), List.of()).rerolls() == 1, "roleta: ficha antiga com 5 giros");
        check(f, !CharacterProfile.DEFAULT.canReroll(), "roleta: sem nascimento não há novo giro");
        // Etapa 4.5: todo pedido de giro tem resposta; pedidos repetidos/atrasados nao duplicam nem gastam o novo giro.
        CharacterProfile fresh = CharacterProfile.DEFAULT;
        check(f, BirthRoulette.validate(fresh, false, 0) == BirthRoulette.Outcome.ROLLED
                && BirthRoulette.rerollsAfter(fresh) == BirthRoulette.FREE_REROLLS, "roleta: primeiro giro aceito");
        check(f, BirthRoulette.validate(fresh, true, 0) == BirthRoulette.Outcome.STALE, "roleta: novo giro antes de nascer recusado");
        check(f, BirthRoulette.validate(born, true, born.rollCount()) == BirthRoulette.Outcome.ROLLED
                && BirthRoulette.rerollsAfter(born) == 0, "roleta: novo giro aceito e consumido");
        check(f, BirthRoulette.validate(born, false, born.rollCount()) == BirthRoulette.Outcome.STALE
                && BirthRoulette.validate(born, true, born.rollCount() - 1) == BirthRoulette.Outcome.STALE,
                "roleta: pedido duplicado/atrasado recusado");
        check(f, BirthRoulette.validate(afterReroll, true, afterReroll.rollCount()) == BirthRoulette.Outcome.NO_REROLLS,
                "roleta: sem novo giro restante");
        check(f, BirthRoulette.Outcome.byOrdinal(99) == BirthRoulette.Outcome.STALE, "roleta: resposta desconhecida");
        // Continuar confirma: o novo giro restante some e a roleta nao pode trocar o resultado aceito.
        CharacterProfile confirmed = BirthRoulette.confirm(born, born.rollCount());
        check(f, confirmed.birthConfirmed() && !confirmed.canReroll() && confirmed.origin().equals(born.origin())
                && confirmed.attributes().equals(born.attributes()) && confirmed.traits().equals(born.traits()),
                "roleta: confirmar descarta o novo giro e mantém o resultado");
        check(f, BirthRoulette.validate(confirmed, true, confirmed.rollCount()) == BirthRoulette.Outcome.NO_REROLLS,
                "roleta: depois de confirmar não há novo giro");
        check(f, BirthRoulette.confirm(born, born.rollCount() - 1) == born && BirthRoulette.confirm(fresh, 0) == fresh,
                "roleta: confirmação atrasada ou sem nascimento não muda a ficha");
        traits(f, born, afterReroll);
    }

    /** Etapa 4.5: tracos de nascimento (sistema separado da origem, dos atributos e das necessidades). */
    private static void traits(List<String> f, CharacterProfile born, CharacterProfile afterReroll) {
        check(f, born.birthTraits().size() == BirthTrait.PER_BIRTH,
                "traços: nascimento sorteia " + BirthTrait.PER_BIRTH + " = " + born.traits());
        check(f, afterReroll.traits().size() == BirthTrait.PER_BIRTH,
                "traços: novo giro substitui (não soma) = " + afterReroll.traits());
        Map<BirthTrait, Integer> seen = new EnumMap<>(BirthTrait.class);
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        for (int i = 0; i < 4_000; i++) {
            for (BirthTrait t : BirthTrait.roll(random, i % 2 == 0 ? BirthTrait.Body.MALE : BirthTrait.Body.FEMALE)) seen.merge(t, 1, Integer::sum);
        }
        check(f, seen.size() == BirthTrait.values().length, "traços: todos sorteáveis = " + seen.keySet());
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (BirthTrait t : BirthTrait.values()) {
            check(f, ids.add(t.id()) && t.weight() > 0, "traços: id duplicado ou peso inválido " + t.id());
            // Nenhum efeito aprovado ainda: nada de bonus oculto.
            check(f, !t.hasEffect(), "traços: efeito não aprovado em " + t.id());
        }
        // Atributos e origem nao dependem dos tracos: mesmo resultado com e sem tracos.
        BirthRoulette.Result r = new BirthRoulette.Result(BirthOrigin.CLASSE_MEDIA, new int[] {4, 5, 5, 6, 4, 3},
                List.of(BirthTrait.HUNG));
        CharacterProfile withTrait = CharacterProfile.DEFAULT.withBirth(r, 1);
        CharacterProfile noTrait = CharacterProfile.DEFAULT.withBirth(new BirthRoulette.Result(r.origin(), r.attributes()), 1);
        check(f, withTrait.attributes().equals(noTrait.attributes()) && withTrait.origin().equals(noTrait.origin())
                && withTrait.skillPoints() == noTrait.skillPoints(), "traços: não alteram atributos, origem nem pontos");
        check(f, new CharacterProfile(true, "sertao", List.of(), 1, 1, 0, 0, List.of(), List.of("hung", "hung", "futuro"))
                .traits().equals(List.of("hung", "futuro"))
                && new CharacterProfile(true, "sertao", List.of(), 1, 1, 0, 0, List.of(), List.of("hung", "futuro"))
                .birthTraits().equals(List.of(BirthTrait.HUNG)),
                "traços: sem duplicatas; id desconhecido guardado mas não exibido");
        traitEligibility(f);
    }

    /** Etapa 4.6: conjunto masculino x feminino, Fertile/Infertile universais, saves e troca de corpo. */
    private static void traitEligibility(List<String> f) {
        BirthTrait.Body male = BirthTrait.Body.MALE;
        BirthTrait.Body female = BirthTrait.Body.FEMALE;
        List<BirthTrait> exclusive = List.of(BirthTrait.HEAVY_LOADER, BirthTrait.LOADLESS, BirthTrait.HUNG, BirthTrait.TINY_DICK);
        // 1. Sorteio masculino: todos os tracos, um por nascimento.
        Map<BirthTrait, Integer> maleSeen = new EnumMap<>(BirthTrait.class);
        Map<BirthTrait, Integer> femaleSeen = new EnumMap<>(BirthTrait.class);
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        boolean onePerBirth = true;
        for (int i = 0; i < 6_000; i++) {
            List<BirthTrait> m = BirthRoulette.roll(random, male).traits();
            List<BirthTrait> w = BirthRoulette.roll(random, female).traits();
            onePerBirth &= m.size() == BirthTrait.PER_BIRTH && w.size() == BirthTrait.PER_BIRTH;
            for (BirthTrait t : m) maleSeen.merge(t, 1, Integer::sum);
            for (BirthTrait t : w) femaleSeen.merge(t, 1, Integer::sum);
        }
        check(f, onePerBirth, "traços 4.6: um traço por nascimento nos dois sorteios");
        check(f, maleSeen.keySet().equals(Set.of(BirthTrait.values())), "traços 4.6: sorteio masculino = " + maleSeen.keySet());
        // 2. Sorteio feminino: exatamente o conjunto pedido.
        Set<BirthTrait> femaleSet = Set.of(BirthTrait.HIGH_ENDURANCE, BirthTrait.LIMP, BirthTrait.FERTILE,
                BirthTrait.INFERTILE, BirthTrait.AVERAGE);
        check(f, femaleSeen.keySet().equals(femaleSet) && Set.copyOf(BirthTrait.pool(female)).equals(femaleSet),
                "traços 4.6: sorteio feminino = " + femaleSeen.keySet());
        // 3/4. Fertile e Infertile universais, com polaridade e peso.
        for (BirthTrait t : List.of(BirthTrait.FERTILE, BirthTrait.INFERTILE)) {
            check(f, t.eligibleFor(male) && t.eligibleFor(female) && t.eligibility() == BirthTrait.Eligibility.ALL
                    && t.weight() > 0 && !t.hasEffect(), "traços 4.6: " + t.id() + " universal e sem efeito");
        }
        check(f, BirthTrait.FERTILE.polarity() == BirthTrait.Polarity.BUFF
                && BirthTrait.INFERTILE.polarity() == BirthTrait.Polarity.DEBUFF, "traços 4.6: polaridade de fertile/infertile");
        // 5. Exclusivos masculinos fora do sorteio feminino (e ainda registrados).
        for (BirthTrait t : exclusive) {
            check(f, !t.eligibleFor(female) && t.eligibleFor(male) && !femaleSeen.containsKey(t)
                    && BirthTrait.byId(t.id()) == t, "traços 4.6: exclusivo masculino no sorteio feminino " + t.id());
        }
        // Sem vantagem entre os sexos: em cada conjunto, peso de buffs = peso de debuffs.
        for (BirthTrait.Body body : BirthTrait.Body.values()) {
            int balance = 0;
            for (BirthTrait t : BirthTrait.pool(body)) balance += t.polarity() == BirthTrait.Polarity.BUFF ? t.weight()
                    : t.polarity() == BirthTrait.Polarity.DEBUFF ? -t.weight() : 0;
            check(f, balance == 0, "traços 4.6: buffs e debuffs equilibrados no conjunto " + body);
        }
        check(f, BirthTrait.Body.fromBodyType(1) == female && BirthTrait.Body.fromBodyType(0) == male
                && com.tupinicraft.character.CharacterAppearance.BODY_IDS[1].equals("feminino"), "traços 4.6: índice do corpo");
        // 6. Ids novos estaveis e persistidos (save e rede).
        check(f, BirthTrait.FERTILE.id().equals("fertile") && BirthTrait.INFERTILE.id().equals("infertile")
                && BirthTrait.byId("fertile") == BirthTrait.FERTILE && BirthTrait.byId("infertile") == BirthTrait.INFERTILE,
                "traços 4.6: ids fertile/infertile");
        for (BirthTrait t : List.of(BirthTrait.FERTILE, BirthTrait.INFERTILE)) {
            CharacterProfile p = CharacterProfile.DEFAULT.withBirth(new BirthRoulette.Result(BirthOrigin.SERTAO,
                    new int[] {4, 5, 5, 6, 4, 3}, List.of(t)), 1);
            Tag tag = CharacterProfile.CODEC.encodeStart(NbtOps.INSTANCE, p).getOrThrow();
            ByteBuf buf = Unpooled.buffer();
            CharacterProfile.STREAM_CODEC.encode(buf, p);
            check(f, ((CompoundTag) tag).getList("birth_traits", Tag.TAG_STRING).getString(0).equals(t.id())
                    && CharacterProfile.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().birthTraits().equals(List.of(t))
                    && CharacterProfile.STREAM_CODEC.decode(buf).equals(p), "traços 4.6: " + t.id() + " persiste no save e na rede");
        }
        // 7. Perfis antigos: traco exclusivo masculino ja confirmado continua, mesmo com corpo feminino.
        CharacterProfile oldMale = new CharacterProfile(true, "sertao", List.of(), 0, 1, 2, 0, List.of(), List.of("tiny_dick"));
        check(f, oldMale.birthConfirmed() && oldMale.withTraitsCoherentWith(female) == oldMale
                && oldMale.birthTraits().equals(List.of(BirthTrait.TINY_DICK)), "traços 4.6: traço confirmado preservado após trocar o corpo");
        CharacterProfile noTraits = new CharacterProfile(true, "sertao", List.of(), 1, 1, 2, 0, List.of(), List.of());
        check(f, noTraits.withTraitsCoherentWith(female) == noTraits && CharacterProfile.DEFAULT.withTraitsCoherentWith(female)
                == CharacterProfile.DEFAULT, "traços 4.6: ficha sem traço ou sem nascimento não ganha traço");
        CharacterProfile future = new CharacterProfile(true, "sertao", List.of(), 1, 1, 2, 0, List.of(), List.of("futuro"));
        check(f, future.withTraitsCoherentWith(female).traits().equals(List.of("futuro")), "traços 4.6: id desconhecido preservado");
        // Troca de corpo num resultado nao confirmado: coerente, mesma polaridade, sem traço extra, sem sortear de novo.
        for (BirthTrait t : exclusive) {
            CharacterProfile pending = CharacterProfile.DEFAULT.withBirth(new BirthRoulette.Result(BirthOrigin.PAMPA,
                    new int[] {6, 4, 4, 3, 3, 5}, List.of(t)), BirthRoulette.FREE_REROLLS);
            CharacterProfile asFemale = pending.withTraitsCoherentWith(female);
            List<BirthTrait> now = asFemale.birthTraits();
            check(f, now.size() == 1 && now.get(0).eligibleFor(female) && now.get(0).polarity() == t.polarity()
                    && asFemale.origin().equals(pending.origin()) && asFemale.attributes().equals(pending.attributes())
                    && asFemale.rerolls() == pending.rerolls() && asFemale.rollCount() == pending.rollCount()
                    && asFemale.skillPoints() == pending.skillPoints(), "traços 4.6: troca de corpo coerente " + t.id() + " -> " + now);
            // Alternar de novo nao muda mais nada (sem pescar tracos) e e deterministico.
            CharacterProfile back = asFemale.withTraitsCoherentWith(male).withTraitsCoherentWith(female).withTraitsCoherentWith(male);
            check(f, back.traits().equals(asFemale.traits())
                    && pending.withTraitsCoherentWith(female).traits().equals(asFemale.traits()),
                    "traços 4.6: alternar o corpo não gera traços novos " + t.id());
        }
        // 8. Reroll unico preservado: trocar o corpo nao devolve nem gasta o novo giro.
        CharacterProfile pending = CharacterProfile.DEFAULT.withBirth(BirthRoulette.roll(random, male), BirthRoulette.FREE_REROLLS);
        CharacterProfile switched = pending.withTraitsCoherentWith(female);
        check(f, switched.canReroll() && switched.rerolls() == 1 && switched.rollCount() == pending.rollCount(),
                "traços 4.6: troca de corpo mantém o único novo giro");
        CharacterProfile rerolled = switched.withBirth(BirthRoulette.roll(random, female), BirthRoulette.rerollsAfter(switched));
        check(f, !rerolled.canReroll() && rerolled.birthTraits().size() == 1 && rerolled.birthTraits().get(0).eligibleFor(female)
                && BirthRoulette.validate(rerolled, true, rerolled.rollCount()) == BirthRoulette.Outcome.NO_REROLLS
                && rerolled.withTraitsCoherentWith(male) == rerolled, "traços 4.6: novo giro feminino consumido e não restaurado");
        CharacterProfile confirmed = BirthRoulette.confirm(switched, switched.rollCount());
        check(f, !confirmed.canReroll() && confirmed.withTraitsCoherentWith(male) == confirmed
                && confirmed.traits().equals(switched.traits()), "traços 4.6: Continue preserva o resultado confirmado");
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
        // Save da v0.5.7 com a origem aposentada: vira Familia Artistica sem perder nascimento, atributos e habilidades.
        CompoundTag legacy = (CompoundTag) tag.copy();
        legacy.putString("origin", "familia_politica");
        CharacterProfile migrated = CharacterProfile.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        check(f, migrated.born() && migrated.originOrNull() == BirthOrigin.FAMILIA_ARTISTICA
                && migrated.attributes().equals(p.attributes()) && migrated.skills().equals(p.skills())
                && migrated.rerolls() == p.rerolls() && migrated.skillPoints() == p.skillPoints(),
                "codec: origem antiga familia_politica migrada para familia_artistica");
        Tag rewritten = CharacterProfile.CODEC.encodeStart(NbtOps.INSTANCE, migrated).getOrThrow();
        check(f, ((CompoundTag) rewritten).getString("origin").equals("familia_artistica"), "codec: ficha migrada grava o id novo");
        check(f, BirthOrigin.byId("familia_politica") == null && BirthOrigin.values().length == 8,
                "roleta: oito origens, sem a origem antiga no catálogo");
        CharacterProfile broken = new CharacterProfile(true, "x", List.of(99, -4), -1, 0, -3, -2, List.of("a", "a"), null);
        check(f, broken.attributes().size() == 6 && broken.attribute(CharacterAttribute.FORCA) == 10
                && broken.skills().size() == 1 && broken.skillPoints() == 0 && broken.traits().isEmpty(),
                "ficha: normalização de dados inválidos");
        profileCompat(f, p);
    }

    /** Etapa 4.5: fichas anteriores aos tracos e ida e volta pela rede. */
    private static void profileCompat(List<String> f, CharacterProfile p) {
        // Ficha salva pela v0.5.7 (sem birth_traits): carrega igual, sem tracos e sem perder nada.
        CompoundTag old = new CompoundTag();
        old.putBoolean("born", true);
        old.putString("origin", "familia_politica");
        net.minecraft.nbt.ListTag attrs = new net.minecraft.nbt.ListTag();
        for (int v : new int[] {4, 5, 5, 6, 4, 3}) attrs.add(net.minecraft.nbt.IntTag.valueOf(v));
        old.put("attributes", attrs);
        old.putInt("rerolls", 1);
        old.putInt("roll_count", 1);
        old.putInt("skill_points", 3);
        old.putInt("level_rewarded", 2);
        net.minecraft.nbt.ListTag skillList = new net.minecraft.nbt.ListTag();
        skillList.add(net.minecraft.nbt.StringTag.valueOf(Skills.PUNHOS.id().toString()));
        old.put("skills", skillList);
        CharacterProfile loaded = CharacterProfile.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        check(f, loaded.born() && loaded.originOrNull() == BirthOrigin.FAMILIA_ARTISTICA && loaded.traits().isEmpty()
                && loaded.attribute(CharacterAttribute.INTELIGENCIA) == 6 && loaded.canReroll() && !loaded.birthConfirmed()
                && loaded.skillPoints() == 3 && loaded.levelRewarded() == 2 && loaded.skills().size() == 1,
                "compat: ficha da v0.5.7 sem traços carrega intacta");
        // Gravar de novo inclui o campo novo e volta igual.
        CharacterProfile withTraits = CharacterProfile.DEFAULT.withBirth(new BirthRoulette.Result(BirthOrigin.PAMPA,
                new int[] {6, 4, 4, 3, 3, 5}, List.of(BirthTrait.LIMP)), 1);
        Tag tag = CharacterProfile.CODEC.encodeStart(NbtOps.INSTANCE, withTraits).getOrThrow();
        check(f, ((CompoundTag) tag).getList("birth_traits", Tag.TAG_STRING).getString(0).equals("limp")
                && CharacterProfile.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().equals(withTraits), "compat: traços ida e volta no save");
        // Rede: a ficha completa, com tracos, sobrevive ao StreamCodec.
        for (CharacterProfile sample : List.of(p, withTraits, loaded, CharacterProfile.DEFAULT)) {
            ByteBuf buf = Unpooled.buffer();
            CharacterProfile.STREAM_CODEC.encode(buf, sample);
            CharacterProfile decoded = CharacterProfile.STREAM_CODEC.decode(buf);
            check(f, decoded.equals(sample) && buf.readableBytes() == 0, "compat: ficha pela rede " + sample.origin());
            buf.release();
        }
    }

    /** Etapa 4.7: raridades do Family Legacy (distribuicao exata, sem redistribuicao, fora da roleta ao vivo). */
    private static void familyLegacy(List<String> f) {
        FamilyLegacyOdds odds = FamilyLegacyOdds.DEFAULT;
        int[] approved = {515, 300, 100, 50, 30, 5};
        FamilyLegacyRarity[] all = FamilyLegacyRarity.values();
        check(f, all.length == approved.length, "legacy: seis raridades");
        int total = 0;
        for (FamilyLegacyRarity r : all) {
            check(f, odds.permille(r) == approved[r.ordinal()], "legacy: chance padrão de " + r.id());
            total += odds.permille(r);
        }
        check(f, total == FamilyLegacyOdds.TOTAL, "legacy: soma padrão " + total);
        check(f, FamilyLegacyRarity.highest() == FamilyLegacyRarity.BONER
                && FamilyLegacyRarity.BONER.isHigherThan(FamilyLegacyRarity.LEGENDARY)
                && FamilyLegacyRarity.LEGENDARY.isHigherThan(FamilyLegacyRarity.EPIC)
                && all[all.length - 1] == FamilyLegacyRarity.BONER, "legacy: BONER acima de Legendary");
        check(f, FamilyLegacyRarity.byId("boner") == FamilyLegacyRarity.BONER && FamilyLegacyRarity.byId("x") == null,
                "legacy: ids de raridade");

        // Bilhetes: cada raridade ocupa exatamente a sua chance (probabilidade exata, sem arredondamento).
        int[] tickets = new int[all.length];
        for (int t = 0; t < FamilyLegacyOdds.TOTAL; t++) tickets[odds.rarityAt(t).ordinal()]++;
        for (FamilyLegacyRarity r : all) {
            check(f, tickets[r.ordinal()] == approved[r.ordinal()], "legacy: bilhetes de " + r.id() + " = " + tickets[r.ordinal()]);
        }
        check(f, odds.rarityAt(514) == FamilyLegacyRarity.COMMON && odds.rarityAt(515) == FamilyLegacyRarity.UNCOMMON
                && odds.rarityAt(964) == FamilyLegacyRarity.EPIC && odds.rarityAt(965) == FamilyLegacyRarity.LEGENDARY
                && odds.rarityAt(994) == FamilyLegacyRarity.LEGENDARY && odds.rarityAt(995) == FamilyLegacyRarity.BONER
                && odds.rarityAt(999) == FamilyLegacyRarity.BONER, "legacy: limites dos bilhetes");

        // Configuravel, mas sempre exata: soma != 100%, negativo, casa decimal extra ou raridade faltando sao recusados.
        check(f, FamilyLegacyOdds.ofPercent(51.5, 30, 10, 5, 3, 0.5).equals(odds), "legacy: porcentagens aprovadas");
        check(f, throwsIae(() -> FamilyLegacyOdds.of(515, 300, 100, 50, 30, 4)), "legacy: soma 99.9% aceita");
        check(f, throwsIae(() -> FamilyLegacyOdds.of(520, 300, 100, 50, 30, 5)), "legacy: soma 100.5% aceita");
        check(f, throwsIae(() -> FamilyLegacyOdds.of(525, 300, 100, 50, 30, -5)), "legacy: chance negativa aceita");
        check(f, throwsIae(() -> FamilyLegacyOdds.of(515, 300, 100, 50, 35)), "legacy: raridade faltando aceita");
        check(f, throwsIae(() -> FamilyLegacyOdds.ofPercent(51.45, 30, 10, 5, 3, 0.55)), "legacy: duas casas aceitas");
        FamilyLegacyOdds custom = FamilyLegacyOdds.ofPercent(50, 30, 10, 5, 4.5, 0.5);
        check(f, custom.permille(FamilyLegacyRarity.LEGENDARY) == 45 && custom.rarityAt(994) == FamilyLegacyRarity.LEGENDARY
                && custom.rarityAt(995) == FamilyLegacyRarity.BONER
                && custom.rarityAt(499) == FamilyLegacyRarity.COMMON && custom.rarityAt(500) == FamilyLegacyRarity.UNCOMMON,
                "legacy: distribuição configurada");

        // Amostra: a raridade sai com a chance aprovada mesmo com raridades vazias (sem redistribuição).
        FamilyLegacy common = new FamilyLegacy("selftest_common", FamilyLegacyRarity.COMMON, BirthTrait.Eligibility.ALL);
        FamilyLegacy maleEpic = new FamilyLegacy("selftest_male_epic", FamilyLegacyRarity.EPIC, BirthTrait.Eligibility.MALE_ONLY);
        FamilyLegacies partial = FamilyLegacies.of(List.of(common, maleEpic));
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        int samples = 200_000;
        int[] counts = new int[all.length];
        int empty = 0;
        for (int i = 0; i < samples; i++) {
            FamilyLegacyRoll.Result r = FamilyLegacyRoll.roll(random, odds, partial, BirthTrait.Body.FEMALE);
            counts[r.rarity().ordinal()]++;
            if (r.hasFamily()) {
                if (r.family().rarity() != r.rarity() || r.status() != FamilyLegacyRoll.Status.ROLLED) {
                    f.add("legacy: família fora da raridade sorteada " + r);
                    return;
                }
            } else {
                empty++;
                if (r.status() != FamilyLegacyRoll.Status.NO_FAMILY_IN_RARITY || r.rarity() == FamilyLegacyRarity.COMMON) {
                    f.add("legacy: resultado vazio inesperado " + r);
                    return;
                }
            }
        }
        for (FamilyLegacyRarity r : all) {
            double expected = samples * approved[r.ordinal()] / 1000.0;
            double tolerance = 5 * Math.sqrt(expected) + 1; // ~5 desvios-padrão
            check(f, Math.abs(counts[r.ordinal()] - expected) <= tolerance,
                    "legacy: amostra de " + r.id() + " = " + counts[r.ordinal()] + " (esperado ~" + (int) expected + ")");
        }
        check(f, empty == samples - counts[FamilyLegacyRarity.COMMON.ordinal()],
                "legacy: raridades vazias redistribuídas (" + empty + ")");

        // Elegibilidade dentro da raridade: só o corpo masculino recebe a família MALE_ONLY.
        check(f, FamilyLegacyRoll.pickWithin(random, FamilyLegacyRarity.EPIC, partial, BirthTrait.Body.MALE).family() == maleEpic,
                "legacy: família masculina não sorteada");
        FamilyLegacyRoll.Result femaleEpic = FamilyLegacyRoll.pickWithin(random, FamilyLegacyRarity.EPIC, partial, BirthTrait.Body.FEMALE);
        check(f, !femaleEpic.hasFamily() && femaleEpic.rarity() == FamilyLegacyRarity.EPIC,
                "legacy: corpo inelegível recebeu família");
        check(f, throwsIae(() -> FamilyLegacies.of(List.of(common, common))), "legacy: id duplicado aceito");

        // Fora da roleta ao vivo até o catálogo ser finalizado; a roleta não sorteia família.
        check(f, FamilyLegacies.CATALOG_FINALIZED || !FamilyLegacies.isLive(), "legacy: ativo sem catálogo finalizado");
        check(f, FamilyLegacies.CATALOG_FINALIZED || FamilyLegacies.CATALOG.isEmpty(), "legacy: famílias inventadas no catálogo");
    }

    private static boolean throwsIae(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** Etapa 4.1: sequencias de animacao (queda -> quatro apoios -> levantar) sem buraco entre os segmentos. */
    private static void defeats(List<String> f) {
        check(f, com.tupinicraft.testgame.DefeatCounter.next(0) == 1, "derrotas: 0 -> 1");
        check(f, com.tupinicraft.testgame.DefeatCounter.next(12) == 13, "derrotas: 12 -> 13");
        check(f, com.tupinicraft.testgame.DefeatCounter.next(-5) == 1, "derrotas: negativo vira 0 antes do +1");
        check(f, com.tupinicraft.testgame.DefeatCounter.next(Integer.MAX_VALUE) == Integer.MAX_VALUE,
                "derrotas: sem estouro no máximo");
    }

    private static void sequences(List<String> f) {
        AnimationSequence fall = PlayerAnimations.FALL_AND_RECOVER;
        check(f, fall.problem().isEmpty(), "sequência: queda inválida " + fall.problem().orElse(""));
        int k = PlayerAnimations.KNOCKDOWN.durationTicks();
        int g = PlayerAnimations.GET_UP.durationTicks();
        check(f, fall.totalTicks() == k + 40 + g, "sequência: duração total");
        long t0 = 1000;
        PlayerAnimationState state = fall.toState(t0, 7, Optional.empty(), new CompoundTag());
        long end = t0 + fall.totalTicks();
        boolean continuous = true;
        for (long t = t0; t < end; t++) {
            PlayerAnimationState seg = state.at(t);
            // Nenhum tick sem animacao (seria a pose em pe) e cada tick dentro do proprio segmento.
            continuous &= !state.finished(t) && seg.startGameTime() <= t && t < seg.segmentEnd() && seg.sequence() == 7;
        }
        check(f, continuous, "sequência: há tick sem segmento entre o início e o fim");
        check(f, state.finished(end), "sequência: não termina no fim do último clip");
        check(f, state.at(t0 + k - 1).animation().equals(PlayerAnimations.KNOCKDOWN.id())
                && state.at(t0 + k).animation().equals(PlayerAnimations.HANDS_AND_KNEES.id())
                && state.at(t0 + k).startGameTime() == t0 + k && state.at(t0 + k).segment() == 1
                && state.at(t0 + k + 39).animation().equals(PlayerAnimations.HANDS_AND_KNEES.id())
                && state.at(t0 + k + 40).animation().equals(PlayerAnimations.GET_UP.id())
                && state.at(t0 + k + 40).segment() == 2, "sequência: instantes de troca");
        check(f, state.hasNext() && state.at(t0 + k).hasNext() && !state.at(t0 + k + 40).hasNext(),
                "sequência: só o último segmento solta o quadro final");
        // Sair do loop antes da hora (advance): o proximo segmento comeca no instante pedido.
        PlayerAnimationState early = state.at(t0 + k + 5).next(t0 + k + 10);
        check(f, early.animation().equals(PlayerAnimations.GET_UP.id()) && early.segment() == 2
                && !early.finished(t0 + k + 10 + g - 1) && early.finished(t0 + k + 10 + g), "sequência: avanço antecipado");
        // Animacao avulsa continua igual (compatibilidade).
        PlayerAnimationState single = new PlayerAnimationState(PlayerAnimations.PUNCH.id(), 0, 26, false, 1,
                Optional.empty(), new CompoundTag());
        PlayerAnimationState loop = new PlayerAnimationState(PlayerAnimations.COMBAT_IDLE.id(), 0, 40, true, 1,
                Optional.empty(), new CompoundTag());
        check(f, !single.finished(25) && single.finished(26) && single.at(30) == single && !loop.finished(1_000_000),
                "sequência: animação avulsa mudou de comportamento");
        // Nenhum estado sem fim: loop sem duração é recusado; duração absurda também.
        check(f, AnimationSequence.of(PlayerAnimations.KNOCKDOWN).then(PlayerAnimations.HANDS_AND_KNEES).problem().isPresent(),
                "sequência: loop sem duração aceito");
        check(f, AnimationSequence.of(PlayerAnimations.KNOCKDOWN, AnimationSequence.MAX_SEGMENT_TICKS + 1).problem().isPresent(),
                "sequência: duração acima do limite aceita");
        // Pacote: ida e volta.
        ByteBuf buf = Unpooled.buffer();
        PlayerAnimationState.STREAM_CODEC.encode(buf, state);
        check(f, PlayerAnimationState.STREAM_CODEC.decode(buf).equals(state), "sequência: pacote ida e volta");
        buf.release();
        // Comando: texto -> sequencia.
        StringBuilder error = new StringBuilder();
        check(f, PlayerAnimationCommands.parseSequence("knockdown hands_and_knees@40 tupinicraft:get_up", error)
                .map(fall::equals).orElse(false), "sequência: comando não reproduz a queda " + error);
        check(f, PlayerAnimationCommands.parseSequence("knockdown hands_and_knees", new StringBuilder()).isEmpty()
                && PlayerAnimationCommands.parseSequence("nada_disso", new StringBuilder()).isEmpty(),
                "sequência: comando aceitou passo inválido");
    }
}
