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
 * @param votedRaces      índices (em {@code races}) das disputas em que ESTE jogador já votou
 */
public record ElectionSyncPayload(int phase, int ticksRemaining, int phaseTotalTicks,
                                  List<RaceView> races, List<Integer> votedRaces) implements CustomPacketPayload {
    public static final Type<ElectionSyncPayload> TYPE = new Type<>(TupiniCraft.id("election_sync"));

    public static final StreamCodec<ByteBuf, ElectionSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ElectionSyncPayload::phase,
            ByteBufCodecs.VAR_INT, ElectionSyncPayload::ticksRemaining,
            ByteBufCodecs.VAR_INT, ElectionSyncPayload::phaseTotalTicks,
            RaceView.STREAM_CODEC.apply(ByteBufCodecs.list()), ElectionSyncPayload::races,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ElectionSyncPayload::votedRaces,
            ElectionSyncPayload::new);

    public static final ElectionSyncPayload INACTIVE =
            new ElectionSyncPayload(ElectionPhase.INACTIVE.ordinal(), 0, 0, List.of(), List.of());

    public ElectionPhase electionPhase() {
        return ElectionPhase.byOrdinal(phase);
    }

    public boolean hasVotedIn(int raceIndex) {
        return votedRaces.contains(raceIndex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * @param label           rótulo pronto da cadeira, ex.: "Prefeito (São Paulo)"
     * @param revealedBallots votos já apurados (somente na apuração; 0 nas outras fases)
     * @param totalBallots    total de votos (somente na apuração; 0 nas outras fases, voto secreto)
     */
    public record RaceView(int office, String label, List<CandidateView> candidates, int revealedBallots, int totalBallots) {
        public static final StreamCodec<ByteBuf, RaceView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RaceView::office,
                ByteBufCodecs.STRING_UTF8, RaceView::label,
                CandidateView.STREAM_CODEC.apply(ByteBufCodecs.list()), RaceView::candidates,
                ByteBufCodecs.VAR_INT, RaceView::revealedBallots,
                ByteBufCodecs.VAR_INT, RaceView::totalBallots,
                RaceView::new);

        public PoliticalOffice politicalOffice() {
            PoliticalOffice[] all = PoliticalOffice.values();
            return office >= 0 && office < all.length ? all[office] : PoliticalOffice.DESEMPREGADO;
        }
    }

    /**
     * @param shownScore pontuação parcial revelada na apuração, ou -1 fora da apuração
     * @param npc        candidato aldeão
     */
    public record CandidateView(UUID id, String name, int popularity, int shownScore, boolean npc) {
        public static final StreamCodec<ByteBuf, CandidateView> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, CandidateView::id,
                ByteBufCodecs.STRING_UTF8, CandidateView::name,
                ByteBufCodecs.VAR_INT, CandidateView::popularity,
                ByteBufCodecs.INT, CandidateView::shownScore,
                ByteBufCodecs.BOOL, CandidateView::npc,
                CandidateView::new);
    }
}
