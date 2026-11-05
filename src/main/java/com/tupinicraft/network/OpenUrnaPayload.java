package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → cliente: abrir a tela da urna eletrônica (após validação no servidor). */
public record OpenUrnaPayload() implements CustomPacketPayload {
    public static final OpenUrnaPayload INSTANCE = new OpenUrnaPayload();
    public static final Type<OpenUrnaPayload> TYPE = new Type<>(TupiniCraft.id("open_urna"));
    public static final StreamCodec<ByteBuf, OpenUrnaPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
