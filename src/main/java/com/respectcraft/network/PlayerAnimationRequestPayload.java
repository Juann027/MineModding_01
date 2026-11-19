package com.respectcraft.network;

import com.respectcraft.RespectCraft;
import com.respectcraft.animation.PlayerAnimationService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: so a intencao de tocar (ou parar) uma animacao; o servidor valida tudo. */
public record PlayerAnimationRequestPayload(ResourceLocation animation, boolean stop) implements CustomPacketPayload {
    public static final Type<PlayerAnimationRequestPayload> TYPE = new Type<>(RespectCraft.id("player_animation_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerAnimationRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, PlayerAnimationRequestPayload::animation,
                    ByteBufCodecs.BOOL, PlayerAnimationRequestPayload::stop,
                    PlayerAnimationRequestPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void receive(PlayerAnimationRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PlayerAnimationService.handleRequest(player, payload.animation(), payload.stop());
        }
    }
}
