package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.mandate.OccupantType;
import com.tupinicraft.mandate.PoliticalMapSnapshot;
import com.tupinicraft.mandate.SeatView;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Representação do snapshot do mapa político enviada ao cliente (preparação para o futuro mapa).
 * <p>
 * <b>Ainda não registrado</b> em {@link ModNetwork}: será registrado junto com um pedido cliente → servidor
 * quando a interface do mapa existir. O cliente só lê este snapshot; nunca altera ocupantes ou resultados.
 * O catálogo de territórios é código comum ({@code Territories}), então só as cadeiras são enviadas.
 *
 * @param gameTime instante do snapshot (para o cliente calcular o tempo restante exibido)
 */
public record PoliticalMapSnapshotPayload(long gameTime, List<SeatEntry> seats) implements CustomPacketPayload {
    public static final Type<PoliticalMapSnapshotPayload> TYPE = new Type<>(TupiniCraft.id("political_map_snapshot"));

    public static final StreamCodec<ByteBuf, PoliticalMapSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PoliticalMapSnapshotPayload::gameTime,
            SeatEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), PoliticalMapSnapshotPayload::seats,
            PoliticalMapSnapshotPayload::new);

    public static PoliticalMapSnapshotPayload from(PoliticalMapSnapshot snapshot) {
        return new PoliticalMapSnapshotPayload(snapshot.gameTime(), snapshot.seats().stream().map(SeatEntry::from).toList());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Cadeira resumida para o cliente.
     *
     * @param occupant       ocupante (vazio se vaga)
     * @param remainingTicks ticks restantes do mandato (0 se vaga)
     */
    public record SeatEntry(ResourceLocation territory, int office, int seat, int occupantType,
                            Optional<OccupantEntry> occupant, long remainingTicks) {
        public static final StreamCodec<ByteBuf, SeatEntry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, SeatEntry::territory,
                ByteBufCodecs.VAR_INT, SeatEntry::office,
                ByteBufCodecs.VAR_INT, SeatEntry::seat,
                ByteBufCodecs.VAR_INT, SeatEntry::occupantType,
                ByteBufCodecs.optional(OccupantEntry.STREAM_CODEC), SeatEntry::occupant,
                ByteBufCodecs.VAR_LONG, SeatEntry::remainingTicks,
                SeatEntry::new);

        static SeatEntry from(SeatView view) {
            Optional<UUID> id = view.occupantType() == OccupantType.PLAYER ? view.playerId() : view.npcId();
            Optional<OccupantEntry> occupant = id.map(uuid -> new OccupantEntry(uuid, view.displayName().orElse("?")));
            return new SeatEntry(ResourceLocation.parse(view.key().territoryId()), view.key().office().ordinal(),
                    view.key().seat(), view.occupantType().ordinal(), occupant, view.remainingTicks().orElse(0));
        }
    }

    /** @param id UUID do jogador ou ID persistente do NPC (o tipo vem de {@link SeatEntry#occupantType()}) */
    public record OccupantEntry(UUID id, String displayName) {
        public static final StreamCodec<ByteBuf, OccupantEntry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, OccupantEntry::id,
                ByteBufCodecs.STRING_UTF8, OccupantEntry::displayName,
                OccupantEntry::new);
    }
}
