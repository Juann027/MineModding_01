package com.tupinicraft.needs;

import com.tupinicraft.registry.ModSounds;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;

/**
 * Catalogo (dados, em codigo) dos efeitos de consumiveis nas necessidades. Para um consumivel novo: registre uma
 * definicao aqui e crie o item com {@code new ConsumableItem(props, NeedEffects.X, tipo)} em {@code ModItems}.
 *
 * <p>Tudo e mecanica ficticia de jogo; nada aqui e recomendacao medica.
 */
public final class NeedEffects {
    private static final Map<String, NeedEffectDef> REGISTRY = new LinkedHashMap<>();
    private static final int S = 20; // ticks por segundo

    // Etapa 3: os seis consumiveis originais (so efeito imediato; valores preservados).
    public static final NeedEffectDef AGUA_DE_COCO = register(NeedEffectDef.builder("agua_de_coco")
            .now(new NeedsDelta(4_000, 0, 1_000, 0, 300)).build());
    public static final NeedEffectDef CAFEZINHO = register(NeedEffectDef.builder("cafezinho")
            .now(new NeedsDelta(500, 3_500, 1_200, 0, 500)).build());
    public static final NeedEffectDef GUARANA = register(NeedEffectDef.builder("guarana")
            .now(new NeedsDelta(2_500, 2_000, 1_000, 0, 600)).build());
    public static final NeedEffectDef PAO_DE_QUEIJO = register(NeedEffectDef.builder("pao_de_queijo")
            .now(new NeedsDelta(0, 300, 0, 800, 400)).build());
    public static final NeedEffectDef COXINHA = register(NeedEffectDef.builder("coxinha")
            .now(new NeedsDelta(0, 0, 0, 1_500, 600)).build());
    public static final NeedEffectDef BRIGADEIRO = register(NeedEffectDef.builder("brigadeiro")
            .now(new NeedsDelta(0, 500, 0, 300, 1_500)).build());

    // Etapa 3.1: consumiveis com efeitos graduais, atrasados, ritmos, buffs e debuffs.
    /** Nada de imediato; 30 s depois o intestino enche de uma vez. */
    public static final NeedEffectDef LAXANTE_INDUSTRIAL = register(NeedEffectDef.builder("laxante_industrial")
            .after(30).later(Need.BOWEL, 6_500).laterEffect(MobEffects.MOVEMENT_SLOWDOWN, 6 * S, 0)
            .rate(Need.BOWEL, 2.0F).over(90)
            .cooldown(60 * S).particle(ParticleTypes.EFFECT).sound(ModSounds.NEEDS_EFFECT).laterSound(ModSounds.NEEDS_EFFECT_LATE)
            .build());
    /** Alivia um pouco agora e quase segura o intestino por 5 min. */
    public static final NeedEffectDef ANTIDIARREICO = register(NeedEffectDef.builder("antidiarreico")
            .now(Need.BOWEL, -1_500).rate(Need.BOWEL, 0.2F).over(300)
            .cooldown(30 * S).particle(ParticleTypes.HAPPY_VILLAGER).sound(ModSounds.NEEDS_EFFECT).build());
    /** Energia e bexiga na hora, pressa e bexiga acelerada por 2 min. */
    public static final NeedEffectDef CAFE_EXTRAFORTE = register(NeedEffectDef.builder("cafe_extraforte")
            .now(Need.ENERGY, 3_000).now(Need.BLADDER, 1_500).now(Need.HYDRATION, -300)
            .effect(MobEffects.DIG_SPEED, 60 * S, 0).effect(MobEffects.MOVEMENT_SPEED, 30 * S, 0)
            .rate(Need.BLADDER, 1.5F).over(120)
            .cooldown(10 * S).particle(ParticleTypes.CLOUD).sound(ModSounds.NEEDS_EFFECT).build());
    /** Hidrata, mas a bexiga enche 2,5x mais rapido por 3 min. */
    public static final NeedEffectDef CHA_DIURETICO = register(NeedEffectDef.builder("cha_diuretico")
            .now(Need.HYDRATION, 1_500).rate(Need.BLADDER, 2.5F).gradual(Need.BLADDER, 1_000).over(180)
            .cooldown(10 * S).particle(ParticleTypes.DRIPPING_WATER).sound(ModSounds.NEEDS_EFFECT).build());
    /** Melhora o humor (na hora e aos poucos), baixa o Coom e segura o acumulo por 3 min. */
    public static final NeedEffectDef CHA_CALMANTE = register(NeedEffectDef.builder("cha_calmante")
            .now(Need.MOOD, 1_500).now(Need.COOM, -2_500).now(Need.HYDRATION, 800)
            .gradual(Need.MOOD, 1_000).rate(Need.COOM, 0.3F).over(180)
            .effect(MobEffects.REGENERATION, 5 * S, 0)
            .cooldown(10 * S).particle(ParticleTypes.HAPPY_VILLAGER).sound(ModSounds.NEEDS_EFFECT).build());
    /** O Coom acumula 3x mais rapido por 2 min. */
    public static final NeedEffectDef AFRODISIACO_MISTERIOSO = register(NeedEffectDef.builder("afrodisiaco_misterioso")
            .now(Need.MOOD, 500).gradual(Need.COOM, 1_500).rate(Need.COOM, 3.0F).over(120)
            .cooldown(60 * S).particle(ParticleTypes.HEART).sound(ModSounds.NEEDS_EFFECT).build());
    /** Buff ficticio de "desempenho": forca, salto e resistencia por 90 s; Coom um pouco mais rapido. */
    public static final NeedEffectDef PILULA_AZUL = register(NeedEffectDef.builder("pilula_azul")
            .effect(MobEffects.DAMAGE_BOOST, 90 * S, 0).effect(MobEffects.JUMP, 90 * S, 0)
            .effect(MobEffects.DAMAGE_RESISTANCE, 90 * S, 0)
            .rate(Need.COOM, 1.5F).over(90)
            .cooldown(120 * S).particle(ParticleTypes.ENCHANTED_HIT).sound(ModSounds.NEEDS_EFFECT).build());
    /** Energia e pressa agora; depois de 60 s, o tombo: energia e humor caem, lentidao e fadiga. */
    public static final NeedEffectDef ENERGETICO_DO_TIGRINHO = register(NeedEffectDef.builder("energetico_do_tigrinho")
            .now(Need.ENERGY, 4_000).now(Need.MOOD, 800).now(Need.BLADDER, 800)
            .effect(MobEffects.MOVEMENT_SPEED, 60 * S, 1).effect(MobEffects.DIG_SPEED, 60 * S, 1)
            .rate(Need.ENERGY, 0F).over(60)
            .after(60).later(Need.ENERGY, -3_500).later(Need.MOOD, -1_200)
            .laterEffect(MobEffects.MOVEMENT_SLOWDOWN, 30 * S, 0).laterEffect(MobEffects.DIG_SLOWDOWN, 30 * S, 0)
            .cooldown(30 * S).particle(ParticleTypes.FLAME).sound(ModSounds.NEEDS_EFFECT).laterSound(ModSounds.NEEDS_EFFECT_LATE)
            .build());

    private NeedEffects() {}

    private static NeedEffectDef register(NeedEffectDef def) {
        if (REGISTRY.putIfAbsent(def.id(), def) != null) throw new IllegalStateException("Efeito duplicado: " + def.id());
        return def;
    }

    public static NeedEffectDef get(String id) { return REGISTRY.get(id); }

    public static Collection<NeedEffectDef> all() { return Collections.unmodifiableCollection(REGISTRY.values()); }
}
