package com.tupinicraft.mandate;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.territory.Territories;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Optional;

/**
 * Persistência dos mandatos (data/tupinicraft_mandates.dat no Overworld).
 * Fonte autoritativa das cadeiras; as consultas e o snapshot do mapa são derivados daqui.
 */
public final class MandateSavedData extends SavedData {
    private static final String FILE_NAME = TupiniCraft.MOD_ID + "_mandates";

    private final MandateRegistry registry = new MandateRegistry();

    public static MandateSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MandateSavedData::new, MandateSavedData::load, null), FILE_NAME);
    }

    /** Altere apenas via {@link MandateService} e marque {@link #setDirty()}. */
    MandateRegistry registry() {
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag seats = new ListTag();
        registry.mandates().forEach((key, mandate) -> {
            CompoundTag seat = writeKey(key);
            Occupant occupant = mandate.occupant();
            seat.putString("occupant_type", occupant.type().name());
            seat.putUUID("occupant_id", occupant.id());
            seat.putString("occupant_name", occupant.displayName());
            seat.putLong("term_start", mandate.termStart());
            seat.putLong("term_end", mandate.termEnd());
            mandate.party().ifPresent(party -> seat.putString("party", party));
            mandate.electionId().ifPresent(id -> seat.putUUID("election_id", id));
            seats.add(seat);
        });
        tag.put("mandates", seats);

        ListTag last = new ListTag();
        registry.lastOccupants().forEach((key, occupant) -> {
            CompoundTag entry = writeKey(key);
            entry.putUUID("occupant_id", occupant);
            last.add(entry);
        });
        tag.put("last_occupants", last);
        return tag;
    }

    private static MandateSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MandateSavedData data = new MandateSavedData();
        ListTag seats = tag.getList("mandates", Tag.TAG_COMPOUND);
        for (int i = 0; i < seats.size(); i++) {
            CompoundTag seat = seats.getCompound(i);
            Optional<SeatKey> key = readKey(seat);
            if (key.isEmpty() || !seat.hasUUID("occupant_id")) {
                continue;
            }
            OccupantType type;
            try {
                type = OccupantType.valueOf(seat.getString("occupant_type"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (type == OccupantType.VACANT) {
                continue;
            }
            Occupant occupant = new Occupant(type, seat.getUUID("occupant_id"), seat.getString("occupant_name"));
            data.registry.restore(key.get(), new Mandate(occupant, seat.getLong("term_start"), seat.getLong("term_end"),
                    seat.contains("party") ? Optional.of(seat.getString("party")) : Optional.empty(),
                    seat.hasUUID("election_id") ? Optional.of(seat.getUUID("election_id")) : Optional.empty()));
        }
        ListTag last = tag.getList("last_occupants", Tag.TAG_COMPOUND);
        for (int i = 0; i < last.size(); i++) {
            CompoundTag entry = last.getCompound(i);
            Optional<SeatKey> key = readKey(entry);
            if (key.isPresent() && entry.hasUUID("occupant_id")) {
                data.registry.restoreLastOccupant(key.get(), entry.getUUID("occupant_id"));
            }
        }
        return data;
    }

    private static CompoundTag writeKey(SeatKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putString("territory", key.territoryId());
        tag.putString("office", key.office().id());
        tag.putInt("seat", key.seat());
        return tag;
    }

    private static Optional<SeatKey> readKey(CompoundTag tag) {
        Optional<PoliticalOffice> office = PoliticalOffice.byId(tag.getString("office"));
        String territory = tag.getString("territory");
        int seat = tag.getInt("seat");
        if (office.isEmpty() || !office.get().isElective() || Territories.get(territory).isEmpty() || seat < 1) {
            TupiniCraft.LOGGER.warn("Cadeira inválida ignorada: {}/{}/{}", territory, tag.getString("office"), seat);
            return Optional.empty();
        }
        return Optional.of(new SeatKey(territory, office.get(), seat));
    }
}
