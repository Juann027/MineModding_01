package com.respectcraft.network;

import com.respectcraft.RespectCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Pacotes do Time Warp (Etapa 4.15), so servidor -> cliente e sempre para um jogador. {@link State}: esta no Time
 * Warp e qual estagio de exposicao (login, troca de dimensao, renascimento e quando o estagio muda; nunca por tick).
 * {@link Cue}: um acontecimento a tocar/mostrar no cliente (som posicionado, passos, pulso de neblina, silencio). O
 * cliente aplica o volume da propria config e a categoria Ambiente do vanilla; nada e decidido no cliente.
 */
public final class TimeWarpNetworking {
    private TimeWarpNetworking() {}

    public record State(boolean inWarp, int stage) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(RespectCraft.id("time_warp_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, State::inWarp, ByteBufCodecs.VAR_INT, State::stage, State::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Tipos de pista (o pacote usa o numero: novos entram no fim). */
    public static final int CUE_SOUND = 0, CUE_STEPS = 1, CUE_FOG = 2, CUE_SILENCE = 3;

    /**
     * @param kind     {@code CUE_*}
     * @param sound    evento de som (SOUND/STEPS)
     * @param x,y,z    origem
     * @param dx,dz    deslocamento por passo (STEPS)
     * @param count    passos (STEPS)
     * @param interval ticks entre passos (STEPS) ou duracao (FOG/SILENCE)
     * @param strength forca do pulso (FOG)
     */
    public record Cue(int kind, ResourceLocation sound, double x, double y, double z, float volume, float pitch,
                      float dx, float dz, int count, int interval, float strength) implements CustomPacketPayload {
        public static final Type<Cue> TYPE = new Type<>(RespectCraft.id("time_warp_cue"));
        public static final StreamCodec<ByteBuf, Cue> STREAM_CODEC = StreamCodec.of((buf, c) -> {
            ByteBufCodecs.VAR_INT.encode(buf, c.kind);
            ResourceLocation.STREAM_CODEC.encode(buf, c.sound);
            buf.writeDouble(c.x);
            buf.writeDouble(c.y);
            buf.writeDouble(c.z);
            buf.writeFloat(c.volume);
            buf.writeFloat(c.pitch);
            buf.writeFloat(c.dx);
            buf.writeFloat(c.dz);
            ByteBufCodecs.VAR_INT.encode(buf, c.count);
            ByteBufCodecs.VAR_INT.encode(buf, c.interval);
            buf.writeFloat(c.strength);
        }, buf -> new Cue(ByteBufCodecs.VAR_INT.decode(buf), ResourceLocation.STREAM_CODEC.decode(buf), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(State.TYPE, State.STREAM_CODEC,
                (payload, context) -> com.respectcraft.client.dimension.TimeWarpClient.onState(payload.inWarp(), payload.stage()));
        registrar.playToClient(Cue.TYPE, Cue.STREAM_CODEC,
                (payload, context) -> com.respectcraft.client.dimension.TimeWarpClient.onCue(payload));
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
