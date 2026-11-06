package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.player.PoliticalData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: dados políticos do próprio jogador (HUD), com os requisitos de XP efetivos
 * (o cliente não conhece a configuração do servidor).
 *
 * @param currentOfficeMinXp XP mínimo do cargo atual (0 para Desempregado)
 * @param nextOfficeMinXp    XP mínimo do próximo cargo, ou -1 se não houver próximo
 */
public record PoliticalDataSyncPayload(PoliticalData data, int currentOfficeMinXp, int nextOfficeMinXp)
        implements CustomPacketPayload {
    public static final Type<PoliticalDataSyncPayload> TYPE = new Type<>(TupiniCraft.id("political_data"));
    public static final StreamCodec<ByteBuf, PoliticalDataSyncPayload> STREAM_CODEC = StreamCodec.composite(
            PoliticalData.STREAM_CODEC, PoliticalDataSyncPayload::data,
            ByteBufCodecs.VAR_INT, PoliticalDataSyncPayload::currentOfficeMinXp,
            ByteBufCodecs.INT, PoliticalDataSyncPayload::nextOfficeMinXp,
            PoliticalDataSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
