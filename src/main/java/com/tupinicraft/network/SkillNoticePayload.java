package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: resultado de compra ou mudança de estado de uma skill, mostrado como notificação
 * (toast) no canto da tela, visível inclusive com a tela da árvore aberta.
 */
public record SkillNoticePayload(boolean success, Component title, Component message) implements CustomPacketPayload {
    public static final Type<SkillNoticePayload> TYPE = new Type<>(TupiniCraft.id("skill_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkillNoticePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SkillNoticePayload::success,
            ComponentSerialization.STREAM_CODEC, SkillNoticePayload::title,
            ComponentSerialization.STREAM_CODEC, SkillNoticePayload::message,
            SkillNoticePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
