package com.tupinicraft.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tupinicraft.origin.RegionalOrigin;
import com.tupinicraft.politics.PoliticalOffice;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * Dados políticos imutáveis de um jogador (independentes do XP vanilla).
 * Altere sempre via {@link PoliticalDataService}, que persiste e sincroniza.
 */
public record PoliticalData(Optional<RegionalOrigin> origin, PoliticalOffice office, int politicalXp, int popularity,
                            int skillPoints, int mandateOnlineSeconds) {
    public static final int MAX_VALUE = 1_000_000;

    public static final PoliticalData DEFAULT = new PoliticalData(Optional.empty(), PoliticalOffice.DESEMPREGADO, 0, 0, 0, 0);

    /** Persistência: guarda a sigla da UF e o id do cargo (estáveis entre versões). */
    public static final Codec<PoliticalData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("origin").xmap(
                    code -> code.flatMap(RegionalOrigin::byCode),
                    origin -> origin.map(RegionalOrigin::code)
            ).forGetter(PoliticalData::origin),
            Codec.STRING.xmap(
                    id -> PoliticalOffice.byId(id).orElse(PoliticalOffice.DESEMPREGADO),
                    PoliticalOffice::id
            ).optionalFieldOf("office", PoliticalOffice.DESEMPREGADO).forGetter(PoliticalData::office),
            Codec.INT.optionalFieldOf("political_xp", 0).forGetter(PoliticalData::politicalXp),
            Codec.INT.optionalFieldOf("popularity", 0).forGetter(PoliticalData::popularity),
            Codec.INT.optionalFieldOf("skill_points", 0).forGetter(PoliticalData::skillPoints),
            Codec.INT.optionalFieldOf("mandate_online_seconds", 0).forGetter(PoliticalData::mandateOnlineSeconds)
    ).apply(instance, PoliticalData::new));

    private static final StreamCodec<ByteBuf, Optional<RegionalOrigin>> ORIGIN_STREAM_CODEC =
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT.map(i -> RegionalOrigin.values()[i], RegionalOrigin::ordinal));
    private static final StreamCodec<ByteBuf, PoliticalOffice> OFFICE_STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> PoliticalOffice.values()[i], PoliticalOffice::ordinal);

    public static final StreamCodec<ByteBuf, PoliticalData> STREAM_CODEC = StreamCodec.composite(
            ORIGIN_STREAM_CODEC, PoliticalData::origin,
            OFFICE_STREAM_CODEC, PoliticalData::office,
            ByteBufCodecs.VAR_INT, PoliticalData::politicalXp,
            ByteBufCodecs.VAR_INT, PoliticalData::popularity,
            ByteBufCodecs.VAR_INT, PoliticalData::skillPoints,
            ByteBufCodecs.VAR_INT, PoliticalData::mandateOnlineSeconds,
            PoliticalData::new);

    public PoliticalData {
        politicalXp = clamp(politicalXp);
        popularity = clamp(popularity);
        skillPoints = clamp(skillPoints);
        mandateOnlineSeconds = clamp(mandateOnlineSeconds);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_VALUE, value));
    }

    public PoliticalData withOrigin(RegionalOrigin newOrigin) {
        return new PoliticalData(Optional.of(newOrigin), office, politicalXp, popularity, skillPoints, mandateOnlineSeconds);
    }

    public PoliticalData withOffice(PoliticalOffice newOffice) {
        return new PoliticalData(origin, newOffice, politicalXp, popularity, skillPoints, mandateOnlineSeconds);
    }

    public PoliticalData withPoliticalXp(int newXp) {
        return new PoliticalData(origin, office, newXp, popularity, skillPoints, mandateOnlineSeconds);
    }

    /** Pontos de habilidade de mandato e segundos online acumulados para o próximo ponto. */
    public PoliticalData withSkillProgress(int newSkillPoints, int newMandateOnlineSeconds) {
        return new PoliticalData(origin, office, politicalXp, popularity, newSkillPoints, newMandateOnlineSeconds);
    }

    public PoliticalData withPopularity(int newPopularity) {
        return new PoliticalData(origin, office, politicalXp, newPopularity, skillPoints, mandateOnlineSeconds);
    }
}
