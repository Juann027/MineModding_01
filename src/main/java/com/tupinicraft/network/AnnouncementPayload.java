package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → cliente: aviso eleitoral exibido como faixa na tela (em vez do chat). */
public record AnnouncementPayload(Component message) implements CustomPacketPayload {
    public static final Type<AnnouncementPayload> TYPE = new Type<>(TupiniCraft.id("announcement"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnnouncementPayload> STREAM_CODEC =
            ComponentSerialization.STREAM_CODEC.map(AnnouncementPayload::new, AnnouncementPayload::message);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
