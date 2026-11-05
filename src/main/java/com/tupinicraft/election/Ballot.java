package com.tupinicraft.election;

import java.util.UUID;

/**
 * Voto registrado. O peso é calculado e congelado no momento do voto.
 * Nesta versão todo voto é favorável ao candidato escolhido.
 */
public record Ballot(UUID voter, UUID candidate, int weight) {
}
