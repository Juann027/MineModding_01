package com.tupinicraft.skilltree;

import java.util.Optional;

/**
 * Regras puras de compra e de efeito ativo das skills (sem Minecraft, testáveis).
 * <ul>
 *   <li>Compra: requisitos da categoria + XP mínimo + saldo/pontos suficientes. XP nunca é consumido.</li>
 *   <li>Efeito ativo: pessoal sempre; regional se a macrorregião bate; partidária se filiado ao partido;
 *       de cargo só com o mandato daquele cargo vigente.</li>
 * </ul>
 */
public final class SkillGate {
    public enum Problem {
        WRONG_REGION,
        NOT_IN_PARTY,
        INSUFFICIENT_XP,
        INSUFFICIENT_MONEY,
        INSUFFICIENT_OFFICE_POINTS
    }

    private SkillGate() {
    }

    public static Optional<Problem> purchaseProblem(SkillRule rule, SkillPlayerContext player) {
        switch (rule.kind()) {
            case REGIONAL -> {
                if (!player.macroregion().equals(rule.region())) {
                    return Optional.of(Problem.WRONG_REGION);
                }
            }
            case PARTY -> {
                if (!player.party().equals(rule.party())) {
                    return Optional.of(Problem.NOT_IN_PARTY);
                }
            }
            default -> {
            }
        }
        if (player.politicalXp() < rule.minPoliticalXp()) {
            return Optional.of(Problem.INSUFFICIENT_XP);
        }
        if (rule.kind() == SkillTreeKind.OFFICE) {
            if (player.pointsOf(rule.office().orElseThrow()) < rule.officePointCost()) {
                return Optional.of(Problem.INSUFFICIENT_OFFICE_POINTS);
            }
        } else if (player.money() < rule.moneyCost()) {
            return Optional.of(Problem.INSUFFICIENT_MONEY);
        }
        return Optional.empty();
    }

    /**
     * A aba da árvore aparece para o jogador? Pessoais e de cargo: sempre (para ele ver o que pode conquistar).
     * Regional: só a da sua macrorregião. Partidária: só a do seu partido. Em todos os casos, também aparece se ele
     * já comprou algo nela (para ver as skills suspensas).
     */
    public static boolean treeVisible(SkillTreeDef tree, SkillPlayerContext player, boolean hasPurchaseInTree) {
        return hasPurchaseInTree || switch (tree.kind()) {
            case PERSONAL, OFFICE -> true;
            case REGIONAL -> player.macroregion().equals(tree.region());
            case PARTY -> player.party().equals(tree.party());
        };
    }

    /** O efeito de uma skill já desbloqueada pode ficar ativo agora? */
    public static boolean effectAllowed(SkillRule rule, SkillPlayerContext player) {
        return switch (rule.kind()) {
            case PERSONAL -> true;
            case REGIONAL -> player.macroregion().equals(rule.region());
            case PARTY -> player.party().equals(rule.party());
            case OFFICE -> player.activeOffice().equals(rule.office());
        };
    }
}
