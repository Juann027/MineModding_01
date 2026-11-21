package com.respectcraft.skill;

import net.minecraft.resources.ResourceLocation;

/**
 * Efeito de uma habilidade. So dados; quem aplica e o servidor ({@link SkillService}, {@code NeedsService}).
 *
 * @param kind   o que o efeito altera
 * @param target id do atributo vanilla ({@code minecraft:generic.attack_damage}), da necessidade, da finalizacao ou
 *               do efeito do Calamidade_Awakening
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
        FINISHER,
        /**
         * Etapa 4.10, so infraestrutura: efeito visual do Calamidade_Awakening ligado a habilidade (target = id do
         * efeito registrado com {@code Effects.register}). Nenhuma habilidade usa ainda e {@code SkillService} nao o
         * aplica (sem atributo, necessidade ou humor); quem o comecar no futuro passa por {@code fx}.
         */
        VISUAL_FX
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

    /** Referencia a um efeito do Calamidade (Etapa 4.10; nao aplicado em nenhuma habilidade ainda). */
    public static SkillEffect visualFx(ResourceLocation effect) {
        return new SkillEffect(Kind.VISUAL_FX, effect.toString(), 0);
    }

    /** Id do efeito do Calamidade, quando este e um {@link Kind#VISUAL_FX} valido. */
    public java.util.Optional<ResourceLocation> visualFxId() {
        return kind == Kind.VISUAL_FX ? java.util.Optional.ofNullable(ResourceLocation.tryParse(target))
                : java.util.Optional.empty();
    }
}
