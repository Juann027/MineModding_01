package com.respectcraft.needs;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;

/**
 * Definicao (so dados) do efeito de um consumivel nas necessidades. Um mesmo registro cobre efeito imediato,
 * gradual, atrasado, ritmos alterados, buffs/debuffs, particulas e sons; o {@link NeedEffectTimeline} (puro)
 * diz o que acontece a cada segundo e o {@link NeedsService} aplica no servidor.
 *
 * <p>Mecanicas ficticias de videogame (nao sao recomendacoes medicas).
 *
 * @param id              id estavel (persistido nos efeitos ativos e usado na traducao {@code effect.respectcraft.need.<id>})
 * @param immediate       variacao aplicada ao consumir
 * @param gradual         variacao total distribuida ao longo de {@code durationSeconds}
 * @param durationSeconds duracao do efeito gradual e dos ritmos alterados (0 = sem duracao)
 * @param rates           multiplicadores de ritmo por necessidade enquanto dura (1 = normal)
 * @param effects         buffs/debuffs vanilla aplicados ao consumir
 * @param delaySeconds    atraso do estagio atrasado (0 = sem estagio atrasado)
 * @param delayed         variacao aplicada quando o atraso termina
 * @param delayedEffects  buffs/debuffs aplicados quando o atraso termina (ex.: cansaco depois do energetico)
 * @param cooldownTicks   recarga do item depois de consumido (0 = sem recarga)
 * @param particle        particula ao consumir e no estagio atrasado (null = nenhuma)
 * @param sound           som ao consumir (null = so o som vanilla de comer/beber)
 * @param delayedSound    som do estagio atrasado (null = nenhum)
 */
public record NeedEffectDef(String id, NeedsDelta immediate, NeedsDelta gradual, int durationSeconds,
                            Map<Need, Float> rates, List<Spec> effects, int delaySeconds, NeedsDelta delayed,
                            List<Spec> delayedEffects, int cooldownTicks, ParticleOptions particle,
                            Holder<SoundEvent> sound, Holder<SoundEvent> delayedSound) {

    /** Um buff/debuff vanilla: efeito, duracao em ticks e nivel (0 = I). */
    public record Spec(Holder<MobEffect> effect, int ticks, int amplifier) {}

    public NeedEffectDef {
        rates = rates.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(rates));
        effects = List.copyOf(effects);
        delayedEffects = List.copyOf(delayedEffects);
    }

    /** Ritmo da necessidade enquanto o efeito dura. */
    public float rate(Need need) { return rates.getOrDefault(need, 1F); }

    /** Precisa ficar ativo depois de consumido (gradual, ritmo ou estagio atrasado)? */
    public boolean persistent() { return lifetimeSeconds() > 0; }

    /** Segundos ate o efeito acabar por completo. */
    public int lifetimeSeconds() {
        int timed = (!gradual.isZero() || !rates.isEmpty()) ? durationSeconds : 0;
        return Math.max(timed, delaySeconds);
    }

    public static Builder builder(String id) { return new Builder(id); }

    /** Monta uma definicao lendo como dados. */
    public static final class Builder {
        private final String id;
        private NeedsDelta immediate = NeedsDelta.NONE, gradual = NeedsDelta.NONE, delayed = NeedsDelta.NONE;
        private int duration, delay, cooldown;
        private final Map<Need, Float> rates = new EnumMap<>(Need.class);
        private final List<Spec> effects = new java.util.ArrayList<>(), delayedEffects = new java.util.ArrayList<>();
        private ParticleOptions particle;
        private Holder<SoundEvent> sound, delayedSound;

        private Builder(String id) { this.id = id; }

        public Builder now(Need need, int value) { immediate = immediate.with(need, immediate.get(need) + value); return this; }
        public Builder now(NeedsDelta delta) { immediate = immediate.plus(delta); return this; }
        public Builder over(int seconds) { duration = seconds; return this; }
        public Builder gradual(Need need, int total) { gradual = gradual.with(need, gradual.get(need) + total); return this; }
        public Builder rate(Need need, float multiplier) { rates.put(need, multiplier); return this; }
        public Builder effect(Holder<MobEffect> effect, int ticks, int amplifier) { effects.add(new Spec(effect, ticks, amplifier)); return this; }
        public Builder after(int seconds) { delay = seconds; return this; }
        public Builder later(Need need, int value) { delayed = delayed.with(need, delayed.get(need) + value); return this; }
        public Builder laterEffect(Holder<MobEffect> effect, int ticks, int amplifier) { delayedEffects.add(new Spec(effect, ticks, amplifier)); return this; }
        public Builder cooldown(int ticks) { cooldown = ticks; return this; }
        public Builder particle(ParticleOptions value) { particle = value; return this; }
        public Builder sound(Holder<SoundEvent> value) { sound = value; return this; }
        public Builder laterSound(Holder<SoundEvent> value) { delayedSound = value; return this; }

        public NeedEffectDef build() {
            return new NeedEffectDef(id, immediate, gradual, duration, rates, effects, delay, delayed, delayedEffects,
                    cooldown, particle, sound, delayedSound);
        }
    }
}
