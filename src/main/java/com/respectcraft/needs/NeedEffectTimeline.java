package com.respectcraft.needs;

import java.util.List;

/** Regras puras da linha do tempo de um efeito de consumivel (um passo por segundo, no servidor). */
public final class NeedEffectTimeline {
    private NeedEffectTimeline() {}

    /** Variacao gradual do segundo {@code elapsed} (0-based). A soma dos segundos fecha exatamente o total. */
    public static NeedsDelta gradualStep(NeedEffectDef def, int elapsed) {
        int d = def.durationSeconds();
        if (d <= 0 || elapsed < 0 || elapsed >= d || def.gradual().isZero()) return NeedsDelta.NONE;
        NeedsDelta total = def.gradual();
        NeedsDelta upTo = total.scaled(elapsed + 1, d);
        NeedsDelta before = total.scaled(elapsed, d);
        return upTo.plus(before.scaled(-1, 1));
    }

    /** O estagio atrasado dispara exatamente quando o segundo {@code elapsed} alcanca o atraso. */
    public static boolean delayedFires(NeedEffectDef def, int elapsed) {
        return def.delaySeconds() > 0 && elapsed + 1 == def.delaySeconds();
    }

    /** O ritmo alterado vale enquanto durar. */
    public static boolean rateActive(NeedEffectDef def, int elapsed) {
        return elapsed >= 0 && elapsed < def.durationSeconds();
    }

    public static boolean expired(NeedEffectDef def, int elapsed) {
        return elapsed >= def.lifetimeSeconds();
    }

    /** Multiplicador combinado dos efeitos ativos (produto, limitado a 0-5). */
    public static float combinedRate(List<NeedEffectDef> defs, List<Integer> elapsed, Need need) {
        float mul = 1F;
        for (int i = 0; i < defs.size(); i++) {
            if (rateActive(defs.get(i), elapsed.get(i))) mul *= defs.get(i).rate(need);
        }
        return Math.max(0F, Math.min(5F, mul));
    }
}
