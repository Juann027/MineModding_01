package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → cliente: saldo da carteira do próprio jogador (só para o HUD). */
public record WalletSyncPayload(long balance) implements CustomPacketPayload {
    public static final Type<WalletSyncPayload> TYPE = new Type<>(TupiniCraft.id("wallet"));
    public static final StreamCodec<ByteBuf, WalletSyncPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(WalletSyncPayload::new, WalletSyncPayload::balance);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
