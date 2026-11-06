package com.tupinicraft.skilltree;

import com.tupinicraft.origin.Macroregion;
import com.tupinicraft.politics.PoliticalOffice;

import java.util.Map;
import java.util.Optional;

/**
 * Fotografia dos dados do jogador que importam para as skills (montada no servidor).
 *
 * @param activeOffice cargo do mandato vigente (vazio se não exerce mandato)
 * @param officePoints pontos disponíveis por cargo
 */
public record SkillPlayerContext(Optional<Macroregion> macroregion, Optional<String> party,
                                 Optional<PoliticalOffice> activeOffice, int politicalXp, long money,
                                 Map<PoliticalOffice, Integer> officePoints) {
    public int pointsOf(PoliticalOffice office) {
        return officePoints.getOrDefault(office, 0);
    }
}
