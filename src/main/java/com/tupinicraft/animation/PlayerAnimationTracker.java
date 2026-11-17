package com.tupinicraft.animation;

/** Estado transitorio de um jogador no servidor (attachment sem persistencia). */
public final class PlayerAnimationTracker {
    PlayerAnimationState state;
    PlayerAnimationDef definition;
    TimedEventCursor cursor = new TimedEventCursor();
    int sequence;
    long lastRequestTime = -1000L;
}
