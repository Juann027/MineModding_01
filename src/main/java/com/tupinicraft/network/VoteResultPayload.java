package com.tupinicraft.network;
import com.tupinicraft.TupiniCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record VoteResultPayload(Component message) implements CustomPacketPayload {
    public static final Type<VoteResultPayload> TYPE = new Type<>(TupiniCraft.id("vote_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoteResultPayload> STREAM_CODEC =
        ComponentSerialization.STREAM_CODEC.map(VoteResultPayload::new, VoteResultPayload::message);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
