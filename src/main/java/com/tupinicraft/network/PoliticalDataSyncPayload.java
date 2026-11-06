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
 * @param currentOfficeMinXp maior XP mínimo de cargo já alcançado (base da barra de XP)
 * @param nextOfficeMinXp    XP mínimo do próximo cargo a desbloquear por XP, ou -1 se todos já estão liberados
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
