package com.respectcraft.notice;

import com.respectcraft.RespectCraft;
import com.respectcraft.dimension.InvertedRealm;
import com.respectcraft.finisher.BossDefeatEvent;
import com.respectcraft.finisher.FinisherParticipants;
import com.respectcraft.network.HoloNetworking;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Avisos holograficos automaticos (Etapa 4.11): o <b>unico</b> caminho de um aviso do servidor ate os cartoes da
 * tela e a secao Alerts do menu K. Monta a audiencia, posta {@link HoloNoticeEvent} (pode trocar/cancelar), envia o
 * pacote e, se o aviso tem local e a categoria tem farol, acende o efeito do Calamidade ({@link NoticeFx}).
 *
 * <p>Fontes automaticas de hoje: chefe ({@code c:bosses}) que aparece, chefe derrotado ({@code BossDefeatEvent}),
 * portal do Nether aceso, chegada ao Inverted Realm e a caca a recompensa. Chefes, portais e eventos futuros so
 * chamam {@link #bossAppeared}, {@link #portalOpened} ou {@link #worldEvent}. Sem estado: nada em {@code static}.
 */
public final class HoloNotices {
    private HoloNotices() {}

    // ------------------------------------------------------------------------------------------------ envio

    /** Envia para a lista; acende o farol em {@code fxLevel} se houver local. Devolve quantos receberam. */
    public static int send(Collection<ServerPlayer> players, HoloNotice notice, ServerLevel fxLevel, Entity anchor) {
        HoloNoticeEvent event = new HoloNoticeEvent(notice, new ArrayList<>(players));
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) return 0;
        HoloNotice out = event.getNotice();
        HoloNetworking.NoticePayload payload = new HoloNetworking.NoticePayload(out);
        int n = 0;
        for (ServerPlayer p : event.getAudience()) {
            if (p == null || p.hasDisconnected()) continue;
            PacketDistributor.sendToPlayer(p, payload);
            n++;
        }
        if (fxLevel != null && out.location().isPresent() && out.kind().beacon() != NoticeKind.Beacon.NONE
                && fxLevel.dimension().location().equals(out.location().get().dimension())) {
            NoticeFx.play(fxLevel, out.kind().beacon(), out.location().get().pos(), anchor);
        }
        return n;
    }

    public static int toPlayer(ServerPlayer player, HoloNotice notice) {
        return send(List.of(player), notice, null, null);
    }

    public static int toAll(MinecraftServer server, HoloNotice notice) {
        return send(server.getPlayerList().getPlayers(), notice, null, null);
    }

    /** Todos os jogadores do nivel; o farol acende no local do aviso. */
    public static int toLevel(ServerLevel level, HoloNotice notice, Entity anchor) {
        return send(level.players(), notice, level, anchor);
    }

    /** Jogadores do nivel dentro do raio do ponto; o farol acende no local do aviso. */
    public static int near(ServerLevel level, Vec3 at, double radius, HoloNotice notice, Entity anchor) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (NoticeRules.inRadius(p.getX() - at.x, p.getY() - at.y, p.getZ() - at.z, radius)) list.add(p);
        }
        return send(list, notice, level, anchor);
    }

    // ------------------------------------------------------------------------------------------------ API para o futuro

    /** Um chefe apareceu: aviso para quem estiver perto e farol vermelho seguindo o chefe. */
    public static int bossAppeared(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) return 0;
        BlockPos pos = boss.blockPosition();
        HoloNotice n = HoloNotice.of(NoticeKind.BOSS_SPAWN,
                        Component.translatable("notice.respectcraft.boss_spawn.title", boss.getDisplayName()),
                        Component.translatable("notice.respectcraft.boss_spawn.body", pos.getX(), pos.getY(), pos.getZ()))
                .at(level, pos).dedupe("boss_spawn:" + boss.getUUID());
        return near(level, boss.position(), NoticeRules.BOSS_RADIUS, n, boss);
    }

    /** Um portal se abriu em {@code pos}; {@code destination} e o nome do destino (ou null). */
    public static int portalOpened(ServerLevel level, BlockPos pos, Component destination) {
        Component body = destination == null
                ? Component.translatable("notice.respectcraft.portal.body", pos.getX(), pos.getY(), pos.getZ())
                : Component.translatable("notice.respectcraft.portal.body_to", destination, pos.getX(), pos.getY(), pos.getZ());
        HoloNotice n = HoloNotice.of(NoticeKind.PORTAL, Component.translatable("notice.respectcraft.portal.title"), body)
                .at(level, pos).dedupe("portal:" + level.dimension().location() + ":" + (pos.getX() >> 3) + "," + (pos.getZ() >> 3));
        return near(level, Vec3.atCenterOf(pos), NoticeRules.PORTAL_RADIUS, n, null);
    }

    /** Evento de mundo generico (para os eventos que ainda virao); {@code pos} opcional. */
    public static int worldEvent(ServerLevel level, Component title, Component body, BlockPos pos) {
        HoloNotice n = HoloNotice.of(NoticeKind.WORLD_EVENT, title, body);
        if (pos != null) n = n.at(level, pos);
        return toLevel(level, n, null);
    }

    // ------------------------------------------------------------------------------------------------ fontes automaticas

    /** Chefe novo no mundo (nao o que so carregou do disco). */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide() || event.isCanceled()) return;
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof ServerPlayer) return;
        if (FinisherParticipants.kindOf(living) != FinisherParticipants.Kind.BOSS) return;
        // O nome e a posicao so ficam certos depois de entrar: manda no proximo tick do servidor.
        MinecraftServer server = living.getServer();
        if (server == null) return;
        server.execute(() -> {
            if (living.isAlive() && !living.isRemoved()) bossAppeared(living);
        });
    }

    /** Derrota de chefe (so anuncio; ninguem recebe recompensa por aqui). */
    public static void onBossDefeat(BossDefeatEvent event) {
        LivingEntity boss = event.getBoss();
        if (!(boss.level() instanceof ServerLevel level)) return;
        BlockPos pos = boss.blockPosition();
        Component body = event.getCredited()
                .<Component>map(p -> Component.translatable(event.getMethod() == BossDefeatEvent.Method.FINISHER
                        ? "notice.respectcraft.boss_defeat.body_finisher" : "notice.respectcraft.boss_defeat.body",
                        p.getDisplayName()))
                .orElse(Component.translatable("notice.respectcraft.boss_defeat.body_nobody"));
        HoloNotice n = HoloNotice.of(NoticeKind.BOSS_DEFEAT,
                        Component.translatable("notice.respectcraft.boss_defeat.title", boss.getDisplayName()), body)
                .at(level, pos).dedupe("boss_defeat:" + boss.getUUID());
        toLevel(level, n, null);
    }

    /** Portal do Nether aceso (prioridade LOWEST: so o que nenhum outro mod cancelou). */
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        portalOpened(level, event.getPos(), null);
    }

    /** Chegada ao Inverted Realm. */
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getTo().equals(InvertedRealm.LEVEL)) return;
        toPlayer(player, HoloNotice.of(NoticeKind.REALM, Component.translatable("notice.respectcraft.realm.title"),
                Component.translatable("notice.respectcraft.realm.body")).dedupe("realm_enter"));
    }

    /**
     * Etapa 4.14.1: janela cinematografica privada para um jogador (sem cartao; o aviso tambem entra no historico da
     * secao Alerts). {@code millis} 0 = duracao padrao do jogador; minimo 0,5 s.
     */
    public static int cinematic(ServerPlayer player, NoticeKind kind, NoticeStyle style, Component title, Component subtitle,
                                Component body, int millis) {
        HoloNotice n = HoloNotice.cinematic(kind, style, title, body).subtitle(subtitle).duration(millis);
        return toPlayer(player, n);
    }

    /** Diagnostico ({@code /respectcraft notice cinematic}): uma janela de exemplo do estilo. */
    public static int testCinematic(Collection<ServerPlayer> players, NoticeStyle style, int millis) {
        HoloNotice n = HoloNotice.cinematic(NoticeKind.SYSTEM, style,
                        Component.translatable("notice.respectcraft.test.cinematic_title"),
                        Component.translatable("notice.respectcraft.test.cinematic_body"))
                .subtitle(Component.translatable(style.translationKey())).duration(millis);
        int sent = send(players, n, null, null);
        RespectCraft.LOGGER.info("RespectCraft cinematic notice {} ({} ms) sent to {} players", style.id(), millis, sent);
        return sent;
    }

    /** Diagnostico ({@code /respectcraft notice test}): um aviso de exemplo da categoria. */
    public static int test(Collection<ServerPlayer> players, NoticeKind kind, ServerLevel level, BlockPos pos) {
        HoloNotice n = HoloNotice.of(kind, Component.translatable("notice.respectcraft.test.title", Component.translatable(kind.translationKey())),
                Component.translatable("notice.respectcraft.test.body"));
        if (pos != null && level != null) n = n.at(level, pos);
        int sent = send(players, n, level, null);
        RespectCraft.LOGGER.info("RespectCraft notice {} sent to {} players", kind.id(), sent);
        return sent;
    }
}
