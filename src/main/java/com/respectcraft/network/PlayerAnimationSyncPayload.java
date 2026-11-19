package com.respectcraft.network;

import com.respectcraft.RespectCraft;
import com.respectcraft.animation.PlayerAnimationState;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> clientes: animacao atual de um jogador; vazio = parou (ou nao ha nenhuma). */
public record PlayerAnimationSyncPayload(UUID player, Optional<PlayerAnimationState> state)
        implements CustomPacketPayload {
    public static final Type<PlayerAnimationSyncPayload> TYPE = new Type<>(RespectCraft.id("player_animation_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerAnimationSyncPayload> STREAM_CODEC =
            StreamCodec.composite(UUIDUtil.STREAM_CODEC, PlayerAnimationSyncPayload::player,
                    ByteBufCodecs.optional(PlayerAnimationState.STREAM_CODEC), PlayerAnimationSyncPayload::state,
                    PlayerAnimationSyncPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
