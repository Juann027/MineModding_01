package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.character.CharacterAppearanceService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Seleção cosmética enviada pelo cliente; servidor valida o inteiro compactado. */
public record CharacterAppearanceUpdatePayload(int appearance) implements CustomPacketPayload {
    public static final Type<CharacterAppearanceUpdatePayload> TYPE =
            new Type<>(TupiniCraft.id("character_appearance_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterAppearanceUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CharacterAppearanceUpdatePayload::appearance,
                    CharacterAppearanceUpdatePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void receive(CharacterAppearanceUpdatePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CharacterAppearanceService.apply(player, payload.appearance());
        }
    }
}
