package com.respectcraft.fx;

import com.respectcraft.RespectCraft;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Efeitos visuais de uma finalizacao, no Calamidade_Awakening (Etapa 4.10). So dados: quem registra os efeitos e os
 * comeca e encerra e {@link FinisherFx}, o unico dono dos efeitos de finalizacao. Uma {@code FinisherDef} sem este
 * campo se comporta como antes (particulas e sons vanilla de {@code FinisherTestMechanic}).
 *
 * <p>O que cada parte descreve:
 * <ul>
 *   <li>{@code look}: o efeito HERO da cena inteira ({@code withPriority(HERO)}), com timeline do tamanho da cena,
 *   comecado no inicio da sessao no ponto da cena e orientado por {@code orientation};</li>
 *   <li>{@code tier}: a intensidade dos golpes e da queda. Cada {@code EVENT_HIT} de forca 1, 2 e 3 dispara o efeito
 *   {@link #hitEffect(int)} (luz breve; anel no chao a partir da forca 2; impact frame so na forca 3 —
 *   {@link FinisherFxRules#layersFor(int)}), e cada {@code EVENT_FALL} dispara {@link #fallEffect()} (onda de poeira e
 *   luz no chao onde o corpo bate). Os instantes vem dos marcadores da timeline da cena, que sao os ticks dos eventos
 *   das animacoes ({@link FinisherFxRules#cues});</li>
 *   <li>contra chefe (tag {@code c:bosses}) a cena ganha a atmosfera {@link #BOSS_ATMOSPHERE}.</li>
 * </ul>
 *
 * @param scene       id do efeito HERO da cena (namespace {@code respectcraft})
 * @param look        desenho da cena (shader e luzes proprios)
 * @param tier        intensidade dos golpes, da queda e da cena
 * @param orientation como a cena e virada
 */
public record FinisherFxDef(ResourceLocation scene, Look look, Tier tier, Orientation orientation) {
    /** Atmosfera das finalizacoes contra chefe (sustain: soltada no fim, parada no cancelamento). */
    public static final ResourceLocation BOSS_ATMOSPHERE = RespectCraft.id("finisher_fx/boss_atmosphere");

    /** Desenho da cena. Cada um tem o seu shader em {@code assets/respectcraft/shaders/fx}. */
    public enum Look {
        /** Linhas de foco de anime convergindo na vitima e luz quente: discreto. */
        TAKEDOWN_FOCUS("finisher_takedown"),
        /** Arco luminoso rente ao chao acompanhando a rasteira: medio. */
        RASTEIRA_SWEEP("finisher_rasteira"),
        /** Cupula de pressao em volta dos dois que pulsa a cada golpe e a cada queda: o mais intenso. */
        COMBO_PRESSURE("finisher_combo");

        private final String shader;

        Look(String shader) { this.shader = shader; }

        /** Programa de pos-processamento (assets/respectcraft/shaders/fx/&lt;shader&gt;.fsh). */
        public String shader() { return RespectCraft.MOD_ID + ":" + shader; }
    }

    /** Intensidade (ordem crescente). Escala a cena, os golpes e a queda. */
    public enum Tier {
        SUBTLE(0.55F), MEDIUM(0.8F), INTENSE(1.0F);

        private final float strength;

        Tier(float strength) { this.strength = strength; }

        /** Multiplicador de brilho e de forca dos passes, (0, 1]. */
        public float strength() { return strength; }

        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** Como a cena e virada: "frente" = do executor para a vitima. */
    public enum Orientation {
        /** {@code Placement.arrivingFrom(executor)}: a direcao real do golpe (inclui o deslocamento lateral). */
        ARRIVING_FROM_ATTACKER,
        /** {@code Placement.withYaw(yaw da ancora da cena)}: o yaw com que a sessao montou o palco. */
        ANCHOR_YAW
    }

    public FinisherFxDef {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(look, "look");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(orientation, "orientation");
        if (!RespectCraft.MOD_ID.equals(scene.getNamespace())) {
            throw new IllegalArgumentException("Efeito de cena fora do namespace do mod: " + scene);
        }
    }

    /** Efeito disparado por um {@code EVENT_HIT} desta forca (forca fora de 1..3 e limitada). */
    public ResourceLocation hitEffect(int strength) { return hitEffect(tier, strength); }

    /** Efeito disparado por um {@code EVENT_FALL}. */
    public ResourceLocation fallEffect() { return fallEffect(tier); }

    /** Id do efeito de golpe de uma intensidade e forca (registrado por {@link FinisherFx}). */
    public static ResourceLocation hitEffect(Tier tier, int strength) {
        return RespectCraft.id("finisher_fx/hit_" + tier.key() + "_" + FinisherFxRules.clampStrength(strength));
    }

    /** Id do efeito de queda de uma intensidade. */
    public static ResourceLocation fallEffect(Tier tier) {
        return RespectCraft.id("finisher_fx/fall_" + tier.key());
    }
}
