package com.respectcraft.network;

import com.respectcraft.RespectCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Estado VISUAL de teste. Nenhuma animacao explicita e transmitida por este pacote. */
public record FinisherVisualSyncPayload(String attackerUuid, String targetUuid, String animationKey,
                                        boolean active, int durationTicks) implements CustomPacketPayload {
    public static final Type<FinisherVisualSyncPayload> TYPE = new Type<>(RespectCraft.id("finisher_visual_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FinisherVisualSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FinisherVisualSyncPayload::attackerUuid,
            ByteBufCodecs.STRING_UTF8, FinisherVisualSyncPayload::targetUuid,
            ByteBufCodecs.STRING_UTF8, FinisherVisualSyncPayload::animationKey,
            ByteBufCodecs.BOOL, FinisherVisualSyncPayload::active,
            ByteBufCodecs.VAR_INT, FinisherVisualSyncPayload::durationTicks,
            FinisherVisualSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
