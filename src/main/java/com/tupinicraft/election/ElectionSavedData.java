package com.tupinicraft.election;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.politics.PoliticalOffice;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estado global da eleição, salvo no Overworld (data/tupinicraft_election.dat).
 * Inclui fase, tempo restante, disputas, candidatos, votos, o cronômetro da próxima
 * eleição automática e promoções pendentes de vencedores que estavam offline.
 * Quem altera os campos deve chamar {@link #setDirty()}.
 */
public final class ElectionSavedData extends SavedData {
    private static final String FILE_NAME = TupiniCraft.MOD_ID + "_election";

    private ElectionPhase phase = ElectionPhase.INACTIVE;
    private int ticksRemaining;
    private int phaseTotalTicks;
    private final List<ElectionRace> races = new ArrayList<>();
    /** -1 = ainda não inicializado a partir da config. */
    private int ticksUntilAutoElection = -1;
    private final Map<UUID, PoliticalOffice> pendingPromotions = new LinkedHashMap<>();

    public static ElectionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ElectionSavedData::new, ElectionSavedData::load, null), FILE_NAME);
    }

    public ElectionPhase phase() {
        return phase;
    }

    public int ticksRemaining() {
        return ticksRemaining;
    }

    public int phaseTotalTicks() {
        return phaseTotalTicks;
    }

    /** Lista mutável das disputas da eleição atual (1 ou 2). */
    public List<ElectionRace> races() {
        return races;
    }

    public Map<UUID, PoliticalOffice> pendingPromotions() {
        return pendingPromotions;
    }

    public int ticksUntilAutoElection() {
        return ticksUntilAutoElection;
    }

    public void setTicksUntilAutoElection(int ticks) {
        this.ticksUntilAutoElection = ticks;
        setDirty();
    }

    public void enterPhase(ElectionPhase newPhase, int durationTicks) {
        this.phase = newPhase;
        this.ticksRemaining = durationTicks;
        this.phaseTotalTicks = durationTicks;
        setDirty();
    }

    public void decrementTicks() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
        setDirty();
    }

    /** Volta para INATIVA e descarta disputas/votos da eleição encerrada. */
    public void reset() {
        phase = ElectionPhase.INACTIVE;
        ticksRemaining = 0;
        phaseTotalTicks = 0;
        races.clear();
        setDirty();
    }

    public ElectionRace raceFor(PoliticalOffice office) {
        for (ElectionRace race : races) {
            if (race.office() == office) {
                return race;
            }
        }
        return null;
    }

    public ElectionRace raceWithCandidate(UUID candidate) {
        for (ElectionRace race : races) {
            if (race.isCandidate(candidate)) {
                return race;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- persistência

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("phase", phase.name());
        tag.putInt("ticks_remaining", ticksRemaining);
        tag.putInt("phase_total_ticks", phaseTotalTicks);
        tag.putInt("ticks_until_auto", ticksUntilAutoElection);

        ListTag raceList = new ListTag();
        for (ElectionRace race : races) {
            CompoundTag raceTag = new CompoundTag();
            raceTag.putString("office", race.office().id());

            ListTag candidateList = new ListTag();
            for (Candidate candidate : race.candidates()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", candidate.id());
                c.putString("name", candidate.name());
                c.putInt("popularity", candidate.popularity());
                candidateList.add(c);
            }
            raceTag.put("candidates", candidateList);

            ListTag ballotList = new ListTag();
            for (Ballot ballot : race.ballots()) {
                CompoundTag b = new CompoundTag();
                b.putUUID("voter", ballot.voter());
                b.putUUID("candidate", ballot.candidate());
                b.putInt("weight", ballot.weight());
                ballotList.add(b);
            }
            raceTag.put("ballots", ballotList);
            raceList.add(raceTag);
        }
        tag.put("races", raceList);

        ListTag pendingList = new ListTag();
        pendingPromotions.forEach((player, office) -> {
            CompoundTag p = new CompoundTag();
            p.putUUID("player", player);
            p.putString("office", office.id());
            pendingList.add(p);
        });
        tag.put("pending_promotions", pendingList);
        return tag;
    }

    private static ElectionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ElectionSavedData data = new ElectionSavedData();
        try {
            data.phase = ElectionPhase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            data.phase = ElectionPhase.INACTIVE;
        }
        data.ticksRemaining = Math.max(0, tag.getInt("ticks_remaining"));
        data.phaseTotalTicks = Math.max(0, tag.getInt("phase_total_ticks"));
        data.ticksUntilAutoElection = tag.contains("ticks_until_auto") ? tag.getInt("ticks_until_auto") : -1;

        ListTag raceList = tag.getList("races", Tag.TAG_COMPOUND);
        for (int i = 0; i < raceList.size(); i++) {
            CompoundTag raceTag = raceList.getCompound(i);
            PoliticalOffice office = PoliticalOffice.byId(raceTag.getString("office")).orElse(null);
            if (office == null || !office.isElective()) {
                TupiniCraft.LOGGER.warn("Disputa com cargo inválido ignorada: {}", raceTag.getString("office"));
                continue;
            }
            ElectionRace race = new ElectionRace(office);
            ListTag candidateList = raceTag.getList("candidates", Tag.TAG_COMPOUND);
            for (int j = 0; j < candidateList.size(); j++) {
                CompoundTag c = candidateList.getCompound(j);
                if (c.hasUUID("id")) {
                    race.candidates().add(new Candidate(c.getUUID("id"), c.getString("name"), c.getInt("popularity")));
                }
            }
            ListTag ballotList = raceTag.getList("ballots", Tag.TAG_COMPOUND);
            for (int j = 0; j < ballotList.size(); j++) {
                CompoundTag b = ballotList.getCompound(j);
                if (b.hasUUID("voter") && b.hasUUID("candidate")) {
                    race.ballots().add(new Ballot(b.getUUID("voter"), b.getUUID("candidate"), b.getInt("weight")));
                }
            }
            data.races.add(race);
        }

        ListTag pendingList = tag.getList("pending_promotions", Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingList.size(); i++) {
            CompoundTag p = pendingList.getCompound(i);
            PoliticalOffice office = PoliticalOffice.byId(p.getString("office")).orElse(null);
            if (p.hasUUID("player") && office != null) {
                data.pendingPromotions.put(p.getUUID("player"), office);
            }
        }

        // Proteção contra estado corrompido: eleição ativa sem disputas volta a INATIVA.
        if (data.phase != ElectionPhase.INACTIVE && data.races.isEmpty()) {
            TupiniCraft.LOGGER.warn("Eleição salva na fase {} sem disputas; reiniciando para INATIVA.", data.phase);
            data.phase = ElectionPhase.INACTIVE;
            data.ticksRemaining = 0;
            data.phaseTotalTicks = 0;
        }
        return data;
    }
}
