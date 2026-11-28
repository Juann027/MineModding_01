package com.respectcraft.dimension.timewarp;

import com.respectcraft.RespectCraft;
import com.respectcraft.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Viagem pelo relogio (Etapa 4.15.3), so no servidor. Segurar o botao de usar com um relogio na mao principal
 * durante {@code chargeSeconds}: o cliente repete o "usar" a cada ~4 ticks enquanto o botao fica apertado, e cada
 * repeticao chega aqui como {@code RightClickItem}; um intervalo maior que {@link TimeWarpRules#CHARGE_GAP_TICKS}
 * recomeca a carga. O relogio vanilla nao tem uso proprio, entao nada do vanilla e cancelado. Overworld -> Time Warp
 * e Time Warp -> Overworld; nas outras dimensoes o relogio nao faz nada.
 *
 * <p>Sem duplicacao: nada e criado nem consumido (o relogio fica com o jogador); um barco montado fica para tras
 * ({@code stopRiding}). Ativacao dupla: a carga zera antes do teleporte, {@code travelling} recusa a mesma viagem
 * duas vezes e o cooldown (relogio do mundo, gravado) recusa a proxima.
 *
 * <p><b>Sem relogio:</b> {@link #tickDrift} — parado e olhando reto para o ceu por 15 s no Time Warp (em terra, boiando
 * ou num barco; com respiracao aquatica enquanto boia), o jogador volta ao ponto onde
 * entrou (sem a escala 3,33), entao perder o relogio nunca prende ninguem e nunca vira viagem rapida de graca.
 */
public final class TimeWarpTravel {
    private TimeWarpTravel() {}

    static TimeWarpSession session(ServerPlayer p) { return p.getData(ModAttachments.TIME_WARP_SESSION); }

    static long now(ServerPlayer p) { return p.server.overworld().getGameTime(); }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.CLOCK) || player.isSpectator() || !TimeWarpConfig.clockTravel()) return;
        boolean inWarp = TimeWarp.isIn(player);
        TimeWarpSession s = session(player);
        long now = now(player);
        if (!inWarp && player.level().dimension() != Level.OVERWORLD) {
            refuse(player, s, now, Component.translatable("message.respectcraft.time_warp.clock_still"));
            return;
        }
        if (TimeWarp.level(player.server) == null) {
            refuse(player, s, now, Component.translatable("message.respectcraft.time_warp.missing"));
            return;
        }
        int cooldown = TimeWarpConfig.cooldownTicks();
        if (!TimeWarpRules.cooldownOver(TimeWarp.lastTravel(player), now, cooldown)) {
            refuse(player, s, now, Component.translatable("message.respectcraft.time_warp.cooldown",
                    TimeWarpRules.cooldownLeftSeconds(TimeWarp.lastTravel(player), now, cooldown)));
            return;
        }
        if (s.travelling) return;
        if (!TimeWarpRules.chargeContinues(s.lastUse, now)) s.chargeStart = now;
        s.lastUse = now;
        int charge = TimeWarpConfig.chargeTicks();
        float progress = Math.min(1F, (now - s.chargeStart) / (float) charge);
        player.displayClientMessage(Component.translatable(inWarp ? "message.respectcraft.time_warp.charge_out" : "message.respectcraft.time_warp.charge_in",
                bar(progress)), true);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.25F, 0.6F + progress * 0.9F);
        if (!TimeWarpRules.chargeComplete(s.chargeStart, now, charge)) return;
        s.resetCharge();
        travel(player, inWarp, now);
    }

    private static void travel(ServerPlayer player, boolean inWarp, long now) {
        TimeWarpSession s = session(player);
        s.travelling = true;
        try {
            double fromX = player.getX(), fromZ = player.getZ();
            String fromDim = player.level().dimension().location().toString();
            TimeWarp.Arrival a = inWarp ? TimeWarp.exit(player) : TimeWarp.enter(player);
            if (a == null) {
                player.displayClientMessage(Component.translatable("message.respectcraft.time_warp.missing"), true);
                return;
            }
            TimeWarp.setLastTravel(player, now);
            player.getCooldowns().addCooldown(Items.CLOCK, TimeWarpConfig.cooldownTicks());
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 0.5F);
            player.displayClientMessage(Component.translatable(inWarp ? "message.respectcraft.time_warp.arrived_out"
                    : (a.water() ? "message.respectcraft.time_warp.arrived_water" : "message.respectcraft.time_warp.arrived_land")), true);
            RespectCraft.LOGGER.info("RespectCraft Time Warp: {} {} ({} {} {}) -> {} {} ({})", player.getGameProfile().getName(),
                    inWarp ? "left" : "entered", fromDim, Math.round(fromX), Math.round(fromZ),
                    a.level().dimension().location(), a.block().toShortString(), a.note());
        } catch (RuntimeException e) {
            // Falha no meio: o jogador fica onde estava (teleporte nao aconteceu ou o vanilla ja o colocou inteiro no destino).
            RespectCraft.LOGGER.error("RespectCraft Time Warp travel failed for {}", player.getGameProfile().getName(), e);
            player.displayClientMessage(Component.translatable("message.respectcraft.time_warp.failed"), true);
        } finally {
            s.travelling = false;
        }
    }

    private static void refuse(ServerPlayer player, TimeWarpSession s, long now, Component message) {
        s.resetCharge();
        if (s.lastRefusal >= 0 && now - s.lastRefusal < 20 && now >= s.lastRefusal) return;
        s.lastRefusal = now;
        player.displayClientMessage(message, true);
    }

    static String bar(float progress) {
        int filled = Math.round(progress * 10);
        return "▮".repeat(filled) + "▯".repeat(10 - filled);
    }

    /** Chamado a cada tick do jogador no Time Warp: recuperacao sem relogio (parado, olhando para o ceu). */
    static void tickDrift(ServerPlayer player, TimeWarpSession s) {
        if (!TimeWarpConfig.driftRecovery()) return;
        if (player.tickCount % 20 == 0) {
            boolean hasClock = player.getInventory().contains(new ItemStack(Items.CLOCK));
            if (hasClock) {
                s.driftTicks = 0;
                return;
            }
            if (!s.driftHinted) {
                s.driftHinted = true;
                player.displayClientMessage(Component.translatable("message.respectcraft.time_warp.no_clock"), false);
            }
        }
        // Gesto explicito que funciona em terra, boiando e num barco: parado, olhando reto para o ceu.
        net.minecraft.world.phys.Vec3 v = player.isPassenger() && player.getVehicle() != null
                ? player.getVehicle().getDeltaMovement() : player.getDeltaMovement();
        boolean still = v.horizontalDistanceSqr() < 1.0E-4 && player.getXRot() <= TimeWarpRules.DRIFT_PITCH;
        if (!still || player.getInventory().contains(new ItemStack(Items.CLOCK))) {
            s.driftTicks = 0;
            return;
        }
        s.driftTicks++;
        if (player.isInWater() && s.driftTicks % 100 == 1) {
            // Boiando parado sem relogio: nunca se afoga esperando a recuperacao.
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING,
                    120, 0, false, false, true));
        }
        if (s.driftTicks % 20 == 0) {
            player.displayClientMessage(Component.translatable("message.respectcraft.time_warp.drifting",
                    bar(s.driftTicks / (float) TimeWarpRules.DRIFT_TICKS)), true);
        }
        if (s.driftTicks >= TimeWarpRules.DRIFT_TICKS) {
            s.driftTicks = 0;
            TimeWarp.Arrival a = TimeWarp.driftBack(player);
            player.displayClientMessage(Component.translatable("message.respectcraft.time_warp.drifted"), true);
            RespectCraft.LOGGER.info("RespectCraft Time Warp: {} drifted back without a clock -> {} {}",
                    player.getGameProfile().getName(), a.level().dimension().location(), a.block().toShortString());
        }
    }
}
