package com.respectcraft.bounty;

import java.util.UUID;

/**
 * Regras puras da caca a recompensa (Etapa 4.11), cobertas pelo {@code SelfTest}. O servidor decide tudo; o
 * cliente so mostra o quadro e manda a intencao.
 *
 * <ul>
 *   <li>Abrir: alvo e outro jogador online, recompensa nao vazia (a pilha da mao principal), duracao de
 *   {@link #DURATION_DAYS}, no maximo {@link #MAX_ACTIVE_PER_POSTER} abertos por pagador e um por par pagador/alvo.</li>
 *   <li>Aceitar: nem o pagador nem o alvo; no maximo {@link #MAX_HUNTERS} cacadores por contrato e
 *   {@link #MAX_CONTRACTS_PER_HUNTER} contratos por cacador.</li>
 *   <li>Cobrar: so um cacador que aceitou, vencendo o alvo (morte ou finalizacao concluida) antes de expirar.</li>
 *   <li>Cancelar: so o pagador (ou um admin) e so enquanto ninguem aceitou; a recompensa volta.</li>
 *   <li>Expirar: a recompensa volta ao pagador (na hora, ou no proximo login).</li>
 * </ul>
 */
public final class BountyRules {
    private BountyRules() {}

    public static final int MAX_ACTIVE_PER_POSTER = 3;
    public static final int MAX_CONTRACTS_PER_HUNTER = 3;
    public static final int MAX_HUNTERS = 8;
    public static final int[] DURATION_DAYS = {1, 3, 7};
    /** Um dia de Minecraft. */
    public static final long TICKS_PER_DAY = 24_000L;
    /** Intervalo minimo entre pedidos de atualizacao do quadro por jogador (ticks). */
    public static final int REFRESH_COOLDOWN_TICKS = 40;

    public enum Result {
        OK, SELF, NO_REWARD, POSTER_LIMIT, DUPLICATE, TARGET_UNKNOWN, BAD_DURATION, NOT_FOUND, EXPIRED,
        OWN_BOUNTY, YOU_ARE_TARGET, ALREADY_HUNTING, NOT_HUNTING, HUNTER_LIMIT, FULL, HAS_HUNTERS, NOT_POSTER;

        public String translationKey() { return "bounty.respectcraft.result." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static boolean validDuration(int days) {
        for (int d : DURATION_DAYS) if (d == days) return true;
        return false;
    }

    public static Result canPlace(UUID poster, UUID target, boolean targetOnline, boolean rewardEmpty,
                                  int activeByPoster, boolean duplicate, int days) {
        if (target == null || !targetOnline) return Result.TARGET_UNKNOWN;
        if (poster.equals(target)) return Result.SELF;
        if (!validDuration(days)) return Result.BAD_DURATION;
        if (rewardEmpty) return Result.NO_REWARD;
        if (duplicate) return Result.DUPLICATE;
        if (activeByPoster >= MAX_ACTIVE_PER_POSTER) return Result.POSTER_LIMIT;
        return Result.OK;
    }

    public static Result canAccept(Bounty b, UUID hunter, int contractsOfHunter, long now) {
        if (b == null) return Result.NOT_FOUND;
        if (expired(b, now)) return Result.EXPIRED;
        if (b.poster().equals(hunter)) return Result.OWN_BOUNTY;
        if (b.target().equals(hunter)) return Result.YOU_ARE_TARGET;
        if (b.hunting(hunter)) return Result.ALREADY_HUNTING;
        if (b.hunters().size() >= MAX_HUNTERS) return Result.FULL;
        if (contractsOfHunter >= MAX_CONTRACTS_PER_HUNTER) return Result.HUNTER_LIMIT;
        return Result.OK;
    }

    public static Result canAbandon(Bounty b, UUID hunter) {
        if (b == null) return Result.NOT_FOUND;
        return b.hunting(hunter) ? Result.OK : Result.NOT_HUNTING;
    }

    public static Result canCancel(Bounty b, UUID who, boolean admin) {
        if (b == null) return Result.NOT_FOUND;
        if (!admin && !b.poster().equals(who)) return Result.NOT_POSTER;
        if (!admin && !b.hunters().isEmpty()) return Result.HAS_HUNTERS;
        return Result.OK;
    }

    /** O cacador {@code killer} cobra este contrato vencendo {@code victim} agora? */
    public static boolean claims(Bounty b, UUID victim, UUID killer, long now) {
        return b != null && killer != null && b.target().equals(victim) && !killer.equals(victim)
                && !b.poster().equals(killer) && b.hunting(killer) && !expired(b, now);
    }

    public static boolean expired(Bounty b, long now) { return now >= b.expires(); }

    public static long remaining(Bounty b, long now) { return Math.max(0L, b.expires() - now); }

    public static long expiresAt(long now, int days) { return now + days * TICKS_PER_DAY; }

    /** Proxima duracao do seletor (ciclo pelos valores validos). */
    public static int nextDuration(int days, int dir) {
        int idx = 0;
        for (int i = 0; i < DURATION_DAYS.length; i++) if (DURATION_DAYS[i] == days) idx = i;
        return DURATION_DAYS[Math.floorMod(idx + dir, DURATION_DAYS.length)];
    }
}
