package com.respectcraft.skill;

import net.minecraft.resources.ResourceLocation;

/**
 * Efeito de uma habilidade. So dados; quem aplica e o servidor ({@link SkillService}, {@code NeedsService}).
 *
 * @param kind   o que o efeito altera
 * @param target id do atributo vanilla ({@code minecraft:generic.attack_damage}), da necessidade ou da finalizacao
 * @param amount valor (soma para atributo/humor; multiplicador para necessidade)
 */
public record SkillEffect(Kind kind, String target, double amount) {
    public enum Kind {
        /** Soma ao atributo vanilla. */
        ATTRIBUTE_ADD,
        /** Multiplica a base do atributo vanilla ({@code +0.05} = +5%). */
        ATTRIBUTE_MULTIPLY,
        /** Multiplica o gasto/enchimento de uma necessidade ({@code 0.8} = 20% mais lento). */
        NEED_RATE,
        /** Soma ao alvo do humor (unidades de Needs). */
        MOOD_BONUS,
        /** Libera uma finalizacao (target = id da finalizacao). */
        FINISHER
    }

    public static SkillEffect add(ResourceLocation attribute, double amount) {
        return new SkillEffect(Kind.ATTRIBUTE_ADD, attribute.toString(), amount);
    }

    public static SkillEffect multiply(ResourceLocation attribute, double amount) {
        return new SkillEffect(Kind.ATTRIBUTE_MULTIPLY, attribute.toString(), amount);
    }

    public static SkillEffect needRate(String need, double multiplier) {
        return new SkillEffect(Kind.NEED_RATE, need, multiplier);
    }

    public static SkillEffect mood(int bonus) {
        return new SkillEffect(Kind.MOOD_BONUS, "humor", bonus);
    }

    public static SkillEffect finisher(ResourceLocation finisher) {
        return new SkillEffect(Kind.FINISHER, finisher.toString(), 1);
    }
}
