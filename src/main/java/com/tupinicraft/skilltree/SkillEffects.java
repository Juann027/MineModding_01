package com.tupinicraft.skilltree;

import com.tupinicraft.TupiniCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Aplica/remove os efeitos (modificadores de atributo) de uma skill. Os modificadores são transitórios (não vão
 * para o arquivo do jogador): o {@link SkillTreeService} os recoloca no login e ao renascer, a partir das
 * skills compradas. ID de cada modificador: {@code tupinicraft:skill/<namespace>/<skill>/<índice>}.
 */
final class SkillEffects {
    private SkillEffects() {
    }

    /** Deixa os efeitos da skill no estado pedido; idempotente (só mexe no que estiver diferente). */
    static void set(ServerPlayer player, SkillDef skill, boolean active) {
        List<SkillEffectDef> effects = skill.effects();
        for (int index = 0; index < effects.size(); index++) {
            SkillEffectDef effect = effects.get(index);
            Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(effect.attribute()));
            AttributeInstance instance = attribute.map(player::getAttribute).orElse(null);
            if (instance == null) {
                continue;
            }
            ResourceLocation id = modifierId(skill.id(), index);
            boolean present = instance.hasModifier(id);
            if (active && !present) {
                instance.addTransientModifier(new AttributeModifier(id, effect.amount(),
                        AttributeModifier.Operation.valueOf(effect.operation().toUpperCase(Locale.ROOT))));
            } else if (!active && present) {
                instance.removeModifier(id);
                if (attribute.get().is(Attributes.MAX_HEALTH) && player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            }
        }
    }

    static ResourceLocation modifierId(String skillId, int index) {
        ResourceLocation skill = ResourceLocation.parse(skillId);
        return TupiniCraft.id("skill/" + skill.getNamespace() + "/" + skill.getPath() + "/" + index);
    }
}
