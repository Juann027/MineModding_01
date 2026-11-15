package com.tupinicraft.testgame;

import com.tupinicraft.network.FinisherVisualSyncPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FINISHER DE COMBATE NAO EXPLICITO PARA TESTES DE SINCRONIZACAO.
 * A vitima jogador deve aceitar CADA convite; para teste com mob, opt-in e revogavel.
 * Shift desmonta/cancela; /stopitstopnow funciona durante a cena e recuperacao.
 * Apenas servidor modifica estado e pontuacao. Nenhuma animacao sexual implementada.
 */
public final class FinisherTestMechanic {
    private static final double RANGE_SQUARED = 3.5 * 3.5;
    private static final String MOB_OPT_IN_KEY = "tupinicraft_allow_mob_finishers_test";
    private static final String ANIMATION_KEY = "test_hold";

    private record Pair(UUID attacker, UUID victim) {}
    private record Invite(UUID attacker, long until) {}
    private record Incapacitated(Vec3 anchor, long until, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}

    private static final class Session {
        final LivingEntity attacker;
        final LivingEntity target;
        final Vec3 attackerAnchor;
        final long finishAt;
        long lastProcessed = Long.MIN_VALUE;

        Session(LivingEntity attacker, LivingEntity target, long finishAt) {
            this.attacker = attacker;
            this.target = target;
            this.attackerAnchor = attacker.position();
            this.finishAt = finishAt;
        }
    }

    private static final Map<UUID, Invite> PENDING = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Incapacitated> INCAPACITATED = new HashMap<>();
    private static final Map<Pair, Long> PAIR_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> VICTIM_COOLDOWNS = new HashMap<>();

    private FinisherTestMechanic() {}

    private static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[TupiniCraft Teste] " + text));
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer attacker)) return;
        if (!attacker.isShiftKeyDown() || !attacker.getMainHandItem().isEmpty()) return;
        Entity raw = event.getTarget();
        if (!(raw instanceof LivingEntity target) || target == attacker || !target.isAlive()) return;
        if (attacker.distanceToSqr(target) > RANGE_SQUARED || attacker.isSpectator()) return;
        if (target instanceof ServerPlayer victim) {
            if (victim.isSpectator() || victim.isCreative()) return;
            if (!FinisherRules.isVulnerable(victim.getHealth(), victim.getMaxHealth())) {
                message(attacker, "O jogador precisa estar abaixo de 25% de vida.");
            } else {
                invite(attacker, victim);
            }
        } else if (target instanceof Mob mob && FinisherRules.isValidMobSize(mob.getBbWidth(), mob.getBbHeight())) {
            if (!FinisherRules.isVulnerable(mob.getHealth(), mob.getMaxHealth())) {
                message(attacker, "O mob precisa estar abaixo de 25% de vida.");
            } else if (!start(attacker, mob)) {
                message(attacker, "Alvo ocupado, protegido ou em cooldown.");
            }
        } else {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void invite(ServerPlayer attacker, ServerPlayer victim) {
        if (!available(attacker, victim)) {
            message(attacker, "Finalizacao indisponivel (cooldown, sessao ativa ou distancia).");
            return;
        }
        long now = victim.level().getGameTime();
        Invite old = PENDING.get(victim.getUUID());
        if (old != null && now < old.until()) {
            message(attacker, "Esse jogador ja recebeu um convite.");
            return;
        }
        PENDING.put(victim.getUUID(), new Invite(attacker.getUUID(), now + FinisherRules.INVITE_TICKS));
        message(attacker, "Convite de finalizacao de COMBATE enviado. Aguardando autorizacao.");
        message(victim, attacker.getGameProfile().getName() + " solicitou uma finalizacao de teste nao explicita. "
                + "/tupinitest finisher accept ou /tupinitest finisher deny (15s). "
                + "Para cancelar durante a acao: /stopitstopnow ou Shift.");
    }

    public static int accept(ServerPlayer victim) {
        Invite invite = PENDING.remove(victim.getUUID());
        if (invite == null || victim.level().getGameTime() >= invite.until()) {
            message(victim, "Nao ha convite valido.");
            return 0;
        }
        ServerPlayer attacker = victim.server.getPlayerList().getPlayer(invite.attacker());
        if (attacker == null || !start(attacker, victim)) {
            message(victim, "A finalizacao nao pode mais ser iniciada.");
            return 0;
        }
        return 1;
    }

    public static int deny(ServerPlayer victim) {
        Invite invite = PENDING.remove(victim.getUUID());
        if (invite == null) return 0;
        message(victim, "Convite recusado.");
        ServerPlayer attacker = victim.server.getPlayerList().getPlayer(invite.attacker());
        if (attacker != null) message(attacker, "Convite recusado.");
        return 1;
    }

    public static int mobOptIn(ServerPlayer player, boolean allowed) {
        player.getPersistentData().putBoolean(MOB_OPT_IN_KEY, allowed);
        message(player, allowed ? "TESTES de finalizacao por mobs permitidos." : "TESTES de finalizacao por mobs desativados.");
        return 1;
    }

    /** API para IA futura: so executa um finisher de combate nao explicito em jogadores que optaram pelo teste. */
    public static boolean tryStartFromMob(Mob mob, ServerPlayer target) {
        return target.getPersistentData().getBoolean(MOB_OPT_IN_KEY) && start(mob, target);
    }

    /** Permite testar a futura API de mobs SEM adicionar IA a todos os mobs existentes. */
    public static int mobDemo(ServerPlayer target) {
        if (!target.getPersistentData().getBoolean(MOB_OPT_IN_KEY)) {
            message(target, "Ative antes: /tupinitest finisher mobs on");
            return 0;
        }
        Mob mob = target.level().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(3.0D),
                m -> m.isAlive() && FinisherRules.isValidMobSize(m.getBbWidth(), m.getBbHeight()))
                .stream().min((a,b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target))).orElse(null);
        if (mob == null || !tryStartFromMob(mob, target)) {
            message(target, "Nenhum mob compativel proximo ou alvo nao vulneravel/disponivel.");
            return 0;
        }
        return 1;
    }

    private static boolean available(LivingEntity attacker, LivingEntity target) {
        if (!attacker.isAlive() || !target.isAlive() || attacker == target || attacker.level() != target.level()) return false;
        if (attacker.distanceToSqr(target) > RANGE_SQUARED) return false;
        if (!FinisherRules.isVulnerable(target.getHealth(), target.getMaxHealth())) return false;
        if (attacker.isPassenger() || !attacker.getPassengers().isEmpty()
                || target.isPassenger() || !target.getPassengers().isEmpty()) return false;
        if (SESSIONS.containsKey(attacker.getUUID()) || SESSIONS.containsKey(target.getUUID())) return false;
        if (INCAPACITATED.containsKey(attacker.getUUID()) || INCAPACITATED.containsKey(target.getUUID())) return false;
        long now = attacker.level().getGameTime();
        return FinisherRules.isCooldownOver(now, PAIR_COOLDOWNS.getOrDefault(new Pair(attacker.getUUID(), target.getUUID()), 0L))
                && FinisherRules.isCooldownOver(now, VICTIM_COOLDOWNS.getOrDefault(target.getUUID(), 0L));
    }

    private static boolean start(LivingEntity attacker, LivingEntity target) {
        if (!available(attacker, target)) return false;
        if (!target.startRiding(attacker, true)) return false;
        Session session = new Session(attacker, target, attacker.level().getGameTime() + FinisherRules.ANIMATION_TICKS);
        SESSIONS.put(attacker.getUUID(), session);
        SESSIONS.put(target.getUUID(), session);
        PENDING.remove(target.getUUID());
        attacker.swing(InteractionHand.MAIN_HAND); // Animacao vanilla PROVISORIA.
        target.swing(InteractionHand.MAIN_HAND);
        sendVisual(session, true);
        if (attacker instanceof ServerPlayer p) message(p, "Finalizacao de teste iniciada. /stopitstopnow cancela.");
        if (target instanceof ServerPlayer p) message(p, "Teste iniciado. Pressione Shift ou /stopitstopnow para cancelar.");
        return true;
    }

    private static void sendVisual(Session s, boolean active) {
        PacketDistributor.sendToAllPlayers(new FinisherVisualSyncPayload(
                s.attacker.getUUID().toString(), s.target.getUUID().toString(), ANIMATION_KEY,
                active, active ? FinisherRules.ANIMATION_TICKS : 0));
    }

    private static void release(Session s) {
        if (SESSIONS.get(s.attacker.getUUID()) != s) return;
        SESSIONS.remove(s.attacker.getUUID());
        SESSIONS.remove(s.target.getUUID());
        if (s.target.getVehicle() == s.attacker) s.target.stopRiding();
        sendVisual(s, false);
    }

    private static void abort(Session s, String reason) {
        if (SESSIONS.get(s.attacker.getUUID()) != s) return;
        release(s);
        if (s.attacker instanceof ServerPlayer p) message(p, "Finalizacao cancelada: " + reason);
        if (s.target instanceof ServerPlayer p) message(p, "Finalizacao cancelada: " + reason);
    }

    private static void complete(Session s) {
        if (SESSIONS.get(s.attacker.getUUID()) != s) return;
        release(s);
        long now = s.target.level().getGameTime();
        PAIR_COOLDOWNS.put(new Pair(s.attacker.getUUID(), s.target.getUUID()),
                now + FinisherRules.SAME_PAIR_COOLDOWN_TICKS);
        VICTIM_COOLDOWNS.put(s.target.getUUID(), now + FinisherRules.VICTIM_COOLDOWN_TICKS);
        if (s.target instanceof ServerPlayer player) {
            int updated = DefeatCounter.increment(player);
            INCAPACITATED.put(player.getUUID(), new Incapacitated(player.position(), now + FinisherRules.INCAPACITATED_TICKS, player.level().dimension()));
            message(player, "Derrota especial de TESTE registrada (" + updated + "). Recuperacao: 10s. /stopitstopnow libera.");
        }
        if (s.attacker instanceof ServerPlayer player) message(player, "Finalizacao de combate de teste concluida.");
    }

    public static int emergencyStop(ServerPlayer player) {
        boolean changed = false;
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) {
            abort(s, "solicitacao do jogador");
            changed = true;
        }
        if (INCAPACITATED.remove(player.getUUID()) != null) changed = true;
        if (PENDING.remove(player.getUUID()) != null) changed = true;
        if (PENDING.entrySet().removeIf(e -> e.getValue().attacker().equals(player.getUUID()))) changed = true;
        if (changed) message(player, "Interacao e/ou recuperacao encerradas.");
        else message(player, "Voce nao esta em uma finalizacao ou recuperacao.");
        return changed ? 1 : 0;
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        if (now % 1200L == 0L) {
            PAIR_COOLDOWNS.entrySet().removeIf(e -> e.getValue() <= now);
            VICTIM_COOLDOWNS.entrySet().removeIf(e -> e.getValue() <= now);
            PENDING.entrySet().removeIf(e -> e.getValue().until() <= now);
        }
        Incapacitated incap = INCAPACITATED.get(player.getUUID());
        if (incap != null) {
            if (now >= incap.until() || !player.level().dimension().equals(incap.dimension())) {
                INCAPACITATED.remove(player.getUUID());
                message(player, "Recuperado.");
            } else {
                freezePlayer(player, incap.anchor());
            }
        }
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.lastProcessed == now) return;
        s.lastProcessed = now;
        if (!s.attacker.isAlive() || !s.target.isAlive()
                || s.attacker.isRemoved() || s.target.isRemoved()
                || s.attacker.level() != s.target.level()
                || s.target.getVehicle() != s.attacker) {
            abort(s, "participante indisponivel ou desmontou");
            return;
        }
        // A vitima pode cancelar por Shift ou comando; o executor tambem pode usar o comando.
        if (s.target instanceof ServerPlayer target && target.isShiftKeyDown()) {
            abort(s, "cancelamento por Shift");
            return;
        }
        if (now >= s.finishAt) {
            complete(s);
            return;
        }
        if (s.attacker instanceof ServerPlayer attacker) freezePlayer(attacker, s.attackerAnchor);
        else {
            s.attacker.setDeltaMovement(Vec3.ZERO);
            if (s.attacker instanceof Mob mob) mob.getNavigation().stop();
        }
        s.target.setDeltaMovement(Vec3.ZERO);
    }

    private static void freezePlayer(ServerPlayer player, Vec3 position) {
        player.setDeltaMovement(Vec3.ZERO);
        if (player.distanceToSqr(position) > 0.12D) {
            // Correcao autoritativa de posicao. A tecla/cmd de emergencia continua disponivel.
            player.connection.teleport(position.x, position.y, position.z, player.getYRot(), player.getXRot());
        }
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Session s = SESSIONS.get(event.getEntity().getUUID());
        if (s != null) {
            // Uma tentativa de dano interrompe a cena, SEM aplicar o dano da tentativa.
            event.setCanceled(true);
            abort(s, "interferencia por dano");
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Session s = SESSIONS.get(event.getEntity().getUUID());
        if (s != null) abort(s, "morte");
        INCAPACITATED.remove(event.getEntity().getUUID());
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) abort(s, "desconexao");
        INCAPACITATED.remove(player.getUUID());
        PENDING.remove(player.getUUID());
        PENDING.entrySet().removeIf(e -> e.getValue().attacker().equals(player.getUUID()));
    }

    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) event.getEntity().getPersistentData().putBoolean(
                MOB_OPT_IN_KEY, event.getOriginal().getPersistentData().getBoolean(MOB_OPT_IN_KEY));
    }

    private static boolean locked(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID()) || INCAPACITATED.containsKey(player.getUUID());
    }

    public static void blockAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) event.setCanceled(true);
    }

    public static void blockEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    public static void blockBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    public static void blockItemInteract(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    public static void blockMining(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && locked(player)) event.setCanceled(true);
    }
}
