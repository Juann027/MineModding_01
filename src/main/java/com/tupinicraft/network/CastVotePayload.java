package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Cliente → servidor: pedido de voto. Contém só o candidato; o eleitor é sempre o jogador
 * da conexão, e todas as regras são revalidadas no servidor.
 */
public record CastVotePayload(UUID candidate) implements CustomPacketPayload {
    public static final Type<CastVotePayload> TYPE = new Type<>(TupiniCraft.id("cast_vote"));
    public static final StreamCodec<ByteBuf, CastVotePayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(CastVotePayload::new, CastVotePayload::candidate);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
