package com.tupinicraft.needs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Valores das necessidades em unidades de 0 a {@link #MAX} (100,00%). Imutavel.
 * O HUD mostra {@code valor / 100}. Persistido (attachment), mas nao copiado na morte: corpo novo, valores novos.
 */
public record Needs(int hydration, int energy, int bladder, int bowel, int mood) {
    public static final int MAX = 10_000;
    public static final Needs DEFAULT = new Needs(MAX, MAX, 0, 0, 6_000);

    public static final Codec<Needs> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("hidratacao", MAX).forGetter(Needs::hydration),
            Codec.INT.optionalFieldOf("energia", MAX).forGetter(Needs::energy),
            Codec.INT.optionalFieldOf("bexiga", 0).forGetter(Needs::bladder),
            Codec.INT.optionalFieldOf("intestino", 0).forGetter(Needs::bowel),
            Codec.INT.optionalFieldOf("humor", 6_000).forGetter(Needs::mood)
    ).apply(i, Needs::new));

    public static final StreamCodec<ByteBuf, Needs> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Needs::hydration, ByteBufCodecs.VAR_INT, Needs::energy,
            ByteBufCodecs.VAR_INT, Needs::bladder, ByteBufCodecs.VAR_INT, Needs::bowel,
            ByteBufCodecs.VAR_INT, Needs::mood, Needs::new);

    public Needs {
        hydration = clamp(hydration);
        energy = clamp(energy);
        bladder = clamp(bladder);
        bowel = clamp(bowel);
        mood = clamp(mood);
    }

    public static int clamp(int value) { return Math.max(0, Math.min(MAX, value)); }

    public int get(Need need) {
        return switch (need) {
            case HYDRATION -> hydration;
            case ENERGY -> energy;
            case BLADDER -> bladder;
            case BOWEL -> bowel;
            case MOOD -> mood;
        };
    }

    public Needs with(Need need, int value) {
        return switch (need) {
            case HYDRATION -> new Needs(value, energy, bladder, bowel, mood);
            case ENERGY -> new Needs(hydration, value, bladder, bowel, mood);
            case BLADDER -> new Needs(hydration, energy, value, bowel, mood);
            case BOWEL -> new Needs(hydration, energy, bladder, value, mood);
            case MOOD -> new Needs(hydration, energy, bladder, bowel, value);
        };
    }

    public Needs plus(NeedsDelta delta) {
        return new Needs(hydration + delta.hydration(), energy + delta.energy(), bladder + delta.bladder(),
                bowel + delta.bowel(), mood + delta.mood());
    }

    /** Porcentagem inteira mostrada no HUD. */
    public int percent(Need need) { return get(need) / 100; }

    /** Mesma leitura no HUD: so sincroniza quando alguma porcentagem muda. */
    public boolean sameDisplay(Needs other) {
        for (Need need : Need.values()) if (percent(need) != other.percent(need)) return false;
        return true;
    }
}
