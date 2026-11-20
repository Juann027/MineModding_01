package com.respectcraft.ui;

import java.util.Arrays;

/**
 * Dono de cada entrada do menu holografico no mundo (Etapa 4.8.4-B). Puro (sem classes do cliente) para o
 * {@code SelfTest} conferir as regras no servidor dedicado.
 *
 * <p><b>Regra.</b> O dono de um botao e decidido <b>no instante em que ele desce</b> e vale ate ele subir: um clique
 * que comecou no holograma nunca vira ataque/mineracao, mesmo se a mira sair da placa no meio do arrasto, e um clique
 * que comecou no mundo nunca vira clique de placa no meio. O holograma so ganha se a placa estiver dentro do alcance e
 * <b>mais perto</b> que o alvo do vanilla (bloco/mob na frente da placa continua sendo do mundo).
 *
 * <p>Fontes: botoes do mouse {@code 0..}{@link #MOUSE_BUTTONS}{@code -1} e as tres acoes do vanilla presas a teclas do
 * teclado ({@link #keySource}), para quem trocou ataque/uso/pegar bloco para o teclado.
 */
public final class HoloInputArbiter {
    public enum Owner { NONE, HOLO, WORLD }

    /** Botoes do mouse acompanhados (GLFW tem 8). */
    public static final int MOUSE_BUTTONS = 8;
    /** Acoes do vanilla (ataque, uso, pegar bloco) quando presas a uma tecla do teclado. */
    public static final int ATTACK = 0, USE = 1, PICK = 2;
    public static final int SOURCES = MOUSE_BUTTONS + 3;

    private final Owner[] owner = new Owner[SOURCES];

    public HoloInputArbiter() { clear(); }

    /** Fonte da acao {@code action} ({@link #ATTACK}, {@link #USE}, {@link #PICK}) presa ao teclado. */
    public static int keySource(int action) { return MOUSE_BUTTONS + action; }

    public static boolean validSource(int source) { return source >= 0 && source < SOURCES; }

    /**
     * O holograma ganha a entrada? {@code holoHit}: o raio acerta a face da frente de uma placa visivel a
     * {@code holoDistance}; {@code worldDistance}: distancia do alvo do vanilla ({@code +inf} se nao acertou nada).
     */
    public static boolean hologramWins(boolean holoHit, double holoDistance, double worldDistance, double reach) {
        return holoHit && holoDistance >= 0.0 && holoDistance <= reach && holoDistance < worldDistance;
    }

    /** Botao desceu: grava e devolve o dono ate ele subir. Um aperto novo sempre decide de novo. */
    public Owner press(int source, boolean hologramWins) {
        if (!validSource(source)) return Owner.WORLD;
        owner[source] = hologramWins ? Owner.HOLO : Owner.WORLD;
        return owner[source];
    }

    /** Botao subiu: devolve quem era o dono (o soltar vai para o mesmo dono) e libera a fonte. */
    public Owner release(int source) {
        if (!validSource(source)) return Owner.NONE;
        Owner o = owner[source];
        owner[source] = Owner.NONE;
        return o;
    }

    public Owner owner(int source) { return validSource(source) ? owner[source] : Owner.NONE; }

    /** Alguma fonte apertada pertence ao holograma (o mundo nao recebe ataque/uso enquanto isso). */
    public boolean anyHolo() {
        for (Owner o : owner) if (o == Owner.HOLO) return true;
        return false;
    }

    /** Esquece todos os donos (logout). O soltar que ainda vier passa direto para o vanilla, que nao tinha nada. */
    public void clear() { Arrays.fill(owner, Owner.NONE); }
}
