package com.respectcraft.skill;

import com.respectcraft.RespectCraft;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Um no da arvore. So dados (sem classe por habilidade).
 *
 * @param id      id estavel, salvo na ficha do jogador
 * @param branch  aba
 * @param parents todas precisam estar compradas (vazio = raiz)
 * @param cost    pontos de habilidade
 * @param x       coluna na tela (unidades de grade)
 * @param y       linha na tela (unidades de grade; 0 = centro)
 * @param icon    textura 128x128 em {@code textures/item/skilltree/}
 * @param effects o que a habilidade faz
 */
public record SkillDef(ResourceLocation id, SkillBranch branch, List<ResourceLocation> parents, int cost,
                       int x, int y, ResourceLocation icon, List<SkillEffect> effects) {
    public SkillDef {
        parents = List.copyOf(parents);
        effects = List.copyOf(effects);
        if (cost <= 0) throw new IllegalArgumentException("Custo deve ser positivo: " + id);
    }

    public boolean root() { return parents.isEmpty(); }

    public String translationKey() { return "skill." + id.getNamespace() + "." + id.getPath().replace('/', '.'); }

    public static ResourceLocation iconTexture(String name) {
        return RespectCraft.id("textures/item/skilltree/" + name + ".png");
    }
}
