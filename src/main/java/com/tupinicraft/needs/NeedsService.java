package com.tupinicraft.needs;

import com.tupinicraft.network.GameplayNetworking;
import com.tupinicraft.profile.CharacterAttribute;
import com.tupinicraft.profile.CharacterProfile;
import com.tupinicraft.profile.ProfileService;
import com.tupinicraft.registry.ModAttachments;
import com.tupinicraft.registry.ModSounds;
import com.tupinicraft.skill.SkillService;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Necessidades no servidor: uma vez por segundo aplica {@link NeedsRules}, os efeitos e os "acidentes".
 * Unico ponto de escrita; sincroniza com o dono so quando a porcentagem exibida muda.
 */
public final class NeedsService {
    private NeedsService() {}

    public static Needs get(ServerPlayer player) { return player.getData(ModAttachments.NEEDS); }

    public static void set(ServerPlayer player, Needs needs) {
        Needs old = get(player);
        player.setData(ModAttachments.NEEDS, needs);
        if (!old.sameDisplay(needs)) sync(player);
    }

    public static void apply(ServerPlayer player, NeedsDelta delta) {
        set(player, get(player).plus(delta));
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new GameplayNetworking.NeedsSync(get(player)));
    }

    public static ActiveNeedEffects activeEffects(ServerPlayer player) { return player.getData(ModAttachments.NEED_EFFECTS); }

    private static void setEffects(ServerPlayer player, ActiveNeedEffects effects) {
        player.setData(ModAttachments.NEED_EFFECTS, effects);
        syncEffects(player);
    }

    public static void clearEffects(ServerPlayer player) { setEffects(player, ActiveNeedEffects.EMPTY); }

    public static void syncEffects(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new GameplayNetworking.NeedEffectsSync(activeEffects(player)));
    }

    /**
     * Consumivel terminado (servidor): efeito imediato, buffs, particulas e som agora; gradual, ritmos e o estagio
     * atrasado ficam registrados e andam um passo por segundo em {@link #tickEffects}.
     */
    public static void consume(ServerPlayer player, NeedEffectDef def) {
        if (!def.immediate().isZero()) apply(player, def.immediate());
        for (NeedEffectDef.Spec spec : def.effects()) player.addEffect(new MobEffectInstance(spec.effect(), spec.ticks(), spec.amplifier()));
        burst(player, def.particle(), 12);
        if (def.sound() != null) {
            player.serverLevel().playSound(null, player.blockPosition(), def.sound().value(), SoundSource.PLAYERS, 0.7F, 1F);
        }
        if (def.persistent()) setEffects(player, activeEffects(player).start(def.id()));
    }

    private static void burst(ServerPlayer player, net.minecraft.core.particles.ParticleOptions particle, int count) {
        if (particle == null) return;
        player.serverLevel().sendParticles(particle, player.getX(), player.getY() + 1.2, player.getZ(), count, 0.35, 0.4, 0.35, 0.02);
    }

    /** Um segundo dos efeitos ativos: aplica o gradual/atrasado e devolve os ritmos combinados por necessidade. */
    private static java.util.EnumMap<Need, Float> tickEffects(ServerPlayer player) {
        java.util.EnumMap<Need, Float> rates = new java.util.EnumMap<>(Need.class);
        ActiveNeedEffects active = activeEffects(player);
        if (active.isEmpty()) return rates;
        java.util.List<NeedEffectDef> defs = new java.util.ArrayList<>();
        java.util.List<Integer> elapsed = new java.util.ArrayList<>();
        java.util.List<ActiveNeedEffects.Entry> next = new java.util.ArrayList<>();
        NeedsDelta delta = NeedsDelta.NONE;
        boolean changed = false;
        for (ActiveNeedEffects.Entry e : active.entries()) {
            NeedEffectDef def = NeedEffects.get(e.id());
            if (def == null) { changed = true; continue; } // efeito removido do catalogo: descarta
            defs.add(def);
            elapsed.add(e.elapsed());
            delta = delta.plus(NeedEffectTimeline.gradualStep(def, e.elapsed()));
            if (NeedEffectTimeline.delayedFires(def, e.elapsed())) {
                delta = delta.plus(def.delayed());
                for (NeedEffectDef.Spec spec : def.delayedEffects()) {
                    player.addEffect(new MobEffectInstance(spec.effect(), spec.ticks(), spec.amplifier()));
                }
                burst(player, def.particle(), 20);
                if (def.delayedSound() != null) {
                    player.serverLevel().playSound(null, player.blockPosition(), def.delayedSound().value(), SoundSource.PLAYERS, 0.9F, 1F);
                }
                player.displayClientMessage(Component.translatable("msg.tupinicraft.needs.effect_late." + def.id()), true);
            }
            if (NeedEffectTimeline.expired(def, e.elapsed() + 1)) changed = true;
            else next.add(new ActiveNeedEffects.Entry(e.id(), e.elapsed() + 1));
        }
        for (Need need : Need.values()) rates.put(need, NeedEffectTimeline.combinedRate(defs, elapsed, need));
        if (!delta.isZero()) apply(player, delta);
        // Sem mudanca de membros, so atualiza o contador salvo (o cliente conta o tempo sozinho).
        if (changed) setEffects(player, new ActiveNeedEffects(next));
        else player.setData(ModAttachments.NEED_EFFECTS, new ActiveNeedEffects(next));
        return rates;
    }

    /** Alivio (penico, banheiro): zera bexiga e intestino e melhora o humor. */
    public static void relieve(ServerPlayer player) {
        Needs n = get(player);
        int moodGain = (n.bladder() + n.bowel()) / 8;
        set(player, new Needs(n.hydration(), n.energy(), 0, 0, n.mood() + moodGain, n.coom()));
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.NEEDS_RELIEF.get(), SoundSource.PLAYERS, 0.8F, 1F);
        player.displayClientMessage(Component.translatable("msg.tupinicraft.needs.relief"), true);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (!player.isAlive()) return;
        // Efeitos de consumiveis andam sempre (inclusive no criativo, para testes); o resto so em sobrevivencia.
        java.util.EnumMap<Need, Float> rates = tickEffects(player);
        if (player.isCreative() || player.isSpectator()) return;
        ProfileService.rewardLevels(player);
        CharacterProfile profile = ProfileService.get(player);
        var level = player.serverLevel();
        boolean hot = level.getBiome(player.blockPosition()).value().getBaseTemperature() > 1.0F
                || level.dimension() == net.minecraft.world.level.Level.NETHER;
        NeedsRules.Context context = new NeedsRules.Context(player.isSprinting(), player.isSleeping(), hot,
                level.isNight(), player.isSwimming() || player.isInWater(),
                profile.attribute(CharacterAttribute.VIGOR), profile.attribute(CharacterAttribute.CARISMA),
                rate(profile, rates, Need.HYDRATION), rate(profile, rates, Need.ENERGY),
                rate(profile, rates, Need.BLADDER), rate(profile, rates, Need.BOWEL),
                SkillService.moodBonus(profile), NeedsConfig.coomRate() * rate(profile, rates, Need.COOM));
        Needs before = get(player);
        Needs after = NeedsRules.tickSecond(before, context);
        if (NeedsRules.accident(after.bladder())) after = accident(player, after, Need.BLADDER);
        if (NeedsRules.accident(after.bowel())) after = accident(player, after, Need.BOWEL);
        set(player, after);
        warn(player, before, after);
        effects(player, after);
    }

    /** Ritmo final: habilidades x efeitos de consumiveis. */
    private static float rate(CharacterProfile profile, java.util.Map<Need, Float> effects, Need need) {
        return SkillService.needRate(profile, need.id()) * effects.getOrDefault(need, 1F);
    }

    private static Needs accident(ServerPlayer player, Needs n, Need need) {
        var level = player.serverLevel();
        if (need == Need.BLADDER) {
            level.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getY() + 0.6, player.getZ(), 40, 0.15, 0.2, 0.15, 0);
            level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.05, player.getZ(), 30, 0.5, 0, 0.5, 0.1);
        } else {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()),
                    player.getX(), player.getY() + 0.4, player.getZ(), 30, 0.2, 0.2, 0.2, 0.05);
        }
        level.playSound(null, player.blockPosition(), ModSounds.NEEDS_ACCIDENT.get(), SoundSource.PLAYERS, 1F, 1F);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false, true));
        player.displayClientMessage(Component.translatable("msg.tupinicraft.needs.accident." + need.id()), true);
        return n.with(need, 0).with(Need.MOOD, n.mood() - NeedsRules.ACCIDENT_MOOD_PENALTY);
    }

    /** Aviso na barra de acao quando um valor cruza o limite (uma vez por travessia). */
    private static void warn(ServerPlayer player, Needs before, Needs after) {
        for (Need need : Need.values()) {
            int b = before.get(need);
            int a = after.get(need);
            boolean crossed = need.highIsGood() ? (b >= NeedsRules.LOW && a < NeedsRules.LOW)
                    : (b <= NeedsRules.URGENT && a > NeedsRules.URGENT);
            if (crossed) player.displayClientMessage(Component.translatable("msg.tupinicraft.needs.warn." + need.id()), true);
        }
    }

    private static void effects(ServerPlayer player, Needs n) {
        if (n.hydration() < NeedsRules.LOW) effect(player, MobEffects.MOVEMENT_SLOWDOWN, 0);
        if (n.hydration() == 0 && player.tickCount % 100 == 0) player.hurt(player.damageSources().dryOut(), 1F);
        if (n.energy() < NeedsRules.LOW) {
            effect(player, MobEffects.DIG_SLOWDOWN, 0);
            if (n.energy() == 0) effect(player, MobEffects.MOVEMENT_SLOWDOWN, 1);
        }
        if (n.mood() < 2_000) effect(player, MobEffects.WEAKNESS, 0);
        if (n.mood() >= NeedsRules.HAPPY) effect(player, MobEffects.LUCK, 0);
        if (n.bladder() > 9_000 || n.bowel() > 9_000) effect(player, MobEffects.MOVEMENT_SLOWDOWN, 0);
        // Coom quase cheio: distraido (mineracao mais lenta). Recurso abstrato, sem acoes associadas nesta etapa.
        if (n.coom() > 9_000) effect(player, MobEffects.DIG_SLOWDOWN, 0);
    }

    private static void effect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> type, int amp) {
        // Curto e renovado a cada segundo: some sozinho quando a necessidade melhora.
        player.addEffect(new MobEffectInstance(type, 50, amp, true, false, true));
    }

    /** Comida e bebida vanilla tambem contam (os consumiveis do mod aplicam o proprio efeito). */
    public static void onItemFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItem();
        if (stack.getItem() instanceof ConsumableItem) return;
        if (stack.is(Items.POTION)) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            boolean water = contents != null && contents.is(Potions.WATER);
            apply(player, new NeedsDelta(water ? 3_000 : 1_500, 0, water ? 900 : 500, 0, 0));
        } else if (stack.is(Items.MILK_BUCKET)) {
            apply(player, new NeedsDelta(2_000, 0, 700, 300, 300));
        } else if (stack.is(Items.HONEY_BOTTLE)) {
            apply(player, new NeedsDelta(1_000, 600, 400, 0, 500));
        } else {
            FoodProperties food = stack.getFoodProperties(player);
            if (food != null) {
                boolean soup = stack.is(Items.MUSHROOM_STEW) || stack.is(Items.BEETROOT_SOUP)
                        || stack.is(Items.RABBIT_STEW) || stack.is(Items.SUSPICIOUS_STEW);
                apply(player, new NeedsDelta(soup ? 1_500 : (stack.is(Items.MELON_SLICE) ? 800 : 0), 0,
                        soup ? 400 : 0, food.nutrition() * 120, food.nutrition() * 40));
            }
        }
    }

    /** Dormir a noite inteira enche a energia. */
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.wakeImmediately() && player.isSleepingLongEnough()) {
            set(player, get(player).with(Need.ENERGY, Needs.MAX));
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
            syncEffects(player);
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
            syncEffects(player);
        }
    }
}
