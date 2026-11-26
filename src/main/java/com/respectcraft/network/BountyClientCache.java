package com.respectcraft.network;

import java.util.List;
import net.minecraft.Util;

/**
 * Copia do cliente do quadro de recompensas (Etapa 4.11), como o servidor a mandou para este jogador. So exibicao;
 * limpa no logout. {@link #receivedMillis} deixa a aba contar o tempo restante sem pedir de novo.
 */
public final class BountyClientCache {
    private BountyClientCache() {}

    private static List<HoloNetworking.BountyEntry> entries = List.of();
    private static long receivedMillis;
    private static boolean received;

    public static synchronized void receive(List<HoloNetworking.BountyEntry> list) {
        entries = List.copyOf(list);
        receivedMillis = Util.getMillis();
        received = true;
    }

    public static synchronized List<HoloNetworking.BountyEntry> entries() { return entries; }
    public static synchronized long receivedMillis() { return receivedMillis; }
    public static synchronized boolean received() { return received; }

    /** Quantos contratos estao abertos contra mim. */
    public static synchronized int onMe() {
        int n = 0;
        for (HoloNetworking.BountyEntry e : entries) if (e.onMe()) n++;
        return n;
    }

    /** Ticks restantes estimados agora (o relogio do servidor anda 20 ticks/s). */
    public static long remainingNow(HoloNetworking.BountyEntry e) {
        long elapsed = (Util.getMillis() - receivedMillis()) / 50L;
        return Math.max(0L, e.remainingTicks() - elapsed);
    }

    public static synchronized void clear() {
        entries = List.of();
        received = false;
        receivedMillis = 0L;
    }
}
