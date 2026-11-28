package com.respectcraft.network;

import com.respectcraft.RespectCraft;
import com.respectcraft.bounty.BountyService;
import com.respectcraft.notice.HoloNotice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Pacotes do menu holografico (Etapa 4.11): avisos automaticos ({@link NoticePayload}) e a caca a recompensa
 * ({@link BountyBoardSync}, {@link BountyActionRequest}). O servidor decide; o cliente so mostra e manda intencoes.
 */
public final class HoloNetworking {
    private HoloNetworking() {}

    /** Servidor -> jogador: um aviso holografico. */
    public record NoticePayload(HoloNotice notice) implements CustomPacketPayload {
        public static final Type<NoticePayload> TYPE = new Type<>(RespectCraft.id("holo_notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NoticePayload> STREAM_CODEC =
                HoloNotice.STREAM_CODEC.map(NoticePayload::new, NoticePayload::notice);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Uma linha do quadro de recompensas como o jogador que recebe a ve. {@code flags}: {@link #MINE} (eu paguei),
     * {@link #ON_ME} (o alvo sou eu), {@link #HUNTING} (aceitei). Rastreio (so para quem aceitou):
     * {@code distance} arredondada ({@code NoticeRules.roughDistance}) e {@code direction} em oito ventos, ou
     * {@link #TRACK_OTHER_DIMENSION}/{@link #TRACK_OFFLINE}/{@link #TRACK_NONE}.
     */
    public record BountyEntry(UUID id, UUID target, String targetName, String posterName, ItemStack reward,
                              long remainingTicks, int hunters, int flags, int distance, int direction) {
        public static final int MINE = 1, ON_ME = 2, HUNTING = 4;
        public static final int TRACK_NONE = -1, TRACK_OTHER_DIMENSION = -2, TRACK_OFFLINE = -3;

        public boolean mine() { return (flags & MINE) != 0; }
        public boolean onMe() { return (flags & ON_ME) != 0; }
        public boolean hunting() { return (flags & HUNTING) != 0; }
        public String shortId() { return id.toString().substring(0, 8); }

        public static final StreamCodec<RegistryFriendlyByteBuf, BountyEntry> STREAM_CODEC = StreamCodec.of(
                (buf, e) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, e.id());
                    UUIDUtil.STREAM_CODEC.encode(buf, e.target());
                    buf.writeUtf(e.targetName(), 64);
                    buf.writeUtf(e.posterName(), 64);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, e.reward());
                    buf.writeVarLong(e.remainingTicks());
                    buf.writeVarInt(e.hunters());
                    buf.writeVarInt(e.flags());
                    buf.writeInt(e.distance());
                    buf.writeByte(e.direction());
                },
                buf -> new BountyEntry(UUIDUtil.STREAM_CODEC.decode(buf), UUIDUtil.STREAM_CODEC.decode(buf),
                        buf.readUtf(64), buf.readUtf(64), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong(),
                        buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readByte()));
    }

    /** Servidor -> jogador: o quadro inteiro (login, quando muda e quando a aba pede). */
    public record BountyBoardSync(List<BountyEntry> entries) implements CustomPacketPayload {
        public static final Type<BountyBoardSync> TYPE = new Type<>(RespectCraft.id("bounty_board_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyBoardSync> STREAM_CODEC =
                BountyEntry.STREAM_CODEC.apply(ByteBufCodecs.list(256)).map(BountyBoardSync::new, BountyBoardSync::entries);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** O que o jogador pede pela aba Bounty Hunt. */
    public enum BountyAction { REFRESH, PLACE, ACCEPT, ABANDON, CANCEL;
        static BountyAction byOrdinal(int i) { BountyAction[] a = values(); return i >= 0 && i < a.length ? a[i] : REFRESH; }
    }

    /**
     * Cliente -> servidor: intencao na aba. PLACE usa {@code target} e {@code days} (a recompensa e sempre a pilha da
     * mao principal, lida no servidor); ACCEPT/ABANDON/CANCEL usam {@code bounty}.
     */
    public record BountyActionRequest(BountyAction action, Optional<UUID> bounty, Optional<UUID> target, int days)
            implements CustomPacketPayload {
        public static final Type<BountyActionRequest> TYPE = new Type<>(RespectCraft.id("bounty_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyActionRequest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.map(BountyAction::byOrdinal, BountyAction::ordinal), BountyActionRequest::action,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), BountyActionRequest::bounty,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), BountyActionRequest::target,
                ByteBufCodecs.VAR_INT, BountyActionRequest::days,
                BountyActionRequest::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // "2": Etapa 4.14.1 (aviso com apresentacao, estilo, subtitulo, duracao, grupo e modificadores).
        var registrar = event.registrar("2");
        registrar.playToClient(NoticePayload.TYPE, NoticePayload.STREAM_CODEC,
                (payload, context) -> NoticeClientCache.receive(payload.notice()));
        registrar.playToClient(BountyBoardSync.TYPE, BountyBoardSync.STREAM_CODEC,
                (payload, context) -> BountyClientCache.receive(payload.entries()));
        registrar.playToServer(BountyActionRequest.TYPE, BountyActionRequest.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) BountyService.handle(player, payload);
        });
    }
}
