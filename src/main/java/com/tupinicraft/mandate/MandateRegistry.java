package com.tupinicraft.mandate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Armazenamento autoritativo dos mandatos (uma entrada por cadeira com histórico mínimo).
 * Classe pura; a persistência fica em {@code MandateSavedData}.
 * <p>
 * Regra central: uma cadeira tem no máximo um ocupante. A posse só é aceita se a cadeira estiver vaga
 * (sem mandato ou com mandato expirado).
 */
public final class MandateRegistry {
    private final Map<SeatKey, Mandate> mandates = new LinkedHashMap<>();
    /** Último ocupante de cada cadeira (para a regra de reeleição consecutiva). */
    private final Map<SeatKey, UUID> lastOccupants = new LinkedHashMap<>();

    public Optional<Mandate> rawMandate(SeatKey key) {
        return Optional.ofNullable(mandates.get(key));
    }

    /** Mandato vigente: existe e não expirou. */
    public Optional<Mandate> activeMandate(SeatKey key, long now, boolean expirationEnabled) {
        Mandate mandate = mandates.get(key);
        return mandate != null && mandate.isActive(now, expirationEnabled) ? Optional.of(mandate) : Optional.empty();
    }

    public boolean isVacant(SeatKey key, long now, boolean expirationEnabled) {
        return activeMandate(key, now, expirationEnabled).isEmpty();
    }

    /**
     * Dá posse a um ocupante. Falha (retorna false) se a cadeira já tem mandato vigente,
     * impedindo dois ocupantes simultâneos.
     */
    public boolean inaugurate(SeatKey key, Mandate mandate, long now, boolean expirationEnabled) {
        if (!isVacant(key, now, expirationEnabled)) {
            return false;
        }
        Mandate previous = mandates.put(key, mandate);
        if (previous != null) {
            lastOccupants.put(key, previous.occupant().id());
        }
        return true;
    }

    /** Encerra o mandato da cadeira (fim do prazo ou ação administrativa). */
    public Optional<Mandate> vacate(SeatKey key) {
        Mandate removed = mandates.remove(key);
        if (removed != null) {
            lastOccupants.put(key, removed.occupant().id());
        }
        return Optional.ofNullable(removed);
    }

    /** Último ocupante: o atual, se houver mandato registrado, senão o anterior. */
    public Optional<UUID> lastOccupant(SeatKey key) {
        Mandate current = mandates.get(key);
        if (current != null) {
            return Optional.of(current.occupant().id());
        }
        return Optional.ofNullable(lastOccupants.get(key));
    }

    public Map<SeatKey, Mandate> mandates() {
        return Collections.unmodifiableMap(mandates);
    }

    public Map<SeatKey, UUID> lastOccupants() {
        return Collections.unmodifiableMap(lastOccupants);
    }

    /** Usado apenas ao carregar do disco. */
    public void restore(SeatKey key, Mandate mandate) {
        mandates.put(key, mandate);
    }

    /** Usado apenas ao carregar do disco. */
    public void restoreLastOccupant(SeatKey key, UUID occupant) {
        lastOccupants.put(key, occupant);
    }
}
