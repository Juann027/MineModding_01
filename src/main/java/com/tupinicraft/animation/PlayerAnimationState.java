package com.tupinicraft.animation;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
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
 * <p>Pode ser uma sequencia ({@link PlayerAnimationService#playSequence}): o registro descreve o segmento atual e
 * {@code then} os seguintes. Os instantes de troca saem so de {@code startGameTime} e das duracoes, entao servidor
 * e clientes trocam de segmento no mesmo tick sem pacote extra (sem voltar a pose vanilla entre os clips).
 * Use {@link #at(long)} para obter o segmento vigente num instante.
 *
 * @param animation     id em {@link PlayerAnimations} (segmento atual)
 * @param startGameTime tempo de jogo (ticks do mundo) em que o segmento atual comecou
 * @param durationTicks duracao do clip (ou ciclo, em loop)
 * @param loop          o clip repete
 * @param sequence      numero da execucao deste jogador; muda a cada novo inicio (repetir a mesma animacao reinicia)
 * @param partner       outro jogador da mesma sequencia sincronizada, se houver
 * @param params        parametros livres para sistemas futuros (variante, intensidade...)
 * @param segmentTicks  quanto o segmento dura; 0 = natural (clip unico: fim do clip; loop: ate mandarem parar)
 * @param segment       indice do segmento na sequencia (0 no primeiro)
 * @param then          segmentos seguintes, em ordem
 */
public record PlayerAnimationState(ResourceLocation animation, long startGameTime, int durationTicks, boolean loop,
                                   int sequence, Optional<UUID> partner, CompoundTag params,
                                   int segmentTicks, int segment, List<Step> then) {
    /** Maximo de segmentos de uma sequencia (protege o pacote). */
    public static final int MAX_STEPS = 16;

    /**
     * Um segmento seguinte.
     *
     * @param segmentTicks quanto tempo fica neste segmento; obrigatorio (&gt; 0) para clips em loop dentro de uma
     *                     sequencia, 0 = o comprimento do clip
     */
    public record Step(ResourceLocation animation, int durationTicks, boolean loop, int segmentTicks) {
        static final StreamCodec<ByteBuf, Step> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Step::animation,
                ByteBufCodecs.VAR_INT, Step::durationTicks,
                ByteBufCodecs.BOOL, Step::loop,
                ByteBufCodecs.VAR_INT, Step::segmentTicks,
                Step::new);
    }

    public PlayerAnimationState {
        then = List.copyOf(then);
    }

    /** Animacao avulsa (sem sequencia), como antes da Etapa 4.1. */
    public PlayerAnimationState(ResourceLocation animation, long startGameTime, int durationTicks, boolean loop,
                                int sequence, Optional<UUID> partner, CompoundTag params) {
        this(animation, startGameTime, durationTicks, loop, sequence, partner, params, 0, 0, List.of());
    }

    public static final StreamCodec<ByteBuf, PlayerAnimationState> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PlayerAnimationState decode(ByteBuf buf) {
            ResourceLocation animation = ResourceLocation.STREAM_CODEC.decode(buf);
            long start = ByteBufCodecs.VAR_LONG.decode(buf);
            int duration = ByteBufCodecs.VAR_INT.decode(buf);
            boolean loop = ByteBufCodecs.BOOL.decode(buf);
            int sequence = ByteBufCodecs.VAR_INT.decode(buf);
            Optional<UUID> partner = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf);
            CompoundTag params = ByteBufCodecs.COMPOUND_TAG.decode(buf);
            int segmentTicks = ByteBufCodecs.VAR_INT.decode(buf);
            int segment = ByteBufCodecs.VAR_INT.decode(buf);
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            if (count < 0 || count > MAX_STEPS) throw new IllegalArgumentException("Sequencia de animacao invalida: " + count);
            List<Step> then = new ArrayList<>(count);
            for (int i = 0; i < count; i++) then.add(Step.STREAM_CODEC.decode(buf));
            return new PlayerAnimationState(animation, start, duration, loop, sequence, partner, params,
                    segmentTicks, segment, then);
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
            ByteBufCodecs.VAR_INT.encode(buf, state.segmentTicks());
            ByteBufCodecs.VAR_INT.encode(buf, state.segment());
            ByteBufCodecs.VAR_INT.encode(buf, state.then().size());
            for (Step step : state.then()) Step.STREAM_CODEC.encode(buf, step);
        }
    };

    /** Ticks decorridos desde o inicio do segmento (nunca negativo). */
    public long elapsed(long gameTime) {
        return Math.max(0L, gameTime - startGameTime);
    }

    /** Ha segmentos depois deste: o clip atual deve segurar o ultimo quadro ate a troca (sem voltar ao vanilla). */
    public boolean hasNext() {
        return !then.isEmpty();
    }

    /** Tick (do mundo) em que o segmento atual termina; {@link Long#MAX_VALUE} = loop sem fim. */
    public long segmentEnd() {
        if (segmentTicks > 0) return startGameTime + segmentTicks;
        return loop ? Long.MAX_VALUE : startGameTime + durationTicks;
    }

    /** Segmento vigente no instante dado (o proprio estado, se ainda nao acabou ou se e o ultimo). */
    public PlayerAnimationState at(long gameTime) {
        PlayerAnimationState current = this;
        while (current.hasNext() && gameTime >= current.segmentEnd()) current = current.next(current.segmentEnd());
        return current;
    }

    /** O proximo segmento, comecando em {@code start} (usado tambem para sair de um loop antes da hora). */
    public PlayerAnimationState next(long start) {
        Step step = then.get(0);
        return new PlayerAnimationState(step.animation(), start, step.durationTicks(), step.loop(), sequence, partner,
                params, step.segmentTicks(), segment + 1, then.subList(1, then.size()));
    }

    /** A sequencia inteira acabou (o ultimo segmento chegou ao fim). */
    public boolean finished(long gameTime) {
        return gameTime >= at(gameTime).segmentEnd();
    }
}
