package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sons proprios. Hoje o {@code sounds.json} aponta para eventos vanilla; para trocar pelo audio definitivo basta
 * colocar os .ogg em {@code assets/tupinicraft/sounds/} e editar o {@code sounds.json} (sem mexer no codigo).
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, TupiniCraft.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_WHOOSH = register("finisher.whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_IMPACT = register("finisher.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_IMPACT_HEAVY = register("finisher.impact_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> FINISHER_STING = register("finisher.sting");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_UNLOCK = register("skill.unlock");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROULETTE_TICK = register("roulette.tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROULETTE_WIN = register("roulette.win");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEEDS_ACCIDENT = register("needs.accident");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEEDS_RELIEF = register("needs.relief");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CLICK = register("ui.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_HOVER = register("ui.hover");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(TupiniCraft.id(path)));
    }

    private ModSounds() {}
}
