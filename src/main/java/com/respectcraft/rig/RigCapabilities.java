package com.respectcraft.rig;

import com.respectcraft.RespectCraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Capacidades de rig conhecidas hoje (Etapa 4.12). Capacidade e um {@link ResourceLocation} livre: familias e bindings
 * podem declarar outras sem codigo (ex.: {@code meu_addon:wings}); estas sao so as que o RespectCraft usa ou documenta.
 * Capacidade descreve o MODELO (o que da para animar/manipular), nunca a classificacao de gameplay (chefe ou nao).
 */
public final class RigCapabilities {
    /** Bracos, antebracos, coxas e canelas articulados (contrato humanoide). */
    public static final ResourceLocation HUMANOID_LIMBS = RespectCraft.id("humanoid_limbs");
    /** Maos com pivo de pulso e sockets de mao (agarrar, segurar arma). */
    public static final ResourceLocation HANDS = RespectCraft.id("hands");
    /** Bones de expressao no rosto (sobrancelhas, olhos, boca). */
    public static final ResourceLocation FACIAL_BONES = RespectCraft.id("facial_bones");
    /** Modelo tem partes destacaveis ({@code detachable}) com geometria propria. */
    public static final ResourceLocation DETACHABLE_PARTS = RespectCraft.id("detachable_parts");
    /** Modelo tem um componente interno revelavel (ex.: parte {@code core}). */
    public static final ResourceLocation INNER_CORE = RespectCraft.id("inner_core");

    private RigCapabilities() {}
}
