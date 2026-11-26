package com.respectcraft.bounty;

import com.respectcraft.RespectCraft;
import com.respectcraft.finisher.FinisherParticipants;
import com.respectcraft.finisher.FinisherSessionEvent;
import com.respectcraft.network.HoloNetworking;
import com.respectcraft.notice.HoloNotice;
import com.respectcraft.notice.HoloNotices;
import com.respectcraft.notice.NoticeKind;
import com.respectcraft.notice.NoticeRules;
import com.respectcraft.notice.NoticeSeverity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Caca a recompensa contra outros jogadores (Etapa 4.11): o <b>unico dono</b> do {@link BountyBoard}. Abre,
 * aceita, abandona, cancela, cobra e expira contratos, sempre no servidor, e manda o quadro a cada jogador (login,
 * quando muda e quando a aba pede, com limite de {@link BountyRules#REFRESH_COOLDOWN_TICKS}). Avisa tudo pelos
 * avisos holograficos ({@link HoloNotices}, categoria BOUNTY).
 *
 * <p>A recompensa sao itens: a pilha inteira da mao principal de quem paga, guardada no quadro ate o contrato
 * terminar (pago ao cacador, ou devolvido ao pagador no cancelamento/expiracao). Nao existe moeda nem economia.
 */
public final class BountyService {
    private BountyService() {}

    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }

    // ------------------------------------------------------------------------------------------------ acoes

    public static void handle(ServerPlayer player, HoloNetworking.BountyActionRequest req) {
        BountyRules.Result r = switch (req.action()) {
            case REFRESH -> {
                refresh(player);
                yield BountyRules.Result.OK;
            }
            case PLACE -> place(player, req.target().orElse(null), req.days());
            case ACCEPT -> accept(player, req.bounty().orElse(null));
            case ABANDON -> abandon(player, req.bounty().orElse(null));
            case CANCEL -> cancel(player, req.bounty().orElse(null), false);
        };
        if (r != BountyRules.Result.OK) {
            HoloNotices.toPlayer(player, HoloNotice.of(NoticeKind.BOUNTY, Component.translatable("notice.respectcraft.bounty.refused"),
                    Component.translatable(r.translationKey())).severity(NoticeSeverity.INFO));
            sync(player);
        }
    }

    public static BountyRules.Result place(ServerPlayer poster, UUID targetId, int days) {
        MinecraftServer server = poster.server;
        BountyBoard board = BountyBoard.get(server);
        ServerPlayer target = targetId == null ? null : server.getPlayerList().getPlayer(targetId);
        ItemStack hand = poster.getMainHandItem();
        int active = 0;
        boolean duplicate = false;
        for (Bounty b : board.all()) {
            if (!b.poster().equals(poster.getUUID())) continue;
            active++;
            if (b.target().equals(targetId)) duplicate = true;
        }
        BountyRules.Result r = BountyRules.canPlace(poster.getUUID(), targetId, target != null, hand.isEmpty(), active, duplicate, days);
        if (r != BountyRules.Result.OK) return r;
        ItemStack reward = hand.copy();
        poster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        long now = now(server);
        Bounty bounty = new Bounty(UUID.randomUUID(), target.getUUID(), target.getGameProfile().getName(), poster.getUUID(),
                poster.getGameProfile().getName(), reward, now, BountyRules.expiresAt(now, days), List.of());
        board.put(bounty);
        RespectCraft.LOGGER.info("Bounty {} placed by {} on {} ({} x{}, {} days)", bounty.shortId(), bounty.posterName(),
                bounty.targetName(), reward.getItem(), reward.getCount(), days);
        Component rewardText = rewardText(reward);
        HoloNotices.toPlayer(target, HoloNotice.of(NoticeKind.BOUNTY, Component.translatable("notice.respectcraft.bounty.on_you.title"),
                Component.translatable("notice.respectcraft.bounty.on_you.body", poster.getDisplayName(), rewardText))
                .severity(NoticeSeverity.CRITICAL));
        List<ServerPlayer> others = new ArrayList<>(server.getPlayerList().getPlayers());
        others.remove(target);
        others.remove(poster);
        HoloNotices.send(others, HoloNotice.of(NoticeKind.BOUNTY, Component.translatable("notice.respectcraft.bounty.new.title"),
                Component.translatable("notice.respectcraft.bounty.new.body", target.getDisplayName(), rewardText)), null, null);
        HoloNotices.toPlayer(poster, HoloNotice.of(NoticeKind.BOUNTY, Component.translatable("notice.respectcraft.bounty.posted.title"),
                Component.translatable("notice.respectcraft.bounty.posted.body", target.getDisplayName(), days)).severity(NoticeSeverity.INFO));
        syncAll(server);
        return r;
    }

    public static BountyRules.Result accept(ServerPlayer hunter, UUID id) {
        MinecraftServer server = hunter.server;
        BountyBoard board = BountyBoard.get(server);
        Bounty b = id == null ? null : board.get(id);
        int contracts = 0;
        for (Bounty other : board.all()) if (other.hunting(hunter.getUUID())) contracts++;
        BountyRules.Result r = BountyRules.canAccept(b, hunter.getUUID(), contracts, now(server));
        if (r != BountyRules.Result.OK) return r;
        board.put(b.withHunter(hunter.getUUID()));
        ServerPlayer target = server.getPlayerList().getPlayer(b.target());
        if (target != null) HoloNotices.toPlayer(target, HoloNotice.of(NoticeKind.BOUNTY,
                Component.translatable("notice.respectcraft.bounty.hunted.title"),
                Component.translatable("notice.respectcraft.bounty.hunted.body", hunter.getDisplayName())).severity(NoticeSeverity.ALERT));
        HoloNotices.toPlayer(hunter, HoloNotice.of(NoticeKind.BOUNTY, Component.translatable("notice.respectcraft.bounty.accepted.title"),
                Component.translatable("notice.respectcraft.bounty.accepted.body", b.targetName())).severity(NoticeSeverity.INFO));
        syncAll(server);
        return r;
    }

    public static BountyRules.Result abandon(ServerPlayer hunter, UUID id) {
        BountyBoard board = BountyBoard.get(hunter.server);
        Bounty b = id == null ? null : board.get(id);
        BountyRules.Result r = BountyRules.canAbandon(b, hunter.getUUID());
        if (r != BountyRules.Result.OK) return r;
        board.put(b.withoutHunter(hunter.getUUID()));
        syncAll(hunter.server);
        return r;
    }

    /** Cancela (pagador sem cacadores, ou admin) e devolve a recompensa. */
    public static BountyRules.Result cancel(ServerPlayer who, UUID id, boolean admin) {
        MinecraftServer server = who.server;
        BountyBoard board = BountyBoard.get(server);
        Bounty b = id == null ? null : board.get(id);
        BountyRules.Result r = BountyRules.canCancel(b, who.getUUID(), admin);
        if (r != BountyRules.Result.OK) return r;
        board.remove(b.id());
        giveBack(server, board, b);
        HoloNotices.toAll(server, HoloNotice.of(NoticeKind.BOUNTY, Component.translatable("notice.respectcraft.bounty.cancelled.title"),
                Component.translatable("notice.respectcraft.bounty.cancelled.body", b.targetName())).severity(NoticeSeverity.INFO));
        syncAll(server);
        return r;
    }

    /** Admin: fecha todos os contratos e devolve as recompensas. */
    public static int clearAll(MinecraftServer server) {
        BountyBoard board = BountyBoard.get(server);
        List<Bounty> all = new ArrayList<>(board.all());
        for (Bounty b : all) {
            board.remove(b.id());
            giveBack(server, board, b);
        }
        syncAll(server);
        return all.size();
    }

    // ------------------------------------------------------------------------------------------------ cobranca

    /** Morte do alvo (LOWEST: morte cancelada, ex. por totem, nao conta). */
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p ? p
                : victim.getKillCredit() instanceof ServerPlayer p2 ? p2 : null;
        if (killer != null) claim(victim, killer, "kill");
    }

    /**
     * Finalizacao concluida jogador x jogador: so cobra com morte confirmada ({@code SUCCESS}). A execucao real
     * (Etapa 4.10.1) ja passa pelo {@link #onDeath}; aqui e a garantia (contrato ja removido = nada a pagar de novo).
     * {@code FAILED} (totem, protecao) e {@code Aborted} nunca cobram.
     */
    public static void onFinisherCompleted(FinisherSessionEvent.Completed event) {
        if (!BountyRules.finisherClaims(event.getInteraction() == FinisherParticipants.Interaction.PLAYER_VS_PLAYER,
                event.killConfirmed())) return;
        if (event.getAttacker() instanceof ServerPlayer hunter && event.getVictim() instanceof ServerPlayer victim) {
            claim(victim, hunter, "finisher");
        }
    }

    static int claim(ServerPlayer victim, ServerPlayer hunter, String how) {
        MinecraftServer server = victim.server;
        BountyBoard board = BountyBoard.get(server);
        long now = now(server);
        int claimed = 0;
        for (Bounty b : new ArrayList<>(board.all())) {
            if (!BountyRules.claims(b, victim.getUUID(), hunter.getUUID(), now)) continue;
            board.remove(b.id());
            give(hunter, b.reward());
            claimed++;
            RespectCraft.LOGGER.info("Bounty {} on {} claimed by {} ({})", b.shortId(), b.targetName(),
                    hunter.getGameProfile().getName(), how);
            HoloNotices.toAll(server, HoloNotice.of(NoticeKind.BOUNTY, Component.translatable("notice.respectcraft.bounty.claimed.title"),
                    Component.translatable("notice.respectcraft.bounty.claimed.body", hunter.getDisplayName(), victim.getDisplayName(),
                            rewardText(b.reward()))).severity(NoticeSeverity.ALERT));
        }
        if (claimed > 0) syncAll(server);
        return claimed;
    }

    // ------------------------------------------------------------------------------------------------ ciclo

    /** A cada 5 s: expira contratos vencidos (recompensa volta). */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = now(server);
        if (now % 100L != 0L) return;
        BountyBoard board = BountyBoard.get(server);
        List<Bounty> expired = new ArrayList<>();
        for (Bounty b : board.all()) if (BountyRules.expired(b, now)) expired.add(b);
        if (expired.isEmpty()) return;
        for (Bounty b : expired) {
            board.remove(b.id());
            giveBack(server, board, b);
            ServerPlayer poster = server.getPlayerList().getPlayer(b.poster());
            if (poster != null) HoloNotices.toPlayer(poster, HoloNotice.of(NoticeKind.BOUNTY,
                    Component.translatable("notice.respectcraft.bounty.expired.title"),
                    Component.translatable("notice.respectcraft.bounty.expired.body", b.targetName())).severity(NoticeSeverity.INFO));
        }
        syncAll(server);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BountyBoard board = BountyBoard.get(player.server);
        List<ItemStack> back = board.takePending(player.getUUID());
        for (ItemStack s : back) give(player, s);
        if (!back.isEmpty()) HoloNotices.toPlayer(player, HoloNotice.of(NoticeKind.BOUNTY,
                Component.translatable("notice.respectcraft.bounty.returned.title"),
                Component.translatable("notice.respectcraft.bounty.returned.body", back.size())).severity(NoticeSeverity.INFO));
        // Nome atualizado: o alvo pode ter trocado de nick.
        for (Bounty b : new ArrayList<>(board.all())) {
            if (b.target().equals(player.getUUID()) && !b.targetName().equals(player.getGameProfile().getName())) {
                board.put(new Bounty(b.id(), b.target(), player.getGameProfile().getName(), b.poster(), b.posterName(), b.reward(),
                        b.created(), b.expires(), b.hunters()));
            }
        }
        sync(player);
        int onMe = 0;
        for (Bounty b : board.all()) if (b.target().equals(player.getUUID())) onMe++;
        if (onMe > 0) HoloNotices.toPlayer(player, HoloNotice.of(NoticeKind.BOUNTY,
                Component.translatable("notice.respectcraft.bounty.still_on_you.title"),
                Component.translatable("notice.respectcraft.bounty.still_on_you.body", onMe)).severity(NoticeSeverity.ALERT));
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) BountyBoard.get(player.server).lastRefresh.remove(player.getUUID());
    }

    // ------------------------------------------------------------------------------------------------ sincronizacao

    private static void refresh(ServerPlayer player) {
        BountyBoard board = BountyBoard.get(player.server);
        long now = now(player.server);
        Long last = board.lastRefresh.get(player.getUUID());
        if (last != null && now - last < BountyRules.REFRESH_COOLDOWN_TICKS && now >= last) return;
        board.lastRefresh.put(player.getUUID(), now);
        sync(player);
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }

    public static void sync(ServerPlayer viewer) {
        PacketDistributor.sendToPlayer(viewer, new HoloNetworking.BountyBoardSync(view(viewer)));
    }

    /** O quadro como {@code viewer} o ve (rastreio grosseiro so dos contratos que ele aceitou). */
    public static List<HoloNetworking.BountyEntry> view(ServerPlayer viewer) {
        MinecraftServer server = viewer.server;
        long now = now(server);
        List<HoloNetworking.BountyEntry> out = new ArrayList<>();
        for (Bounty b : BountyBoard.get(server).all()) {
            int flags = (b.poster().equals(viewer.getUUID()) ? HoloNetworking.BountyEntry.MINE : 0)
                    | (b.target().equals(viewer.getUUID()) ? HoloNetworking.BountyEntry.ON_ME : 0)
                    | (b.hunting(viewer.getUUID()) ? HoloNetworking.BountyEntry.HUNTING : 0);
            int distance = HoloNetworking.BountyEntry.TRACK_NONE, dir = -1;
            if (b.hunting(viewer.getUUID())) {
                Player target = server.getPlayerList().getPlayer(b.target());
                if (target == null) distance = HoloNetworking.BountyEntry.TRACK_OFFLINE;
                else if (target.level() != viewer.level()) distance = HoloNetworking.BountyEntry.TRACK_OTHER_DIMENSION;
                else {
                    double dx = target.getX() - viewer.getX(), dz = target.getZ() - viewer.getZ();
                    distance = NoticeRules.roughDistance(Math.sqrt(dx * dx + dz * dz));
                    dir = NoticeRules.direction(dx, dz);
                }
            }
            out.add(new HoloNetworking.BountyEntry(b.id(), b.target(), b.targetName(), b.posterName(), b.reward(),
                    BountyRules.remaining(b, now), b.hunters().size(), flags, distance, dir));
            if (out.size() >= 256) break;
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ itens

    private static void giveBack(MinecraftServer server, BountyBoard board, Bounty b) {
        ServerPlayer poster = server.getPlayerList().getPlayer(b.poster());
        if (poster != null) give(poster, b.reward());
        else board.addPending(b.poster(), b.reward());
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.copy();
        if (copy.isEmpty()) return;
        if (!player.getInventory().add(copy) && !copy.isEmpty()) player.drop(copy, false);
    }

    public static Component rewardText(ItemStack reward) {
        return Component.translatable("bounty.respectcraft.reward", reward.getCount(), reward.getHoverName());
    }
}
