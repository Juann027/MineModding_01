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
            rouletteFailures(f);
            rouletteMachine(f);
            rouletteRandom(f);
            creatorOptions(f);
            needs(f);
            coomAndEffects(f);
            skills(f);
            finishers(f);
            codecs(f);
            sequences(f);
            defeats(f);
            specialities(f);
            rouletteWheel(f);
            multiRig(f);
            bossDefeats(f);
            finisherExecutions(f);
            rimulib(f);
            finisherFx(f);
            holoGeometry(f);
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
        RandomGenerator random = BirthRoulette.newRandom();
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
        CharacterProfile born = CharacterProfile.DEFAULT.withBirth(BirthRoulette.roll(BirthRoulette.newRandom(), BirthTrait.Body.MALE), BirthRoulette.FREE_REROLLS);
        check(f, born.canReroll() && born.rerolls() == 1, "roleta: um novo giro após nascer");
        CharacterProfile afterReroll = born.withBirth(BirthRoulette.roll(BirthRoulette.newRandom(), BirthTrait.Body.MALE), born.rerolls() - 1);
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

    /**
     * Etapa 4.8.2: falha inesperada no servidor vira resposta estruturada. Ordinais estaveis na rede, decisao
     * ERROR x ROLLED depois da excecao, requestId de ida e volta e nenhum giro duplicado ou gasto ao tentar de novo.
     */
    private static void rouletteFailures(List<String> f) {
        BirthRoulette.Outcome[] o = BirthRoulette.Outcome.values();
        check(f, o.length == 4 && o[0] == BirthRoulette.Outcome.ROLLED && o[1] == BirthRoulette.Outcome.NO_REROLLS
                && o[2] == BirthRoulette.Outcome.STALE && o[3] == BirthRoulette.Outcome.ERROR
                && BirthRoulette.Outcome.byOrdinal(3) == BirthRoulette.Outcome.ERROR,
                "roleta/erro: ordinais da resposta estaveis (ERROR no fim)");
        check(f, BirthRoulette.afterFailure(2, 2) == BirthRoulette.Outcome.ERROR, "roleta/erro: falha antes de gravar = ERROR");
        check(f, BirthRoulette.afterFailure(2, 3) == BirthRoulette.Outcome.ROLLED, "roleta/erro: falha depois de gravar = ROLLED");
        check(f, BirthRoulette.afterFailure(-1, -1) == BirthRoulette.Outcome.ERROR
                && BirthRoulette.afterFailure(2, -1) == BirthRoulette.Outcome.ERROR
                && BirthRoulette.afterFailure(-1, 3) == BirthRoulette.Outcome.ERROR, "roleta/erro: ficha ilegivel = ERROR");

        var reply = new com.respectcraft.network.GameplayNetworking.BirthRollReply(BirthRoulette.Outcome.ERROR, 4, 77);
        ByteBuf buf = Unpooled.buffer();
        com.respectcraft.network.GameplayNetworking.BirthRollReply.STREAM_CODEC.encode(buf, reply);
        var back = com.respectcraft.network.GameplayNetworking.BirthRollReply.STREAM_CODEC.decode(buf);
        check(f, back.equals(reply) && back.answers(77) && !back.answers(76), "roleta/erro: resposta ERROR com requestId na rede");
        var request = new com.respectcraft.network.GameplayNetworking.BirthRollRequest(true, 3, 77);
        ByteBuf rb = Unpooled.buffer();
        com.respectcraft.network.GameplayNetworking.BirthRollRequest.STREAM_CODEC.encode(rb, request);
        check(f, com.respectcraft.network.GameplayNetworking.BirthRollRequest.STREAM_CODEC.decode(rb).equals(request),
                "roleta/erro: pedido com requestId na rede");

        // ERROR = ficha intacta: tentar de novo e aceito e so entao gasta o novo giro (uma vez).
        CharacterProfile born = CharacterProfile.DEFAULT.withBirth(new BirthRoulette.Result(Speciality.VANILLA,
                new int[] {5, 5, 5, 5, 5, 5}, List.of()), BirthRoulette.FREE_REROLLS);
        check(f, BirthRoulette.afterFailure(born.rollCount(), born.rollCount()) == BirthRoulette.Outcome.ERROR
                && born.canReroll() && BirthRoulette.validate(born, true, born.rollCount()) == BirthRoulette.Outcome.ROLLED
                && BirthRoulette.rerollsAfter(born) == 0, "roleta/erro: depois de ERROR o novo giro continua e a nova tentativa vale");
        check(f, BirthRoulette.validate(CharacterProfile.DEFAULT, false, 0) == BirthRoulette.Outcome.ROLLED,
                "roleta/erro: primeiro giro com erro pode ser repetido");
        // Gravou e depois falhou: ROLLED; repetir o pedido sobre a ficha antiga e STALE (sem giro duplicado).
        CharacterProfile after = born.withBirth(new BirthRoulette.Result(Speciality.DOMINANTS, new int[] {4, 5, 5, 6, 4, 3},
                List.of()), BirthRoulette.rerollsAfter(born));
        check(f, BirthRoulette.afterFailure(born.rollCount(), after.rollCount()) == BirthRoulette.Outcome.ROLLED
                && BirthRoulette.validate(after, true, born.rollCount()) == BirthRoulette.Outcome.STALE
                && !after.canReroll(), "roleta/erro: giro gravado antes da falha não se repete");
    }

    /**
     * Etapa 4.8.4-C: maquina de estados da roleta comum a tela e a placa holografica (extraida da tela da 4.8.2):
     * so a resposta com o requestId do pedido encerra a espera, ERROR na hora, timeout separado, resposta atrasada
     * aplicada uma vez, a roda para na raridade da ficha e o novo giro so com giro sobrando.
     */
    private static void rouletteMachine(List<String> f) {
        var reply = (java.util.function.BiFunction<BirthRoulette.Outcome, int[], com.respectcraft.network.GameplayNetworking.BirthRollReply>)
                (o, a) -> new com.respectcraft.network.GameplayNetworking.BirthRollReply(o, a[0], a[1]);
        CharacterProfile fresh = CharacterProfile.DEFAULT;
        com.respectcraft.ui.RouletteMachine m = new com.respectcraft.ui.RouletteMachine(fresh);
        check(f, m.phase() == com.respectcraft.ui.RouletteMachine.Phase.IDLE && m.canSpin(fresh) && !m.canContinue(fresh),
                "roleta/máquina: ficha nova começa aguardando o giro");
        var req = m.spin(fresh, 41);
        check(f, req != null && !req.reroll() && req.expectedRollCount() == fresh.rollCount() && req.requestId() == 41
                && m.busy() && !m.canSpin(fresh) && m.spin(fresh, 42) == null, "roleta/máquina: pedido único com requestId");
        // resposta de outro pedido nao encerra a espera
        m.tick(fresh, reply.apply(BirthRoulette.Outcome.ROLLED, new int[] {1, 40}), null);
        check(f, m.phase() == com.respectcraft.ui.RouletteMachine.Phase.WAITING, "roleta/máquina: resposta de outro pedido ignorada");
        // erro do servidor: aviso na hora, ficha intacta, pode tentar de novo
        m.tick(fresh, reply.apply(BirthRoulette.Outcome.ERROR, new int[] {0, 41}), null);
        check(f, m.status() == com.respectcraft.ui.RouletteMachine.Status.ERROR && m.statusRequestId() == 41,
                "roleta/máquina: ERROR mostrado com o número do pedido");
        for (int i = 0; i < 40; i++) m.tick(fresh, null, null);
        check(f, m.phase() == com.respectcraft.ui.RouletteMachine.Phase.IDLE && m.canSpin(fresh), "roleta/máquina: depois do ERROR volta a girar");

        // giro com resultado: desacelera ate a raridade da ficha, revela os 6 atributos uma vez e libera Continuar
        CharacterProfile born = fresh.withBirth(new BirthRoulette.Result(Speciality.DOMINANTS, new int[] {4, 5, 6, 7, 3, 5},
                List.of()), BirthRoulette.FREE_REROLLS);
        m.spin(fresh, 43);
        m.tick(fresh, null, null);
        int[] revealed = new int[1], steps = new int[1];
        com.respectcraft.ui.RouletteMachine.Listener l = new com.respectcraft.ui.RouletteMachine.Listener() {
            @Override public void revealed(CharacterProfile p) { revealed[0]++; }
            @Override public void revealStep(int step) { steps[0]++; }
        };
        m.tick(born, reply.apply(BirthRoulette.Outcome.ROLLED, new int[] {born.rollCount(), 43}), l);
        for (int i = 0; i < 200 && m.phase() != com.respectcraft.ui.RouletteMachine.Phase.DONE; i++) m.tick(born, null, l);
        check(f, m.phase() == com.respectcraft.ui.RouletteMachine.Phase.DONE && revealed[0] == 1
                && steps[0] == CharacterAttribute.values().length && m.attributesShown() == CharacterAttribute.values().length
                && m.attributeValue(born, 3, 0F) == 7, "roleta/máquina: revelação única dos 6 atributos");
        check(f, com.respectcraft.profile.RouletteLayout.rarityAtPointer(m.angle(1F)) == Speciality.DOMINANTS.rarity(),
                "roleta/máquina: a roda para na raridade da ficha");
        check(f, m.canContinue(born) && m.canSpin(born), "roleta/máquina: resultado com Continuar e novo giro");
        var again = m.spin(born, 44);
        check(f, again != null && again.reroll() && again.expectedRollCount() == born.rollCount(), "roleta/máquina: novo giro pede reroll");
        // sem resposta: timeout separado, depois a resposta atrasada (ficha nova) e aplicada uma vez
        for (int i = 0; i <= com.respectcraft.ui.RouletteMachine.TIMEOUT_TICKS; i++) m.tick(born, null, null);
        check(f, m.status() == com.respectcraft.ui.RouletteMachine.Status.TIMEOUT && m.statusRequestId() == 44,
                "roleta/máquina: timeout com o número do pedido");
        for (int i = 0; i < 60; i++) m.tick(born, null, null);
        CharacterProfile late = born.withBirth(new BirthRoulette.Result(Speciality.VANILLA, new int[] {5, 5, 5, 5, 5, 5},
                List.of()), BirthRoulette.rerollsAfter(born));
        revealed[0] = 0;
        for (int i = 0; i < 200; i++) m.tick(late, null, l);
        check(f, revealed[0] == 1 && m.phase() == com.respectcraft.ui.RouletteMachine.Phase.DONE && !m.canSpin(late)
                && m.canContinue(late) && com.respectcraft.profile.RouletteLayout.rarityAtPointer(m.angle(1F)) == Speciality.VANILLA.rarity(),
                "roleta/máquina: resposta atrasada aplicada uma vez, sem novo giro");
        // reabrir com resultado: pronto, parado na raridade
        com.respectcraft.ui.RouletteMachine reopened = new com.respectcraft.ui.RouletteMachine(late);
        check(f, reopened.phase() == com.respectcraft.ui.RouletteMachine.Phase.DONE && reopened.canContinue(late) && !reopened.canSpin(late),
                "roleta/máquina: reabrir mostra o resultado salvo");
    }

    /**
     * Causa confirmada do erro da roleta: {@code RandomGenerator.of("L64X128MixRandom")} exige o modulo opcional
     * {@code jdk.random}. O gerador dos sorteios tem de vir do {@code java.base} e sortear normalmente.
     */
    private static void rouletteRandom(List<String> f) {
        RandomGenerator random = BirthRoulette.newRandom();
        check(f, random instanceof java.util.SplittableRandom && "java.base".equals(random.getClass().getModule().getName()),
                "roleta/rng: gerador do java.base (sem jdk.random)");
        BirthRoulette.Result r = BirthRoulette.roll(random, BirthTrait.Body.FEMALE, SpecialityOdds.DEFAULT, null);
        check(f, r.speciality() != null && r.attributes().length == CharacterAttribute.values().length
                && r.traits().size() == BirthTrait.PER_BIRTH, "roleta/rng: sorteio completo com o gerador portátil");
    }

    /** Etapa 4.8.4-C: Skin e Face sairam do criador; aparencias antigas continuam validas e passam a usar a skin da conta. */
    private static void creatorOptions(List<String> f) {
        var old = new com.respectcraft.character.CharacterAppearance(true, 1, 3, 2, 0, 1);
        var back = com.respectcraft.character.CharacterAppearance.fromPacked(old.pack());
        check(f, back != null && back.equals(old), "criador: aparência antiga com pele/rosto continua válida");
        check(f, !old.customSkinTone() && !old.customFace(), "criador: pele e rosto antigos não aparecem mais");
        var norm = old.withAccountSkinAndFace();
        check(f, norm.bodyType() == 1 && norm.hairStyle() == 3 && norm.hairColor() == 2 && norm.created()
                && norm.skinTone() == com.respectcraft.character.CharacterAppearance.SKIN_FROM_ACCOUNT
                && norm.face() == com.respectcraft.character.CharacterAppearance.FACE_FROM_ACCOUNT && norm.valid(),
                "criador: salvar mantém corpo e cabelo e usa a pele/rosto da conta");
    }

    /** Etapa 4.5: tracos de nascimento (sistema separado da origem, dos atributos e das necessidades). */
    private static void traits(List<String> f, CharacterProfile born, CharacterProfile afterReroll) {
        check(f, born.birthTraits().size() == BirthTrait.PER_BIRTH,
                "traços: nascimento sorteia " + BirthTrait.PER_BIRTH + " = " + born.traits());
        check(f, afterReroll.traits().size() == BirthTrait.PER_BIRTH,
                "traços: novo giro substitui (não soma) = " + afterReroll.traits());
        Map<BirthTrait, Integer> seen = new EnumMap<>(BirthTrait.class);
        RandomGenerator random = BirthRoulette.newRandom();
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
        RandomGenerator random = BirthRoulette.newRandom();
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
        RandomGenerator random = BirthRoulette.newRandom();
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
        RandomGenerator random = BirthRoulette.newRandom();
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

    private static boolean throwsIse(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** Etapa 4.1: sequencias de animacao (queda -> quatro apoios -> levantar) sem buraco entre os segmentos. */
    /**
     * Etapa 4.8: participantes com rigs diferentes. Cada lado resolve o proprio clip (rig do jogador, rig GeckoLib da
     * entidade ou parado); jogador x jogador fica igual; participante invalido e recusado; cancelamento por morte,
     * despawn, saida, dimensao e distancia; contador so de jogador.
     */
    private static void multiRig(List<String> f) {
        using(f, () -> {
            var P = com.respectcraft.finisher.FinisherParticipants.Kind.PLAYER;
            var M = com.respectcraft.finisher.FinisherParticipants.Kind.MOB;
            var B = com.respectcraft.finisher.FinisherParticipants.Kind.BOSS;
            var I = com.respectcraft.finisher.FinisherParticipants.Interaction.class;
            check(f, interaction(P, P).equals("PLAYER_VS_PLAYER") && interaction(P, M).equals("PLAYER_VS_MOB")
                    && interaction(P, B).equals("PLAYER_VS_BOSS") && interaction(M, P).equals("MOB_VS_PLAYER")
                    && interaction(B, P).equals("BOSS_VS_PLAYER"), "rigs: tipos de interação");
            check(f, interaction(M, M).isEmpty() && interaction(B, M).isEmpty() && interaction(M, B).isEmpty()
                    && interaction(B, B).isEmpty(), "rigs: sem jogador não há sessão (nada fica preso)");
            check(f, I.getEnumConstants().length == 5, "rigs: catálogo de interações");

            var ATT = com.respectcraft.finisher.FinisherRole.ATTACKER;
            var VIC = com.respectcraft.finisher.FinisherRole.VICTIM;
            // Id fictício de teste (nenhuma entidade registrada): as regras de vários rigs são puras e não precisam dela.
            net.minecraft.resources.ResourceLocation testMob = com.respectcraft.RespectCraft.id("test_rig_mob");
            // Finalizacoes atuais: jogador toca o mesmo clip de antes; mob sem clip fica parado; nenhuma tem clip de mob.
            for (FinisherDef def : Finishers.all()) {
                var a = com.respectcraft.finisher.FinisherParticipants.resolve(def, ATT, P, null, false);
                var v = com.respectcraft.finisher.FinisherParticipants.resolve(def, VIC, P, null, false);
                check(f, a.valid() && a.clip().get() instanceof com.respectcraft.finisher.FinisherParticipants.PlayerClip pa
                        && pa.def() == def.attacker(), "rigs: executor jogador mantém o clip " + def.id());
                check(f, v.valid() && v.clip().get() instanceof com.respectcraft.finisher.FinisherParticipants.PlayerClip pv
                        && pv.def() == def.victim(), "rigs: vítima jogador mantém o clip " + def.id());
                var mob = com.respectcraft.finisher.FinisherParticipants.resolve(def, VIC, M, testMob, true);
                check(f, mob.valid() && mob.clip().get() instanceof com.respectcraft.finisher.FinisherParticipants.Hold h
                        && h.durationTicks() == def.durationTicks(), "rigs: mob sem clip fica parado " + def.id());
                check(f, def.entityClips().isEmpty(), "rigs: nenhuma finalizacao inventa clip de mob " + def.id());
            }
            var auto = com.respectcraft.finisher.EntityFinisherClip.Alignment.AUTO;
            check(f, Math.abs(com.respectcraft.finisher.FinisherParticipants.stageDistance(auto, 0.6, 0.6) - 1.15) < 1e-9,
                    "rigs: palco jogador x jogador continua em 1,15");
            check(f, Math.abs(com.respectcraft.finisher.FinisherParticipants.stageDistance(auto, 0.6, 1.4) - 1.25) < 1e-9,
                    "rigs: rig largo afasta a vítima pelas caixas de colisão");
            var fixed = new com.respectcraft.finisher.EntityFinisherClip.Alignment(2.0, 0.5, 90F);
            check(f, com.respectcraft.finisher.FinisherParticipants.stageDistance(fixed, 0.6, 1.4) == 2.0,
                    "rigs: alinhamento do rig define a distância");
            net.minecraft.world.phys.Vec3 front = com.respectcraft.finisher.FinisherParticipants.stage(
                    net.minecraft.world.phys.Vec3.ZERO, 0F, 1.0, 0.0);
            net.minecraft.world.phys.Vec3 right = com.respectcraft.finisher.FinisherParticipants.stage(
                    net.minecraft.world.phys.Vec3.ZERO, 0F, 1.0, 1.0);
            check(f, Math.abs(front.z - 1) < 1e-6 && Math.abs(front.x) < 1e-6, "rigs: vítima à frente do executor");
            check(f, Math.abs(right.x + 1) < 1e-6 && Math.abs(right.z - 1) < 1e-6, "rigs: deslocamento lateral à direita");

            // Clip de entidade de teste (NAO registrado, nunca tocado): so valida a resolucao por tipo e papel.
            FinisherDef base = Finishers.TAKEDOWN;
            var clip = new com.respectcraft.finisher.EntityFinisherClip(testMob, ATT, "body", "selftest_only",
                    base.durationTicks(), fixed);
            FinisherDef withMob = base.withEntityClip(clip);
            check(f, Finishers.get(base.id()) == base && base.entityClips().isEmpty(), "rigs: cópia não altera o catálogo");
            var geo = com.respectcraft.finisher.FinisherParticipants.resolve(withMob, ATT, M, testMob, true);
            check(f, geo.valid() && geo.clip().get() instanceof com.respectcraft.finisher.FinisherParticipants.EntityClip ec
                    && ec.clip() == clip, "rigs: entidade GeckoLib recebe o clip do próprio rig");
            var boss = com.respectcraft.finisher.FinisherParticipants.resolve(withMob, ATT, B, testMob, true);
            check(f, boss.valid() && boss.clip().get() instanceof com.respectcraft.finisher.FinisherParticipants.EntityClip,
                    "rigs: chefe usa o mesmo caminho de clip por tipo");
            var noRig = com.respectcraft.finisher.FinisherParticipants.resolve(withMob, ATT, M, testMob, false);
            check(f, !noRig.valid() && noRig.problem() != null, "rigs: clip sem GeoEntity é recusado");
            var otherRole = com.respectcraft.finisher.FinisherParticipants.resolve(withMob, VIC, M, testMob, true);
            check(f, otherRole.valid() && otherRole.clip().get() instanceof com.respectcraft.finisher.FinisherParticipants.Hold,
                    "rigs: clip vale só no próprio papel");
            var otherType = com.respectcraft.finisher.FinisherParticipants.resolve(withMob, ATT, M,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("zombie"), true);
            check(f, otherType.clip().orElse(null) instanceof com.respectcraft.finisher.FinisherParticipants.Hold,
                    "rigs: clip vale só no próprio tipo de entidade");
            var hold = new com.respectcraft.finisher.FinisherParticipants.Hold(1);
            var playerClip = a0(base);
            net.minecraft.resources.ResourceLocation zombie = net.minecraft.resources.ResourceLocation.withDefaultNamespace("zombie");
            check(f, com.respectcraft.finisher.FinisherParticipants.yawOffset(geo.clip().get()) == 90F
                    && com.respectcraft.finisher.FinisherParticipants.alignment(geo.clip().get(), testMob, hold, zombie) == fixed,
                    "rigs: alinhamento do clip do executor vence");
            // Palco por tipo (mob sem clip): o tipo fictício a 1,9; mob sem palco = automatico. O registro do palco de
            // teste é feito uma vez (o selftest pode rodar várias vezes) e nunca casa com uma entidade real.
            var testStaging = com.respectcraft.finisher.FinisherParticipants.staging(testMob).orElseGet(() ->
                    com.respectcraft.finisher.FinisherParticipants.registerStaging(testMob,
                            new com.respectcraft.finisher.EntityFinisherClip.Alignment(1.9, 0.0, 0F)));
            check(f, com.respectcraft.finisher.FinisherParticipants.staging(testMob).orElse(null) == testStaging
                    && com.respectcraft.finisher.FinisherParticipants.alignment(playerClip, null, hold, testMob) == testStaging
                    && com.respectcraft.finisher.FinisherParticipants.alignment(hold, testMob, playerClip, null) == testStaging,
                    "rigs: palco por tipo nos dois papéis");
            check(f, com.respectcraft.finisher.FinisherParticipants.stageDistance(testStaging, 0.6, 1.4) == 1.9,
                    "rigs: palco por tipo a 1,9 blocos");
            check(f, throwsIse(() -> com.respectcraft.finisher.FinisherParticipants.registerStaging(testMob,
                    com.respectcraft.finisher.EntityFinisherClip.Alignment.AUTO)), "rigs: palco duplicado é recusado");
            check(f, com.respectcraft.finisher.FinisherParticipants.alignment(playerClip, null, hold, zombie)
                    == com.respectcraft.finisher.EntityFinisherClip.Alignment.AUTO
                    && com.respectcraft.finisher.FinisherParticipants.alignment(playerClip, null, playerClip, null)
                    == com.respectcraft.finisher.EntityFinisherClip.Alignment.AUTO, "rigs: sem palco próprio é automático");
            check(f, throwsIae(() -> base.withEntityClip(new com.respectcraft.finisher.EntityFinisherClip(testMob, VIC, "body",
                    "x", base.durationTicks() + 1))), "rigs: clip com duração diferente da cena é recusado");
            check(f, throwsIae(() -> withMob.withEntityClip(clip)), "rigs: clip repetido é recusado");
            check(f, throwsIae(() -> new com.respectcraft.finisher.EntityFinisherClip(testMob, ATT, " ", "x", 10)),
                    "rigs: controlador vazio é recusado");
            check(f, throwsIae(() -> new com.respectcraft.finisher.EntityFinisherClip.Alignment(9.0, 0, 0)),
                    "rigs: distância de palco absurda é recusada");
            // Animacao de jogador ausente do catalogo: a resolucao recusa (a cena nao comeca).
            PlayerAnimationDef ghost = PlayerAnimationDef.builder(com.respectcraft.RespectCraft.id("selftest/unregistered"),
                    PlayerAnimations.PLAYER_FILE, "animation.selftest.unregistered", base.durationTicks()).build();
            FinisherDef missing = new FinisherDef(com.respectcraft.RespectCraft.id("selftest/missing"), ghost, base.victim(),
                    base.camera(), null, 1);
            var miss = com.respectcraft.finisher.FinisherParticipants.resolve(missing, ATT, P, null, false);
            check(f, !miss.valid() && miss.problem() != null, "rigs: clip de jogador ausente é recusado");
            check(f, Finishers.get(missing.id()) == null, "rigs: finalização de teste não registrada");

            // Contador de derrotas: so jogador.
            check(f, com.respectcraft.testgame.FinisherRules.countsDefeat(P)
                    && !com.respectcraft.testgame.FinisherRules.countsDefeat(M)
                    && !com.respectcraft.testgame.FinisherRules.countsDefeat(B), "rigs: contador só para jogador");
            // Cancelamento e limpeza: morte/despawn/saida (ausente), dimensao e distancia cancelam; o resto segue.
            check(f, com.respectcraft.testgame.FinisherRules.abortReason(true, true, true, 1.3) == null, "rigs: cena normal segue");
            check(f, com.respectcraft.testgame.FinisherRules.abortReason(false, true, true, 1.3) != null, "rigs: executor ausente cancela");
            check(f, com.respectcraft.testgame.FinisherRules.abortReason(true, false, true, 1.3) != null, "rigs: vítima ausente cancela");
            check(f, com.respectcraft.testgame.FinisherRules.abortReason(true, true, false, 1.3) != null, "rigs: troca de dimensão cancela");
            check(f, com.respectcraft.testgame.FinisherRules.abortReason(true, true, true, 16.5) != null, "rigs: distância cancela");
        });
    }

    private static com.respectcraft.finisher.FinisherParticipants.Clip a0(FinisherDef def) {
        return new com.respectcraft.finisher.FinisherParticipants.PlayerClip(def.attacker());
    }

    private static String interaction(com.respectcraft.finisher.FinisherParticipants.Kind a,
                                      com.respectcraft.finisher.FinisherParticipants.Kind b) {
        return com.respectcraft.finisher.FinisherParticipants.interaction(a, b).map(Enum::name).orElse("");
    }

    /**
     * Etapa 4.8 (revista na 4.10.1): derrota de chefe anunciada no maximo uma vez; so a morte pelo golpe final da
     * execucao, com o credito gravado no mesmo tick, e FINISHER. Espada depois de uma cena, ou credito velho, e NORMAL.
     */
    private static void bossDefeats(List<String> f) {
        java.util.UUID finisherPlayer = new java.util.UUID(1, 1);
        java.util.UUID killer = new java.util.UUID(2, 2);
        var credit = Optional.of(new com.respectcraft.finisher.BossDefeats.Credit(finisherPlayer, Finishers.TAKEDOWN_ID, 1000));
        var executed = com.respectcraft.finisher.BossDefeats.decide(false, credit, 1000, Optional.of(finisherPlayer), true);
        check(f, executed.isPresent() && executed.get().method() == com.respectcraft.finisher.BossDefeatEvent.Method.FINISHER
                && executed.get().credited().equals(Optional.of(finisherPlayer))
                && executed.get().finisher().equals(Optional.of(Finishers.TAKEDOWN_ID)), "chefe: morte pela execução credita o executor");
        var sword = com.respectcraft.finisher.BossDefeats.decide(false, credit, 1000, Optional.of(killer), false);
        check(f, sword.isPresent() && sword.get().method() == com.respectcraft.finisher.BossDefeatEvent.Method.NORMAL
                && sword.get().credited().equals(Optional.of(killer)) && sword.get().finisher().isEmpty(),
                "chefe: morte por dano comum é derrota comum (mesmo com crédito)");
        var stale = com.respectcraft.finisher.BossDefeats.decide(false, credit, 1001, Optional.of(killer), true);
        check(f, stale.isPresent() && stale.get().method() == com.respectcraft.finisher.BossDefeatEvent.Method.NORMAL,
                "chefe: crédito de outro tick não conta");
        var noCredit = com.respectcraft.finisher.BossDefeats.decide(false, Optional.empty(), 5, Optional.of(killer), true);
        check(f, noCredit.isPresent() && noCredit.get().method() == com.respectcraft.finisher.BossDefeatEvent.Method.NORMAL,
                "chefe: execução sem crédito (finalização comum) não é FINISHER");
        var normal = com.respectcraft.finisher.BossDefeats.decide(false, Optional.empty(), 5, Optional.empty(), false);
        check(f, normal.isPresent() && normal.get().method() == com.respectcraft.finisher.BossDefeatEvent.Method.NORMAL
                && normal.get().credited().isEmpty(), "chefe: morte sem jogador não credita ninguém");
        // Sem disparo duplo: depois de anunciado, nenhuma outra decisao (nem por finalizacao, nem comum).
        boolean posted = false;
        int fired = 0;
        for (int i = 0; i < 3; i++) {
            if (com.respectcraft.finisher.BossDefeats.decide(posted, credit, 1000, Optional.of(killer), true).isPresent()) {
                fired++;
                posted = true;
            }
        }
        check(f, fired == 1, "chefe: derrota anunciada uma única vez");
    }

    /**
     * Etapa 4.10.1: execucoes reais e regra de chefes. Finalizacoes comuns nunca executam chefes; uma exclusiva so o
     * chefe designado. As exclusivas daqui sao FIXTURES isoladas (nunca registradas no catalogo).
     */
    private static void finisherExecutions(List<String> f) {
        var P = com.respectcraft.finisher.FinisherParticipants.Kind.PLAYER;
        var M = com.respectcraft.finisher.FinisherParticipants.Kind.MOB;
        var B = com.respectcraft.finisher.FinisherParticipants.Kind.BOSS;
        var ELIGIBLE = com.respectcraft.finisher.FinisherTargeting.Verdict.ELIGIBLE;
        var NEEDS = com.respectcraft.finisher.FinisherTargeting.Verdict.BOSS_REQUIRES_EXCLUSIVE;
        var OTHER = com.respectcraft.finisher.FinisherTargeting.Verdict.EXCLUSIVE_OTHER_TARGET;
        var REGULAR = com.respectcraft.finisher.FinisherTargeting.REGULAR;
        var wither = net.minecraft.resources.ResourceLocation.withDefaultNamespace("wither");
        var dragon = net.minecraft.resources.ResourceLocation.withDefaultNamespace("ender_dragon");
        var zombie = net.minecraft.resources.ResourceLocation.withDefaultNamespace("zombie");
        var bossA = com.respectcraft.RespectCraft.id("selftest_boss_a");
        var bossB = com.respectcraft.RespectCraft.id("selftest_boss_b");

        // Regra de alvos.
        check(f, REGULAR.regular() && REGULAR.check(P, null) == ELIGIBLE && REGULAR.check(M, zombie) == ELIGIBLE,
                "execução: comum vale para jogador e mob");
        check(f, REGULAR.check(B, wither) == NEEDS && REGULAR.check(B, dragon) == NEEDS && REGULAR.check(B, bossA) == NEEDS,
                "execução: comum é recusada contra qualquer chefe");
        var onlyA = com.respectcraft.finisher.FinisherTargeting.exclusiveTo(bossA);
        check(f, !onlyA.regular() && onlyA.check(B, bossA) == ELIGIBLE, "execução: exclusiva vale para o chefe dela");
        check(f, onlyA.check(B, bossB) == OTHER && onlyA.check(B, wither) == OTHER, "execução: exclusiva de A não executa B");
        check(f, onlyA.check(M, bossA) == OTHER && onlyA.check(M, zombie) == OTHER && onlyA.check(P, null) == OTHER,
                "execução: exclusiva não executa mob nem jogador");
        check(f, throwsNpe(() -> com.respectcraft.finisher.FinisherTargeting.exclusiveTo(null)), "execução: exclusiva sem chefe é recusada");

        // Catalogo de producao: tudo comum; Wither e Dragao do Fim recusados; nenhuma exclusiva registrada.
        for (FinisherDef def : Finishers.all()) {
            check(f, def.targeting().regular(), "execução: " + def.id() + " é comum");
            check(f, !def.targeting().check(B, wither).eligible() && !def.targeting().check(B, dragon).eligible(),
                    "execução: " + def.id() + " recusada contra Wither e Dragão do Fim");
        }
        var random = com.respectcraft.profile.BirthRoulette.newRandom();
        check(f, Finishers.pickFor(B, wither, s -> true, random).isEmpty() && Finishers.pickFor(B, dragon, s -> true, random).isEmpty(),
                "execução: nenhum sorteio contra chefe sem exclusiva");
        check(f, Finishers.pickFor(M, zombie, s -> true, random).isPresent() && Finishers.pickFor(P, null, s -> false, random).isPresent(),
                "execução: sorteio comum continua contra mob e jogador");

        // Extensao futura sem reescrever o motor: fixtures exclusivas de A e B (B travada por habilidade).
        FinisherDef base = Finishers.TAKEDOWN;
        check(f, base.withTargeting(null).targeting() == REGULAR, "execução: regra nula vira comum");
        FinisherDef exA = new FinisherDef(com.respectcraft.RespectCraft.id("selftest/exclusive_a"), base.attacker(), base.victim(),
                base.camera(), null, 1).withTargeting(onlyA);
        var lock = com.respectcraft.RespectCraft.id("selftest/unlock_b");
        FinisherDef exB = new FinisherDef(com.respectcraft.RespectCraft.id("selftest/exclusive_b"), base.attacker(), base.victim(),
                base.camera(), lock, 1).withTargeting(com.respectcraft.finisher.FinisherTargeting.exclusiveTo(bossB));
        check(f, Finishers.get(exA.id()) == null && Finishers.get(exB.id()) == null && Finishers.TAKEDOWN.targeting() == REGULAR,
                "execução: fixtures não entram no catálogo");
        List<FinisherDef> catalog = new ArrayList<>(Finishers.all());
        catalog.add(exA);
        catalog.add(exB);
        boolean regularOnly = true;
        for (int i = 0; i < 200; i++) {
            FinisherDef picked = Finishers.pickFor(catalog, M, zombie, s -> true, random).orElse(null);
            FinisherDef pickedPlayer = Finishers.pickFor(catalog, P, null, s -> true, random).orElse(null);
            if (picked == null || !picked.targeting().regular() || pickedPlayer == null || !pickedPlayer.targeting().regular()) regularOnly = false;
        }
        check(f, regularOnly, "execução: exclusiva nunca é sorteada contra mob ou jogador");
        check(f, Finishers.pickFor(catalog, B, bossA, s -> false, random).orElse(null) == exA, "execução: chefe A sorteia só a exclusiva de A");
        check(f, Finishers.pickFor(catalog, B, bossB, s -> false, random).isEmpty(), "execução: exclusiva travada não é sorteada");
        check(f, Finishers.pickFor(catalog, B, bossB, lock::equals, random).orElse(null) == exB, "execução: exclusiva liberada de B");
        check(f, Finishers.pickFor(catalog, B, wither, s -> true, random).isEmpty(), "execução: chefe sem exclusiva fica sem finalização");
        bossRegistry(f, catalog, exA, exB, bossA, bossB, lock);

        // Golpe final: dano finito, acima de vida + absorcao; resultado so com morte confirmada.
        float[][] cases = {{4, 0, 20}, {1, 16, 20}, {300, 0, 300}, {0, 0, 0}, {1.0E9F, 1.0E9F, 1.0E9F}};
        boolean damageOk = true;
        for (float[] c : cases) {
            float d = com.respectcraft.finisher.FinisherExecution.damageFor(c[0], c[1], c[2]);
            if (!Float.isFinite(d) || d <= 0 || (c[0] + c[1] < 1.0E5F && d <= c[0] + c[1])) damageOk = false;
        }
        check(f, damageOk, "execução: dano do golpe final");
        var success = com.respectcraft.finisher.FinisherExecution.outcome(true, true);
        var prevented = com.respectcraft.finisher.FinisherExecution.outcome(false, false);
        var survived = com.respectcraft.finisher.FinisherExecution.outcome(true, false);
        check(f, success.killConfirmed() && success.outcome() == com.respectcraft.finisher.FinisherExecution.Outcome.SUCCESS,
                "execução: morte confirmada = SUCCESS");
        check(f, !prevented.killConfirmed() && prevented.outcome() == com.respectcraft.finisher.FinisherExecution.Outcome.FAILED
                && !survived.killConfirmed() && survived.outcome() == com.respectcraft.finisher.FinisherExecution.Outcome.FAILED
                && !prevented.detail().equals(survived.detail()), "execução: dano impedido e totem = FAILED (motivos distintos)");
        var aborted = new com.respectcraft.finisher.FinisherSessionEvent.Aborted(null, null, base, null, "test");
        var completed = new com.respectcraft.finisher.FinisherSessionEvent.Completed(null, null, base, null, survived);
        check(f, aborted.getOutcome() == com.respectcraft.finisher.FinisherExecution.Outcome.CANCELLED
                && completed.getOutcome() == com.respectcraft.finisher.FinisherExecution.Outcome.FAILED && !completed.killConfirmed(),
                "execução: cancelada e cena concluída sem morte nunca são SUCCESS");

        // Mundo (servidor rodando): tags de chefe e o tipo de dano com as regras de morte normais.
        check(f, com.respectcraft.finisher.FinisherParticipants.isBossType(net.minecraft.world.entity.EntityType.WITHER)
                && com.respectcraft.finisher.FinisherParticipants.isBossType(net.minecraft.world.entity.EntityType.ENDER_DRAGON)
                && !com.respectcraft.finisher.FinisherParticipants.isBossType(net.minecraft.world.entity.EntityType.ZOMBIE),
                "execução: Wither e Dragão do Fim são chefes (c:bosses), zumbi não");
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            var type = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getHolder(com.respectcraft.finisher.FinisherExecution.DAMAGE_TYPE);
            check(f, type.isPresent(), "execução: tipo de dano registrado");
            type.ifPresent(h -> {
                check(f, h.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) && h.is(net.minecraft.tags.DamageTypeTags.NO_KNOCKBACK)
                        && h.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN), "execução: dano ignora armadura, recarga e repulsão");
                check(f, !h.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY), "execução: dano respeita totem e invulnerabilidade");
            });
        }
    }

    /**
     * Etapa 4.11: fichas de chefe ({@code boss.Bosses}) com fixtures que nunca entram no registro. Validacao contra o
     * catalogo, execucao so pela exclusiva listada, condicoes (vida, habilidade, progressao ausente) e evento de recusa.
     */
    private static void bossRegistry(List<String> f, List<FinisherDef> catalog, FinisherDef exA, FinisherDef exB,
                                     net.minecraft.resources.ResourceLocation bossA, net.minecraft.resources.ResourceLocation bossB,
                                     net.minecraft.resources.ResourceLocation lock) {
        java.util.function.Function<net.minecraft.resources.ResourceLocation, FinisherDef> lookup =
                id -> catalog.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
        var profileA = com.respectcraft.boss.BossProfile.of(bossA).withExclusiveFinisher(exA.id())
                .withCondition(new com.respectcraft.boss.BossCondition.HealthBelow(0.10F));
        var profileB = com.respectcraft.boss.BossProfile.of(bossB).withExclusiveFinisher(exB.id())
                .withCondition(new com.respectcraft.boss.BossCondition.RequiresSkill(lock))
                .withCondition(new com.respectcraft.boss.BossCondition.RequiresProgress(com.respectcraft.RespectCraft.id("selftest/progress_b")));
        check(f, com.respectcraft.boss.Bosses.validate(profileA, lookup).isEmpty()
                && com.respectcraft.boss.Bosses.validate(profileB, lookup).isEmpty(), "chefes: fichas de teste válidas");
        check(f, !com.respectcraft.boss.Bosses.validate(com.respectcraft.boss.BossProfile.of(bossA).withExclusiveFinisher(exB.id()), lookup).isEmpty(),
                "chefes: exclusiva de outro chefe recusada na ficha");
        check(f, !com.respectcraft.boss.Bosses.validate(com.respectcraft.boss.BossProfile.of(bossA)
                .withExclusiveFinisher(Finishers.TAKEDOWN_ID), lookup).isEmpty(), "chefes: finalização comum recusada na ficha");
        check(f, !com.respectcraft.boss.Bosses.validate(com.respectcraft.boss.BossProfile.of(bossA)
                .withExclusiveFinisher(com.respectcraft.RespectCraft.id("selftest/missing")), lookup).isEmpty(), "chefes: finalização ausente recusada");
        check(f, !com.respectcraft.boss.Bosses.validate(profileA.withExclusiveFinisher(exA.id()), lookup).isEmpty(),
                "chefes: exclusiva repetida recusada");
        check(f, com.respectcraft.boss.Bosses.get(bossA).isEmpty() && com.respectcraft.boss.Bosses.all().isEmpty(),
                "chefes: registro real vazio (nenhum chefe definitivo)");
        check(f, throwsIllegalArgument(() -> new com.respectcraft.boss.BossCondition.HealthBelow(0F))
                && throwsIllegalArgument(() -> new com.respectcraft.boss.BossCondition.HealthBelow(1.5F)), "chefes: fração de vida inválida");

        var low = new com.respectcraft.boss.BossCondition.Context(0.05F, s -> false, k -> false);
        var high = new com.respectcraft.boss.BossCondition.Context(0.20F, s -> true, k -> true);
        var skilled = new com.respectcraft.boss.BossCondition.Context(0.20F, lock::equals, k -> false);
        var full = new com.respectcraft.boss.BossCondition.Context(0.20F, lock::equals, k -> true);
        check(f, com.respectcraft.boss.Bosses.checkExecution(java.util.Optional.of(profileA), exA, low).isEmpty(),
                "chefes: A executável com vida abaixo de 10%");
        check(f, com.respectcraft.boss.Bosses.checkExecution(java.util.Optional.of(profileA), exA, high).isPresent(),
                "chefes: A recusado com vida acima de 10%");
        check(f, com.respectcraft.boss.Bosses.checkExecution(java.util.Optional.empty(), exA, low).isPresent(),
                "chefes: chefe sem ficha nunca é executado");
        check(f, com.respectcraft.boss.Bosses.checkExecution(java.util.Optional.of(profileA), exB, low).isPresent(),
                "chefes: exclusiva fora da ficha recusada");
        check(f, com.respectcraft.boss.Bosses.checkExecution(java.util.Optional.of(profileB), exB, low).isPresent()
                && com.respectcraft.boss.Bosses.checkExecution(java.util.Optional.of(profileB), exB, skilled).isPresent()
                && com.respectcraft.boss.Bosses.checkExecution(java.util.Optional.of(profileB), exB, full).isEmpty(),
                "chefes: B exige habilidade e progressão (todas as condições)");

        var event = new com.respectcraft.boss.BossExecutionCheckEvent(null, null, profileA, exA);
        check(f, event.getDenial().isEmpty(), "chefes: evento sem ouvinte não recusa");
        event.deny("first");
        event.deny("second");
        check(f, event.getDenial().orElse("").equals("first"), "chefes: primeira recusa do evento vale");
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean throwsNpe(Runnable action) {
        try {
            action.run();
            return false;
        } catch (NullPointerException e) {
            return true;
        }
    }

    /**
     * Etapa 4.8.1: menu holografico. O clique volta pelo mesmo caminho do desenho (inversa de projecao * visao *
     * modelo): o plano de referencia cai pixel a pixel na tela mesmo com o olho deslocado (paralaxe), um painel
     * inclinado devolve o ponto desenhado, e raios fora do painel, atras do olho ou paralelos a ele sao recusados.
     */
    private static void holoGeometry(List<String> f) {
        float w = 427F, h = 240F;
        float d = com.respectcraft.ui.HoloGeometry.referenceDistance(h, 40F);
        org.joml.Matrix4f proj = new org.joml.Matrix4f(), view = new org.joml.Matrix4f();
        org.joml.Matrix4f layout = com.respectcraft.ui.HoloGeometry.layoutToMenu(new org.joml.Matrix4f(), w, h, 1F);
        org.joml.Matrix4f flat = com.respectcraft.ui.HoloGeometry.panel(new org.joml.Matrix4f(), 100F, 50F, 200F, 100F,
                0.5F, 0.5F, 0F, 0F, 0F, 1F, 1F);
        org.joml.Vector3f out = new org.joml.Vector3f();
        for (float[] eye : new float[][]{{0F, 0F}, {12F, -7F}}) {
            com.respectcraft.ui.HoloGeometry.offAxis(proj, w, h, d, eye[0], eye[1], 10F, 4000F);
            com.respectcraft.ui.HoloGeometry.view(view, d, eye[0], eye[1], 0F, 0F);
            org.joml.Matrix4f pvm = new org.joml.Matrix4f(proj).mul(view).mul(layout).mul(flat);
            org.joml.Matrix4f inv = new org.joml.Matrix4f(pvm).invert();
            boolean ok = com.respectcraft.ui.HoloGeometry.hit(inv, com.respectcraft.ui.HoloGeometry.guiToNdcX(150F, w),
                    com.respectcraft.ui.HoloGeometry.guiToNdcY(70F, h), 200F, 100F, out);
            check(f, ok && Math.abs(out.x - 50F) < 0.01F && Math.abs(out.y - 20F) < 0.01F,
                    "holo: ponto da tela -> painel no plano de referencia (olho " + eye[0] + "," + eye[1] + ") = " + out);
            ok = com.respectcraft.ui.HoloGeometry.project(pvm, 0F, 0F, 0F, out);
            check(f, ok && Math.abs(com.respectcraft.ui.HoloGeometry.ndcToGuiX(out.x, w) - 100F) < 0.01F
                    && Math.abs(com.respectcraft.ui.HoloGeometry.ndcToGuiY(out.y, h) - 50F) < 0.01F,
                    "holo: canto do painel cai no pixel do layout (olho " + eye[0] + "," + eye[1] + ")");
        }
        // Painel inclinado e mais perto, cena girada: desenhar e clicar no mesmo ponto local.
        com.respectcraft.ui.HoloGeometry.offAxis(proj, w, h, d, 5F, 3F, 10F, 4000F);
        com.respectcraft.ui.HoloGeometry.view(view, d, 5F, 3F, 7F, -4F);
        org.joml.Matrix4f tilted = com.respectcraft.ui.HoloGeometry.panel(new org.joml.Matrix4f(), 20F, 30F, 90F, 180F,
                1F, 0.5F, 14F, 16F, -5F, 1F, 1F);
        org.joml.Matrix4f pvm = new org.joml.Matrix4f(proj).mul(view).mul(layout).mul(tilted);
        org.joml.Matrix4f inv = new org.joml.Matrix4f(pvm).invert();
        boolean drawn = com.respectcraft.ui.HoloGeometry.project(pvm, 30F, 40F, 0F, out);
        boolean back = drawn && com.respectcraft.ui.HoloGeometry.hit(inv, out.x, out.y, 90F, 180F, out);
        check(f, back && Math.abs(out.x - 30F) < 0.05F && Math.abs(out.y - 40F) < 0.05F, "holo: painel inclinado ida e volta = " + out);
        drawn = com.respectcraft.ui.HoloGeometry.project(pvm, 120F, 40F, 0F, out);
        check(f, drawn && !com.respectcraft.ui.HoloGeometry.hit(inv, out.x, out.y, 90F, 180F, new org.joml.Vector3f())
                && com.respectcraft.ui.HoloGeometry.intersect(inv, out.x, out.y, new org.joml.Vector3f()),
                "holo: ponto fora do painel recusado (arrasto ainda acompanha o plano)");
        // Painel atras do olho e painel de perfil (raio paralelo) nao recebem clique.
        com.respectcraft.ui.HoloGeometry.offAxis(proj, w, h, d, 0F, 0F, 10F, 4000F);
        com.respectcraft.ui.HoloGeometry.view(view, d, 0F, 0F, 0F, 0F);
        org.joml.Matrix4f behind = com.respectcraft.ui.HoloGeometry.panel(new org.joml.Matrix4f(), 113.5F, 70F, 200F, 100F,
                0.5F, 0.5F, d + 50F, 0F, 0F, 1F, 1F);
        inv = new org.joml.Matrix4f(proj).mul(view).mul(layout).mul(behind).invert();
        check(f, !com.respectcraft.ui.HoloGeometry.hit(inv, 0F, 0F, 200F, 100F, out), "holo: painel atras do olho recusado");
        org.joml.Matrix4f edge = com.respectcraft.ui.HoloGeometry.panel(new org.joml.Matrix4f(), 113.5F, 70F, 200F, 100F,
                0.5F, 0.5F, 0F, 90F, 0F, 1F, 1F);
        inv = new org.joml.Matrix4f(proj).mul(view).mul(layout).mul(edge).invert();
        check(f, !com.respectcraft.ui.HoloGeometry.hit(inv, 0F, 0F, 200F, 100F, out), "holo: painel de perfil (raio paralelo) recusado");
        // Ordem de profundidade: o painel mais perto tem t menor.
        org.joml.Matrix4f near = com.respectcraft.ui.HoloGeometry.panel(new org.joml.Matrix4f(), 113.5F, 70F, 200F, 100F,
                0.5F, 0.5F, 40F, 0F, 0F, 1F, 1F);
        org.joml.Vector3f a = new org.joml.Vector3f(), b = new org.joml.Vector3f();
        boolean hitNear = com.respectcraft.ui.HoloGeometry.hit(new org.joml.Matrix4f(proj).mul(view).mul(layout).mul(near).invert(),
                0F, 0F, 200F, 100F, a);
        boolean hitFlat = com.respectcraft.ui.HoloGeometry.hit(new org.joml.Matrix4f(proj).mul(view).mul(layout)
                .mul(com.respectcraft.ui.HoloGeometry.panel(new org.joml.Matrix4f(), 113.5F, 70F, 200F, 100F, 0.5F, 0.5F,
                        0F, 0F, 0F, 1F, 1F)).invert(), 0F, 0F, 200F, 100F, b);
        check(f, hitNear && hitFlat && a.z < b.z, "holo: painel mais perto vence a sobreposicao");
        holoWorld(f);
        holoInput(f);
    }

    /**
     * Etapa 4.8.4-A: holograma no mundo. A matriz layout -> mundo leva o olho do arco para a ancora e a frente para o
     * yaw; o raio da mira acha o mesmo ponto local que o desenho, de qualquer posicao da camera (a ancora nao se mexe);
     * a escolha da posicao encolhe a distancia diante de uma parede, gira o arco quando a frente esta bloqueada e
     * avisa quando nada cabe.
     */
    /**
     * Etapa 4.8.4-B: dono da entrada do holograma no mundo (decidido no aperto, segurado ate soltar) e o raio da mira
     * fora do olho (olhos do jogador != camera, ex.: terceira pessoa) caindo no mesmo ponto local pela face da frente.
     */
    private static void holoInput(List<String> f) {
        double inf = Double.POSITIVE_INFINITY;
        check(f, com.respectcraft.ui.HoloInputArbiter.hologramWins(true, 2.0, inf, 6.0), "holo entrada: placa sem nada atras ganha");
        check(f, com.respectcraft.ui.HoloInputArbiter.hologramWins(true, 2.0, 3.5, 6.0), "holo entrada: placa na frente do bloco ganha");
        check(f, !com.respectcraft.ui.HoloInputArbiter.hologramWins(true, 2.0, 1.4, 6.0), "holo entrada: mob/bloco na frente da placa = mundo");
        check(f, !com.respectcraft.ui.HoloInputArbiter.hologramWins(true, 6.5, inf, 6.0), "holo entrada: placa fora do alcance = mundo");
        check(f, !com.respectcraft.ui.HoloInputArbiter.hologramWins(false, -1.0, inf, 6.0), "holo entrada: sem placa = mundo");

        com.respectcraft.ui.HoloInputArbiter a = new com.respectcraft.ui.HoloInputArbiter();
        var HOLO = com.respectcraft.ui.HoloInputArbiter.Owner.HOLO;
        var WORLD = com.respectcraft.ui.HoloInputArbiter.Owner.WORLD;
        var NONE = com.respectcraft.ui.HoloInputArbiter.Owner.NONE;
        // apertou na placa: segue do holograma mesmo com a mira fora (o soltar tambem e dele)
        check(f, a.press(0, true) == HOLO && a.owner(0) == HOLO && a.anyHolo(), "holo entrada: aperto na placa = holograma");
        check(f, a.release(0) == HOLO && a.owner(0) == NONE && !a.anyHolo(), "holo entrada: soltar vai para o mesmo dono");
        // apertou no mundo: a placa entrar na mira no meio nao muda o dono
        check(f, a.press(0, false) == WORLD && a.owner(0) == WORLD && !a.anyHolo(), "holo entrada: aperto no mundo = mundo");
        check(f, a.release(0) == WORLD, "holo entrada: mineracao segurada continua do mundo ate soltar");
        // fontes independentes: botao direito do holograma, esquerdo do mundo, ataque no teclado
        int key = com.respectcraft.ui.HoloInputArbiter.keySource(com.respectcraft.ui.HoloInputArbiter.ATTACK);
        a.press(1, true);
        a.press(0, false);
        a.press(key, true);
        check(f, a.owner(1) == HOLO && a.owner(0) == WORLD && a.owner(key) == HOLO, "holo entrada: fontes independentes");
        check(f, a.release(5) == NONE && a.release(99) == NONE && a.press(99, true) == WORLD, "holo entrada: fonte sem aperto/invalida");
        a.clear();
        check(f, !a.anyHolo() && a.owner(1) == NONE && a.owner(key) == NONE, "holo entrada: logout limpa os donos");

        // Raio dos olhos do jogador (fora da camera) no espaco do olho do quadro: mesmo ponto local da placa.
        float cx = 320F, cy = 180F, eyeZ = 469F, s = 2F / 469F;
        org.joml.Matrix4f panel = com.respectcraft.ui.HoloGeometry.panel(new org.joml.Matrix4f(), cx - 147F, 48F, 294F, 264F,
                0.5F, 0.5F, 0F, -31F, 3F, 1F, 1F);
        org.joml.Matrix4f view = new org.joml.Matrix4f().rotationX(0.2F).rotateY(-0.7F);
        float ax = 10.5F, ay = 66.62F, az = 4.25F, camX = 12F, camY = 67.5F, camZ = 7F;
        org.joml.Matrix4f mv = new org.joml.Matrix4f(view).mul(com.respectcraft.ui.HoloGeometry.layoutToWorld(new org.joml.Matrix4f(),
                ax - camX, ay - camY, az - camZ, 15F, s, cx, cy, eyeZ)).mul(panel);
        org.joml.Matrix4f inv = new org.joml.Matrix4f(mv).invert();
        org.joml.Vector3f out = new org.joml.Vector3f();
        org.joml.Vector3f eye = view.transformPosition(new org.joml.Vector3f(ax - camX, ay - camY, az - camZ));
        org.joml.Vector3f target = mv.transformPosition(new org.joml.Vector3f(200F, 30F, 0F));
        org.joml.Vector3f dir = new org.joml.Vector3f(target).sub(eye).normalize();
        boolean front = inv.transformPosition(new org.joml.Vector3f(eye)).z > 0F;
        boolean ok = com.respectcraft.ui.HoloGeometry.rayHit(inv, eye.x, eye.y, eye.z, dir.x, dir.y, dir.z, 294F, 264F, out);
        check(f, front && ok && Math.abs(out.x - 200F) < 0.05F && Math.abs(out.y - 30F) < 0.05F
                && Math.abs(out.z - target.distance(eye)) < 1e-3F, "holo entrada: raio dos olhos -> ponto local = " + out);
        // atras da placa: a origem fica do lado -z local (face de tras nao clica)
        org.joml.Vector3f behind = mv.transformPosition(new org.joml.Vector3f(147F, 132F, -60F));
        check(f, inv.transformPosition(new org.joml.Vector3f(behind)).z < 0F, "holo entrada: olho atras da placa e detectado");
        // arrasto capturado: o raio fora do retangulo ainda cai no plano (sem recorte) e da x local fora de 0..w
        org.joml.Vector3f outside = mv.transformPosition(new org.joml.Vector3f(340F, 30F, 0F));
        org.joml.Vector3f d2 = new org.joml.Vector3f(outside).sub(eye).normalize();
        check(f, !com.respectcraft.ui.HoloGeometry.rayHit(inv, eye.x, eye.y, eye.z, d2.x, d2.y, d2.z, 294F, 264F, out)
                && com.respectcraft.ui.HoloGeometry.rayLocal(inv, eye.x, eye.y, eye.z, d2.x, d2.y, d2.z, out)
                && Math.abs(out.x - 340F) < 0.05F, "holo entrada: arrasto fora da placa continua no plano = " + out);
    }

    private static void holoWorld(List<String> f) {
        float cx = 320F, cy = 180F, eyeZ = 469F, s = 2F / 469F;
        org.joml.Vector3f out = new org.joml.Vector3f();
        for (float yaw : new float[]{0F, 90F, -135F, 200F}) {
            org.joml.Matrix4f m = com.respectcraft.ui.HoloGeometry.layoutToWorld(new org.joml.Matrix4f(), 10F, 64F, -3F, yaw, s,
                    cx, cy, eyeZ);
            m.transformPosition(out.set(cx, cy, eyeZ));
            check(f, out.distance(10F, 64F, -3F) < 1e-3F, "holo mundo: olho do arco cai na ancora (yaw " + yaw + ")");
            // placa da frente (z = 0 do layout) a 2 blocos na direcao do yaw, na altura dos olhos
            m.transformPosition(out.set(cx, cy, 0F));
            float fx = (float) com.respectcraft.ui.HoloPlacement.forwardX(yaw), fz = (float) com.respectcraft.ui.HoloPlacement.forwardZ(yaw);
            check(f, out.distance(10F + 2F * fx, 64F, -3F + 2F * fz) < 1e-3F, "holo mundo: frente do arco segue o yaw " + yaw + " = " + out);
            // x do layout = direita de quem olha; y do layout = para baixo
            m.transformPosition(out.set(cx + 100F, cy + 50F, 0F));
            float rx = (float) com.respectcraft.ui.HoloPlacement.rightX(yaw), rz = (float) com.respectcraft.ui.HoloPlacement.rightZ(yaw);
            check(f, out.distance(10F + 2F * fx + 100F * s * rx, 64F - 50F * s, -3F + 2F * fz + 100F * s * rz) < 1e-3F,
                    "holo mundo: direita e baixo do layout (yaw " + yaw + ")");
        }
        // Mesmo ponto local pelo desenho e pelo raio, com a camera em lugares diferentes em volta da ancora fixa.
        org.joml.Matrix4f panel = com.respectcraft.ui.HoloGeometry.panel(new org.joml.Matrix4f(), cx - 147F, 48F, 294F, 264F,
                0.5F, 0.5F, 0F, 31F, 3F, 1F, 1F);
        float ax = 100.5F, ay = 70.62F, az = -40.25F;
        for (float[] cam : new float[][]{{ax, ay, az}, {ax + 1.3F, ay - 0.4F, az + 0.6F}, {ax - 2.5F, ay + 0.3F, az - 3.5F}}) {
            org.joml.Matrix4f model = com.respectcraft.ui.HoloGeometry.layoutToWorld(new org.joml.Matrix4f(), ax - cam[0], ay - cam[1],
                    az - cam[2], 37F, s, cx, cy, eyeZ).mul(panel);
            org.joml.Vector3f target = model.transformPosition(new org.joml.Vector3f(60F, 200F, 0F));
            org.joml.Vector3f dir = new org.joml.Vector3f(target).normalize();
            boolean ok = com.respectcraft.ui.HoloGeometry.rayHit(new org.joml.Matrix4f(model).invert(), 0F, 0F, 0F, dir.x, dir.y, dir.z,
                    294F, 264F, out);
            check(f, ok && Math.abs(out.x - 60F) < 0.05F && Math.abs(out.y - 200F) < 0.05F && Math.abs(out.z - target.length()) < 1e-3F,
                    "holo mundo: raio da camera -> ponto local da placa ancorada = " + out);
            check(f, !com.respectcraft.ui.HoloGeometry.rayHit(new org.joml.Matrix4f(model).invert(), 0F, 0F, 0F, -dir.x, -dir.y, -dir.z,
                    294F, 264F, out), "holo mundo: placa atras da camera recusada");
        }
        check(f, Math.abs(com.respectcraft.ui.HoloGeometry.distanceToRect(10F, 20F, -3F, 100F, 50F) - 3F) < 1e-4F
                && Math.abs(com.respectcraft.ui.HoloGeometry.distanceToRect(-3F, 54F, 0F, 100F, 50F) - 5F) < 1e-4F,
                "holo mundo: distancia ate o retangulo da placa");

        // Escolha da posicao: amostras de um arco simples (frente, vizinhas e pontas), olho em (0.5, 65.62, 0.5).
        List<com.respectcraft.ui.HoloPlacement.Sample> samples = new ArrayList<>();
        for (int i = -2; i <= 2; i++) {
            double a = Math.toRadians(i * 31.0);
            int kind = i == 0 ? com.respectcraft.ui.HoloPlacement.ESSENTIAL
                    : Math.abs(i) == 1 ? com.respectcraft.ui.HoloPlacement.IMPORTANT : com.respectcraft.ui.HoloPlacement.OPTIONAL;
            for (float up : new float[]{-0.25F, 0F, 0.25F}) samples.add(new com.respectcraft.ui.HoloPlacement.Sample(
                    (float) Math.sin(a) * 1.2F, up, (float) Math.cos(a) * 1.2F, kind));
        }
        double ex = 0.5, ey = 65.62, ez = 0.5;
        com.respectcraft.ui.HoloPlacement.Result open = com.respectcraft.ui.HoloPlacement.choose(ex, ey, ez, 0F, 2F, 0.7F, samples,
                (x0, y0, z0, x1, y1, z1) -> true);
        check(f, open.fits() && open.yaw() == 0F && open.distance() == 2F, "holo mundo: campo aberto usa a distancia preferida = " + open);
        // parede em z = 2 (yaw 0 olha para +z): encolhe ate a placa da frente caber antes dela
        com.respectcraft.ui.HoloPlacement.Sight wall = (x0, y0, z0, x1, y1, z1) -> z1 < 2.0;
        com.respectcraft.ui.HoloPlacement.Result near = com.respectcraft.ui.HoloPlacement.choose(ex, ey, ez, 0F, 2F, 0.7F, samples, wall);
        check(f, near.fits() && near.yaw() == 0F && near.distance() < 2F && ez + near.distance() * 1.2F < 2.0,
                "holo mundo: parede na frente aproxima as placas = " + near);
        // pilar logo a frente (x perto do olho, z >= 1.2): a frente e as vizinhas batem -> o arco gira para o lado livre
        com.respectcraft.ui.HoloPlacement.Sight pillar = (x0, y0, z0, x1, y1, z1) -> !(z1 >= 1.2 && Math.abs(x1 - 0.5) < 0.6);
        com.respectcraft.ui.HoloPlacement.Result turned = com.respectcraft.ui.HoloPlacement.choose(ex, ey, ez, 0F, 2F, 0.7F, samples, pillar);
        check(f, turned.fits() && Math.abs(turned.yaw()) == 50F && turned.distance() == 2F, "holo mundo: pilar na frente gira o arco = " + turned);
        // cubiculo: nada cabe -> fica com a menor distancia e avisa
        com.respectcraft.ui.HoloPlacement.Result boxed = com.respectcraft.ui.HoloPlacement.choose(ex, ey, ez, 0F, 2F, 0.7F, samples,
                (x0, y0, z0, x1, y1, z1) -> Math.abs(x1 - x0) < 0.3 && Math.abs(z1 - z0) < 0.3);
        check(f, !boxed.fits() && boxed.distance() <= 0.7F + 1e-4F, "holo mundo: espaco minimo avisa e usa a menor distancia = " + boxed);
    }

    /** Etapa 4.8: Rimulib carregado e conectado pela API comum (segura no servidor dedicado). */
    private static void rimulib(List<String> f) {
        using(f, () -> {
            check(f, cyou.rimuru.rimulib.mc1211.api.RimulibApi.isLoaded("rimulib"), "rimulib: mod não carregado");
            check(f, versionAtLeast(cyou.rimuru.rimulib.mc1211.api.RimulibApi.version(), com.respectcraft.RespectCraft.RIMULIB_MINIMUM),
                    "rimulib: versão abaixo de " + com.respectcraft.RespectCraft.RIMULIB_MINIMUM);
            check(f, com.respectcraft.RespectCraft.RIMULIB != null
                    && com.respectcraft.RespectCraft.MOD_ID.equals(com.respectcraft.RespectCraft.RIMULIB.id())
                    && "respectcraft:x".equals(com.respectcraft.RespectCraft.RIMULIB.id("x")), "rimulib: conexão do RespectCraft");
        });
    }

    /**
     * Etapa 4.10: ponte Finisher Engine → Calamidade_Awakening. Regras puras: escolha do efeito pela forca, FxDef
     * opcional (sem ele, particulas vanilla), momentos da cena tirados das animacoes, encerramento, fonte,
     * acessibilidade, keyframes {@code calamidade:} do GeckoLib e o {@code SkillEffect} de efeito visual.
     */
    private static void finisherFx(List<String> f) {
        using(f, () -> {
            var L = com.respectcraft.fx.FinisherFxRules.HitLayer.class;
            var light = com.respectcraft.fx.FinisherFxRules.HitLayer.LIGHT;
            var ring = com.respectcraft.fx.FinisherFxRules.HitLayer.RING;
            var frame = com.respectcraft.fx.FinisherFxRules.HitLayer.IMPACT_FRAME;
            check(f, com.respectcraft.fx.FinisherFxRules.layersFor(1).equals(java.util.EnumSet.of(light))
                    && com.respectcraft.fx.FinisherFxRules.layersFor(2).equals(java.util.EnumSet.of(light, ring))
                    && com.respectcraft.fx.FinisherFxRules.layersFor(3).equals(java.util.EnumSet.allOf(L)),
                    "fx: luz breve; anel a partir da força 2; impact frame só na força 3");
            check(f, com.respectcraft.fx.FinisherFxRules.layersFor(0).equals(com.respectcraft.fx.FinisherFxRules.layersFor(1))
                    && com.respectcraft.fx.FinisherFxRules.layersFor(9).equals(com.respectcraft.fx.FinisherFxRules.layersFor(3)),
                    "fx: força fora de 1..3 é limitada");
            java.util.Set<net.minecraft.resources.ResourceLocation> hitIds = new java.util.HashSet<>();
            for (var tier : com.respectcraft.fx.FinisherFxDef.Tier.values()) {
                for (int s = 1; s <= 3; s++) hitIds.add(com.respectcraft.fx.FinisherFxDef.hitEffect(tier, s));
                hitIds.add(com.respectcraft.fx.FinisherFxDef.fallEffect(tier));
            }
            check(f, hitIds.size() == 12, "fx: um efeito por intensidade e força, e um de queda por intensidade");

            // FxDef opcional: as tres finalizacoes tem; sem ele, comportamento antigo (particulas vanilla).
            var T = com.respectcraft.fx.FinisherFxDef.Tier.class;
            check(f, Finishers.TAKEDOWN.fx().map(x -> x.tier() == com.respectcraft.fx.FinisherFxDef.Tier.SUBTLE).orElse(false)
                    && Finishers.RASTEIRA.fx().map(x -> x.tier() == com.respectcraft.fx.FinisherFxDef.Tier.MEDIUM).orElse(false)
                    && Finishers.COMBO.fx().map(x -> x.tier() == com.respectcraft.fx.FinisherFxDef.Tier.INTENSE).orElse(false),
                    "fx: TAKEDOWN discreto, RASTEIRA médio, COMBO intenso");
            var tiers = T.getEnumConstants();
            check(f, tiers[0].strength() < tiers[1].strength() && tiers[1].strength() < tiers[2].strength(),
                    "fx: intensidade crescente");
            FinisherDef plain = Finishers.TAKEDOWN.withFx(null);
            check(f, plain.fx().isEmpty() && com.respectcraft.fx.FinisherFxRules.vanillaParticles(plain)
                    && !com.respectcraft.fx.FinisherFxRules.vanillaParticles(Finishers.TAKEDOWN),
                    "fx: sem FinisherFxDef a finalização usa as partículas vanilla");
            var clip = new com.respectcraft.finisher.EntityFinisherClip(com.respectcraft.RespectCraft.id("test_rig_mob"),
                    com.respectcraft.finisher.FinisherRole.ATTACKER, "body", "selftest_only", Finishers.TAKEDOWN.durationTicks());
            check(f, Finishers.TAKEDOWN.withEntityClip(clip).fx().equals(Finishers.TAKEDOWN.fx()),
                    "fx: cópia com clip de mob mantém os efeitos");
            check(f, throwsIae(() -> new com.respectcraft.fx.FinisherFxDef(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("x"),
                    com.respectcraft.fx.FinisherFxDef.Look.TAKEDOWN_FOCUS, com.respectcraft.fx.FinisherFxDef.Tier.SUBTLE,
                    com.respectcraft.fx.FinisherFxDef.Orientation.ANCHOR_YAW)), "fx: efeito de cena fora do namespace é recusado");

            // Momentos da cena = eventos das animacoes (ticks e forcas), marcadores unicos.
            var hits = com.respectcraft.fx.FinisherFxRules.CueKind.HIT;
            var falls = com.respectcraft.fx.FinisherFxRules.CueKind.FALL;
            var take = com.respectcraft.fx.FinisherFxRules.cues(Finishers.TAKEDOWN);
            check(f, take.size() == 3 && take.get(0).tick() == 14 && take.get(1).tick() == 26 && take.get(2).tick() == 44
                    && take.get(2).strength() == 3 && take.get(0).strength() == 1 && take.stream().allMatch(c -> c.kind() == hits),
                    "fx: golpes do TAKEDOWN nos ticks 14, 26 e 44 (o último de força 3)");
            var combo = com.respectcraft.fx.FinisherFxRules.cues(Finishers.COMBO);
            check(f, combo.size() == 4 && combo.get(0).tick() == 25 && combo.get(1).tick() == 43
                    && combo.get(2).kind() == falls && combo.get(2).tick() == 62 && Math.abs(combo.get(2).back() - 0.4) < 1e-9
                    && combo.get(3).tick() == 65 && combo.get(3).strength() == 1, "fx: golpes e quedas do COMBO");
            var rast = com.respectcraft.fx.FinisherFxRules.cues(Finishers.RASTEIRA);
            check(f, rast.size() == 1 && rast.get(0).tick() == 14 && rast.get(0).strength() == 2, "fx: golpe da RASTEIRA");
            for (FinisherDef def : Finishers.all()) {
                var cues = com.respectcraft.fx.FinisherFxRules.cues(def);
                check(f, cues.stream().map(com.respectcraft.fx.FinisherFxRules.Cue::marker).distinct().count() == cues.size()
                        && cues.stream().allMatch(c -> c.tick() >= 0 && c.tick() < def.durationTicks()),
                        "fx: marcadores únicos dentro da cena " + def.id());
                var timeline = com.respectcraft.fx.FinisherFx.sceneTimeline(def);
                check(f, timeline.duration() == def.durationTicks() && timeline.hasPhase("scene")
                        && cues.stream().allMatch(c -> timeline.hasMarker(c.marker()) && timeline.tick(c.marker()) == c.tick())
                        && timeline.hasTrack("pulse") && timeline.hasTrack("energy"),
                        "fx: timeline HERO do tamanho da cena com marcadores nos ticks dos eventos " + def.id());
                // Cada momento dispara uma vez em janelas seguidas, mesmo com um tick pulado.
                int fired = 0, last = 0;
                for (int t = 1; t <= def.durationTicks(); t += (t % 7 == 0 ? 2 : 1)) {
                    fired += com.respectcraft.fx.FinisherFxRules.due(cues, last, t).size();
                    last = t;
                }
                fired += com.respectcraft.fx.FinisherFxRules.due(cues, last, def.durationTicks()).size();
                check(f, fired == cues.size(), "fx: cada golpe/queda dispara uma vez " + def.id());
            }
            check(f, com.respectcraft.fx.FinisherFx.sceneTimeline(Finishers.TAKEDOWN).hasPhase("impact_frame_hit_2")
                    && !com.respectcraft.fx.FinisherFx.sceneTimeline(Finishers.COMBO).phases().stream()
                    .anyMatch(p -> p.startsWith("impact_frame_")), "fx: a cena cede o passe só nos golpes de força 3");
            check(f, com.respectcraft.fx.FinisherFxRules.forSession(combo, true).size() == 4
                    && com.respectcraft.fx.FinisherFxRules.forSession(combo, false).size() == 2
                    && com.respectcraft.fx.FinisherFxRules.forSession(combo, false).stream().allMatch(c -> c.kind() == hits),
                    "fx: queda só com vítima jogador (mob vítima fica parado)");
            double[] bad = com.respectcraft.fx.FinisherFxRules.parseFall("x,y,z");
            double[] far = com.respectcraft.fx.FinisherFxRules.parseFall("7,9");
            check(f, bad[0] == 1 && bad[1] == 0 && far[0] == 3 && far[1] == 1.5, "fx: dados de queda inválidos são limitados");

            // Encerramento e fonte.
            check(f, com.respectcraft.fx.FinisherFxRules.endAction(com.respectcraft.fx.FinisherFxRules.EndReason.COMPLETE)
                    == com.respectcraft.fx.FinisherFxRules.EndAction.RELEASE
                    && com.respectcraft.fx.FinisherFxRules.endAction(com.respectcraft.fx.FinisherFxRules.EndReason.ABORT)
                    == com.respectcraft.fx.FinisherFxRules.EndAction.STOP, "fx: fim solta, cancelamento para na hora");
            com.respectcraft.fx.FinisherFx.end(null, com.respectcraft.fx.FinisherFxRules.EndReason.ABORT);
            com.respectcraft.fx.FinisherFx.tick(null, 10);
            check(f, com.respectcraft.fx.FinisherFxRules.sourceRole(true, false) == com.respectcraft.finisher.FinisherRole.ATTACKER
                    && com.respectcraft.fx.FinisherFxRules.sourceRole(true, true) == com.respectcraft.finisher.FinisherRole.ATTACKER
                    && com.respectcraft.fx.FinisherFxRules.sourceRole(false, true) == com.respectcraft.finisher.FinisherRole.VICTIM
                    && com.respectcraft.fx.FinisherFxRules.sourceRole(false, false) == null,
                    "fx: fonte = jogador executor (senão a vítima jogador)");

            // Acessibilidade do Calamidade.
            var subtle = com.respectcraft.fx.FinisherFxDef.Tier.SUBTLE;
            var intense = com.respectcraft.fx.FinisherFxDef.Tier.INTENSE;
            check(f, com.respectcraft.fx.FinisherFxRules.screenStrength(1, intense, 0, true) == 0
                    && Math.abs(com.respectcraft.fx.FinisherFxRules.screenStrength(1, intense, 1, false) - 0.35F) < 1e-6
                    && com.respectcraft.fx.FinisherFxRules.screenStrength(1, subtle, 1, true)
                    < com.respectcraft.fx.FinisherFxRules.screenStrength(1, intense, 1, true),
                    "fx: intensidade dos efeitos de tela e ocultar clarões");
            check(f, !com.respectcraft.fx.FinisherFxRules.impactFrameInverts(false, 5)
                    && com.respectcraft.fx.FinisherFxRules.impactFrameInverts(true, 5)
                    && !com.respectcraft.fx.FinisherFxRules.impactFrameInverts(true, 0), "fx: impact frame só pisca com clarões");

            // Gancho GeckoLib -> Calamidade (keyframes "calamidade:<efeito>").
            check(f, com.respectcraft.fx.GeoFxKeyframes.parseInstruction("calamidade:golpe")
                    .equals(Optional.of(com.respectcraft.RespectCraft.id("golpe")))
                    && com.respectcraft.fx.GeoFxKeyframes.parseInstruction(" calamidade:minecraft:x ")
                    .equals(Optional.of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("x")))
                    && com.respectcraft.fx.GeoFxKeyframes.parseInstruction("sound:abc").isEmpty()
                    && com.respectcraft.fx.GeoFxKeyframes.parseInstruction("calamidade:").isEmpty()
                    && com.respectcraft.fx.GeoFxKeyframes.parseInstruction("calamidade:Bad Id").isEmpty(),
                    "fx: instrução calamidade:<efeito> do keyframe");
            check(f, com.respectcraft.fx.GeoFxKeyframes.parseInstructions("calamidade:a; outra; calamidade:respectcraft:b;")
                    .equals(List.of(com.respectcraft.RespectCraft.id("a"), com.respectcraft.RespectCraft.id("b"))),
                    "fx: várias instruções no mesmo keyframe");
            var json = com.google.gson.JsonParser.parseString("{\"animations\":{\"animation.t.attack\":{\"timeline\":"
                    + "{\"1.0\":[\"x\",\"calamidade:respectcraft:fim\"],\"0.6\":\"calamidade:golpe;\",\"bad\":\"calamidade:z\"}}}}")
                    .getAsJsonObject();
            var scanned = com.respectcraft.fx.GeoFxKeyframes.scan(json, "animation.t.attack");
            check(f, scanned.size() == 2 && scanned.get(0).tick() == 12 && scanned.get(1).tick() == 20
                    && scanned.get(1).effect().equals(com.respectcraft.RespectCraft.id("fim"))
                    && com.respectcraft.fx.GeoFxKeyframes.scan(json, "animation.t.none").isEmpty(),
                    "fx: keyframes do arquivo GeckoLib em ticks (segundos × 20)");

            // SkillEffect de efeito visual: existe, mas nenhuma habilidade usa ainda.
            var vfx = com.respectcraft.skill.SkillEffect.visualFx(com.respectcraft.RespectCraft.id("finisher_fx/takedown_scene"));
            check(f, vfx.kind() == com.respectcraft.skill.SkillEffect.Kind.VISUAL_FX
                    && vfx.visualFxId().equals(Optional.of(com.respectcraft.RespectCraft.id("finisher_fx/takedown_scene")))
                    && com.respectcraft.skill.SkillEffect.mood(1).visualFxId().isEmpty(), "fx: SkillEffect referencia um efeito");
            check(f, Skills.all().stream().flatMap(d -> d.effects().stream())
                    .noneMatch(e -> e.kind() == com.respectcraft.skill.SkillEffect.Kind.VISUAL_FX),
                    "fx: nenhuma habilidade aplica efeito visual ainda");
        });
    }

    static boolean versionAtLeast(String version, String minimum) {
        String[] a = version.split("[^0-9]+"), b = minimum.split("[^0-9]+");
        for (int i = 0; i < b.length; i++) {
            int x = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
            int y = b[i].isEmpty() ? 0 : Integer.parseInt(b[i]);
            if (x != y) return x > y;
        }
        return true;
    }

    private static void using(List<String> f, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            f.add("exceção: " + e);
        }
    }

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
