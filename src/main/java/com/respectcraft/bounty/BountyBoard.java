package com.respectcraft.bounty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Quadro de recompensas do mundo (Etapa 4.11), gravado no Overworld como {@value #NAME} (pasta {@code data/}).
 * Guarda os contratos abertos e as recompensas a devolver a pagadores que estavam offline quando o contrato
 * expirou. So {@link BountyService} mexe aqui. O tempo dos pedidos de atualizacao e transitorio (nao e gravado).
 */
public final class BountyBoard extends SavedData {
    public static final String NAME = "respectcraft_bounties";

    /** Recompensa esperando o dono voltar. */
    record PendingReturn(UUID owner, List<ItemStack> items) {
        static final Codec<PendingReturn> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(PendingReturn::owner),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(PendingReturn::items)
        ).apply(i, PendingReturn::new));
    }

    private final Map<UUID, Bounty> bounties = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();
    /** Transitorio: ultimo pedido de atualizacao de cada jogador (tempo de jogo). */
    final Map<UUID, Long> lastRefresh = new HashMap<>();

    public static BountyBoard get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BountyBoard::new, BountyBoard::load), NAME);
    }

    public Collection<Bounty> all() { return bounties.values(); }

    public Bounty get(UUID id) { return bounties.get(id); }

    /** Contrato pelo id inteiro ou pelo comeco dele (comandos). */
    public Bounty find(String prefix) {
        if (prefix == null || prefix.isEmpty()) return null;
        Bounty found = null;
        for (Bounty b : bounties.values()) {
            if (b.id().toString().startsWith(prefix)) {
                if (found != null) return null; // ambiguo
                found = b;
            }
        }
        return found;
    }

    void put(Bounty b) {
        bounties.put(b.id(), b);
        setDirty();
    }

    Bounty remove(UUID id) {
        Bounty b = bounties.remove(id);
        if (b != null) setDirty();
        return b;
    }

    void addPending(UUID owner, ItemStack stack) {
        if (stack.isEmpty()) return;
        pending.computeIfAbsent(owner, k -> new ArrayList<>()).add(stack.copy());
        setDirty();
    }

    List<ItemStack> takePending(UUID owner) {
        List<ItemStack> items = pending.remove(owner);
        if (items != null) setDirty();
        return items == null ? List.of() : items;
    }

    int pendingCount() { return pending.values().stream().mapToInt(List::size).sum(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, provider);
        tag.put("bounties", Bounty.CODEC.listOf().encodeStart(ops, new ArrayList<>(bounties.values())).getOrThrow());
        List<PendingReturn> returns = new ArrayList<>();
        pending.forEach((k, v) -> returns.add(new PendingReturn(k, v)));
        tag.put("pending_returns", PendingReturn.CODEC.listOf().encodeStart(ops, returns).getOrThrow());
        return tag;
    }

    static BountyBoard load(CompoundTag tag, HolderLookup.Provider provider) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, provider);
        BountyBoard board = new BountyBoard();
        if (tag.contains("bounties")) {
            Bounty.CODEC.listOf().parse(ops, tag.get("bounties")).resultOrPartial(com.respectcraft.RespectCraft.LOGGER::warn)
                    .ifPresent(list -> list.forEach(b -> board.bounties.put(b.id(), b)));
        }
        if (tag.contains("pending_returns")) {
            PendingReturn.CODEC.listOf().parse(ops, tag.get("pending_returns")).resultOrPartial(com.respectcraft.RespectCraft.LOGGER::warn)
                    .ifPresent(list -> list.forEach(r -> board.pending.put(r.owner(), new ArrayList<>(r.items()))));
        }
        return board;
    }
}
