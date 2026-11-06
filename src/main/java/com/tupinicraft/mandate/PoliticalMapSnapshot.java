package com.tupinicraft.mandate;

import com.tupinicraft.territory.Territory;

import java.util.List;

/**
 * Snapshot somente leitura de territórios e cadeiras, pensado para o futuro mapa político.
 * É gerado sob demanda a partir do {@link MandateRegistry} (não é uma segunda cópia persistente).
 *
 * @param gameTime instante (ticks do mundo) em que o snapshot foi gerado
 */
public record PoliticalMapSnapshot(long gameTime, List<Territory> territories, List<SeatView> seats) {
    public PoliticalMapSnapshot {
        territories = List.copyOf(territories);
        seats = List.copyOf(seats);
    }
}
