package com.tupinicraft.skilltree;

import com.tupinicraft.origin.Macroregion;
import com.tupinicraft.politics.PoliticalOffice;

import java.util.Optional;

/**
 * Custos e requisitos de uma skill (o tipo e o requisito vêm da árvore dela; ver {@link SkillDataParser}).
 *
 * @param skillId        ID da skill, ex.: {@code tupinicraft:pessoal_jeitinho}
 * @param region         macrorregião exigida (só REGIONAL)
 * @param party          partido exigido (só PARTY)
 * @param office         cargo exigido (só OFFICE)
 * @param moneyCost      custo em reais fictícios (PERSONAL, REGIONAL, PARTY)
 * @param officePointCost custo em pontos do cargo (só OFFICE)
 * @param minPoliticalXp XP político mínimo (verificado, nunca consumido)
 */
public record SkillRule(String skillId, SkillTreeKind kind, Optional<Macroregion> region, Optional<String> party,
                        Optional<PoliticalOffice> office, long moneyCost, int officePointCost, int minPoliticalXp) {
    public SkillRule {
        if (skillId == null || !skillId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("ID de skill inválido: " + skillId);
        }
        if (moneyCost < 0 || officePointCost < 0 || minPoliticalXp < 0) {
            throw new IllegalArgumentException(skillId + ": custos e requisitos não podem ser negativos");
        }
        switch (kind) {
            case PERSONAL -> require(region.isEmpty() && party.isEmpty() && office.isEmpty() && officePointCost == 0, skillId,
                    "skill pessoal não pode exigir região, partido, cargo ou pontos de cargo");
            case REGIONAL -> require(region.isPresent() && party.isEmpty() && office.isEmpty() && officePointCost == 0, skillId,
                    "skill regional precisa de \"regiao\" e não usa partido, cargo ou pontos de cargo");
            case PARTY -> require(party.isPresent() && region.isEmpty() && office.isEmpty() && officePointCost == 0, skillId,
                    "skill partidária precisa de \"partido\" e não usa região, cargo ou pontos de cargo");
            case OFFICE -> require(office.isPresent() && office.get().isElective() && region.isEmpty() && party.isEmpty()
                    && moneyCost == 0, skillId, "skill de cargo precisa de \"cargo\" eletivo e é paga só com pontos do cargo");
        }
    }

    private static void require(boolean condition, String skillId, String message) {
        if (!condition) {
            throw new IllegalArgumentException(skillId + ": " + message);
        }
    }
}
