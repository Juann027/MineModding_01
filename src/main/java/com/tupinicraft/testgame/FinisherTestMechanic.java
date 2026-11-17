package com.tupinicraft.testgame;

import com.tupinicraft.animation.PlayerAnimationDef;
import com.tupinicraft.animation.PlayerAnimationEvent;
import com.tupinicraft.animation.PlayerAnimationService;
import com.tupinicraft.finisher.FinisherDef;
import com.tupinicraft.finisher.Finishers;
import com.tupinicraft.network.FinisherVisualSyncPayload;
import com.tupinicraft.profile.ProfileService;
import com.tupinicraft.registry.ModSounds;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
        final FinisherDef finisher;
        final Vec3 attackerAnchor;
        final Vec3 targetAnchor;
        final long finishAt;
        long lastProcessed = Long.MIN_VALUE;

        Session(LivingEntity attacker, LivingEntity target, FinisherDef finisher, Vec3 targetAnchor, long finishAt) {
            this.attacker = attacker;
            this.target = target;
            this.finisher = finisher;
            this.attackerAnchor = attacker.position();
            this.targetAnchor = targetAnchor;
            this.finishAt = finishAt;
        }
    }

    /** Distancia entre os participantes durante a cena (frente a frente). */
    private static final double STAGE_DISTANCE = 1.15;

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
        FinisherDef finisher = attacker instanceof ServerPlayer player
                ? Finishers.pick(skill -> ProfileService.get(player).hasSkill(skill.toString()), java.util.random.RandomGenerator.of("L64X128MixRandom"))
                : Finishers.TAKEDOWN;
        // Palco: vitima de frente para o executor, a STAGE_DISTANCE blocos, se houver espaco.
        float yaw = attacker.getYRot();
        Vec3 facing = Vec3.directionFromRotation(0, yaw);
        Vec3 staged = attacker.position().add(facing.scale(STAGE_DISTANCE));
        Vec3 targetAnchor = target.level().noCollision(target, target.getBoundingBox().move(staged.subtract(target.position())))
                ? staged : target.position();
        float targetYaw = Mth.wrapDegrees(yaw + 180F);
        place(target, targetAnchor, targetYaw);
        place(attacker, attacker.position(), yaw);

        long now = attacker.level().getGameTime();
        Session session = new Session(attacker, target, finisher, targetAnchor, now + finisher.durationTicks());
        SESSIONS.put(attacker.getUUID(), session);
        SESSIONS.put(target.getUUID(), session);
        PENDING.remove(target.getUUID());

        CompoundTag params = new CompoundTag();
        Vec3 middle = attacker.position().add(targetAnchor).scale(0.5);
        params.putString(Finishers.PARAM_FINISHER, finisher.id().toString());
        params.putDouble(Finishers.PARAM_X, middle.x);
        params.putDouble(Finishers.PARAM_Y, Math.min(attacker.getY(), targetAnchor.y));
        params.putDouble(Finishers.PARAM_Z, middle.z);
        params.putFloat(Finishers.PARAM_YAW, yaw);
        if (attacker instanceof ServerPlayer a && target instanceof ServerPlayer v) {
            PlayerAnimationService.playTogether(a, finisher.attacker(), v, finisher.victim(), params);
        } else if (attacker instanceof ServerPlayer a) {
            PlayerAnimationService.play(a, finisher.attacker(), now, Optional.of(target.getUUID()), params, true);
        } else if (target instanceof ServerPlayer v) {
            PlayerAnimationService.play(v, finisher.victim(), now, Optional.of(attacker.getUUID()), params, true);
        }
        sendVisual(session, true);
        if (attacker instanceof ServerPlayer p) message(p, "Finalizacao de teste iniciada. /stopitstopnow cancela.");
        if (target instanceof ServerPlayer p) message(p, "Teste iniciado. Pressione Shift ou /stopitstopnow para cancelar.");
        return true;
    }

    private static void place(LivingEntity entity, Vec3 position, float yaw) {
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(position.x, position.y, position.z, yaw, 0F);
        } else {
            entity.moveTo(position.x, position.y, position.z, yaw, 0F);
            entity.setYHeadRot(yaw);
            entity.setYBodyRot(yaw);
        }
        entity.setDeltaMovement(Vec3.ZERO);
    }

    /** Para as animacoes da cena (so se ainda forem desta finalizacao). */
    private static void stopAnimations(Session s) {
        stopIfOwn(s.attacker, s.finisher.attacker());
        stopIfOwn(s.target, s.finisher.victim());
    }

    private static void stopIfOwn(LivingEntity entity, PlayerAnimationDef def) {
        if (entity instanceof ServerPlayer player && PlayerAnimationService.current(player)
                .filter(state -> state.animation().equals(def.id())).isPresent()) {
            PlayerAnimationService.stop(player, PlayerAnimationEvent.EndReason.STOPPED);
        }
    }

    /** Impactos da finalizacao: som, particulas e o "tranco" da vitima, decididos aqui e vistos por todos. */
    public static void onAnimationTimed(PlayerAnimationEvent.Timed event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !event.getEvent().type().equals(Finishers.EVENT_HIT)) return;
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.attacker != player) return;
        int strength = Mth.clamp(parseInt(event.getEvent().data(), 1), 1, 3);
        LivingEntity target = s.target;
        var level = player.serverLevel();
        double y = target.getY() + target.getBbHeight() * 0.6;
        level.sendParticles(ParticleTypes.CRIT, target.getX(), y, target.getZ(), 10 * strength, 0.25, 0.25, 0.25, 0.4);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), y, target.getZ(), 1, 0, 0, 0, 0);
        if (strength >= 3) {
            level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.3, target.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.1, target.getZ(), 20, 0.6, 0.05, 0.6, 0.05);
        }
        level.playSound(null, target.blockPosition(), strength >= 2 ? ModSounds.FINISHER_IMPACT_HEAVY.get()
                : ModSounds.FINISHER_IMPACT.get(), SoundSource.PLAYERS, 1F, 0.9F + level.random.nextFloat() * 0.2F);
        if (strength >= 3) level.playSound(null, target.blockPosition(), ModSounds.FINISHER_STING.get(), SoundSource.PLAYERS, 0.7F, 1F);
        // Lampejo vermelho e inclinacao de dano nos clientes, sem dano real (a cena nao mata).
        level.broadcastDamageEvent(target, player.damageSources().playerAttack(player));
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void sendVisual(Session s, boolean active) {
        PacketDistributor.sendToAllPlayers(new FinisherVisualSyncPayload(
                s.attacker.getUUID().toString(), s.target.getUUID().toString(), ANIMATION_KEY,
                active, active ? s.finisher.durationTicks() : 0));
    }

    private static void release(Session s) {
        if (SESSIONS.get(s.attacker.getUUID()) != s) return;
        SESSIONS.remove(s.attacker.getUUID());
        SESSIONS.remove(s.target.getUUID());
        sendVisual(s, false);
    }

    private static void abort(Session s, String reason) {
        if (SESSIONS.get(s.attacker.getUUID()) != s) return;
        release(s);
        stopAnimations(s);
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
            // Fica caida durante a recuperacao (loop parado pelo servidor quando ela se recupera).
            PlayerAnimationService.play(player, Finishers.KNOCKED_DOWN, now, Optional.empty(), new CompoundTag(), true);
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
        if (INCAPACITATED.remove(player.getUUID()) != null) {
            changed = true;
            stopIfOwn(player, Finishers.KNOCKED_DOWN);
        }
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
                stopIfOwn(player, Finishers.KNOCKED_DOWN);
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
                || s.attacker.distanceToSqr(s.target) > 16.0D) {
            abort(s, "participante indisponivel ou afastado");
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
        hold(s.attacker, s.attackerAnchor);
        hold(s.target, s.targetAnchor);
    }

    private static void hold(LivingEntity entity, Vec3 anchor) {
        if (entity instanceof ServerPlayer player) {
            freezePlayer(player, anchor);
            return;
        }
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity instanceof Mob mob) mob.getNavigation().stop();
        if (entity.distanceToSqr(anchor) > 0.01D) entity.setPos(anchor.x, anchor.y, anchor.z);
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
