package com.tupinicraft.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Ficha do personagem (persistida, copiada na morte): nascimento, atributos e habilidades compradas.
 * Imutavel; altere so por {@link ProfileService}. Nomes de campos do Codec sao estaveis.
 *
 * @param born          a roleta de nascimento ja foi girada
 * @param origin        id de {@link BirthOrigin} (vazio antes do nascimento)
 * @param attributes    um valor por {@link CharacterAttribute}, na ordem do enum
 * @param rerolls       novas tentativas de roleta restantes
 * @param rollCount     quantas vezes a roleta ja girou (a tela anima quando muda)
 * @param skillPoints   pontos de habilidade disponiveis
 * @param levelRewarded maior nivel de XP vanilla que ja rendeu pontos
 * @param skills        ids das habilidades compradas
 * @param traits        ids dos {@link BirthTrait} sorteados no nascimento (Etapa 4.5; vazio em fichas anteriores)
 */
public record CharacterProfile(boolean born, String origin, List<Integer> attributes, int rerolls, int rollCount,
                               int skillPoints, int levelRewarded, List<String> skills, List<String> traits) {
    /** Limite de tracos guardados/enviados (protege o pacote; o sorteio usa {@link BirthTrait#PER_BIRTH}). */
    public static final int MAX_TRAITS = 8;
    public static final CharacterProfile DEFAULT = new CharacterProfile(false, "", defaultAttributes(), 0, 0, 0, 0, List.of(), List.of());

    public static final Codec<CharacterProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("born", false).forGetter(CharacterProfile::born),
            // Ids de origem aposentados sao convertidos na leitura (LegacyOriginIds).
            Codec.STRING.xmap(LegacyOriginIds::migrate, id -> id).optionalFieldOf("origin", "").forGetter(CharacterProfile::origin),
            Codec.INT.listOf().optionalFieldOf("attributes", defaultAttributes()).forGetter(CharacterProfile::attributes),
            Codec.INT.optionalFieldOf("rerolls", 0).forGetter(CharacterProfile::rerolls),
            Codec.INT.optionalFieldOf("roll_count", 0).forGetter(CharacterProfile::rollCount),
            Codec.INT.optionalFieldOf("skill_points", 0).forGetter(CharacterProfile::skillPoints),
            Codec.INT.optionalFieldOf("level_rewarded", 0).forGetter(CharacterProfile::levelRewarded),
            Codec.STRING.listOf().optionalFieldOf("skills", List.of()).forGetter(CharacterProfile::skills),
            // Etapa 4.5: fichas antigas nao tem o campo e carregam sem tracos (estado neutro).
            Codec.STRING.listOf().optionalFieldOf("birth_traits", List.of()).forGetter(CharacterProfile::traits)
    ).apply(i, CharacterProfile::new));

    public static final StreamCodec<ByteBuf, CharacterProfile> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CharacterProfile decode(ByteBuf buf) {
            boolean born = ByteBufCodecs.BOOL.decode(buf);
            String origin = ByteBufCodecs.STRING_UTF8.decode(buf);
            List<Integer> attributes = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)).decode(buf);
            int rerolls = ByteBufCodecs.VAR_INT.decode(buf);
            int rollCount = ByteBufCodecs.VAR_INT.decode(buf);
            int points = ByteBufCodecs.VAR_INT.decode(buf);
            int level = ByteBufCodecs.VAR_INT.decode(buf);
            List<String> skills = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).decode(buf);
            List<String> traits = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_TRAITS)).decode(buf);
            return new CharacterProfile(born, origin, attributes, rerolls, rollCount, points, level, skills, traits);
        }

        @Override
        public void encode(ByteBuf buf, CharacterProfile p) {
            ByteBufCodecs.BOOL.encode(buf, p.born());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.origin());
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)).encode(buf, p.attributes());
            ByteBufCodecs.VAR_INT.encode(buf, p.rerolls());
            ByteBufCodecs.VAR_INT.encode(buf, p.rollCount());
            ByteBufCodecs.VAR_INT.encode(buf, p.skillPoints());
            ByteBufCodecs.VAR_INT.encode(buf, p.levelRewarded());
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).encode(buf, p.skills());
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_TRAITS)).encode(buf, p.traits());
        }
    };

    public CharacterProfile {
        attributes = normalize(attributes);
        skills = List.copyOf(new LinkedHashSet<>(skills));
        // Sem duplicatas; ids desconhecidos ficam guardados (versao futura), mas so os conhecidos aparecem.
        traits = traits == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(traits)).stream()
                .filter(t -> t != null && !t.isEmpty()).limit(MAX_TRAITS).toList();
        // Regra da Etapa 3.1: no maximo um novo giro, e so depois do nascimento (fichas antigas sao corrigidas).
        rerolls = born ? Math.max(0, Math.min(BirthRoulette.FREE_REROLLS, rerolls)) : 0;
        skillPoints = Math.max(0, skillPoints);
        levelRewarded = Math.max(0, levelRewarded);
    }

    private static List<Integer> defaultAttributes() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < CharacterAttribute.values().length; i++) list.add(CharacterAttribute.BASE);
        return List.copyOf(list);
    }

    /** Lista com exatamente um valor valido por atributo (dados antigos ou corrompidos sao corrigidos). */
    private static List<Integer> normalize(List<Integer> raw) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < CharacterAttribute.values().length; i++) {
            list.add(raw != null && i < raw.size() && raw.get(i) != null
                    ? CharacterAttribute.clamp(raw.get(i)) : CharacterAttribute.BASE);
        }
        return List.copyOf(list);
    }

    public int attribute(CharacterAttribute attribute) { return attributes.get(attribute.ordinal()); }

    public BirthOrigin originOrNull() { return BirthOrigin.byId(origin); }

    /** Pode pedir o (unico) novo giro? */
    public boolean canReroll() { return born && rerolls > 0; }

    public boolean hasSkill(String id) { return skills.contains(id); }

    public Set<String> skillSet() { return new LinkedHashSet<>(skills); }

    /** Tracos de nascimento conhecidos por esta versao (vazio em fichas anteriores a Etapa 4.5). */
    public List<BirthTrait> birthTraits() {
        List<BirthTrait> list = new ArrayList<>();
        for (String id : traits) {
            BirthTrait trait = BirthTrait.byId(id);
            if (trait != null) list.add(trait);
        }
        return List.copyOf(list);
    }

    /**
     * Nascimento confirmado: ja nasceu e nao ha mais novo giro (gastou o unico ou confirmou com Continuar).
     * Fichas antigas com o novo giro guardado continuam podendo usa-lo uma vez.
     */
    public boolean birthConfirmed() { return born && rerolls == 0; }

    /** Confirma o resultado atual: o novo giro restante e descartado (nao volta mais). */
    public CharacterProfile confirmBirth() {
        return new CharacterProfile(born, origin, attributes, 0, rollCount, skillPoints, levelRewarded, skills, traits);
    }

    public CharacterProfile withBirth(BirthRoulette.Result result, int rerollsLeft) {
        List<Integer> values = new ArrayList<>();
        for (int value : result.attributes()) values.add(value);
        int startPoints = BirthRoulette.startingSkillPoints(result.attributes()[CharacterAttribute.INTELIGENCIA.ordinal()]);
        List<String> traitIds = new ArrayList<>();
        for (BirthTrait trait : result.traits()) traitIds.add(trait.id());
        // Re-girar devolve os pontos gastos: as habilidades sao zeradas junto com os atributos. Os tracos do
        // giro anterior sao substituidos pelos novos (nunca somados).
        return new CharacterProfile(true, result.origin().id(), values, rerollsLeft, rollCount + 1,
                startPoints + Math.max(0, levelRewarded), levelRewarded, List.of(), traitIds);
    }

    /**
     * Troca de corpo durante a criacao: num resultado <b>ainda nao confirmado</b> (nasceu e ainda tem o novo giro),
     * cada traco que nao pode sair para o corpo novo vira um traco elegivel de mesma polaridade
     * ({@link BirthTrait#coherentWith}). Nao sorteia, nao muda quantidade, origem, atributos, pontos nem o novo
     * giro. Resultado confirmado (ou sem nascimento) fica intacto: traco gravado nunca e apagado por elegibilidade.
     * Devolve a mesma instancia quando nada muda.
     */
    public CharacterProfile withTraitsCoherentWith(BirthTrait.Body body) {
        if (!born || birthConfirmed()) return this;
        List<String> next = new ArrayList<>();
        boolean changed = false;
        for (String id : traits) {
            BirthTrait trait = BirthTrait.byId(id);
            BirthTrait coherent = trait == null ? null : BirthTrait.coherentWith(trait, body, rollCount);
            changed |= coherent != trait;
            next.add(coherent == null ? id : coherent.id());
        }
        if (!changed) return this;
        return new CharacterProfile(born, origin, attributes, rerolls, rollCount, skillPoints, levelRewarded, skills, next);
    }

    public CharacterProfile withSkills(List<String> newSkills, int newPoints) {
        return new CharacterProfile(born, origin, attributes, rerolls, rollCount, newPoints, levelRewarded, newSkills, traits);
    }

    public CharacterProfile withPoints(int newPoints, int newLevelRewarded) {
        return new CharacterProfile(born, origin, attributes, rerolls, rollCount, newPoints, newLevelRewarded, skills, traits);
    }
}
