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
 * Caminho preparado para recompensas de chefe (Etapa 4.8, revisto na 4.10.1): distingue derrota comum de execucao
 * pela finalizacao EXCLUSIVA do chefe e posta {@link BossDefeatEvent} no maximo uma vez por chefe.
 *
 * <p>Etapa 4.10.1: finalizacoes comuns nunca comecam contra chefes ({@link FinisherTargeting}); so a exclusiva
 * designada para aquele chefe pode executa-lo, e a derrota so e {@link BossDefeatEvent.Method#FINISHER} quando a morte
 * confirmada veio do golpe final dessa execucao ({@link FinisherExecution}). Cinematica concluida sem morte, morte
 * posterior por espada, ou morte por outra finalizacao nunca contam como execucao.
 *
 * <p>Estado so na propria entidade chefe ({@code persistentData}), nada em {@code static}: {@value #CREDIT_KEY} guarda
 * a execucao em andamento (jogador, finalizacao, tick) apenas durante o golpe final ({@link #markExecution} /
 * {@link #clearExecution}) e {@value #POSTED_KEY} marca que a derrota ja foi anunciada. NAO concede nada.
 */
public final class BossDefeats {
    static final String CREDIT_KEY = "respectcraft_boss_finisher_credit";
    static final String POSTED_KEY = "respectcraft_boss_defeat_posted";

    private BossDefeats() {}

    /** Credito de execucao gravado no chefe durante o golpe final. */
    public record Credit(UUID player, ResourceLocation finisher, long gameTime) {}

    /** Decisao de anunciar uma derrota. */
    public record Decision(BossDefeatEvent.Method method, Optional<UUID> credited, Optional<ResourceLocation> finisher) {}

    /**
     * Regra pura (coberta pelo {@code SelfTest}): ja anunciado = nada; morte pelo dano de execucao com credito do
     * mesmo tick = FINISHER creditado ao executor; qualquer outra morte = NORMAL creditado a quem matou (se jogador).
     */
    public static Optional<Decision> decide(boolean alreadyPosted, Optional<Credit> credit, long now, Optional<UUID> killer,
                                            boolean executionDamage) {
        if (alreadyPosted) return Optional.empty();
        if (executionDamage && credit.isPresent() && credit.get().gameTime() == now) {
            return Optional.of(new Decision(BossDefeatEvent.Method.FINISHER, Optional.of(credit.get().player()),
                    Optional.of(credit.get().finisher())));
        }
        return Optional.of(new Decision(BossDefeatEvent.Method.NORMAL, killer, Optional.empty()));
    }

    /** Golpe final de uma finalizacao exclusiva contra este chefe vai comecar (so {@link FinisherExecution}). */
    static void markExecution(LivingEntity boss, ServerPlayer player, FinisherDef finisher) {
        CompoundTag credit = new CompoundTag();
        credit.putUUID("player", player.getUUID());
        credit.putString("finisher", finisher.id().toString());
        credit.putLong("time", boss.level().getGameTime());
        boss.getPersistentData().put(CREDIT_KEY, credit);
    }

    /** Fim do golpe final: o credito nao sobrevive a ele (chefe que sobreviveu nao guarda nada). */
    static void clearExecution(LivingEntity boss) {
        boss.getPersistentData().remove(CREDIT_KEY);
    }

    /** Registrado com prioridade LOWEST: morte cancelada por outro mod (ex.: totem) nao e derrota. */
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity boss = event.getEntity();
        if (event.isCanceled() || boss.level().isClientSide()) return;
        if (FinisherParticipants.kindOf(boss) != FinisherParticipants.Kind.BOSS) return;
        CompoundTag data = boss.getPersistentData();
        Optional<Decision> decision = decide(data.getBoolean(POSTED_KEY), readCredit(data), boss.level().getGameTime(),
                killer(event), FinisherExecution.isExecution(event.getSource()));
        if (decision.isEmpty()) return;
        data.putBoolean(POSTED_KEY, true);
        data.remove(CREDIT_KEY);
        ServerPlayer credited = decision.get().credited()
                .map(id -> boss.getServer() == null ? null : boss.getServer().getPlayerList().getPlayer(id))
                .orElse(null);
        RespectCraft.LOGGER.info("Boss defeat {} ({}), credited {}", FinisherParticipants.typeId(boss),
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
