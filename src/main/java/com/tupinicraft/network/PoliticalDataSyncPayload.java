package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.player.PoliticalData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → cliente: dados políticos do próprio jogador (HUD). */
public record PoliticalDataSyncPayload(PoliticalData data) implements CustomPacketPayload {
    public static final Type<PoliticalDataSyncPayload> TYPE = new Type<>(TupiniCraft.id("political_data"));
    public static final StreamCodec<ByteBuf, PoliticalDataSyncPayload> STREAM_CODEC =
            PoliticalData.STREAM_CODEC.map(PoliticalDataSyncPayload::new, PoliticalDataSyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
