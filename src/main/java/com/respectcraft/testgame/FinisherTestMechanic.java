package com.respectcraft.testgame;

import com.respectcraft.animation.PlayerAnimationDef;
import com.respectcraft.animation.PlayerAnimationEvent;
import com.respectcraft.animation.PlayerAnimationService;
import com.respectcraft.RespectCraft;
import com.respectcraft.finisher.FinisherDef;
import com.respectcraft.finisher.FinisherExecution;
import com.respectcraft.finisher.FinisherParticipants;
import com.respectcraft.finisher.FinisherRole;
import com.respectcraft.finisher.FinisherSessionEvent;
import com.respectcraft.finisher.FinisherTargeting;
import com.respectcraft.finisher.Finishers;
import com.respectcraft.fx.FinisherFx;
import com.respectcraft.fx.FinisherFxRules;
import com.respectcraft.network.FinisherVisualSyncPayload;
import com.respectcraft.profile.ProfileService;
import com.respectcraft.registry.ModSounds;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FINISHER DE COMBATE NAO EXPLICITO PARA TESTES DE SINCRONIZACAO.
 * A vitima jogador deve aceitar CADA convite; para teste com mob, opt-in e revogavel.
 * Shift desmonta/cancela; /stopitstopnow funciona durante a cena e recuperacao.
 * Apenas servidor modifica estado e pontuacao. Nenhuma animacao sexual implementada.
 *
 * <p>Etapa 4.8 (varios rigs): cada participante resolve o proprio clip por {@link FinisherParticipants} — rig do
 * jogador, rig GeckoLib proprio da entidade ({@code triggerAnim} no controlador dela) ou parado. Combinacoes sem
 * jogador e participantes invalidos sao recusados antes de comecar. Fim e cancelamento postam
 * {@link FinisherSessionEvent} uma vez por sessao.
 *
 * <p>Etapa 4.10: os efeitos visuais sao do {@link FinisherFx} (Calamidade_Awakening), comecados aqui no inicio da
 * sessao, avancados no tick da sessao e encerrados em {@link #abort} e {@link #complete}. Finalizacao sem
 * {@code FinisherFxDef} continua com as particulas vanilla; os sons ficam aqui nos dois casos.
 *
 * <p>Etapa 4.10.1 (execucoes reais): a cena concluida termina com o golpe final de {@link FinisherExecution} — dano
 * comum do Minecraft, uma vez por sessao, depois de o fim da cena liberar os participantes. Cancelamento (Shift,
 * comando, dano, morte externa, saida, troca de dimensao, distancia) nunca executa. Chefes so podem ser executados
 * pela finalizacao exclusiva deles ({@link FinisherTargeting}): a recusa acontece em {@link #start}, antes de
 * qualquer palco, animacao, trava ou efeito.
 */
public final class FinisherTestMechanic {
    private static final double RANGE_SQUARED = 3.5 * 3.5;
    private static final String MOB_OPT_IN_KEY = "respectcraft_allow_mob_finishers_test";
    private static final String ANIMATION_KEY = "test_hold";

    private record Pair(UUID attacker, UUID victim) {}
    private record Invite(UUID attacker, long until) {}
    private record Incapacitated(Vec3 anchor, long until, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}

    private static final class Session {
        final LivingEntity attacker;
        final LivingEntity target;
        final FinisherDef finisher;
        final FinisherParticipants.Interaction interaction;
        final FinisherParticipants.Clip attackerClip;
        final FinisherParticipants.Clip targetClip;
        final Vec3 attackerAnchor;
        final Vec3 targetAnchor;
        final float attackerYaw;
        final float targetYaw;
        final long finishAt;
        final long startedAt;
        long lastProcessed = Long.MIN_VALUE;
        /** Efeitos do Calamidade desta sessao (null = finalizacao sem fx). */
        FinisherFx.Scene fx;

        Session(LivingEntity attacker, LivingEntity target, FinisherDef finisher, FinisherParticipants.Interaction interaction,
                FinisherParticipants.Clip attackerClip, FinisherParticipants.Clip targetClip,
                Vec3 targetAnchor, float attackerYaw, float targetYaw, long finishAt) {
            this.attacker = attacker;
            this.target = target;
            this.finisher = finisher;
            this.interaction = interaction;
            this.attackerClip = attackerClip;
            this.targetClip = targetClip;
            this.attackerAnchor = attacker.position();
            this.targetAnchor = targetAnchor;
            this.attackerYaw = attackerYaw;
            this.targetYaw = targetYaw;
            this.finishAt = finishAt;
            this.startedAt = finishAt - finisher.durationTicks();
        }
    }

    private static final Map<UUID, Invite> PENDING = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Incapacitated> INCAPACITATED = new HashMap<>();
    private static final Map<Pair, Long> PAIR_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> VICTIM_COOLDOWNS = new HashMap<>();

    private FinisherTestMechanic() {}

    /** Resposta de {@link #start}: comecou, ou o motivo da recusa (ingles, para o jogador/comando). */
    public record StartResult(boolean started, String message) {
        static final StartResult STARTED = new StartResult(true, "Finisher started.");
        static StartResult refused(String message) { return new StartResult(false, message); }
    }

    private static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[RespectCraft Test] " + text));
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer attacker)) return;
        if (!attacker.isShiftKeyDown() || !attacker.getMainHandItem().isEmpty()) return;
        // Partes (Dragao do Fim) contam como a entidade dona, para a regra de chefe valer nelas tambem.
        Entity raw = FinisherParticipants.owner(event.getTarget());
        if (!(raw instanceof LivingEntity target) || target == attacker || !target.isAlive() || attacker.isSpectator()) return;
        if (FinisherParticipants.kindOf(target) == FinisherParticipants.Kind.BOSS) {
            // Etapa 4.10.1: chefe so pela finalizacao exclusiva dele. A recusa sai antes de tamanho, vida e distancia
            // (nada de cena, trava ou dano); um dia, com a exclusiva liberada, start segue as regras normais.
            StartResult result = start(attacker, target, null, false);
            if (!result.started()) message(attacker, result.message());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (attacker.distanceToSqr(target) > RANGE_SQUARED) return;
        if (target instanceof ServerPlayer victim) {
            if (victim.isSpectator() || victim.isCreative()) return;
            if (!FinisherRules.isVulnerable(victim.getHealth(), victim.getMaxHealth())) {
                message(attacker, "The player must be below 25% health.");
            } else {
                invite(attacker, victim);
            }
        } else if (target instanceof Mob mob && FinisherRules.isValidMobSize(mob.getBbWidth(), mob.getBbHeight())) {
            if (!FinisherRules.isVulnerable(mob.getHealth(), mob.getMaxHealth())) {
                message(attacker, "The mob must be below 25% health.");
            } else {
                StartResult result = start(attacker, mob, null, false);
                if (!result.started()) message(attacker, result.message());
            }
        } else {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void invite(ServerPlayer attacker, ServerPlayer victim) {
        if (!available(attacker, victim)) {
            message(attacker, "Finisher unavailable (cooldown, active session or distance).");
            return;
        }
        if (!victim.canHarmPlayer(attacker)) {
            // Etapa 4.10.1: a finalizacao termina com dano real; sem PvP (servidor ou times), nem convida.
            message(attacker, "PvP is not allowed between you and " + name(victim) + " (server PvP setting or team rules).");
            return;
        }
        long now = victim.level().getGameTime();
        Invite old = PENDING.get(victim.getUUID());
        if (old != null && now < old.until()) {
            message(attacker, "That player already has an invite.");
            return;
        }
        PENDING.put(victim.getUUID(), new Invite(attacker.getUUID(), now + FinisherRules.INVITE_TICKS));
        message(attacker, "COMBAT finisher invite sent. Waiting for consent.");
        message(victim, attacker.getGameProfile().getName() + " requested a non-explicit test finisher. "
                + "/respectcraft test finisher accept or /respectcraft test finisher deny (15s). "
                + "To cancel during the action: /stopitstopnow or Shift.");
    }

    public static int accept(ServerPlayer victim) {
        Invite invite = PENDING.remove(victim.getUUID());
        if (invite == null || victim.level().getGameTime() >= invite.until()) {
            message(victim, "There is no valid invite.");
            return 0;
        }
        ServerPlayer attacker = victim.server.getPlayerList().getPlayer(invite.attacker());
        StartResult result = attacker == null ? StartResult.refused("The finisher can no longer start.")
                : start(attacker, victim, null, false);
        if (!result.started()) {
            message(victim, result.message());
            if (attacker != null) message(attacker, result.message());
            return 0;
        }
        return 1;
    }

    public static int deny(ServerPlayer victim) {
        Invite invite = PENDING.remove(victim.getUUID());
        if (invite == null) return 0;
        message(victim, "Invite declined.");
        ServerPlayer attacker = victim.server.getPlayerList().getPlayer(invite.attacker());
        if (attacker != null) message(attacker, "Invite declined.");
        return 1;
    }

    public static int mobOptIn(ServerPlayer player, boolean allowed) {
        player.getPersistentData().putBoolean(MOB_OPT_IN_KEY, allowed);
        message(player, allowed ? "Mob finisher TESTS allowed." : "Mob finisher TESTS disabled.");
        return 1;
    }

    /** API para IA futura: so executa um finisher de combate nao explicito em jogadores que optaram pelo teste. */
    public static boolean tryStartFromMob(Mob mob, ServerPlayer target) {
        return target.getPersistentData().getBoolean(MOB_OPT_IN_KEY) && start(mob, target, null, false).started();
    }

    /** Permite testar a futura API de mobs SEM adicionar IA a todos os mobs existentes. */
    public static int mobDemo(ServerPlayer target) {
        if (!target.getPersistentData().getBoolean(MOB_OPT_IN_KEY)) {
            message(target, "Enable it first: /respectcraft test finisher mobs on");
            return 0;
        }
        Mob mob = target.level().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(3.0D),
                m -> m.isAlive() && FinisherRules.isValidMobSize(m.getBbWidth(), m.getBbHeight()))
                .stream().min((a,b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target))).orElse(null);
        if (mob == null || !tryStartFromMob(mob, target)) {
            message(target, "No compatible mob nearby, or the target is not vulnerable/available.");
            return 0;
        }
        return 1;
    }

    private static boolean stageable(LivingEntity attacker, LivingEntity target) {
        if (!attacker.isAlive() || !target.isAlive() || attacker == target || attacker.level() != target.level()) return false;
        if (attacker.distanceToSqr(target) > 64.0D) return false;
        if (attacker.isPassenger() || target.isPassenger()) return false;
        return !SESSIONS.containsKey(attacker.getUUID()) && !SESSIONS.containsKey(target.getUUID())
                && !INCAPACITATED.containsKey(attacker.getUUID()) && !INCAPACITATED.containsKey(target.getUUID());
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

    /**
     * Ferramenta de administracao/teste ({@code /respectcraft admin finisher}): inicia a cena sem convite, sem exigir
     * vida baixa e sem cooldown. As demais travas (distancia, sessao ativa, montaria) continuam valendo, e tambem a
     * regra de chefes e o PvP (Etapa 4.10.1): o comando de admin nunca executa um chefe com finalizacao comum.
     */
    public static StartResult forceStart(LivingEntity attacker, LivingEntity target, FinisherDef finisher) {
        return start(attacker, target, finisher, true);
    }

    /** Nome do alvo para as mensagens. */
    private static String name(LivingEntity entity) {
        return entity.getDisplayName().getString();
    }

    /**
     * Elegibilidade no servidor, antes de qualquer mudanca no mundo (Etapa 4.10.1): finalizacao escolhida (ou sorteada
     * entre as que podem executar o alvo), regra de chefes e PvP. Null = pode seguir; senao, a recusa.
     */
    private static StartResult eligibility(LivingEntity attacker, LivingEntity target, FinisherDef finisher) {
        FinisherParticipants.Kind kind = FinisherParticipants.kindOf(target);
        if (finisher == null && kind != FinisherParticipants.Kind.BOSS) {
            return StartResult.refused("No finisher is available against " + name(target) + ".");
        }
        FinisherTargeting.Verdict verdict = finisher == null
                ? (kind == FinisherParticipants.Kind.BOSS ? FinisherTargeting.Verdict.BOSS_REQUIRES_EXCLUSIVE
                        : FinisherTargeting.Verdict.ELIGIBLE)
                : finisher.targeting().check(kind, FinisherParticipants.typeId(target));
        if (!verdict.eligible()) {
            RespectCraft.LOGGER.info("Finisher {} refused against {} ({}): {}", finisher == null ? "(none)" : finisher.id(),
                    FinisherParticipants.typeId(target), kind, verdict);
            return StartResult.refused(switch (verdict) {
                case BOSS_REQUIRES_EXCLUSIVE -> (finisher == null ? "Regular finishers" : "The " + finisher.id().getPath() + " finisher")
                        + " cannot be used against " + name(target) + ". Bosses can only be executed by their own exclusive finisher.";
                case EXCLUSIVE_OTHER_TARGET -> "The " + finisher.id().getPath() + " finisher is exclusive to another boss and cannot be used against "
                        + name(target) + ".";
                case ELIGIBLE -> throw new IllegalStateException();
            });
        }
        if (attacker instanceof ServerPlayer player && target instanceof ServerPlayer victim && !victim.canHarmPlayer(player)) {
            return StartResult.refused("PvP is not allowed between you and " + name(victim) + " (server PvP setting or team rules).");
        }
        return null;
    }

    private static StartResult start(LivingEntity attacker, LivingEntity target, FinisherDef chosen, boolean force) {
        // Primeiro: sem jogador nao ha sessao (ela so avanca no tick do jogador), seja qual for a distancia.
        Optional<FinisherParticipants.Interaction> interaction = FinisherParticipants.interaction(
                FinisherParticipants.kindOf(attacker), FinisherParticipants.kindOf(target));
        if (interaction.isEmpty()) {
            RespectCraft.LOGGER.debug("Finisher refused: {} vs {} has no player participant",
                    FinisherParticipants.typeId(attacker), FinisherParticipants.typeId(target));
            return StartResult.refused("Could not start: a finisher needs a player participant.");
        }
        // Etapa 4.10.1: a finalizacao e escolhida ANTES das travas, entre as que podem executar este alvo (chefe sem
        // exclusiva liberada = nenhuma), e a regra de alvos/PvP recusa antes de qualquer palco, animacao ou trava.
        FinisherDef finisher = chosen != null ? chosen : attacker instanceof ServerPlayer player
                ? Finishers.pickFor(FinisherParticipants.kindOf(target), FinisherParticipants.typeId(target),
                        skill -> ProfileService.get(player).hasSkill(skill.toString()),
                        com.respectcraft.profile.BirthRoulette.newRandom()).orElse(null)
                : Finishers.TAKEDOWN;
        StartResult refused = eligibility(attacker, target, finisher);
        if (refused != null) return refused;
        if (force ? !stageable(attacker, target) : !available(attacker, target)) {
            return StartResult.refused(force ? "Could not start (distance, active session or mount)."
                    : "Target is busy, protected or on cooldown.");
        }
        // Cada lado resolve o proprio clip no proprio rig; participante invalido recusa a cena antes de mexer em algo.
        FinisherParticipants.Resolution attackerRes = FinisherParticipants.resolve(finisher, FinisherRole.ATTACKER, attacker);
        FinisherParticipants.Resolution targetRes = FinisherParticipants.resolve(finisher, FinisherRole.VICTIM, target);
        if (!attackerRes.valid() || !targetRes.valid()) {
            RespectCraft.LOGGER.warn("Finisher {} refused: {}", finisher.id(),
                    attackerRes.valid() ? targetRes.problem() : attackerRes.problem());
            return StartResult.refused("Could not start: invalid rig for this finisher.");
        }
        FinisherParticipants.Clip attackerClip = attackerRes.clip().get();
        FinisherParticipants.Clip targetClip = targetRes.clip().get();
        // Palco: vitima de frente para o executor, a distancia do palco (1,15 entre jogadores; maior se as caixas de
        // colisao se sobrepuserem; ou a do alinhamento do clip/palco do tipo de entidade), se houver espaco.
        var alignment = FinisherParticipants.alignment(attackerClip, FinisherParticipants.typeId(attacker),
                targetClip, FinisherParticipants.typeId(target));
        float yaw = attacker.getYRot();
        Vec3 staged = FinisherParticipants.stage(attacker.position(), yaw,
                FinisherParticipants.stageDistance(alignment, attacker.getBbWidth(), target.getBbWidth()), alignment.lateral());
        Vec3 targetAnchor = target.level().noCollision(target, target.getBoundingBox().move(staged.subtract(target.position())))
                ? staged : target.position();
        float attackerYaw = Mth.wrapDegrees(yaw + FinisherParticipants.yawOffset(attackerClip));
        float targetYaw = Mth.wrapDegrees(yaw + 180F + FinisherParticipants.yawOffset(targetClip));
        place(target, targetAnchor, targetYaw);
        place(attacker, attacker.position(), attackerYaw);

        long now = attacker.level().getGameTime();
        Session session = new Session(attacker, target, finisher, interaction.get(), attackerClip, targetClip,
                targetAnchor, attackerYaw, targetYaw, now + finisher.durationTicks());
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
        // Rigs GeckoLib proprios: mesmo tick do servidor que os jogadores (o GeckoLib sincroniza com quem ve).
        triggerEntityClip(attacker, attackerClip);
        triggerEntityClip(target, targetClip);
        sendVisual(session, true);
        // Calamidade: efeito HERO da cena no ponto da cena (o mesmo dos parametros da animacao) e, contra chefe,
        // a atmosfera. Comeca no mesmo tick dos clips; golpes e quedas saem nos marcadores (onPlayerTick).
        boolean boss = FinisherParticipants.kindOf(attacker) == FinisherParticipants.Kind.BOSS
                || FinisherParticipants.kindOf(target) == FinisherParticipants.Kind.BOSS;
        session.fx = FinisherFx.start(attacker, target, finisher,
                new Vec3(middle.x, Math.min(attacker.getY(), targetAnchor.y), middle.z), yaw, boss);
        if (attacker instanceof ServerPlayer p) message(p, "Test finisher started. /stopitstopnow cancels.");
        if (target instanceof ServerPlayer p) message(p, "Test started. Press Shift or /stopitstopnow to cancel.");
        return StartResult.STARTED;
    }

    private static void triggerEntityClip(LivingEntity entity, FinisherParticipants.Clip clip) {
        if (clip instanceof FinisherParticipants.EntityClip e && entity instanceof GeoEntity geo) {
            geo.stopTriggeredAnim(e.clip().controller(), null);
            geo.triggerAnim(e.clip().controller(), e.clip().animation());
        }
    }

    /** Devolve o controlador da entidade ao estado normal dela (fim ou cancelamento; seguro se ja acabou). */
    private static void stopEntityClip(LivingEntity entity, FinisherParticipants.Clip clip) {
        if (clip instanceof FinisherParticipants.EntityClip e && entity instanceof GeoEntity geo && !entity.isRemoved()) {
            geo.stopTriggeredAnim(e.clip().controller(), e.clip().animation());
        }
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
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (event.getEvent().type().equals(Finishers.EVENT_FALL)) {
            onFall(player, event.getEvent().data());
            return;
        }
        if (!event.getEvent().type().equals(Finishers.EVENT_HIT)) return;
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.attacker != player) return;
        int strength = Mth.clamp(parseInt(event.getEvent().data(), 1), 1, 3);
        LivingEntity target = s.target;
        var level = player.serverLevel();
        double y = target.getY() + target.getBbHeight() * 0.6;
        // Com FinisherFxDef o Calamidade desenha o golpe (FinisherFx, no marcador deste tick); sem ele, vanilla.
        boolean vanilla = FinisherFxRules.vanillaParticles(s.finisher);
        if (vanilla) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), y, target.getZ(), 10 * strength, 0.25, 0.25, 0.25, 0.4);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), y, target.getZ(), 1, 0, 0, 0, 0);
        }
        if (vanilla && strength >= 3) {
            level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.3, target.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.1, target.getZ(), 20, 0.6, 0.05, 0.6, 0.05);
        }
        level.playSound(null, target.blockPosition(), strength >= 2 ? ModSounds.FINISHER_IMPACT_HEAVY.get()
                : ModSounds.FINISHER_IMPACT.get(), SoundSource.PLAYERS, 1F, 0.9F + level.random.nextFloat() * 0.2F);
        if (strength >= 3) level.playSound(null, target.blockPosition(), ModSounds.FINISHER_STING.get(), SoundSource.PLAYERS, 0.7F, 1F);
        // Lampejo vermelho e inclinacao de dano nos clientes, sem dano real: so o golpe final (FinisherExecution, no
        // fim da cena) fere a vitima.
        level.broadcastDamageEvent(target, player.damageSources().playerAttack(player));
    }

    /** Queda da vitima: poeira do bloco do chao e baque onde o corpo bate (recuo para tras da vitima). */
    private static void onFall(ServerPlayer player, String data) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.target != player) return;
        double[] fall = FinisherFxRules.parseFall(data);
        int strength = (int) fall[0];
        double back = fall[1];
        Vec3 behind = Vec3.directionFromRotation(0, player.getYRot()).scale(-back);
        double x = player.getX() + behind.x, y = player.getY(), z = player.getZ() + behind.z;
        var level = player.serverLevel();
        var ground = level.getBlockState(net.minecraft.core.BlockPos.containing(x, y - 0.2, z));
        // Com FinisherFxDef a onda de poeira e a luz no chao sao do Calamidade (FinisherFx); o baque fica aqui.
        if (FinisherFxRules.vanillaParticles(s.finisher)) {
            if (!ground.isAir()) {
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground),
                        x, y + 0.05, z, 14 * strength, 0.35, 0.02, 0.35, 0.15);
            }
            level.sendParticles(ParticleTypes.POOF, x, y + 0.1, z, 4 * strength, 0.3, 0.02, 0.3, 0.02);
        }
        level.playSound(null, x, y, z, strength >= 2 ? net.minecraft.sounds.SoundEvents.PLAYER_BIG_FALL
                : net.minecraft.sounds.SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, 0.9F, 0.9F);
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
        stopEntityClip(s.attacker, s.attackerClip);
        stopEntityClip(s.target, s.targetClip);
        sendVisual(s, false);
    }

    private static void abort(Session s, String reason) {
        if (SESSIONS.get(s.attacker.getUUID()) != s) return;
        release(s);
        FinisherFx.end(s.fx, FinisherFxRules.EndReason.ABORT);
        stopAnimations(s);
        // Etapa 4.10.1: cancelamento nunca executa (nenhum golpe final); fica no log para auditoria.
        RespectCraft.LOGGER.info("Finisher {} by {} on {}: CANCELLED ({})", s.finisher.id(),
                FinisherParticipants.typeId(s.attacker), FinisherParticipants.typeId(s.target), reason);
        if (s.attacker instanceof ServerPlayer p) message(p, "Finisher cancelled: " + reason);
        if (s.target instanceof ServerPlayer p) message(p, "Finisher cancelled: " + reason);
        NeoForge.EVENT_BUS.post(new FinisherSessionEvent.Aborted(s.attacker, s.target, s.finisher, s.interaction, reason));
    }

    private static void complete(Session s) {
        // Idempotente: a sessao sai do mapa antes do golpe final; uma segunda chamada (outro tick, outro jogador)
        // nao passa daqui, entao uma sessao nunca executa duas vezes.
        if (SESSIONS.get(s.attacker.getUUID()) != s) return;
        release(s);
        FinisherFx.end(s.fx, FinisherFxRules.EndReason.COMPLETE);
        long now = s.target.level().getGameTime();
        PAIR_COOLDOWNS.put(new Pair(s.attacker.getUUID(), s.target.getUUID()),
                now + FinisherRules.SAME_PAIR_COOLDOWN_TICKS);
        VICTIM_COOLDOWNS.put(s.target.getUUID(), now + FinisherRules.VICTIM_COOLDOWN_TICKS);
        // Contador so de jogador (FinisherRules.countsDefeat): mob e chefe vencidos nunca recebem contador.
        boolean counts = FinisherRules.countsDefeat(FinisherParticipants.kindOf(s.target));
        if (counts && s.target instanceof ServerPlayer player) {
            int updated = DefeatCounter.increment(player);
            message(player, "Special TEST defeat recorded (" + updated + ").");
        }
        // Etapa 4.10.1: golpe final real, com os participantes ja liberados (o dano nao cai mais na trava da sessao).
        String targetName = name(s.target);
        FinisherExecution.Result result = FinisherExecution.execute(s.attacker, s.target, s.finisher);
        RespectCraft.LOGGER.info("Finisher {} by {} on {} ({}): {} ({})", s.finisher.id(), FinisherParticipants.typeId(s.attacker),
                FinisherParticipants.typeId(s.target), targetName, result.outcome(), result.detail());
        if (!result.killConfirmed() && counts && s.target instanceof ServerPlayer player && player.isAlive()
                && !s.finisher.recoveryInScene()) {
            // Sobreviveu (totem, protecao): fica caida durante a recuperacao, como antes desta etapa.
            INCAPACITATED.put(player.getUUID(), new Incapacitated(player.position(), now + FinisherRules.INCAPACITATED_TICKS, player.level().dimension()));
            message(player, "Recovery: 10s. /stopitstopnow releases you.");
            PlayerAnimationService.play(player, Finishers.KNOCKED_DOWN, now, Optional.empty(), new CompoundTag(), true);
        }
        if (!result.killConfirmed() && s.target instanceof ServerPlayer player && player.isAlive()) {
            message(player, "You survived the execution (" + result.detail() + ").");
        }
        if (s.attacker instanceof ServerPlayer player) {
            message(player, result.killConfirmed() ? "Execution complete: " + targetName + " was killed."
                    : "Execution failed: " + targetName + " survived (" + result.detail() + ").");
        }
        NeoForge.EVENT_BUS.post(new FinisherSessionEvent.Completed(s.attacker, s.target, s.finisher, s.interaction, result));
    }

    public static int emergencyStop(ServerPlayer player) {
        boolean changed = false;
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) {
            abort(s, "player request");
            changed = true;
        }
        if (INCAPACITATED.remove(player.getUUID()) != null) {
            changed = true;
            stopIfOwn(player, Finishers.KNOCKED_DOWN);
        }
        if (PENDING.remove(player.getUUID()) != null) changed = true;
        if (PENDING.entrySet().removeIf(e -> e.getValue().attacker().equals(player.getUUID()))) changed = true;
        if (changed) message(player, "Interaction and/or recovery ended.");
        else message(player, "You are not in a finisher or recovery.");
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
                message(player, "Recovered.");
            } else {
                freezePlayer(player, incap.anchor());
            }
        }
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.lastProcessed == now) return;
        s.lastProcessed = now;
        String problem = FinisherRules.abortReason(present(s.attacker), present(s.target),
                s.attacker.level() == s.target.level(), s.attacker.distanceToSqr(s.target));
        if (problem != null) {
            abort(s, problem);
            return;
        }
        // A vitima pode cancelar por Shift ou comando; o executor tambem pode usar o comando.
        if (s.target instanceof ServerPlayer target && target.isShiftKeyDown()) {
            abort(s, "cancelled with Shift");
            return;
        }
        if (now >= s.finishAt) {
            complete(s);
            return;
        }
        FinisherFx.tick(s.fx, (int) (now - s.startedAt));
        hold(s.attacker, s.attackerAnchor, s.attackerYaw);
        hold(s.target, s.targetAnchor, s.targetYaw);
    }

    /** Vivo, nao removido (despawn/descarregado) e, se jogador, conectado. */
    private static boolean present(LivingEntity entity) {
        return entity.isAlive() && !entity.isRemoved()
                && !(entity instanceof ServerPlayer player && player.hasDisconnected());
    }

    /**
     * Mantem o participante no palco. Mob: so posicao, giro e navegacao, reaplicados a cada tick; nada e gravado na
     * entidade (sem NoAI), entao ao fim da cena — ou se o servidor cair no meio — ela volta ao normal sozinha.
     */
    private static void hold(LivingEntity entity, Vec3 anchor, float yaw) {
        if (entity instanceof ServerPlayer player) {
            freezePlayer(player, anchor);
            return;
        }
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity instanceof Mob mob) mob.getNavigation().stop();
        if (entity.distanceToSqr(anchor) > 0.01D) entity.setPos(anchor.x, anchor.y, anchor.z);
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
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
            // Uma tentativa de dano interrompe a cena, SEM aplicar o dano da tentativa. Excecao (Etapa 4.8): o golpe
            // automatico da IA do PROPRIO parceiro mob faz parte da cena; e anulado sem cancelar a finalizacao.
            event.setCanceled(true);
            LivingEntity partner = s.attacker == event.getEntity() ? s.target : s.attacker;
            if (partner instanceof ServerPlayer || event.getSource().getEntity() != partner) abort(s, "interrupted by damage");
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Session s = SESSIONS.get(event.getEntity().getUUID());
        if (s != null) abort(s, "death");
        INCAPACITATED.remove(event.getEntity().getUUID());
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) abort(s, "disconnect");
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
