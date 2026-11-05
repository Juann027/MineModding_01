package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.election.ElectionPhase;
import com.tupinicraft.politics.PoliticalOffice;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/**
 * Servidor → cliente: visão da eleição para HUD e urna. Enviado apenas em mudanças,
 * a cada 5 s durante inscrição/votação e a cada 1 s durante a apuração.
 *
 * @param ticksRemaining  tempo restante no momento do envio (o cliente só usa para exibição)
 * @param votedOffices    ordinais dos cargos em que ESTE jogador já votou
 */
public record ElectionSyncPayload(int phase, int ticksRemaining, int phaseTotalTicks,
                                  List<RaceView> races, List<Integer> votedOffices) implements CustomPacketPayload {
    public static final Type<ElectionSyncPayload> TYPE = new Type<>(TupiniCraft.id("election_sync"));

    public static final StreamCodec<ByteBuf, ElectionSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ElectionSyncPayload::phase,
            ByteBufCodecs.VAR_INT, ElectionSyncPayload::ticksRemaining,
            ByteBufCodecs.VAR_INT, ElectionSyncPayload::phaseTotalTicks,
            RaceView.STREAM_CODEC.apply(ByteBufCodecs.list()), ElectionSyncPayload::races,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ElectionSyncPayload::votedOffices,
            ElectionSyncPayload::new);

    public static final ElectionSyncPayload INACTIVE =
            new ElectionSyncPayload(ElectionPhase.INACTIVE.ordinal(), 0, 0, List.of(), List.of());

    public ElectionPhase electionPhase() {
        return ElectionPhase.byOrdinal(phase);
    }

    public boolean hasVotedFor(PoliticalOffice office) {
        return votedOffices.contains(office.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * @param revealedBallots votos já apurados (somente na apuração; 0 nas outras fases)
     * @param totalBallots    total de votos (somente na apuração; 0 nas outras fases, voto secreto)
     */
    public record RaceView(int office, List<CandidateView> candidates, int revealedBallots, int totalBallots) {
        public static final StreamCodec<ByteBuf, RaceView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RaceView::office,
                CandidateView.STREAM_CODEC.apply(ByteBufCodecs.list()), RaceView::candidates,
                ByteBufCodecs.VAR_INT, RaceView::revealedBallots,
                ByteBufCodecs.VAR_INT, RaceView::totalBallots,
                RaceView::new);

        public PoliticalOffice politicalOffice() {
            PoliticalOffice[] all = PoliticalOffice.values();
            return office >= 0 && office < all.length ? all[office] : PoliticalOffice.DESEMPREGADO;
        }
    }

    /** @param shownScore pontuação parcial revelada na apuração, ou -1 fora da apuração */
    public record CandidateView(UUID id, String name, int popularity, int shownScore) {
        public static final StreamCodec<ByteBuf, CandidateView> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, CandidateView::id,
                ByteBufCodecs.STRING_UTF8, CandidateView::name,
                ByteBufCodecs.VAR_INT, CandidateView::popularity,
                ByteBufCodecs.INT, CandidateView::shownScore,
                CandidateView::new);
    }
}
