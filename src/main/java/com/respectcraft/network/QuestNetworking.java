package com.respectcraft.network;

import com.respectcraft.RespectCraft;
import com.respectcraft.quest.QuestService;
import com.respectcraft.quest.QuestState;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Pacotes das missoes (Etapa 4.14.2). Sincronizacao <b>individual</b>: cada jogador so recebe as proprias missoes
 * ja descobertas. Missao oculta nunca aparece aqui (nem id, texto, condicao ou objetivo); a unica coisa de uma
 * missao oculta que pode sair e a pista ({@code hint}) quando a definicao a declara, sem id.
 *
 * <p>Servidor -> cliente: {@link QuestSnapshot} (login, reload, descoberta, reset) e {@link QuestUpdate}
 * (incremental). Cliente -> servidor: {@link QuestActionRequest}, so intencao; o {@code QuestService} valida tudo.
 */
public final class QuestNetworking {
    private QuestNetworking() {}

    public static final int MAX_ENTRIES = 256, MAX_LINES = 16, MAX_HINTS = 16;

    /** Uma linha de objetivo como o jogador ve: texto pronto, valor e meta. */
    public record ObjectiveView(Component label, int value, int count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ObjectiveView> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, ObjectiveView::label,
                ByteBufCodecs.VAR_INT, ObjectiveView::value,
                ByteBufCodecs.VAR_INT, ObjectiveView::count,
                ObjectiveView::new);
    }

    /**
     * Uma missao descoberta. {@code remainingTicks}: tempo limite restante (ativa) ou intervalo ate reabrir
     * (repetivel resgatada); -1 = nada. {@code flags}: ver as constantes.
     */
    public record QuestView(ResourceLocation id, String category, QuestState state, Component title, Component description,
                            List<ObjectiveView> objectives, List<Component> rewards, List<Component> requirements,
                            long remainingTicks, int completions, int flags, int sort) {
        public static final int SECRET = 1, TRACKED = 2, MANUAL_ACCEPT = 4, MANUAL_CLAIM = 8, ABANDONABLE = 16,
                REPEATABLE = 32, CAN_ACCEPT = 64, RETRY = 128;

        public boolean has(int flag) { return (flags & flag) != 0; }

        public static final StreamCodec<RegistryFriendlyByteBuf, QuestView> STREAM_CODEC = StreamCodec.of(
                (buf, v) -> {
                    buf.writeResourceLocation(v.id());
                    buf.writeUtf(v.category(), 32);
                    buf.writeVarInt(v.state().ordinal());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, v.title());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, v.description());
                    ObjectiveView.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)).encode(buf, v.objectives());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)).encode(buf, v.rewards());
                    ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)).encode(buf, v.requirements());
                    buf.writeVarLong(v.remainingTicks() + 1);
                    buf.writeVarInt(v.completions());
                    buf.writeVarInt(v.flags());
                    buf.writeInt(v.sort());
                },
                buf -> new QuestView(buf.readResourceLocation(), buf.readUtf(32), QuestState.byOrdinal(buf.readVarInt()),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ObjectiveView.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)).decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)).decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)).decode(buf),
                        buf.readVarLong() - 1, buf.readVarInt(), buf.readVarInt(), buf.readInt()));
    }

    /** Todas as missoes descobertas do jogador + as pistas declaradas das ocultas (sem id). */
    public record QuestSnapshot(List<QuestView> quests, List<Component> hints) implements CustomPacketPayload {
        public static final Type<QuestSnapshot> TYPE = new Type<>(RespectCraft.id("quest_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestSnapshot> STREAM_CODEC = StreamCodec.composite(
                QuestView.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), QuestSnapshot::quests,
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_HINTS)), QuestSnapshot::hints,
                QuestSnapshot::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Uma missao mudou (progresso, estado, rastreio). */
    public record QuestUpdate(QuestView quest) implements CustomPacketPayload {
        public static final Type<QuestUpdate> TYPE = new Type<>(RespectCraft.id("quest_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestUpdate> STREAM_CODEC =
                QuestView.STREAM_CODEC.map(QuestUpdate::new, QuestUpdate::quest);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public enum QuestAction { REFRESH, ACCEPT, ABANDON, CLAIM, TRACK, UNTRACK;
        public static QuestAction byOrdinal(int i) { QuestAction[] a = values(); return i >= 0 && i < a.length ? a[i] : REFRESH; }
    }

    /** Cliente -> servidor: o que o jogador pediu na secao Quests. */
    public record QuestActionRequest(QuestAction action, ResourceLocation quest) implements CustomPacketPayload {
        public static final Type<QuestActionRequest> TYPE = new Type<>(RespectCraft.id("quest_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestActionRequest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.map(QuestAction::byOrdinal, QuestAction::ordinal), QuestActionRequest::action,
                ResourceLocation.STREAM_CODEC, QuestActionRequest::quest,
                QuestActionRequest::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(QuestSnapshot.TYPE, QuestSnapshot.STREAM_CODEC,
                (payload, context) -> QuestClientCache.snapshot(payload.quests(), payload.hints()));
        registrar.playToClient(QuestUpdate.TYPE, QuestUpdate.STREAM_CODEC,
                (payload, context) -> QuestClientCache.update(payload.quest()));
        registrar.playToServer(QuestActionRequest.TYPE, QuestActionRequest.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) QuestService.handle(player, payload);
        });
    }
}
