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
 */
public record CharacterProfile(boolean born, String origin, List<Integer> attributes, int rerolls, int rollCount,
                               int skillPoints, int levelRewarded, List<String> skills) {
    public static final CharacterProfile DEFAULT = new CharacterProfile(false, "", defaultAttributes(), 0, 0, 0, 0, List.of());

    public static final Codec<CharacterProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("born", false).forGetter(CharacterProfile::born),
            // Ids de origem aposentados sao convertidos na leitura (LegacyOriginIds).
            Codec.STRING.xmap(LegacyOriginIds::migrate, id -> id).optionalFieldOf("origin", "").forGetter(CharacterProfile::origin),
            Codec.INT.listOf().optionalFieldOf("attributes", defaultAttributes()).forGetter(CharacterProfile::attributes),
            Codec.INT.optionalFieldOf("rerolls", 0).forGetter(CharacterProfile::rerolls),
            Codec.INT.optionalFieldOf("roll_count", 0).forGetter(CharacterProfile::rollCount),
            Codec.INT.optionalFieldOf("skill_points", 0).forGetter(CharacterProfile::skillPoints),
            Codec.INT.optionalFieldOf("level_rewarded", 0).forGetter(CharacterProfile::levelRewarded),
            Codec.STRING.listOf().optionalFieldOf("skills", List.of()).forGetter(CharacterProfile::skills)
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
            return new CharacterProfile(born, origin, attributes, rerolls, rollCount, points, level, skills);
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
        }
    };

    public CharacterProfile {
        attributes = normalize(attributes);
        skills = List.copyOf(new LinkedHashSet<>(skills));
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

    public CharacterProfile withBirth(BirthRoulette.Result result, int rerollsLeft) {
        List<Integer> values = new ArrayList<>();
        for (int value : result.attributes()) values.add(value);
        int startPoints = BirthRoulette.startingSkillPoints(result.attributes()[CharacterAttribute.INTELIGENCIA.ordinal()]);
        // Re-girar devolve os pontos gastos: as habilidades sao zeradas junto com os atributos.
        return new CharacterProfile(true, result.origin().id(), values, rerollsLeft, rollCount + 1,
                startPoints + Math.max(0, levelRewarded), levelRewarded, List.of());
    }

    public CharacterProfile withSkills(List<String> newSkills, int newPoints) {
        return new CharacterProfile(born, origin, attributes, rerolls, rollCount, newPoints, levelRewarded, newSkills);
    }

    public CharacterProfile withPoints(int newPoints, int newLevelRewarded) {
        return new CharacterProfile(born, origin, attributes, rerolls, rollCount, newPoints, newLevelRewarded, skills);
    }
}
