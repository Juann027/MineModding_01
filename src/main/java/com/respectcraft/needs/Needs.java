package com.respectcraft.needs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Valores das necessidades em unidades de 0 a {@link #MAX} (100,00%). Imutavel.
 * O HUD mostra {@code valor / 100}. Persistido (attachment), mas nao copiado na morte: corpo novo, valores novos.
 * {@code coom} entrou na Etapa 3.1 (campo opcional: saves antigos carregam com 0).
 */
public record Needs(int hydration, int energy, int bladder, int bowel, int mood, int coom) {
    public static final int MAX = 10_000;
    public static final Needs DEFAULT = new Needs(MAX, MAX, 0, 0, 6_000, 0);

    public static final Codec<Needs> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("hidratacao", MAX).forGetter(Needs::hydration),
            Codec.INT.optionalFieldOf("energia", MAX).forGetter(Needs::energy),
            Codec.INT.optionalFieldOf("bexiga", 0).forGetter(Needs::bladder),
            Codec.INT.optionalFieldOf("intestino", 0).forGetter(Needs::bowel),
            Codec.INT.optionalFieldOf("humor", 6_000).forGetter(Needs::mood),
            Codec.INT.optionalFieldOf("coom", 0).forGetter(Needs::coom)
    ).apply(i, Needs::new));

    public static final StreamCodec<ByteBuf, Needs> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Needs::hydration, ByteBufCodecs.VAR_INT, Needs::energy,
            ByteBufCodecs.VAR_INT, Needs::bladder, ByteBufCodecs.VAR_INT, Needs::bowel,
            ByteBufCodecs.VAR_INT, Needs::mood, ByteBufCodecs.VAR_INT, Needs::coom, Needs::new);

    public Needs {
        hydration = clamp(hydration);
        energy = clamp(energy);
        bladder = clamp(bladder);
        bowel = clamp(bowel);
        mood = clamp(mood);
        coom = clamp(coom);
    }

    /** Compatibilidade com o formato da Etapa 3 (sem coom). */
    public Needs(int hydration, int energy, int bladder, int bowel, int mood) {
        this(hydration, energy, bladder, bowel, mood, 0);
    }

    public static int clamp(int value) { return Math.max(0, Math.min(MAX, value)); }

    public int get(Need need) {
        return switch (need) {
            case HYDRATION -> hydration;
            case ENERGY -> energy;
            case BLADDER -> bladder;
            case BOWEL -> bowel;
            case MOOD -> mood;
            case COOM -> coom;
        };
    }

    public Needs with(Need need, int value) {
        return switch (need) {
            case HYDRATION -> new Needs(value, energy, bladder, bowel, mood, coom);
            case ENERGY -> new Needs(hydration, value, bladder, bowel, mood, coom);
            case BLADDER -> new Needs(hydration, energy, value, bowel, mood, coom);
            case BOWEL -> new Needs(hydration, energy, bladder, value, mood, coom);
            case MOOD -> new Needs(hydration, energy, bladder, bowel, value, coom);
            case COOM -> new Needs(hydration, energy, bladder, bowel, mood, value);
        };
    }

    public Needs plus(NeedsDelta delta) {
        return new Needs(hydration + delta.hydration(), energy + delta.energy(), bladder + delta.bladder(),
                bowel + delta.bowel(), mood + delta.mood(), coom + delta.coom());
    }

    /** Porcentagem inteira mostrada no HUD. */
    public int percent(Need need) { return get(need) / 100; }

    /** Mesma leitura no HUD: so sincroniza quando alguma porcentagem muda. */
    public boolean sameDisplay(Needs other) {
        for (Need need : Need.values()) if (percent(need) != other.percent(need)) return false;
        return true;
    }
}
