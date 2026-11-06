package com.tupinicraft.mandate;

import java.util.Optional;
import java.util.UUID;

/**
 * Mandato registrado numa cadeira. Os tempos são ticks do relógio do mundo (tempo de jogo do Overworld),
 * que só avança com o servidor ligado.
 *
 * @param termStart  início do mandato
 * @param termEnd    fim previsto (fixado na posse; mudar a config não altera mandatos em curso)
 * @param party      partido político (futuro)
 * @param electionId eleição responsável pela posse (vazio para posse administrativa)
 */
public record Mandate(Occupant occupant, long termStart, long termEnd, Optional<String> party, Optional<UUID> electionId) {
    public boolean isActive(long now, boolean expirationEnabled) {
        return !expirationEnabled || now < termEnd;
    }

    public long remainingTicks(long now) {
        return Math.max(0, termEnd - now);
    }
}
