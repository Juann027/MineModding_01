package com.tupinicraft.animation;

import java.util.function.Consumer;

/** Entrega cada evento temporizado uma vez por ciclo, mesmo se varios ticks passarem de uma vez. Puro. */
public final class TimedEventCursor {
    private long delivered = -1; // ultimo tick absoluto (desde o inicio) ja processado

    /** Quem entra no meio (cliente que comecou a ver o jogador depois) nao recebe os eventos ja passados. */
    public void skipTo(long elapsed) { delivered = Math.max(delivered, elapsed); }

    /** Processa os ticks em (ultimo, elapsed]. Em loop os eventos se repetem a cada ciclo. */
    public void advance(PlayerAnimationDef def, boolean loop, long elapsed, Consumer<PlayerAnimationDef.TimedEvent> sink) {
        if (def.events().isEmpty()) { delivered = elapsed; return; }
        long last = loop ? elapsed : Math.min(elapsed, def.durationTicks() - 1L);
        // Nunca varre mais que dois ciclos de uma vez (cliente que ficou parado, por exemplo).
        long from = Math.max(delivered + 1, last - 2L * def.durationTicks());
        for (long tick = from; tick <= last; tick++) {
            int local = (int) (loop ? tick % def.durationTicks() : tick);
            for (PlayerAnimationDef.TimedEvent event : def.events()) {
                if (event.tick() == local) sink.accept(event);
            }
        }
        delivered = Math.max(delivered, last);
    }
}
