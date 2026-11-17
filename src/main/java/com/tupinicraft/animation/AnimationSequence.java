package com.tupinicraft.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Sequencia de clips tocada sem voltar a pose vanilla entre eles (ex.: queda -&gt; quatro apoios -&gt; levantar).
 * So dados; quem toca e {@link PlayerAnimationService#playSequence}. Exemplo:
 * <pre>{@code AnimationSequence.of(KNOCKDOWN).thenFor(HANDS_AND_KNEES, 40).then(GET_UP)}</pre>
 *
 * <p>Clips em loop precisam de duracao explicita ({@link #thenFor}); para sair antes, o servidor chama
 * {@link PlayerAnimationService#advance}. Nenhum segmento fica sem fim.
 */
public record AnimationSequence(List<Entry> entries) {
    /** Limite de um segmento (5 min), para nenhum estado ficar preso. */
    public static final int MAX_SEGMENT_TICKS = 20 * 60 * 5;

    /** @param ticks quanto tempo fica no clip; 0 = o comprimento do clip (so clips sem loop) */
    public record Entry(PlayerAnimationDef def, int ticks) {}

    public AnimationSequence {
        entries = List.copyOf(entries);
    }

    public static AnimationSequence of(PlayerAnimationDef first) {
        return new AnimationSequence(List.of(new Entry(first, 0)));
    }

    public static AnimationSequence of(PlayerAnimationDef first, int ticks) {
        return new AnimationSequence(List.of(new Entry(first, ticks)));
    }

    /** Proximo clip pelo comprimento dele. */
    public AnimationSequence then(PlayerAnimationDef def) {
        return thenFor(def, 0);
    }

    /** Proximo clip por {@code ticks} (obrigatorio para loop; num clip sem loop corta antes ou segura o ultimo quadro). */
    public AnimationSequence thenFor(PlayerAnimationDef def, int ticks) {
        List<Entry> list = new ArrayList<>(entries);
        list.add(new Entry(def, ticks));
        return new AnimationSequence(list);
    }

    /** Motivo de a sequencia ser invalida, ou vazio se pode tocar. */
    public Optional<String> problem() {
        if (entries.isEmpty()) return Optional.of("sequência vazia");
        if (entries.size() > PlayerAnimationState.MAX_STEPS + 1) {
            return Optional.of("sequência longa demais (máximo " + (PlayerAnimationState.MAX_STEPS + 1) + " clips)");
        }
        for (Entry entry : entries) {
            if (entry.def() == null) return Optional.of("animação desconhecida");
            if (entry.ticks() < 0 || entry.ticks() > MAX_SEGMENT_TICKS) {
                return Optional.of(entry.def().id() + ": duração fora de 0.." + MAX_SEGMENT_TICKS + " ticks");
            }
            if (entry.def().loop() && entry.ticks() == 0) {
                return Optional.of(entry.def().id() + " é um loop: informe quantos ticks fica nele");
            }
        }
        return Optional.empty();
    }

    /** Duracao total em ticks (valida). */
    public int totalTicks() {
        int total = 0;
        for (Entry entry : entries) total += entry.ticks() > 0 ? entry.ticks() : entry.def().durationTicks();
        return total;
    }

    /** Estado do primeiro segmento com os demais encadeados (o servidor completa sequencia/parceiro/params). */
    public PlayerAnimationState toState(long start, int sequence, Optional<UUID> partner, CompoundTag params) {
        List<PlayerAnimationState.Step> steps = new ArrayList<>();
        for (Entry entry : entries.subList(1, entries.size())) {
            steps.add(new PlayerAnimationState.Step(entry.def().id(), entry.def().durationTicks(), entry.def().loop(),
                    entry.ticks()));
        }
        Entry first = entries.get(0);
        return new PlayerAnimationState(first.def().id(), start, first.def().durationTicks(), first.def().loop(),
                sequence, partner, params.copy(), first.ticks(), 0, steps);
    }
}
