package com.tupinicraft.animation;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Estado de uma animacao em andamento, decidido pelo servidor e enviado aos clientes.
 *
 * @param animation     id em {@link PlayerAnimations}
 * @param startGameTime tempo de jogo (ticks do mundo) em que comecou; o cliente calcula quanto ja passou
 * @param durationTicks duracao (ou ciclo, em loop)
 * @param loop          repete ate o servidor mandar parar
 * @param sequence      numero da execucao deste jogador; muda a cada novo inicio (repetir a mesma animacao reinicia)
 * @param partner       outro jogador da mesma sequencia sincronizada, se houver
 * @param params        parametros livres para sistemas futuros (variante, intensidade...)
 */
public record PlayerAnimationState(ResourceLocation animation, long startGameTime, int durationTicks, boolean loop,
                                   int sequence, Optional<UUID> partner, CompoundTag params) {
    public static final StreamCodec<ByteBuf, PlayerAnimationState> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PlayerAnimationState decode(ByteBuf buf) {
            return new PlayerAnimationState(ResourceLocation.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf),
                    ByteBufCodecs.COMPOUND_TAG.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, PlayerAnimationState state) {
            ResourceLocation.STREAM_CODEC.encode(buf, state.animation());
            ByteBufCodecs.VAR_LONG.encode(buf, state.startGameTime());
            ByteBufCodecs.VAR_INT.encode(buf, state.durationTicks());
            ByteBufCodecs.BOOL.encode(buf, state.loop());
            ByteBufCodecs.VAR_INT.encode(buf, state.sequence());
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buf, state.partner());
            ByteBufCodecs.COMPOUND_TAG.encode(buf, state.params());
        }
    };

    /** Ticks decorridos desde o inicio (nunca negativo). */
    public long elapsed(long gameTime) {
        return Math.max(0L, gameTime - startGameTime);
    }

    public boolean finished(long gameTime) {
        return !loop && gameTime - startGameTime >= durationTicks;
    }
}
