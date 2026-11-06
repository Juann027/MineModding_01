package com.tupinicraft.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tupinicraft.origin.RegionalOrigin;
import com.tupinicraft.politics.PoliticalOffice;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Dados políticos imutáveis de um jogador (independentes do XP vanilla).
 * Altere sempre via {@link PoliticalDataService}, que persiste e sincroniza.
 *
 * @param office         cargo atual: o do mandato vigente, ou Desempregado sem mandato
 *                       (títulos da v0.1.0 sem cadeira são preservados como estavam)
 * @param officeProgress progresso de habilidades por cargo; mantido quando o mandato termina
 */
public record PoliticalData(Optional<RegionalOrigin> origin, PoliticalOffice office, int politicalXp, int popularity,
                            Map<PoliticalOffice, OfficeProgress> officeProgress) {
    public static final int MAX_VALUE = 1_000_000;

    public static final PoliticalData DEFAULT = new PoliticalData(Optional.empty(), PoliticalOffice.DESEMPREGADO, 0, 0, Map.of());

    private static final Codec<OfficeProgress> PROGRESS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("skill_points", 0).forGetter(OfficeProgress::skillPoints),
            Codec.INT.optionalFieldOf("online_seconds", 0).forGetter(OfficeProgress::onlineSeconds)
    ).apply(instance, OfficeProgress::new));

    /** Mapa por id do cargo; ids desconhecidos são ignorados ao carregar. */
    private static final MapCodec<Map<PoliticalOffice, OfficeProgress>> PROGRESS_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, PROGRESS_CODEC).xmap(
                    raw -> {
                        Map<PoliticalOffice, OfficeProgress> map = new EnumMap<>(PoliticalOffice.class);
                        raw.forEach((id, progress) -> PoliticalOffice.byId(id)
                                .filter(PoliticalOffice::isElective)
                                .ifPresent(office -> map.put(office, progress)));
                        return map;
                    },
                    map -> {
                        Map<String, OfficeProgress> raw = new LinkedHashMap<>();
                        map.forEach((office, progress) -> raw.put(office.id(), progress));
                        return raw;
                    }
            ).optionalFieldOf("office_progress", Map.of());

    /**
     * Persistência: guarda a sigla da UF e o id do cargo (estáveis entre versões). Os quatro primeiros campos
     * são os mesmos da v0.1.0, então dados antigos carregam sem perda.
     */
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
            PROGRESS_MAP_CODEC.forGetter(PoliticalData::officeProgress)
    ).apply(instance, PoliticalData::new));

    private static final StreamCodec<ByteBuf, Optional<RegionalOrigin>> ORIGIN_STREAM_CODEC =
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT.map(i -> RegionalOrigin.values()[i], RegionalOrigin::ordinal));
    private static final StreamCodec<ByteBuf, PoliticalOffice> OFFICE_STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> PoliticalOffice.values()[i], PoliticalOffice::ordinal);
    private static final StreamCodec<ByteBuf, OfficeProgress> PROGRESS_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OfficeProgress::skillPoints,
            ByteBufCodecs.VAR_INT, OfficeProgress::onlineSeconds,
            OfficeProgress::new);
    private static final StreamCodec<ByteBuf, Map<PoliticalOffice, OfficeProgress>> PROGRESS_MAP_STREAM_CODEC =
            ByteBufCodecs.<ByteBuf, PoliticalOffice, OfficeProgress, Map<PoliticalOffice, OfficeProgress>>map(
                    HashMap::new, OFFICE_STREAM_CODEC, PROGRESS_STREAM_CODEC);

    public static final StreamCodec<ByteBuf, PoliticalData> STREAM_CODEC = StreamCodec.composite(
            ORIGIN_STREAM_CODEC, PoliticalData::origin,
            OFFICE_STREAM_CODEC, PoliticalData::office,
            ByteBufCodecs.VAR_INT, PoliticalData::politicalXp,
            ByteBufCodecs.VAR_INT, PoliticalData::popularity,
            PROGRESS_MAP_STREAM_CODEC, PoliticalData::officeProgress,
            PoliticalData::new);

    public PoliticalData {
        politicalXp = clamp(politicalXp);
        popularity = clamp(popularity);
        officeProgress = officeProgress == null || officeProgress.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(SkillProgression.copy(officeProgress));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_VALUE, value));
    }

    /** Pontos de habilidade acumulados no cargo informado (mantidos entre mandatos). */
    public int skillPoints(PoliticalOffice target) {
        return SkillProgression.skillPoints(officeProgress, target);
    }

    public PoliticalData withOrigin(RegionalOrigin newOrigin) {
        return new PoliticalData(Optional.of(newOrigin), office, politicalXp, popularity, officeProgress);
    }

    public PoliticalData withOffice(PoliticalOffice newOffice) {
        return new PoliticalData(origin, newOffice, politicalXp, popularity, officeProgress);
    }

    public PoliticalData withPoliticalXp(int newXp) {
        return new PoliticalData(origin, office, newXp, popularity, officeProgress);
    }

    public PoliticalData withPopularity(int newPopularity) {
        return new PoliticalData(origin, office, politicalXp, newPopularity, officeProgress);
    }

    public PoliticalData withOfficeProgress(Map<PoliticalOffice, OfficeProgress> newProgress) {
        return new PoliticalData(origin, office, politicalXp, popularity, newProgress);
    }
}
