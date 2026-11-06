package com.tupinicraft.territory;

import com.tupinicraft.origin.RegionalOrigin;

import java.util.Optional;

/**
 * Território político (país, estado ou município). Dado estático de catálogo, não estado do mundo.
 *
 * @param id     identificador estável no formato {@code tupinicraft:<caminho>}, independente do nome exibido
 * @param name   nome exibido
 * @param parent id do território pai (estado do município, país do estado); vazio para o país
 * @param uf     UF associada (estados e municípios); vazio para o país
 */
public record Territory(String id, String name, TerritoryType type, Optional<String> parent, Optional<RegionalOrigin> uf) {
}
