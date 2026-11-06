package com.tupinicraft.election;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.mandate.SeatKey;
import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.territory.Territories;
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
 * Inclui fase, tempo restante, regras congeladas, disputas, candidatos, votos, os cronômetros dos
 * calendários geral e municipal e promoções pendentes de vencedores que estavam offline.
 * Quem altera os campos deve chamar {@link #setDirty()}.
 */
public final class ElectionSavedData extends SavedData {
    private static final String FILE_NAME = TupiniCraft.MOD_ID + "_election";

    private ElectionPhase phase = ElectionPhase.INACTIVE;
    private int ticksRemaining;
    private int phaseTotalTicks;
    private UUID electionId;
    private ElectionCalendar calendar = ElectionCalendar.MANUAL;
    private ElectionRules rules = ElectionRules.DEFAULTS;
    private final List<ElectionRace> races = new ArrayList<>();
    /** -1 = ainda não inicializado a partir da configuração. */
    private int ticksUntilGeneral = -1;
    private int ticksUntilMunicipal = -1;
    /** Último calendário automático iniciado (para alternar quando os dois vencem juntos). */
    private ElectionCalendar lastAutoCalendar;
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

    public UUID electionId() {
        return electionId;
    }

    public ElectionCalendar calendar() {
        return calendar;
    }

    /** Regras congeladas no início da eleição ativa. */
    public ElectionRules rules() {
        return rules;
    }

    /** Lista mutável das disputas da eleição atual (1 ou 2). */
    public List<ElectionRace> races() {
        return races;
    }

    /** Promoções pendentes gravadas pela v0.1.0 (preservadas e aplicadas no login). Não use em código novo. */
    public Map<UUID, PoliticalOffice> pendingPromotions() {
        return pendingPromotions;
    }

    public int ticksUntil(ElectionCalendar target) {
        return target == ElectionCalendar.GENERAL ? ticksUntilGeneral : ticksUntilMunicipal;
    }

    public void setTicksUntil(ElectionCalendar target, int ticks) {
        if (target == ElectionCalendar.GENERAL) {
            ticksUntilGeneral = ticks;
        } else if (target == ElectionCalendar.MUNICIPAL) {
            ticksUntilMunicipal = ticks;
        }
        setDirty();
    }

    public ElectionCalendar lastAutoCalendar() {
        return lastAutoCalendar;
    }

    /** Inicia uma nova eleição na fase de inscrição, congelando as regras. */
    public void begin(ElectionCalendar newCalendar, ElectionRules frozenRules, List<ElectionRace> newRaces, int registrationTicks) {
        electionId = UUID.randomUUID();
        calendar = newCalendar;
        if (newCalendar != ElectionCalendar.MANUAL) {
            lastAutoCalendar = newCalendar;
        }
        rules = frozenRules;
        races.clear();
        races.addAll(newRaces);
        enterPhase(ElectionPhase.REGISTRATION, registrationTicks);
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
        electionId = null;
        calendar = ElectionCalendar.MANUAL;
        rules = ElectionRules.DEFAULTS;
        races.clear();
        setDirty();
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
        tag.putInt("version", 2);
        tag.putString("phase", phase.name());
        tag.putInt("ticks_remaining", ticksRemaining);
        tag.putInt("phase_total_ticks", phaseTotalTicks);
        if (electionId != null) {
            tag.putUUID("election_id", electionId);
        }
        tag.putString("calendar", calendar.name());
        tag.put("rules", writeRules(rules));
        tag.putInt("ticks_until_general", ticksUntilGeneral);
        tag.putInt("ticks_until_municipal", ticksUntilMunicipal);
        if (lastAutoCalendar != null) {
            tag.putString("last_auto_calendar", lastAutoCalendar.name());
        }

        ListTag raceList = new ListTag();
        for (ElectionRace race : races) {
            CompoundTag raceTag = new CompoundTag();
            raceTag.putString("office", race.office().id());
            raceTag.putString("territory", race.territoryId());
            raceTag.putInt("seat", race.seat().seat());
            raceTag.putInt("minimum_score", race.minimumScore());
            raceTag.putInt("npc_popularity_min", race.npcPopularityMin());
            raceTag.putInt("npc_popularity_max", race.npcPopularityMax());

            ListTag candidateList = new ListTag();
            for (Candidate candidate : race.candidates()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", candidate.id());
                c.putString("name", candidate.name());
                c.putInt("popularity", candidate.popularity());
                c.putBoolean("npc", candidate.npc());
                c.putInt("simulated_support", candidate.simulatedSupport());
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
        data.electionId = tag.hasUUID("election_id") ? tag.getUUID("election_id") : null;
        try {
            data.calendar = ElectionCalendar.valueOf(tag.getString("calendar"));
        } catch (IllegalArgumentException e) {
            data.calendar = ElectionCalendar.MANUAL;
        }
        data.rules = tag.contains("rules") ? readRules(tag.getCompound("rules")) : ElectionRules.DEFAULTS;
        // v0.1.0 tinha um único cronômetro ("ticks_until_auto"): vira o do calendário geral.
        data.ticksUntilGeneral = tag.contains("ticks_until_general") ? tag.getInt("ticks_until_general")
                : tag.contains("ticks_until_auto") ? tag.getInt("ticks_until_auto") : -1;
        data.ticksUntilMunicipal = tag.contains("ticks_until_municipal") ? tag.getInt("ticks_until_municipal") : -1;
        if (tag.contains("last_auto_calendar")) {
            try {
                data.lastAutoCalendar = ElectionCalendar.valueOf(tag.getString("last_auto_calendar"));
            } catch (IllegalArgumentException e) {
                data.lastAutoCalendar = null;
            }
        }

        boolean legacyRaces = false;
        ListTag raceList = tag.getList("races", Tag.TAG_COMPOUND);
        for (int i = 0; i < raceList.size(); i++) {
            CompoundTag raceTag = raceList.getCompound(i);
            PoliticalOffice office = PoliticalOffice.byId(raceTag.getString("office")).orElse(null);
            String territory = raceTag.getString("territory");
            if (office == null || !office.isElective() || territory.isEmpty()
                    || Territories.get(territory).map(t -> t.type() != office.jurisdiction()).orElse(true)) {
                legacyRaces = true;
                continue;
            }
            ElectionRace race = new ElectionRace(new SeatKey(territory, office, Math.max(1, raceTag.getInt("seat"))),
                    raceTag.getInt("minimum_score"), raceTag.getInt("npc_popularity_min"), raceTag.getInt("npc_popularity_max"));
            ListTag candidateList = raceTag.getList("candidates", Tag.TAG_COMPOUND);
            for (int j = 0; j < candidateList.size(); j++) {
                CompoundTag c = candidateList.getCompound(j);
                if (c.hasUUID("id")) {
                    race.candidates().add(new Candidate(c.getUUID("id"), c.getString("name"), c.getInt("popularity"),
                            c.getBoolean("npc"), c.getInt("simulated_support")));
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

        // Eleições salvas pela v0.1.0 (sem território) ou corrompidas não podem continuar com segurança.
        if (data.phase != ElectionPhase.INACTIVE && (legacyRaces || data.races.isEmpty() || data.electionId == null)) {
            TupiniCraft.LOGGER.warn("Eleição salva na fase {} sem dados de território (versão anterior ou corrompida); "
                    + "voltando para INATIVA.", data.phase);
            data.phase = ElectionPhase.INACTIVE;
            data.ticksRemaining = 0;
            data.phaseTotalTicks = 0;
            data.races.clear();
            data.electionId = null;
        }
        return data;
    }

    private static CompoundTag writeRules(ElectionRules rules) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("vote_multiplier", rules.voteMultiplier());
        tag.putInt("popularity_divisor", rules.popularityDivisor());
        tag.putInt("max_vote_weight", rules.maxVoteWeight());
        tag.putBoolean("villagers", rules.villagersEnabled());
        tag.putInt("min_candidates", rules.minCandidates());
        tag.putInt("max_npcs", rules.maxNpcCandidates());
        tag.putInt("npc_support_min", rules.npcSupportMin());
        tag.putInt("npc_support_max", rules.npcSupportMax());
        tag.putInt("voting_ticks", rules.votingTicks());
        tag.putInt("counting_ticks", rules.countingTicks());
        return tag;
    }

    private static ElectionRules readRules(CompoundTag tag) {
        return new ElectionRules(tag.getInt("vote_multiplier"), tag.getInt("popularity_divisor"), tag.getInt("max_vote_weight"),
                tag.getBoolean("villagers"), tag.getInt("min_candidates"), tag.getInt("max_npcs"),
                tag.getInt("npc_support_min"), tag.getInt("npc_support_max"),
                tag.getInt("voting_ticks"), tag.getInt("counting_ticks"));
    }
}
