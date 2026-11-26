package com.respectcraft.notice;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Um aviso holografico (Etapa 4.11): categoria, gravidade, titulo, texto, local opcional (dimensao + bloco, para a
 * distancia/direcao no cliente e o farol do Calamidade) e uma chave de duplicata (o cliente ignora o mesmo aviso
 * repetido dentro de {@link NoticeRules#DEDUPE_MILLIS}). So exibicao: nada aqui muda dados de jogo.
 */
public record HoloNotice(NoticeKind kind, NoticeSeverity severity, Component title, Component body,
                         Optional<Location> location, String dedupeKey) {
    /** Onde o evento aconteceu. */
    public record Location(ResourceLocation dimension, BlockPos pos) {}

    public HoloNotice {
        dedupeKey = dedupeKey == null ? "" : dedupeKey;
        if (dedupeKey.length() > 128) dedupeKey = dedupeKey.substring(0, 128);
    }

    public static HoloNotice of(NoticeKind kind, Component title, Component body) {
        return new HoloNotice(kind, kind.defaultSeverity(), title, body, Optional.empty(), "");
    }

    public HoloNotice at(Level level, BlockPos pos) {
        return new HoloNotice(kind, severity, title, body,
                Optional.of(new Location(level.dimension().location(), pos.immutable())), dedupeKey);
    }

    public HoloNotice severity(NoticeSeverity s) {
        return new HoloNotice(kind, s, title, body, location, dedupeKey);
    }

    public HoloNotice dedupe(String key) {
        return new HoloNotice(kind, severity, title, body, location, key);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, HoloNotice> STREAM_CODEC = StreamCodec.of(
            (buf, n) -> {
                buf.writeVarInt(n.kind().ordinal());
                buf.writeVarInt(n.severity().ordinal());
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, n.title());
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, n.body());
                buf.writeBoolean(n.location().isPresent());
                n.location().ifPresent(l -> {
                    buf.writeResourceLocation(l.dimension());
                    buf.writeBlockPos(l.pos());
                });
                buf.writeUtf(n.dedupeKey(), 128);
            },
            buf -> {
                NoticeKind kind = NoticeKind.byOrdinal(buf.readVarInt());
                NoticeSeverity severity = NoticeSeverity.byOrdinal(buf.readVarInt());
                Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                Component body = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                Optional<Location> location = buf.readBoolean()
                        ? Optional.of(new Location(buf.readResourceLocation(), buf.readBlockPos())) : Optional.empty();
                return new HoloNotice(kind, severity, title, body, location, buf.readUtf(128));
            });
}
