package com.respectcraft.finisher;

import com.respectcraft.RespectCraft;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Execucao real de uma finalizacao concluida (Etapa 4.10.1). So servidor; chamada UMA vez por sessao, por
 * {@code FinisherTestMechanic.complete}, depois de a cena chegar ao fim (nunca no inicio, nunca no cancelamento).
 *
 * <p>O golpe final e dano comum do Minecraft: {@link LivingEntity#hurt} com o tipo de dano
 * {@code respectcraft:finisher_execution} (dados em {@code data/respectcraft/damage_type/}) causado pelo executor.
 * Por isso o Minecraft processa tudo como numa morte normal: eventos de dano e morte, credito do matador
 * ({@code lastHurtByPlayer}), loot e XP de morte por jogador, estatisticas de abate, criterios de conquista, mensagem
 * de morte, totem da imortalidade, invulnerabilidade (criativo, {@code Invulnerable}), PvP desligado/times e mods de
 * protecao (que cancelam o dano). Nada de {@code discard()} nem vida forcada a zero. O tipo ignora armadura, escudo,
 * efeitos, encantamentos, recarga de dano e repulsao (tags de {@code data/minecraft/tags/damage_type/}), mas NAO a
 * invulnerabilidade nem o totem.
 *
 * <p>Sucesso so com a morte CONFIRMADA: o {@link LivingDeathEvent} desta execucao chegou ao fim sem ser cancelado
 * ({@link #onDeathConfirmed}, prioridade LOWEST). Dano recusado ou vitima que sobreviveu (totem, mod) = FAILED, sem
 * segunda tentativa.
 */
public final class FinisherExecution {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, RespectCraft.id("finisher_execution"));

    /** Resultado de uma sessao de finalizacao. */
    public enum Outcome {
        /** A cena terminou e a morte da vitima pela execucao foi confirmada pelo servidor. */
        SUCCESS,
        /** A cena foi interrompida antes do fim: nenhuma execucao foi tentada. */
        CANCELLED,
        /** A cena terminou, mas a execucao nao matou (dano recusado, vitima sobreviveu ou erro interno). */
        FAILED
    }

    /** Resultado da execucao, com o motivo legivel (ingles) quando nao houve morte. */
    public record Result(Outcome outcome, String detail) {
        public boolean killConfirmed() { return outcome == Outcome.SUCCESS; }
    }

    /** Execucoes em andamento (so durante a chamada sincrona de {@code hurt}): vitima -> morte confirmada. */
    private static final Map<UUID, boolean[]> IN_PROGRESS = new HashMap<>();

    private FinisherExecution() {}

    // ------------------------------------------------------------- regras puras (SelfTest)

    /**
     * Dano da execucao: bem acima da vida + absorcao (finito, para mods que multiplicam o dano). A morte continua
     * dependendo do Minecraft (totem, invulnerabilidade, eventos).
     */
    public static float damageFor(float health, float absorption, float maxHealth) {
        float base = Math.max(0F, health) + Math.max(0F, absorption) + Math.max(1F, maxHealth);
        return Math.min(base * 4F + 20F, 1.0E6F);
    }

    /** Resultado a partir do que o Minecraft fez: dano aceito e morte confirmada. */
    public static Result outcome(boolean damageApplied, boolean deathConfirmed) {
        if (deathConfirmed) return new Result(Outcome.SUCCESS, "killed");
        if (!damageApplied) return new Result(Outcome.FAILED, "damage was prevented");
        return new Result(Outcome.FAILED, "death was prevented");
    }

    // ------------------------------------------------------------- mundo (servidor)

    /**
     * Aplica o golpe final. Revalida tudo no servidor (participantes presentes, mesmo nivel, regra de alvos, PvP) e
     * so entao fere a vitima uma vez. Nunca lanca: erro interno vira FAILED com log.
     */
    public static Result execute(LivingEntity attacker, LivingEntity victim, FinisherDef finisher) {
        if (victim.level().isClientSide()) return new Result(Outcome.FAILED, "client side");
        if (!victim.isAlive() || victim.isRemoved()) return new Result(Outcome.FAILED, "target is no longer present");
        if (!attacker.isAlive() || attacker.isRemoved()
                || (attacker instanceof ServerPlayer player && player.hasDisconnected())) {
            return new Result(Outcome.FAILED, "executor is no longer present");
        }
        if (attacker.level() != victim.level()) return new Result(Outcome.FAILED, "participants are in different dimensions");
        FinisherParticipants.Kind kind = FinisherParticipants.kindOf(victim);
        FinisherTargeting.Verdict verdict = finisher.targeting().check(kind, FinisherParticipants.typeId(victim));
        if (!verdict.eligible()) return new Result(Outcome.FAILED, "finisher cannot execute this target (" + verdict + ")");
        if (attacker instanceof ServerPlayer player && victim instanceof ServerPlayer target && !target.canHarmPlayer(player)) {
            return new Result(Outcome.FAILED, "PvP is not allowed");
        }
        // Protecao do servidor (mods de area, claims): o golpe final e um ataque do jogador (AttackEntityEvent, sem o
        // gancho de item de CommonHooks.onPlayerAttackTarget: a mao do executor nao faz parte da execucao).
        if (attacker instanceof ServerPlayer player && NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, victim)).isCanceled()) {
            return new Result(Outcome.FAILED, "attack was blocked by server protection");
        }
        boolean bossCredit = kind == FinisherParticipants.Kind.BOSS && finisher.targeting().category() == FinisherTargeting.Category.BOSS_EXCLUSIVE
                && attacker instanceof ServerPlayer;
        boolean[] confirmed = new boolean[1];
        boolean applied = false;
        try {
            DamageSource source = new DamageSource(victim.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(DAMAGE_TYPE), attacker, attacker);
            if (bossCredit) BossDefeats.markExecution(victim, (ServerPlayer) attacker, finisher);
            IN_PROGRESS.put(victim.getUUID(), confirmed);
            applied = victim.hurt(source, damageFor(victim.getHealth(), victim.getAbsorptionAmount(), victim.getMaxHealth()));
        } catch (RuntimeException e) {
            RespectCraft.LOGGER.error("Finisher {} execution of {} failed", finisher.id(), FinisherParticipants.typeId(victim), e);
            if (!confirmed[0]) return new Result(Outcome.FAILED, "internal error");
        } finally {
            IN_PROGRESS.remove(victim.getUUID());
            // Credito de chefe so vale dentro desta chamada (sobreviveu = nada fica gravado para uma morte posterior).
            if (bossCredit) BossDefeats.clearExecution(victim);
        }
        return outcome(applied, confirmed[0]);
    }

    /** Registrado com prioridade LOWEST: morte cancelada por outro mod (ou totem, que nem posta o evento) nao conta. */
    public static void onDeathConfirmed(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide()) return;
        boolean[] confirmed = IN_PROGRESS.get(event.getEntity().getUUID());
        if (confirmed != null && event.getSource().is(DAMAGE_TYPE)) confirmed[0] = true;
    }

    /** A morte veio do golpe final de uma finalizacao (tipo de dano proprio). */
    public static boolean isExecution(DamageSource source) {
        return source.is(DAMAGE_TYPE);
    }
}
