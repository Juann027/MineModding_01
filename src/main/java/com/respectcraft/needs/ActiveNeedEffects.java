package com.respectcraft.needs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Efeitos de consumiveis em andamento (attachment {@code respectcraft:need_effects}, salvo, nao copiado na morte).
 * Cada entrada guarda so o id da definicao e os segundos decorridos; o resto vem de {@link NeedEffects}.
 */
public record ActiveNeedEffects(List<Entry> entries) {
    public static final ActiveNeedEffects EMPTY = new ActiveNeedEffects(List.of());

    public record Entry(String id, int elapsed) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                Codec.INT.optionalFieldOf("elapsed", 0).forGetter(Entry::elapsed)
        ).apply(i, Entry::new));
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::id, ByteBufCodecs.VAR_INT, Entry::elapsed, Entry::new);
    }

    public static final Codec<ActiveNeedEffects> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(ActiveNeedEffects::entries)
    ).apply(i, ActiveNeedEffects::new));

    public static final StreamCodec<ByteBuf, ActiveNeedEffects> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(64)).map(ActiveNeedEffects::new, ActiveNeedEffects::entries);

    public ActiveNeedEffects {
        entries = List.copyOf(entries);
    }

    /** Consumir de novo o mesmo item reinicia o efeito (nao acumula duas copias). */
    public ActiveNeedEffects start(String id) {
        List<Entry> next = new ArrayList<>();
        for (Entry e : entries) if (!e.id().equals(id)) next.add(e);
        next.add(new Entry(id, 0));
        return new ActiveNeedEffects(next);
    }

    public boolean isEmpty() { return entries.isEmpty(); }
}
