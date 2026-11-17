package com.tupinicraft.animation;

import com.tupinicraft.network.PlayerAnimationSyncPayload;
import com.tupinicraft.registry.ModAttachments;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Animation Manager no servidor: unico ponto que inicia, troca e interrompe animacoes de jogadores.
 * O servidor decide; os clientes so reproduzem o estado recebido ({@link PlayerAnimationSyncPayload}).
 */
public final class PlayerAnimationService {
    /** Intervalo minimo entre pedidos do proprio jogador (anti-spam de pacotes). */
    private static final int REQUEST_COOLDOWN_TICKS = 5;

    private PlayerAnimationService() {}

    // ------------------------------------------------------------------ API

    /** Inicia a animacao agora, respeitando a prioridade da que estiver em andamento. */
    public static boolean play(ServerPlayer player, ResourceLocation animation) {
        PlayerAnimationDef def = PlayerAnimations.get(animation);
        return def != null && play(player, def, now(player), Optional.empty(), new CompoundTag(), false);
    }

    /**
     * @param startGameTime tempo de jogo do inicio (use o mesmo valor em dois jogadores para sincroniza-los)
     * @param partner       outro participante da mesma sequencia
     * @param force         ignora a prioridade da animacao em andamento
     */
    public static boolean play(ServerPlayer player, PlayerAnimationDef def, long startGameTime,
                               Optional<UUID> partner, CompoundTag params, boolean force) {
        return play(player, def, startGameTime, partner, params, force, def.loop());
    }

    /** Como acima, escolhendo nesta execucao se o clip repete ate ser interrompido (ex.: pose mantida). */
    public static boolean play(ServerPlayer player, PlayerAnimationDef def, long startGameTime,
                               Optional<UUID> partner, CompoundTag params, boolean force, boolean loop) {
        if (!canStart(player, def, force)) return false;
        PlayerAnimationTracker tracker = player.getData(ModAttachments.PLAYER_ANIMATION);
        begin(player, tracker, def, new PlayerAnimationState(def.id(), startGameTime, def.durationTicks(), loop,
                tracker.sequence + 1, partner, params.copy()));
        return true;
    }

    /**
     * Toca varios clips em sequencia sem voltar a pose vanilla entre eles. Todos os instantes de troca saem do
     * relogio do servidor e vao num pacote so; clientes e servidor trocam de segmento no mesmo tick.
     * Cancelamento, morte, saida, dano ({@code stopOnHurt} do segmento vigente) e animacoes de prioridade maior
     * encerram a sequencia inteira, como uma animacao comum.
     */
    public static boolean playSequence(ServerPlayer player, AnimationSequence sequence, CompoundTag params, boolean force) {
        if (sequence.problem().isPresent() || !canStart(player, sequence.entries().get(0).def(), force)) return false;
        PlayerAnimationTracker tracker = player.getData(ModAttachments.PLAYER_ANIMATION);
        begin(player, tracker, sequence.entries().get(0).def(),
                sequence.toState(now(player), tracker.sequence + 1, Optional.empty(), params));
        return true;
    }

    /**
     * Sai agora do segmento vigente e passa ao seguinte (ex.: deixar o loop de quatro apoios quando uma condicao
     * do servidor for cumprida). Retorna false se nao ha sequencia ou se o segmento vigente e o ultimo.
     */
    public static boolean advance(ServerPlayer player) {
        PlayerAnimationTracker tracker = player.getExistingData(ModAttachments.PLAYER_ANIMATION).orElse(null);
        long now = now(player);
        if (tracker == null || tracker.state == null || tracker.state.finished(now)) return false;
        PlayerAnimationState segment = tracker.state.at(now);
        if (!segment.hasNext()) return false;
        tracker.state = segment.next(now);
        enterSegment(player, tracker, tracker.state);
        broadcast(player, Optional.of(tracker.state));
        return true;
    }

    private static boolean canStart(ServerPlayer player, PlayerAnimationDef def, boolean force) {
        if (!player.isAlive() || player.isSpectator() || player.hasDisconnected()) return false;
        PlayerAnimationTracker tracker = player.getData(ModAttachments.PLAYER_ANIMATION);
        if (tracker.state != null && !tracker.state.finished(now(player))) {
            if (!force && def.priority() < currentDefinition(player, tracker).priority()) return false;
            finish(player, tracker, PlayerAnimationEvent.EndReason.REPLACED, false);
        }
        return true;
    }

    private static void begin(ServerPlayer player, PlayerAnimationTracker tracker, PlayerAnimationDef def,
                              PlayerAnimationState state) {
        tracker.sequence = state.sequence();
        tracker.definition = def;
        tracker.state = state;
        tracker.segment = state;
        tracker.cursor = new TimedEventCursor();
        NeoForge.EVENT_BUS.post(new PlayerAnimationEvent.Start(player, def, state));
        broadcast(player, Optional.of(state));
    }

    /** Troca de segmento: fecha o anterior (FINISHED) e abre o seguinte, sem pacote (o cliente calcula igual). */
    private static void enterSegment(ServerPlayer player, PlayerAnimationTracker tracker, PlayerAnimationState segment) {
        PlayerAnimationDef next = PlayerAnimations.get(segment.animation());
        if (next == null) { // nao acontece: a sequencia so aceita animacoes registradas
            finish(player, tracker, PlayerAnimationEvent.EndReason.STOPPED, true);
            return;
        }
        NeoForge.EVENT_BUS.post(new PlayerAnimationEvent.End(player, tracker.definition, tracker.segment,
                PlayerAnimationEvent.EndReason.FINISHED));
        tracker.definition = next;
        tracker.segment = segment;
        tracker.cursor = new TimedEventCursor();
        NeoForge.EVENT_BUS.post(new PlayerAnimationEvent.Start(player, next, segment));
    }

    /** Definicao do segmento vigente (numa sequencia, muda com o tempo). */
    private static PlayerAnimationDef currentDefinition(ServerPlayer player, PlayerAnimationTracker tracker) {
        PlayerAnimationDef def = PlayerAnimations.get(tracker.state.at(now(player)).animation());
        return def != null ? def : tracker.definition;
    }

    /** Duas entidades na mesma sequencia: mesmo instante de inicio e referencia cruzada do parceiro. */
    public static boolean playTogether(ServerPlayer first, PlayerAnimationDef firstDef,
                                       ServerPlayer second, PlayerAnimationDef secondDef, CompoundTag params) {
        if (first == second || !canReplace(first, firstDef) || !canReplace(second, secondDef)) return false;
        long start = now(first);
        return play(first, firstDef, start, Optional.of(second.getUUID()), params, true)
                && play(second, secondDef, start, Optional.of(first.getUUID()), params, true);
    }

    /** Interrompe a animacao atual. Retorna false se nao havia nenhuma. */
    public static boolean stop(ServerPlayer player, PlayerAnimationEvent.EndReason reason) {
        Optional<PlayerAnimationTracker> tracker = player.getExistingData(ModAttachments.PLAYER_ANIMATION);
        if (tracker.isEmpty() || tracker.get().state == null) return false;
        finish(player, tracker.get(), reason, true);
        return true;
    }

    public static Optional<PlayerAnimationState> current(ServerPlayer player) {
        return player.getExistingData(ModAttachments.PLAYER_ANIMATION)
                .map(tracker -> tracker.state)
                .filter(state -> !state.finished(now(player)))
                .map(state -> state.at(now(player)));
    }

    /** Pedido vindo do cliente: so a intencao; tudo e revalidado aqui. */
    public static void handleRequest(ServerPlayer player, ResourceLocation animation, boolean stop) {
        PlayerAnimationTracker tracker = player.getData(ModAttachments.PLAYER_ANIMATION);
        long now = now(player);
        if (now >= tracker.lastRequestTime && now - tracker.lastRequestTime < REQUEST_COOLDOWN_TICKS) return;
        tracker.lastRequestTime = now;
        if (stop) {
            // O jogador so cancela o que ele mesmo poderia ter iniciado; cenas do servidor nao.
            if (tracker.state != null && currentDefinition(player, tracker).playerTriggerable()) {
                stop(player, PlayerAnimationEvent.EndReason.STOPPED);
            }
            return;
        }
        PlayerAnimationDef def = PlayerAnimations.get(animation);
        if (def == null || !def.playerTriggerable()) return;
        play(player, def, now, Optional.empty(), new CompoundTag(), false);
    }

    // -------------------------------------------------------------- eventos

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerAnimationTracker tracker = player.getExistingData(ModAttachments.PLAYER_ANIMATION).orElse(null);
            if (tracker == null || tracker.state == null) continue;
            PlayerAnimationState state = tracker.state;
            long now = now(player);
            if (now < state.startGameTime()) continue;
            PlayerAnimationState segment = state.at(now);
            if (segment.segment() != tracker.segment.segment()) enterSegment(player, tracker, segment);
            if (tracker.state != state) continue;
            PlayerAnimationDef def = tracker.definition;
            tracker.cursor.advance(def, segment.loop(), segment.elapsed(now),
                    timed -> NeoForge.EVENT_BUS.post(new PlayerAnimationEvent.Timed(player, def, segment, timed)));
            // Os clientes encerram sozinhos pelo tempo; nao e preciso pacote no fim natural.
            if (tracker.state == state && state.finished(now)) {
                finish(player, tracker, PlayerAnimationEvent.EndReason.FINISHED, false);
            }
        }
    }

    /** Quem comeca a ver um jogador recebe o estado atual dele (ou a limpeza de um estado antigo). */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(observer, new PlayerAnimationSyncPayload(target.getUUID(), current(target)));
        }
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new PlayerAnimationSyncPayload(player.getUUID(), current(player)));
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new PlayerAnimationSyncPayload(player.getUUID(), Optional.empty()));
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player, PlayerAnimationEvent.EndReason.DEATH);
    }

    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) return;
        PlayerAnimationTracker tracker = player.getExistingData(ModAttachments.PLAYER_ANIMATION).orElse(null);
        if (tracker != null && tracker.state != null && currentDefinition(player, tracker).stopOnHurt()) {
            stop(player, PlayerAnimationEvent.EndReason.HURT);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAnimationTracker tracker = player.getExistingData(ModAttachments.PLAYER_ANIMATION).orElse(null);
        if (tracker != null && tracker.state != null) {
            finish(player, tracker, PlayerAnimationEvent.EndReason.DISCONNECT, false);
        }
        // Todos esquecem o estado de quem saiu, inclusive quem nao o estava vendo.
        PacketDistributor.sendToAllPlayers(new PlayerAnimationSyncPayload(player.getUUID(), Optional.empty()));
    }

    // -------------------------------------------------------------- interno

    private static boolean canReplace(ServerPlayer player, PlayerAnimationDef def) {
        if (!player.isAlive() || player.isSpectator() || player.hasDisconnected()) return false;
        PlayerAnimationTracker tracker = player.getExistingData(ModAttachments.PLAYER_ANIMATION).orElse(null);
        return tracker == null || tracker.state == null || tracker.state.finished(now(player))
                || def.priority() >= currentDefinition(player, tracker).priority();
    }

    private static void finish(ServerPlayer player, PlayerAnimationTracker tracker,
                               PlayerAnimationEvent.EndReason reason, boolean notifyClients) {
        PlayerAnimationState state = tracker.segment != null ? tracker.segment : tracker.state;
        PlayerAnimationDef def = tracker.definition;
        tracker.state = null;
        tracker.segment = null;
        tracker.definition = null;
        NeoForge.EVENT_BUS.post(new PlayerAnimationEvent.End(player, def, state, reason));
        if (notifyClients) broadcast(player, Optional.empty());
    }

    private static void broadcast(ServerPlayer player, Optional<PlayerAnimationState> state) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new PlayerAnimationSyncPayload(player.getUUID(), state));
    }

    /** Tempo de jogo compartilhado por todas as dimensoes (o cliente recebe o mesmo relogio). */
    private static long now(ServerPlayer player) {
        return player.serverLevel().getGameTime();
    }
}
