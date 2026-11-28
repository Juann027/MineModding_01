package com.respectcraft.quest;

/**
 * Acontecimentos que movem missoes (Etapa 4.14.2). O catalogo indexa condicoes e objetivos por gatilho: um evento so
 * olha as definicoes que dependem dele, nunca todas.
 */
public enum QuestTrigger {
    /** Conquista obtida agora ({@code AdvancementEvent.AdvancementEarnEvent}); vanilla, do mod ou de outro mod. */
    ADVANCEMENT,
    /** Entidade morta pelo jogador (morte confirmada; inclui a execucao real de uma finalizacao). */
    KILL,
    /** Finalizacao concluida com {@code SUCCESS} (morte confirmada pelo golpe final). Nunca CANCELLED/FAILED. */
    FINISHER,
    /** Chefe derrotado ({@code BossDefeatEvent}) com credito para o jogador. */
    BOSS_DEFEAT,
    /** Chegada a uma dimensao. */
    DIMENSION,
    /** Outra missao mudou de estado (concluida, resgatada, falhou): cadeias de missoes. */
    QUEST
}
