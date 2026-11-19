package com.respectcraft.selftest;

import com.respectcraft.animation.AnimationSequence;
import com.respectcraft.animation.PlayerAnimationCommands;
import com.respectcraft.animation.PlayerAnimationDef;
import com.respectcraft.animation.PlayerAnimationState;
import com.respectcraft.animation.PlayerAnimations;
import com.respectcraft.finisher.CameraTrack;
import com.respectcraft.finisher.FinisherDef;
import com.respectcraft.finisher.Finishers;
import com.respectcraft.needs.Needs;
import com.respectcraft.needs.NeedsRules;
import com.respectcraft.profile.BirthOrigin;
import com.respectcraft.profile.BirthRoulette;
import com.respectcraft.profile.BirthTrait;
import com.respectcraft.profile.CharacterAttribute;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.Specialities;
import com.respectcraft.profile.Speciality;
import com.respectcraft.profile.SpecialityOdds;
import com.respectcraft.profile.SpecialityRarity;
import com.respectcraft.profile.SpecialityRoll;
import com.respectcraft.skill.SkillDef;
import com.respectcraft.skill.Skills;
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
 * {@code /respectcraft selftest} no servidor dedicado do CI. Substitui um runner de testes separado.
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
            specialities(f);
            rouletteWheel(f);
        } catch (RuntimeException e) {
            f.add("exceção: " + e);
        }
        return f;
    }

    /**
     * Etapa 4.7.1: a roda tem seis fatias iguais (so visual) e sempre para na raridade da ficha; as chances reais
     * vao do servidor para a tela num pacote que so carrega milesimos validos.
     */
    private static void rouletteWheel(List<String> f) {
        float total = 0;
        for (SpecialityRarity rarity : SpecialityRarity.values()) {
            check(f, Math.abs(com.respectcraft.profile.RouletteLayout.sliceStart(rarity) - total) < 1e-3F,
                    "roda: fatias iguais e em ordem (" + rarity.id() + ")");
            total += com.respectcraft.profile.RouletteLayout.SLICE;
            for (int rollCount = 0; rollCount < 64; rollCount++) {
                float rest = com.respectcraft.profile.RouletteLayout.restingRotation(rarity, rollCount);
                // Igual a tela: a roda chega ao repouso depois de voltas inteiras a partir de qualquer angulo.
                float landed = 1234.5F + 720F + net.minecraft.util.Mth.positiveModulo(rest - (1234.5F + 720F), 360F);
                check(f, com.respectcraft.profile.RouletteLayout.rarityAtPointer(rest) == rarity
                        && com.respectcraft.profile.RouletteLayout.rarityAtPointer(landed) == rarity,
                        "roda: para na raridade sorteada (" + rarity.id() + ", rollCount " + rollCount + ")");
            }
        }
        check(f, Math.abs(total - 360F) < 1e-3F, "roda: fatias fecham 360 graus");
        SpecialityOdds custom = SpecialityOdds.of(500, 200, 150, 100, 40, 10);
        ByteBuf buf = Unpooled.buffer();
        com.respectcraft.network.GameplayNetworking.SpecialityOddsSync.STREAM_CODEC.encode(buf,
                com.respectcraft.network.GameplayNetworking.SpecialityOddsSync.of(custom));
        check(f, com.respectcraft.network.GameplayNetworking.SpecialityOddsSync.STREAM_CODEC.decode(buf).odds().equals(custom),
                "roda: chances do servidor chegam iguais ao cliente");
        check(f, SpecialityOdds.ofOrDefault(new int[]{999, 0, 0, 0, 0, 0}) == SpecialityOdds.DEFAULT
                && SpecialityOdds.ofOrDefault(new int[]{1000}) == SpecialityOdds.DEFAULT, "roda: chances inválidas caem no padrão");
        check(f, SpecialityOdds.DEFAULT.permille(SpecialityRarity.COMMON) == 415 && SpecialityOdds.DEFAULT.permille(SpecialityRarity.UNCOMMON) == 250
                && SpecialityOdds.DEFAULT.permille(SpecialityRarity.RARE) == 200 && SpecialityOdds.DEFAULT.permille(SpecialityRarity.EPIC) == 100
                && SpecialityOdds.DEFAULT.permille(SpecialityRarity.LEGENDARY) == 30 && SpecialityOdds.DEFAULT.permille(SpecialityRarity.BONER) == 5,
                "roda: chances padrão aprovadas 41.5/25/20/10/3/0.5");
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) f.add(what);
    }

    private static void roulette(List<String> f) {
        // Etapa 4.7: o nascimento sorteia uma Speciality (raridade primeiro); atributos sem bonus de origem.
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        int rolls = 40_000;
        Map<SpecialityRarity, Integer> counts = new EnumMap<>(SpecialityRarity.class);
        Map<Speciality, Integer> seen = new EnumMap<>(Speciality.class);
        for (int i = 0; i < rolls; i++) {
            BirthRoulette.Result r = BirthRoulette.roll(random, i % 2 == 0 ? BirthTrait.Body.MALE : BirthTrait.Body.FEMALE);
            counts.merge(r.rarity(), 1, Integer::sum);
            seen.merge(r.speciality(), 1, Integer::sum);
            int sum = 0;
            for (int v : r.attributes()) {
                if (v < CharacterAttribute.MIN || v > BirthRoulette.ROLL_CAP) {
                    f.add("roleta: atributo fora da faixa " + v);
                    return;
                }
                sum += v;
            }
            if (sum != CharacterAttribute.BASE * 6 + BirthRoulette.EXTRA_POINTS) {
                f.add("roleta: soma inesperada " + sum + " (Speciality não dá bônus de atributo)");
                return;
            }
        }
        for (SpecialityRarity rarity : SpecialityRarity.values()) {
            double expected = rolls * SpecialityOdds.DEFAULT.permille(rarity) / 1000.0;
            int got = counts.getOrDefault(rarity, 0);
            check(f, Math.abs(got - expected) <= 5 * Math.sqrt(expected) + 1,
                    "roleta: frequência de " + rarity.id() + " = " + got + " (esperado ~" + (int) expected + ")");
        }
        check(f, seen.size() == Speciality.values().length, "roleta: todas as Specialities sorteáveis = " + seen.keySet());
        check(f, BirthRoulette.startingSkillPoints(3) == 2 && BirthRoulette.startingSkillPoints(9) == 5, "roleta: pontos iniciais");
        // Raridade forcada (so o teste administrativo usa): a Speciality sai dentro dela, o resto e sorteado igual.
        for (SpecialityRarity rarity : SpecialityRarity.values()) {
            BirthRoulette.Result forced = BirthRoulette.roll(random, BirthTrait.Body.FEMALE, SpecialityOdds.DEFAULT, rarity);
            check(f, forced.rarity() == rarity && forced.traits().size() == BirthTrait.PER_BIRTH,
                    "roleta: raridade forçada " + rarity.id());
        }
        check(f, BirthRoulette.roll(random, BirthTrait.Body.MALE, SpecialityOdds.DEFAULT, SpecialityRarity.BONER).speciality()
                == Speciality.ISLAND_GUY, "roleta: BONER forçado = Island Guy");
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
        check(f, new com.respectcraft.needs.NeedsDelta(4_000, 3_000, 100, 200, 300, 400).activeOnly()
                .equals(new com.respectcraft.needs.NeedsDelta(0, 0, 100, 200, 300, 400)), "necessidades: consumíveis ignoram as legadas");
        check(f, com.respectcraft.needs.Need.ACTIVE.length == 4 && !com.respectcraft.needs.Need.HYDRATION.active()
                && !com.respectcraft.needs.Need.ENERGY.active(), "necessidades: quatro ativas");
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
        check(f, NeedsRules.moodTarget(Needs.DEFAULT.with(com.respectcraft.needs.Need.COOM, 9_000), neutral)
                == NeedsRules.moodTarget(Needs.DEFAULT, neutral) - NeedsRules.COOM_MOOD_PENALTY, "coom: humor");
        // Save da Etapa 3 (sem o campo coom) carrega com coom 0.
        CompoundTag old = new CompoundTag();
        old.putInt("hidratacao", 5_000);
        Needs loaded = Needs.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        check(f, loaded.coom() == 0 && loaded.hydration() == 5_000 && loaded.mood() == 6_000, "coom: save antigo");

        for (com.respectcraft.needs.NeedEffectDef def : com.respectcraft.needs.NeedEffects.all()) {
            if (def.durationSeconds() > 0 && !def.gradual().isZero()) {
                com.respectcraft.needs.NeedsDelta sum = com.respectcraft.needs.NeedsDelta.NONE;
                for (int s = -2; s < def.durationSeconds() + 5; s++) sum = sum.plus(com.respectcraft.needs.NeedEffectTimeline.gradualStep(def, s));
                check(f, sum.equals(def.gradual()), "efeitos: gradual não fecha o total " + def.id());
            }
            int fired = 0;
            for (int s = 0; s < def.lifetimeSeconds() + 5; s++) if (com.respectcraft.needs.NeedEffectTimeline.delayedFires(def, s)) fired++;
            check(f, fired == (def.delaySeconds() > 0 ? 1 : 0), "efeitos: estágio atrasado " + def.id());
            check(f, com.respectcraft.needs.NeedEffectTimeline.expired(def, def.lifetimeSeconds()), "efeitos: não expira " + def.id());
        }
        var lax = com.respectcraft.needs.NeedEffects.LAXANTE_INDUSTRIAL;
        check(f, lax.immediate().isZero() && lax.delayed().bowel() > 0 && lax.delaySeconds() > 0, "efeitos: laxante atrasado");
        var calm = com.respectcraft.needs.NeedEffects.CHA_CALMANTE;
        check(f, calm.immediate().coom() < 0 && calm.rate(com.respectcraft.needs.Need.COOM) < 1F, "efeitos: chá calmante");
        check(f, com.respectcraft.needs.NeedEffectTimeline.combinedRate(List.of(com.respectcraft.needs.NeedEffects.AFRODISIACO_MISTERIOSO),
                List.of(0), com.respectcraft.needs.Need.COOM) == 3F, "efeitos: ritmo do afrodisíaco");
        check(f, com.respectcraft.needs.NeedEffectTimeline.combinedRate(List.of(com.respectcraft.needs.NeedEffects.AFRODISIACO_MISTERIOSO),
                List.of(500), com.respectcraft.needs.Need.COOM) == 1F, "efeitos: ritmo expira");
        var active = com.respectcraft.needs.ActiveNeedEffects.EMPTY.start("cha_calmante").start("cha_calmante");
        check(f, active.entries().size() == 1, "efeitos: repetir reinicia em vez de acumular");
        Tag at = com.respectcraft.needs.ActiveNeedEffects.CODEC.encodeStart(NbtOps.INSTANCE, active).getOrThrow();
        check(f, com.respectcraft.needs.ActiveNeedEffects.CODEC.parse(NbtOps.INSTANCE, at).getOrThrow().equals(active), "efeitos: codec");

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
        check(f, confirmed.birthConfirmed() && !confirmed.canReroll() && confirmed.birthSpeciality().equals(born.birthSpeciality())
                && confirmed.currentSpeciality().equals(born.currentSpeciality())
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
        // Atributos e Speciality nao dependem dos tracos: mesmo resultado com e sem tracos.
        BirthRoulette.Result r = new BirthRoulette.Result(Speciality.DOMINANTS, new int[] {4, 5, 5, 6, 4, 3},
                List.of(BirthTrait.HUNG));
        CharacterProfile withTrait = CharacterProfile.DEFAULT.withBirth(r, 1);
        CharacterProfile noTrait = CharacterProfile.DEFAULT.withBirth(new BirthRoulette.Result(r.speciality(), r.attributes(), List.of()), 1);
        check(f, withTrait.attributes().equals(noTrait.attributes()) && withTrait.birthSpeciality().equals(noTrait.birthSpeciality())
                && withTrait.skillPoints() == noTrait.skillPoints(), "traços: não alteram atributos, Speciality nem pontos");
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
                && com.respectcraft.character.CharacterAppearance.BODY_IDS[1].equals("feminino"), "traços 4.6: índice do corpo");
        // 6. Ids novos estaveis e persistidos (save e rede).
        check(f, BirthTrait.FERTILE.id().equals("fertile") && BirthTrait.INFERTILE.id().equals("infertile")
                && BirthTrait.byId("fertile") == BirthTrait.FERTILE && BirthTrait.byId("infertile") == BirthTrait.INFERTILE,
                "traços 4.6: ids fertile/infertile");
        for (BirthTrait t : List.of(BirthTrait.FERTILE, BirthTrait.INFERTILE)) {
            CharacterProfile p = CharacterProfile.DEFAULT.withBirth(new BirthRoulette.Result(Speciality.VOYEURS,
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
            CharacterProfile pending = CharacterProfile.DEFAULT.withBirth(new BirthRoulette.Result(Speciality.ROPEBOUND,
                    new int[] {6, 4, 4, 3, 3, 5}, List.of(t)), BirthRoulette.FREE_REROLLS);
            CharacterProfile asFemale = pending.withTraitsCoherentWith(female);
            List<BirthTrait> now = asFemale.birthTraits();
            check(f, now.size() == 1 && now.get(0).eligibleFor(female) && now.get(0).polarity() == t.polarity()
                    && asFemale.currentSpeciality().equals(pending.currentSpeciality()) && asFemale.attributes().equals(pending.attributes())
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
        CharacterProfile p = CharacterProfile.DEFAULT.withBirth(new BirthRoulette.Result(Speciality.LATEX_ROYALS,
                new int[] {5, 4, 9, 3, 6, 2}, List.of()), 1).withSkills(List.of(Skills.PUNHOS.id().toString()), 3);
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
        CharacterProfile withTraits = CharacterProfile.DEFAULT.withBirth(new BirthRoulette.Result(Speciality.ISLAND_GUY,
                new int[] {6, 4, 4, 3, 3, 5}, List.of(BirthTrait.LIMP)), 1);
        Tag tag = CharacterProfile.CODEC.encodeStart(NbtOps.INSTANCE, withTraits).getOrThrow();
        check(f, ((CompoundTag) tag).getList("birth_traits", Tag.TAG_STRING).getString(0).equals("limp")
                && CharacterProfile.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().equals(withTraits), "compat: traços ida e volta no save");
        // Rede: a ficha completa, com tracos, sobrevive ao StreamCodec.
        for (CharacterProfile sample : List.of(p, withTraits, loaded, CharacterProfile.DEFAULT)) {
            ByteBuf buf = Unpooled.buffer();
            CharacterProfile.STREAM_CODEC.encode(buf, sample);
            CharacterProfile decoded = CharacterProfile.STREAM_CODEC.decode(buf);
            check(f, decoded.equals(sample) && buf.readableBytes() == 0, "compat: ficha pela rede " + sample.origin()
                    + "/" + sample.birthSpeciality());
            buf.release();
        }
        specialityCompat(f, loaded, withTraits);
    }

    /**
     * Etapa 4.7: persistencia das Specialities. Ficha antiga com origem social continua com ela (nenhuma
     * Speciality aleatoria ao carregar); nascimento novo grava a Speciality de nascimento e a ativa (iguais).
     */
    private static void specialityCompat(List<String> f, CharacterProfile legacy, CharacterProfile fresh) {
        check(f, legacy.birthSpeciality().isEmpty() && legacy.currentSpeciality().isEmpty() && legacy.specialityOrNull() == null
                && legacy.hasBirthResult(), "speciality: ficha antiga mantém a origem legada e fica sem Speciality");
        CharacterProfile legacyConfirmed = BirthRoulette.confirm(legacy, legacy.rollCount());
        check(f, legacyConfirmed.originOrNull() == BirthOrigin.FAMILIA_ARTISTICA && legacyConfirmed.specialityOrNull() == null
                && legacyConfirmed.attributes().equals(legacy.attributes()) && legacyConfirmed.skills().equals(legacy.skills()),
                "speciality: confirmar ficha antiga não troca a origem nem atribui Speciality");
        CharacterProfile legacyAfterBody = legacy.withTraitsCoherentWith(BirthTrait.Body.FEMALE);
        check(f, legacyAfterBody.origin().equals(legacy.origin()) && legacyAfterBody.specialityOrNull() == null,
                "speciality: troca de corpo não mexe na origem legada");
        // O unico novo giro de uma ficha antiga e um nascimento novo: Speciality no lugar da origem social.
        CharacterProfile rerolled = legacy.withBirth(new BirthRoulette.Result(Speciality.SPITTERS, new int[] {4, 4, 4, 4, 5, 5},
                List.of(BirthTrait.AVERAGE)), 0);
        check(f, rerolled.origin().isEmpty() && rerolled.specialityOrNull() == Speciality.SPITTERS
                && rerolled.birthSpecialityOrNull() == Speciality.SPITTERS && !rerolled.canReroll()
                && rerolled.levelRewarded() == legacy.levelRewarded(), "speciality: novo giro de ficha antiga");
        check(f, fresh.birthSpecialityOrNull() == Speciality.ISLAND_GUY && fresh.specialityOrNull() == Speciality.ISLAND_GUY
                && fresh.origin().isEmpty(), "speciality: nascimento novo = Speciality de nascimento e ativa iguais");
        Tag tag = CharacterProfile.CODEC.encodeStart(NbtOps.INSTANCE, fresh).getOrThrow();
        CompoundTag nbt = (CompoundTag) tag;
        check(f, nbt.getString("birth_speciality").equals("island_guy") && nbt.getString("current_speciality").equals("island_guy"),
                "speciality: campos gravados no save");
        // Sem current_speciality gravado, a ativa e a de nascimento; id desconhecido fica guardado, mas nao aparece.
        CompoundTag noCurrent = nbt.copy();
        noCurrent.remove("current_speciality");
        CharacterProfile parsed = CharacterProfile.CODEC.parse(NbtOps.INSTANCE, noCurrent).getOrThrow();
        check(f, parsed.equals(fresh), "speciality: ativa vazia = a de nascimento");
        CompoundTag future = nbt.copy();
        future.putString("birth_speciality", "futura");
        future.putString("current_speciality", "futura");
        CharacterProfile unknown = CharacterProfile.CODEC.parse(NbtOps.INSTANCE, future).getOrThrow();
        check(f, unknown.birthSpeciality().equals("futura") && unknown.specialityOrNull() == null
                && unknown.attributes().equals(fresh.attributes()), "speciality: id desconhecido preservado");
        // A troca futura (nao implementada) so mudaria a ativa: os dois campos sao independentes no save.
        CharacterProfile switched = new CharacterProfile(fresh.born(), fresh.origin(), fresh.attributes(), fresh.rerolls(),
                fresh.rollCount(), fresh.skillPoints(), fresh.levelRewarded(), fresh.skills(), fresh.traits(), "island_guy", "vanilla");
        CharacterProfile switchedBack = CharacterProfile.CODEC.parse(NbtOps.INSTANCE,
                CharacterProfile.CODEC.encodeStart(NbtOps.INSTANCE, switched).getOrThrow()).getOrThrow();
        check(f, switchedBack.birthSpecialityOrNull() == Speciality.ISLAND_GUY && switchedBack.specialityOrNull() == Speciality.VANILLA,
                "speciality: nascimento e ativa gravadas separadamente");
    }

    /** Etapa 4.7: raridades e catalogo das Specialities (distribuicao exata, sem redistribuicao). */
    private static void specialities(List<String> f) {
        SpecialityOdds odds = SpecialityOdds.DEFAULT;
        int[] approved = {415, 250, 200, 100, 30, 5};
        SpecialityRarity[] all = SpecialityRarity.values();
        check(f, all.length == approved.length, "speciality: seis raridades");
        int total = 0;
        for (SpecialityRarity r : all) {
            check(f, odds.permille(r) == approved[r.ordinal()], "speciality: chance padrão de " + r.id());
            total += odds.permille(r);
        }
        check(f, total == SpecialityOdds.TOTAL, "speciality: soma padrão " + total);
        check(f, SpecialityRarity.highest() == SpecialityRarity.BONER
                && SpecialityRarity.BONER.isHigherThan(SpecialityRarity.LEGENDARY)
                && SpecialityRarity.LEGENDARY.isHigherThan(SpecialityRarity.EPIC)
                && all[all.length - 1] == SpecialityRarity.BONER, "speciality: BONER acima de Legendary");
        check(f, SpecialityRarity.byId("boner") == SpecialityRarity.BONER && SpecialityRarity.byId("x") == null,
                "speciality: ids de raridade");

        // Bilhetes: cada raridade ocupa exatamente a sua chance (probabilidade exata, sem arredondamento).
        int[] tickets = new int[all.length];
        for (int t = 0; t < SpecialityOdds.TOTAL; t++) tickets[odds.rarityAt(t).ordinal()]++;
        for (SpecialityRarity r : all) {
            check(f, tickets[r.ordinal()] == approved[r.ordinal()], "speciality: bilhetes de " + r.id() + " = " + tickets[r.ordinal()]);
        }
        check(f, odds.rarityAt(414) == SpecialityRarity.COMMON && odds.rarityAt(415) == SpecialityRarity.UNCOMMON
                && odds.rarityAt(664) == SpecialityRarity.UNCOMMON && odds.rarityAt(665) == SpecialityRarity.RARE
                && odds.rarityAt(864) == SpecialityRarity.RARE && odds.rarityAt(865) == SpecialityRarity.EPIC
                && odds.rarityAt(964) == SpecialityRarity.EPIC && odds.rarityAt(965) == SpecialityRarity.LEGENDARY
                && odds.rarityAt(994) == SpecialityRarity.LEGENDARY && odds.rarityAt(995) == SpecialityRarity.BONER
                && odds.rarityAt(999) == SpecialityRarity.BONER, "speciality: limites dos bilhetes");

        // Configuravel, mas sempre exata: soma != 100%, negativo, casa decimal extra ou raridade faltando sao recusados.
        check(f, SpecialityOdds.ofPercent(41.5, 25, 20, 10, 3, 0.5).equals(odds), "speciality: porcentagens aprovadas");
        check(f, throwsIae(() -> SpecialityOdds.of(415, 250, 200, 100, 30, 4)), "speciality: soma 99.9% aceita");
        check(f, throwsIae(() -> SpecialityOdds.of(420, 250, 200, 100, 30, 5)), "speciality: soma 100.5% aceita");
        check(f, throwsIae(() -> SpecialityOdds.of(425, 250, 200, 100, 30, -5)), "speciality: chance negativa aceita");
        check(f, throwsIae(() -> SpecialityOdds.of(415, 250, 200, 100, 35)), "speciality: raridade faltando aceita");
        check(f, throwsIae(() -> SpecialityOdds.ofPercent(41.45, 25, 20, 10, 3, 0.55)), "speciality: duas casas aceitas");
        SpecialityOdds custom = SpecialityOdds.ofPercent(50, 30, 10, 5, 4.5, 0.5);
        check(f, custom.permille(SpecialityRarity.LEGENDARY) == 45 && custom.rarityAt(994) == SpecialityRarity.LEGENDARY
                && custom.rarityAt(995) == SpecialityRarity.BONER
                && custom.rarityAt(499) == SpecialityRarity.COMMON && custom.rarityAt(500) == SpecialityRarity.UNCOMMON,
                "speciality: distribuição configurada");

        // Catalogo: exatamente as 15 aprovadas, com a raridade aprovada, sem efeito e sorteaveis pelos dois corpos.
        Map<SpecialityRarity, List<String>> expected = new EnumMap<>(SpecialityRarity.class);
        expected.put(SpecialityRarity.COMMON, List.of("vanilla", "voyeurs", "exhibitionists", "foot_worshippers"));
        expected.put(SpecialityRarity.UNCOMMON, List.of("ropebound", "spitters"));
        expected.put(SpecialityRarity.RARE, List.of("showers_of_gold", "bootstompers", "dominants", "latex_royals"));
        expected.put(SpecialityRarity.EPIC, List.of("scatborn", "cuckholders", "humiliationists"));
        expected.put(SpecialityRarity.LEGENDARY, List.of("ultra_gooner"));
        expected.put(SpecialityRarity.BONER, List.of("island_guy"));
        check(f, Speciality.values().length == 15 && Specialities.CATALOG.all().size() == 15, "speciality: 15 no catálogo");
        for (SpecialityRarity r : all) {
            List<String> ids = Specialities.CATALOG.inRarity(r).stream().map(Speciality::id).toList();
            check(f, ids.equals(expected.get(r)), "speciality: catálogo de " + r.id() + " = " + ids);
            for (BirthTrait.Body body : BirthTrait.Body.values()) {
                check(f, !Specialities.CATALOG.eligible(r, body).isEmpty(), "speciality: " + r.id() + " vazia para " + body);
            }
        }
        for (Speciality sp : Speciality.values()) check(f, Speciality.byId(sp.id()) == sp, "speciality: id " + sp.id());
        check(f, Speciality.byId("") == null && Speciality.byId("futura") == null, "speciality: id vazio/desconhecido");

        // Amostra: a raridade sai com a chance aprovada mesmo com raridades vazias (sem redistribuicao).
        Specialities partial = Specialities.of(List.of(Speciality.VANILLA, Speciality.SCATBORN));
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        int samples = 200_000;
        int[] counts = new int[all.length];
        int empty = 0;
        for (int i = 0; i < samples; i++) {
            SpecialityRoll.Result r = SpecialityRoll.roll(random, odds, partial, BirthTrait.Body.FEMALE);
            counts[r.rarity().ordinal()]++;
            if (r.hasSpeciality()) {
                if (r.speciality().rarity() != r.rarity() || r.status() != SpecialityRoll.Status.ROLLED) {
                    f.add("speciality: Speciality fora da raridade sorteada " + r);
                    return;
                }
            } else {
                empty++;
                if (r.status() != SpecialityRoll.Status.NO_SPECIALITY_IN_RARITY
                        || r.rarity() == SpecialityRarity.COMMON || r.rarity() == SpecialityRarity.EPIC) {
                    f.add("speciality: resultado vazio inesperado " + r);
                    return;
                }
            }
        }
        for (SpecialityRarity r : all) {
            double exp = samples * approved[r.ordinal()] / 1000.0;
            double tolerance = 5 * Math.sqrt(exp) + 1; // ~5 desvios-padrao
            check(f, Math.abs(counts[r.ordinal()] - exp) <= tolerance,
                    "speciality: amostra de " + r.id() + " = " + counts[r.ordinal()] + " (esperado ~" + (int) exp + ")");
        }
        check(f, empty == samples - counts[SpecialityRarity.COMMON.ordinal()] - counts[SpecialityRarity.EPIC.ordinal()],
                "speciality: raridades vazias redistribuídas (" + empty + ")");
        SpecialityRoll.Result forcedEmpty = SpecialityRoll.pickWithin(random, SpecialityRarity.BONER, partial, BirthTrait.Body.MALE);
        check(f, !forcedEmpty.hasSpeciality() && forcedEmpty.rarity() == SpecialityRarity.BONER,
                "speciality: raridade vazia forçada não cai para outra");
        check(f, throwsIae(() -> Specialities.of(List.of(Speciality.VANILLA, Speciality.VANILLA))), "speciality: duplicata aceita");
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
        check(f, com.respectcraft.testgame.DefeatCounter.next(0) == 1, "derrotas: 0 -> 1");
        check(f, com.respectcraft.testgame.DefeatCounter.next(12) == 13, "derrotas: 12 -> 13");
        check(f, com.respectcraft.testgame.DefeatCounter.next(-5) == 1, "derrotas: negativo vira 0 antes do +1");
        check(f, com.respectcraft.testgame.DefeatCounter.next(Integer.MAX_VALUE) == Integer.MAX_VALUE,
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
        check(f, PlayerAnimationCommands.parseSequence("knockdown hands_and_knees@40 respectcraft:get_up", error)
                .map(fall::equals).orElse(false), "sequência: comando não reproduz a queda " + error);
        check(f, PlayerAnimationCommands.parseSequence("knockdown hands_and_knees", new StringBuilder()).isEmpty()
                && PlayerAnimationCommands.parseSequence("nada_disso", new StringBuilder()).isEmpty(),
                "sequência: comando aceitou passo inválido");
    }
}
