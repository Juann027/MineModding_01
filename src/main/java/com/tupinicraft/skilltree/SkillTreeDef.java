package com.tupinicraft.skilltree;

import com.tupinicraft.origin.Macroregion;
import com.tupinicraft.politics.PoliticalOffice;

import java.util.Optional;

/**
 * Uma árvore (aba da tela): tipo, requisito que vale para todas as skills dela e aparência.
 * Lida de {@code data/<ns>/tupinicraft_skilltree/arvores/<nome>.json}.
 *
 * @param id         ex.: {@code tupinicraft:regional_nordeste}
 * @param icon       ID do item mostrado na aba
 * @param background textura repetida no fundo (ex.: {@code minecraft:textures/block/sand.png})
 * @param order      ordem das abas (menor primeiro)
 */
public record SkillTreeDef(String id, SkillTreeKind kind, Optional<Macroregion> region, Optional<String> party,
                           Optional<PoliticalOffice> office, String name, String icon, String background, int order) {
    public SkillTreeDef {
        SkillDataParser.requireId(id, "árvore");
        SkillDataParser.requireId(icon, id + ": ícone");
        SkillDataParser.requireId(background, id + ": fundo");
        switch (kind) {
            case PERSONAL -> SkillDataParser.require(region.isEmpty() && party.isEmpty() && office.isEmpty(), id,
                    "árvore pessoal não exige região, partido nem cargo");
            case REGIONAL -> SkillDataParser.require(region.isPresent() && party.isEmpty() && office.isEmpty(), id,
                    "árvore regional precisa de \"regiao\"");
            case PARTY -> SkillDataParser.require(party.isPresent() && region.isEmpty() && office.isEmpty(), id,
                    "árvore partidária precisa de \"partido\"");
            case OFFICE -> SkillDataParser.require(office.isPresent() && office.get().isElective() && region.isEmpty()
                    && party.isEmpty(), id, "árvore de cargo precisa de \"cargo\" eletivo");
        }
    }
}
