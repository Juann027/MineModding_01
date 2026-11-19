package com.respectcraft.network;

import com.respectcraft.RespectCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Aparência enviada para os clientes; appearance=-1 remove o jogador do cache. */
public record CharacterAppearanceSyncPayload(String uuid, int appearance) implements CustomPacketPayload {
    public static final Type<CharacterAppearanceSyncPayload> TYPE =
            new Type<>(RespectCraft.id("character_appearance_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterAppearanceSyncPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CharacterAppearanceSyncPayload::uuid,
                    ByteBufCodecs.VAR_INT, CharacterAppearanceSyncPayload::appearance,
                    CharacterAppearanceSyncPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
