package com.respectcraft.animation;

/** Estado transitorio de um jogador no servidor (attachment sem persistencia). */
public final class PlayerAnimationTracker {
    /** Estado como foi iniciado/enviado (numa sequencia, inclui os segmentos seguintes). */
    PlayerAnimationState state;
    /** Segmento vigente ({@code state.at(agora)}) e a definicao dele; trocados pelo servidor no tick da troca. */
    PlayerAnimationState segment;
    PlayerAnimationDef definition;
    TimedEventCursor cursor = new TimedEventCursor();
    int sequence;
    long lastRequestTime = -1000L;
}
