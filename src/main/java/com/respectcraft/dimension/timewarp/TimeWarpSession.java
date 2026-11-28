package com.respectcraft.dimension.timewarp;

/**
 * Estado transitorio de um jogador no Time Warp (Etapa 4.15), no attachment {@code respectcraft:time_warp_session}
 * (sem save). Carga do relogio, recuperacao sem relogio e dicas ja mostradas nesta conexao.
 */
public final class TimeWarpSession {
    /** Tick (relogio do Overworld) em que a carga do relogio comecou; -1 = sem carga. */
    long chargeStart = -1;
    /** Ultimo "uso" do relogio recebido. */
    long lastUse = -1;
    /** Ultima mensagem de recusa (evita spam na barra de acao). */
    long lastRefusal = -1;
    /** Ticks agachado e parado sem relogio (recuperacao). */
    int driftTicks;
    /** A dica de recuperacao ja apareceu nesta estadia. */
    boolean driftHinted;
    /** Ultimo estagio enviado ao cliente (-1 = nada enviado). */
    int sentStage = -1;
    boolean sentInWarp;
    /** Viagem em andamento (impede ativacao dupla no mesmo tick). */
    boolean travelling;

    void resetCharge() {
        chargeStart = -1;
        lastUse = -1;
    }
}
