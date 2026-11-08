package com.tupinicraft.skilltree;

import com.tupinicraft.TupiniCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Aplica/remove os efeitos (modificadores de atributo) de uma skill. Os modificadores são transitórios (não vão
 * para o arquivo do jogador): o {@link SkillTreeService} os recoloca no login e ao renascer, a partir das
 * skills compradas. ID de cada modificador: {@code tupinicraft:skill/<namespace>/<skill>/<índice>}.
 */
final class SkillEffects {
    private static final Map<ServerPlayer, EffectReconciler<SkillEffectDef>> APPLIED = new WeakHashMap<>();

    private SkillEffects() {
    }

    /** Mantém só os efeitos ativos do catálogo atual, inclusive após /reload e troca de atributo/valor. */
    static void reconcile(ServerPlayer player, Map<String, List<SkillEffectDef>> desired) {
        APPLIED.computeIfAbsent(player, ignored -> new EffectReconciler<>()).reconcile(desired,
                (id, effects) -> set(player, id, effects, false),
                (id, effects) -> set(player, id, effects, true));
        // Só limita a vida depois de instalar todos os novos bônus; evita perder vida numa atualização de bônus.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void set(ServerPlayer player, String skillId, List<SkillEffectDef> effects, boolean active) {
        for (int index = 0; index < effects.size(); index++) {
            SkillEffectDef effect = effects.get(index);
            Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(effect.attribute()));
            AttributeInstance instance = attribute.map(player::getAttribute).orElse(null);
            if (instance == null) {
                continue;
            }
            ResourceLocation id = modifierId(skillId, index);
            boolean present = instance.hasModifier(id);
            if (active && !present) {
                instance.addTransientModifier(new AttributeModifier(id, effect.amount(),
                        AttributeModifier.Operation.valueOf(effect.operation().toUpperCase(Locale.ROOT))));
            } else if (!active && present) {
                instance.removeModifier(id);
            }
        }
    }

    static ResourceLocation modifierId(String skillId, int index) {
        ResourceLocation skill = ResourceLocation.parse(skillId);
        return TupiniCraft.id("skill/" + skill.getNamespace() + "/" + skill.getPath() + "/" + index);
    }
}
