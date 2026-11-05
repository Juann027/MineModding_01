package com.tupinicraft.client;

import com.tupinicraft.election.ElectionPhase;
import com.tupinicraft.network.ElectionSyncPayload;
import net.minecraft.Util;

/** Cópia visual da eleição recebida do servidor. O relógio local serve apenas para exibição. */
public final class ClientElectionCache {
    private static ElectionSyncPayload state = ElectionSyncPayload.INACTIVE;
    private static long receivedAtMillis;
    private static int version;

    private ClientElectionCache() {
    }

    public static ElectionSyncPayload get() {
        return state;
    }

    public static ElectionPhase phase() {
        return state.electionPhase();
    }

    /** Incrementa a cada atualização; usado pela tela da urna para se reconstruir. */
    public static int version() {
        return version;
    }

    /** Estimativa de ticks restantes para exibição (o servidor reenvia o valor real periodicamente). */
    public static int displayTicksRemaining() {
        long elapsedTicks = (Util.getMillis() - receivedAtMillis) / 50L;
        return (int) Math.max(0, state.ticksRemaining() - elapsedTicks);
    }

    static void update(ElectionSyncPayload payload) {
        state = payload;
        receivedAtMillis = Util.getMillis();
        version++;
    }

    static void clear() {
        update(ElectionSyncPayload.INACTIVE);
    }
}
