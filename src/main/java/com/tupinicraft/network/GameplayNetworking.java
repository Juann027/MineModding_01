package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.needs.Needs;
import com.tupinicraft.profile.CharacterProfile;
import com.tupinicraft.profile.ProfileService;
import com.tupinicraft.skill.SkillService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Pacotes da ficha do personagem, das habilidades e das necessidades. O cliente so recebe copias para exibir
 * e envia intencoes (girar a roleta, comprar uma habilidade); o servidor revalida tudo.
 */
public final class GameplayNetworking {
    private GameplayNetworking() {}

    /** Servidor -> dono: ficha completa. */
    public record ProfileSync(CharacterProfile profile) implements CustomPacketPayload {
        public static final Type<ProfileSync> TYPE = new Type<>(TupiniCraft.id("profile_sync"));
        public static final StreamCodec<ByteBuf, ProfileSync> STREAM_CODEC =
                CharacterProfile.STREAM_CODEC.map(ProfileSync::new, ProfileSync::profile);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Servidor -> dono: necessidades (so quando alguma porcentagem muda). */
    public record NeedsSync(Needs needs) implements CustomPacketPayload {
        public static final Type<NeedsSync> TYPE = new Type<>(TupiniCraft.id("needs_sync"));
        public static final StreamCodec<ByteBuf, NeedsSync> STREAM_CODEC =
                Needs.STREAM_CODEC.map(NeedsSync::new, NeedsSync::needs);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cliente -> servidor: girar a roleta de nascimento (primeiro giro ou nova tentativa). */
    public record BirthRollRequest(boolean reroll) implements CustomPacketPayload {
        public static final Type<BirthRollRequest> TYPE = new Type<>(TupiniCraft.id("birth_roll_request"));
        public static final StreamCodec<ByteBuf, BirthRollRequest> STREAM_CODEC =
                ByteBufCodecs.BOOL.map(BirthRollRequest::new, BirthRollRequest::reroll);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cliente -> servidor: comprar a habilidade. */
    public record SkillPurchaseRequest(ResourceLocation skill) implements CustomPacketPayload {
        public static final Type<SkillPurchaseRequest> TYPE = new Type<>(TupiniCraft.id("skill_purchase_request"));
        public static final StreamCodec<ByteBuf, SkillPurchaseRequest> STREAM_CODEC =
                ResourceLocation.STREAM_CODEC.map(SkillPurchaseRequest::new, SkillPurchaseRequest::skill);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(ProfileSync.TYPE, ProfileSync.STREAM_CODEC,
                (payload, context) -> ProfileClientCache.receive(payload.profile()));
        registrar.playToClient(NeedsSync.TYPE, NeedsSync.STREAM_CODEC,
                (payload, context) -> ProfileClientCache.receive(payload.needs()));
        registrar.playToServer(BirthRollRequest.TYPE, BirthRollRequest.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) ProfileService.requestRoll(player, payload.reroll());
        });
        registrar.playToServer(SkillPurchaseRequest.TYPE, SkillPurchaseRequest.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) SkillService.purchase(player, payload.skill());
        });
    }
}
