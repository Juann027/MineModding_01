package com.respectcraft.finisher;

import com.respectcraft.RespectCraft;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Caminho preparado para recompensas de chefe (Etapa 4.8): distingue derrota comum, derrota por finalizacao
 * qualificante e o jogador creditado, e posta {@link BossDefeatEvent} no maximo uma vez por chefe.
 *
 * <p>Estado so na propria entidade chefe ({@code persistentData}), nada em {@code static}:
 * {@value #CREDIT_KEY} guarda a ultima finalizacao concluida contra ele (jogador, finalizacao, tick) e
 * {@value #POSTED_KEY} marca que a derrota ja foi anunciada. NAO concede nada.
 */
public final class BossDefeats {
    /** Janela (ticks) em que a morte do chefe ainda conta como derrota pela finalizacao concluida. Provisoria. */
    public static final int FINISHER_CREDIT_TICKS = 100;
    static final String CREDIT_KEY = "respectcraft_boss_finisher_credit";
    static final String POSTED_KEY = "respectcraft_boss_defeat_posted";

    private BossDefeats() {}

    /** Credito de finalizacao gravado no chefe. */
    public record Credit(UUID player, ResourceLocation finisher, long gameTime) {}

    /** Decisao de anunciar uma derrota. */
    public record Decision(BossDefeatEvent.Method method, Optional<UUID> credited, Optional<ResourceLocation> finisher) {}

    /**
     * Regra pura (coberta pelo {@code SelfTest}): ja anunciado = nada; credito de finalizacao dentro da janela =
     * FINISHER creditado ao executor; senao NORMAL creditado a quem matou (se jogador).
     */
    public static Optional<Decision> decide(boolean alreadyPosted, Optional<Credit> credit, long now, Optional<UUID> killer) {
        if (alreadyPosted) return Optional.empty();
        if (credit.isPresent() && now >= credit.get().gameTime() && now - credit.get().gameTime() <= FINISHER_CREDIT_TICKS) {
            return Optional.of(new Decision(BossDefeatEvent.Method.FINISHER, Optional.of(credit.get().player()),
                    Optional.of(credit.get().finisher())));
        }
        return Optional.of(new Decision(BossDefeatEvent.Method.NORMAL, killer, Optional.empty()));
    }

    /** Finalizacao concluida por um jogador contra um chefe: grava o credito no chefe (substitui o anterior). */
    public static void onFinisherCompleted(FinisherSessionEvent.Completed event) {
        if (event.getInteraction() != FinisherParticipants.Interaction.PLAYER_VS_BOSS) return;
        if (!(event.getAttacker() instanceof ServerPlayer player) || event.getVictim().level().isClientSide()) return;
        CompoundTag credit = new CompoundTag();
        credit.putUUID("player", player.getUUID());
        credit.putString("finisher", event.getFinisher().id().toString());
        credit.putLong("time", event.getVictim().level().getGameTime());
        event.getVictim().getPersistentData().put(CREDIT_KEY, credit);
    }

    /** Registrado com prioridade LOWEST: morte cancelada por outro mod (ex.: totem) nao e derrota. */
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity boss = event.getEntity();
        if (event.isCanceled() || boss.level().isClientSide()) return;
        if (FinisherParticipants.kindOf(boss) != FinisherParticipants.Kind.BOSS) return;
        CompoundTag data = boss.getPersistentData();
        Optional<Decision> decision = decide(data.getBoolean(POSTED_KEY), readCredit(data), boss.level().getGameTime(),
                killer(event));
        if (decision.isEmpty()) return;
        data.putBoolean(POSTED_KEY, true);
        data.remove(CREDIT_KEY);
        ServerPlayer credited = decision.get().credited()
                .map(id -> boss.getServer() == null ? null : boss.getServer().getPlayerList().getPlayer(id))
                .orElse(null);
        RespectCraft.LOGGER.debug("Boss defeat {} ({}), credited {}", FinisherParticipants.typeId(boss),
                decision.get().method(), credited == null ? "nobody" : credited.getGameProfile().getName());
        NeoForge.EVENT_BUS.post(new BossDefeatEvent(boss, decision.get().method(), credited,
                decision.get().finisher().orElse(null)));
    }

    private static Optional<UUID> killer(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player player) return Optional.of(player.getUUID());
        return event.getEntity().getKillCredit() instanceof Player player ? Optional.of(player.getUUID()) : Optional.empty();
    }

    private static Optional<Credit> readCredit(CompoundTag data) {
        if (!data.contains(CREDIT_KEY)) return Optional.empty();
        CompoundTag tag = data.getCompound(CREDIT_KEY);
        ResourceLocation finisher = ResourceLocation.tryParse(tag.getString("finisher"));
        if (!tag.hasUUID("player") || finisher == null) return Optional.empty();
        return Optional.of(new Credit(tag.getUUID("player"), finisher, tag.getLong("time")));
    }
}
