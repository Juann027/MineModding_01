package com.tupinicraft.tigrinho;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned UUID ledger: survives death, dimensions and normal restarts. */
public final class TigrinhoLedger extends SavedData {
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Long> nextSpin = new HashMap<>();
    public static TigrinhoLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TigrinhoLedger::new,TigrinhoLedger::load,null),"tupinicraft_tigrinho");
    }
    public long balance(UUID id) {
        if (!balances.containsKey(id)) { balances.put(id,(long)TigrinhoConfig.INITIAL.get()); setDirty(); }
        return balances.get(id);
    }
    public long next(UUID id) { return nextSpin.getOrDefault(id,0L); }
    public void set(UUID id,long balance,long next) {
        if (balance<0 || balance>SlotRules.MAX_BALANCE) throw new IllegalArgumentException("Saldo fora dos limites");
        balances.put(id,balance); nextSpin.put(id,next); setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        CompoundTag accounts = new CompoundTag();
        balances.forEach((id,balance) -> { CompoundTag a=new CompoundTag(); a.putLong("chips",balance);
            a.putLong("nextSpin",next(id)); accounts.put(id.toString(),a); });
        tag.put("accounts",accounts); return tag;
    }
    private static TigrinhoLedger load(CompoundTag tag,HolderLookup.Provider registries) {
        TigrinhoLedger data=new TigrinhoLedger(); CompoundTag accounts=tag.getCompound("accounts");
        for(String key:accounts.getAllKeys()) {
            try { UUID id=UUID.fromString(key); CompoundTag a=accounts.getCompound(key);
                data.balances.put(id,Math.clamp(a.getLong("chips"),0L,SlotRules.MAX_BALANCE));
                data.nextSpin.put(id,Math.max(0,a.getLong("nextSpin")));
            } catch (IllegalArgumentException ignored) { /* Ignore malformed account UUIDs. */ }
        }
        return data;
    }
}
