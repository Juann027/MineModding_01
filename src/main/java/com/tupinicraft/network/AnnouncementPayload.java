package com.tupinicraft.network;
import com.tupinicraft.TupiniCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** Global event card. Stable category ID is independent of translated message text. */
public record AnnouncementPayload(String category, Component title, Component message, int accent, int durationMillis) implements CustomPacketPayload {
    public AnnouncementPayload {
        durationMillis = Math.max(2500, Math.min(15000, durationMillis));
        accent |= 0xFF000000;
    }
    public AnnouncementPayload(Component message) {
        this("global", Component.literal("EVENTO GLOBAL"), message, 0xFF70E0B7, 6500);
    }
    public static final Type<AnnouncementPayload> TYPE = new Type<>(TupiniCraft.id("announcement"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnnouncementPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, AnnouncementPayload::category,
        ComponentSerialization.STREAM_CODEC, AnnouncementPayload::title,
        ComponentSerialization.STREAM_CODEC, AnnouncementPayload::message,
        ByteBufCodecs.INT, AnnouncementPayload::accent,
        ByteBufCodecs.VAR_INT, AnnouncementPayload::durationMillis, AnnouncementPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
