package com.respectcraft.finisher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Politica permanente de selecao de finalizacoes por categoria de vitima (Etapa 4.13.3). Regra pura, separada dos
 * requisitos tecnicos de compatibilidade ({@link FinisherTargeting} = quem pode ser executado;
 * {@link FinisherEligibility} = rigs, clips e requisitos dos dois lados, passado aqui como {@code supported}).
 *
 * <ul>
 *   <li><b>PLAYER</b>: so finalizacoes comuns ({@link FinisherTargeting.Category#GENERIC}), com o desbloqueio de cada uma
 *       ({@code requiresSkill}). Consentimento/PvP/times continuam no {@code FinisherTestMechanic}. O futuro seletor
 *       (Random/Preset/Manual) escolhe dentro desta mesma lista.</li>
 *   <li><b>BOSS</b>: so a exclusiva daquele chefe (com o desbloqueio dela); nunca fallback para comuns.</li>
 *   <li><b>MOB / NPC</b>: o servidor sorteia uma cena completa (executor + vitima + camera + efeitos). Se o tipo tem
 *       pool proprio ({@link FinisherTargeting.Category#NPC_EXCLUSIVE} com este tipo), so ele, sem desbloqueio pessoal;
 *       senao, as comuns compativeis com mob (com o desbloqueio delas). Nada elegivel = vazio (recusa antes de mexer no
 *       mundo).</li>
 * </ul>
 */
public final class FinisherSelection {
    private FinisherSelection() {}

    /** Candidatas a este alvo, na ordem do catalogo. */
    public static List<FinisherDef> candidates(Collection<FinisherDef> catalog, FinisherParticipants.Kind victimKind,
                                               ResourceLocation victimType, Predicate<ResourceLocation> hasSkill,
                                               Predicate<FinisherDef> supported) {
        boolean ownPool = victimKind == FinisherParticipants.Kind.MOB && victimType != null
                && catalog.stream().anyMatch(def -> def.targeting().ownPoolOf(victimType));
        List<FinisherDef> out = new ArrayList<>();
        for (FinisherDef def : catalog) {
            if (!def.targeting().check(victimKind, victimType).eligible()) continue;
            boolean npc = def.targeting().category() == FinisherTargeting.Category.NPC_EXCLUSIVE;
            if (ownPool != npc) continue; // pool proprio substitui as comuns; sem pool, so comuns
            if (!npc && def.requiresSkill() != null && !hasSkill.test(def.requiresSkill())) continue;
            if (!supported.test(def)) continue;
            out.add(def);
        }
        return out;
    }

    /** Pode entrar no futuro loadout de PvP (Random/Preset/Manual)? So as comuns. */
    public static boolean pvpLoadoutAllowed(FinisherDef def) {
        return def.targeting().category() == FinisherTargeting.Category.GENERIC && def.victim() != null;
    }
}
