package com.respectcraft.fx;

import com.respectcraft.animation.PlayerAnimationDef;
import com.respectcraft.finisher.FinisherDef;
import com.respectcraft.finisher.FinisherRole;
import com.respectcraft.finisher.Finishers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Regras puras da ponte Finisher Engine → Calamidade_Awakening (Etapa 4.10). Sem Minecraft em execucao: entram no
 * {@code SelfTest}. Quem age sobre elas e {@link FinisherFx} (servidor) e {@code client.fx.FinisherFxClient} (passes).
 */
public final class FinisherFxRules {
    /** Camadas visuais de um golpe. */
    public enum HitLayer {
        /** Luz breve no ponto do golpe. */
        LIGHT,
        /** Anel de luz no chao em volta da vitima. */
        RING,
        /** Impact frame de duas cores na tela (so na forca 3). */
        IMPACT_FRAME
    }

    /** O que um marcador da cena dispara. */
    public enum CueKind { HIT, FALL }

    /**
     * Um momento da cena, tirado dos eventos das animacoes da finalizacao.
     *
     * @param tick     tick da cena (0 = inicio da sessao)
     * @param kind     golpe ({@code EVENT_HIT} do executor) ou queda ({@code EVENT_FALL} da vitima)
     * @param strength forca 1..3
     * @param back     queda: recuo em blocos para tras da vitima, onde o corpo bate (0 para golpes)
     * @param marker   nome do marcador na timeline da cena ({@code hit_0}, {@code fall_0}...)
     */
    public record Cue(int tick, CueKind kind, int strength, double back, String marker) {}

    /** Por que os efeitos de uma sessao terminam. */
    public enum EndReason { COMPLETE, ABORT }

    /** Como cada instancia ativa e encerrada. */
    public enum EndAction {
        /** {@code Handle.release()} (sustain toca a saida); sem sustain, cai para {@code stop()}. */
        RELEASE,
        /** {@code Handle.stop()} agora. */
        STOP
    }

    private FinisherFxRules() {}

    public static int clampStrength(int strength) { return Math.max(1, Math.min(3, strength)); }

    /** Camadas de um golpe: luz sempre; anel a partir da forca 2; impact frame so na forca 3. */
    public static Set<HitLayer> layersFor(int strength) {
        int s = clampStrength(strength);
        EnumSet<HitLayer> layers = EnumSet.of(HitLayer.LIGHT);
        if (s >= 2) layers.add(HitLayer.RING);
        if (s >= 3) layers.add(HitLayer.IMPACT_FRAME);
        return layers;
    }

    /** Fim normal solta (saida suave da atmosfera); cancelamento para tudo na hora. */
    public static EndAction endAction(EndReason reason) {
        return reason == EndReason.COMPLETE ? EndAction.RELEASE : EndAction.STOP;
    }

    /** Sem {@link FinisherFxDef}, a finalizacao mantem as particulas vanilla de antes; com ele, o Calamidade desenha. */
    public static boolean vanillaParticles(FinisherDef def) { return def.fx().isEmpty(); }

    /**
     * Papel do jogador que e a fonte dos efeitos ({@code EffectSource.player}, para usar as vagas reservadas aos
     * jogadores): o executor quando e jogador, senao a vitima. Sem jogador nao ha sessao (null).
     */
    public static FinisherRole sourceRole(boolean attackerIsPlayer, boolean targetIsPlayer) {
        if (attackerIsPlayer) return FinisherRole.ATTACKER;
        return targetIsPlayer ? FinisherRole.VICTIM : null;
    }

    /** {@code EVENT_FALL}: "forca,recuo" (forca 1..3; recuo 0..1,5 blocos). Entrada ruim vira forca 1, recuo 0. */
    public static double[] parseFall(String data) {
        String[] parts = data == null ? new String[0] : data.split(",");
        int strength = clampStrength(parseInt(parts.length > 0 ? parts[0] : "", 1));
        double back = parts.length > 1 ? Math.max(0, Math.min(1.5, parseDouble(parts[1], 0))) : 0;
        return new double[] {strength, back};
    }

    /**
     * Momentos da cena, na ordem: golpes do clip do executor e quedas do clip da vitima. Os dois clips comecam no
     * mesmo tick do servidor ({@code playTogether}), entao o tick do evento e o tick da cena.
     */
    public static List<Cue> cues(FinisherDef def) {
        List<Cue> out = new ArrayList<>();
        int hits = 0, falls = 0;
        for (PlayerAnimationDef.TimedEvent e : sorted(def.attacker().events())) {
            if (e.type().equals(Finishers.EVENT_HIT)) {
                out.add(new Cue(e.tick(), CueKind.HIT, clampStrength(parseInt(e.data(), 1)), 0, "hit_" + hits++));
            }
        }
        for (PlayerAnimationDef.TimedEvent e : sorted(def.victimSideEvents())) {
            if (e.type().equals(Finishers.EVENT_FALL)) {
                double[] fall = parseFall(e.data());
                out.add(new Cue(e.tick(), CueKind.FALL, (int) fall[0], fall[1], "fall_" + falls++));
            }
        }
        out.sort(Comparator.comparingInt(Cue::tick).thenComparing(Cue::kind));
        return List.copyOf(out);
    }

    /**
     * Momentos que valem nesta sessao. Golpe: sempre (a vitima reage no proprio clip, e com executor mob o golpe e o da
     * cena). Queda: so com vitima jogador — ela vem do clip de jogador da vitima; mob vitima fica parado e nao cai.
     */
    public static List<Cue> forSession(List<Cue> cues, boolean victimIsPlayer) {
        List<Cue> out = new ArrayList<>();
        for (Cue cue : cues) if (cue.kind() == CueKind.HIT || victimIsPlayer) out.add(cue);
        return List.copyOf(out);
    }

    /** Momentos com tick em (depois, ate]: cada um dispara uma vez, mesmo se um tick da sessao for pulado. */
    public static List<Cue> due(List<Cue> cues, int after, int upTo) {
        List<Cue> out = new ArrayList<>();
        for (Cue cue : cues) if (cue.tick() > after && cue.tick() <= upTo) out.add(cue);
        return out;
    }

    /** Forca do golpe mais forte da cena (1..3); 1 sem golpes. */
    public static int peakStrength(List<Cue> cues) {
        int peak = 1;
        for (Cue cue : cues) if (cue.kind() == CueKind.HIT) peak = Math.max(peak, cue.strength());
        return peak;
    }

    /**
     * Forca de um passe de tela proprio, ja com a acessibilidade do Calamidade: peso da fase × intensidade da
     * finalizacao × intensidade dos efeitos de tela escolhida pelo jogador; com "ocultar clarões", 35%.
     */
    public static float screenStrength(float weight, FinisherFxDef.Tier tier, float screenEffects, boolean flashAllowed) {
        float w = Math.max(0F, Math.min(1F, weight)) * tier.strength() * Math.max(0F, Math.min(1F, screenEffects));
        return flashAllowed ? w : w * 0.35F;
    }

    /** O impact frame so inverte as cores (pisca) quando clarões sao permitidos. */
    public static boolean impactFrameInverts(boolean flashAllowed, double ticksSinceHit) {
        return flashAllowed && ticksSinceHit >= 2;
    }

    private static List<PlayerAnimationDef.TimedEvent> sorted(List<PlayerAnimationDef.TimedEvent> events) {
        List<PlayerAnimationDef.TimedEvent> copy = new ArrayList<>(events);
        copy.sort(Comparator.comparingInt(PlayerAnimationDef.TimedEvent::tick));
        return copy;
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double parseDouble(String text, double fallback) {
        try {
            double v = Double.parseDouble(text.trim());
            return Double.isFinite(v) ? v : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
