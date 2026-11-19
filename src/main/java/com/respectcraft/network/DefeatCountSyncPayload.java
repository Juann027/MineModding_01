package com.respectcraft.network;

import com.respectcraft.RespectCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pacote somente servidor -> cliente para o número na nameplate. */
public record DefeatCountSyncPayload(String playerUuid, int count) implements CustomPacketPayload {
    public static final Type<DefeatCountSyncPayload> TYPE = new Type<>(RespectCraft.id("defeat_count_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DefeatCountSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DefeatCountSyncPayload::playerUuid,
            ByteBufCodecs.VAR_INT, DefeatCountSyncPayload::count,
            DefeatCountSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
