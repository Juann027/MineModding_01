package com.tupinicraft.player;

import com.tupinicraft.politics.PoliticalOffice;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Regras puras de acúmulo de pontos de habilidade por cargo (sem Minecraft, testáveis). */
public final class SkillProgression {
    private SkillProgression() {
    }

    /** Resultado de um segundo de mandato: novo mapa de progresso e se um ponto foi ganho agora. */
    public record Tick(Map<PoliticalOffice, OfficeProgress> progress, boolean gainedPoint) {
    }

    /** Soma 1 segundo online exercendo {@code office}; a cada {@code secondsPerPoint} ganha 1 ponto naquele cargo. */
    public static Tick addOnlineSecond(Map<PoliticalOffice, OfficeProgress> progress, PoliticalOffice office, int secondsPerPoint) {
        Map<PoliticalOffice, OfficeProgress> next = copy(progress);
        OfficeProgress current = next.getOrDefault(office, OfficeProgress.EMPTY);
        int seconds = current.onlineSeconds() + 1;
        boolean gained = seconds >= Math.max(1, secondsPerPoint);
        next.put(office, gained
                ? new OfficeProgress(current.skillPoints() + 1, seconds - Math.max(1, secondsPerPoint))
                : new OfficeProgress(current.skillPoints(), seconds));
        return new Tick(Collections.unmodifiableMap(next), gained);
    }

    public static int skillPoints(Map<PoliticalOffice, OfficeProgress> progress, PoliticalOffice office) {
        return progress.getOrDefault(office, OfficeProgress.EMPTY).skillPoints();
    }

    public static Map<PoliticalOffice, OfficeProgress> copy(Map<PoliticalOffice, OfficeProgress> progress) {
        Map<PoliticalOffice, OfficeProgress> copy = new EnumMap<>(PoliticalOffice.class);
        copy.putAll(progress);
        return copy;
    }
}
