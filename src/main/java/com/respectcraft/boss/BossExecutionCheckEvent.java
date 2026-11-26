package com.respectcraft.boss;

import com.respectcraft.finisher.FinisherDef;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * Ponto de integracao (Etapa 4.11): postado no {@code NeoForge.EVENT_BUS}, so no servidor, quando uma finalizacao
 * EXCLUSIVA passou na regra de alvos e nas condicoes da {@link BossProfile} e vai comecar contra o chefe. Sistemas
 * futuros (habilidades, progressao, fases do chefe) podem recusar com {@link #deny}; a recusa acontece antes de
 * qualquer palco, animacao, trava ou efeito. Hoje ninguem escuta.
 *
 * <p>So pode RECUSAR: nenhum ouvinte libera uma finalizacao comum contra chefe nem pula as travas do motor.
 */
public final class BossExecutionCheckEvent extends Event {
    private final ServerPlayer executor;
    private final LivingEntity boss;
    private final BossProfile profile;
    private final FinisherDef finisher;
    private String denial;

    public BossExecutionCheckEvent(ServerPlayer executor, LivingEntity boss, BossProfile profile, FinisherDef finisher) {
        this.executor = executor;
        this.boss = boss;
        this.profile = profile;
        this.finisher = finisher;
    }

    public ServerPlayer getExecutor() { return executor; }
    public LivingEntity getBoss() { return boss; }
    public BossProfile getProfile() { return profile; }
    public FinisherDef getFinisher() { return finisher; }

    /** Recusa a execucao com este motivo (ingles, mostrado ao jogador). A primeira recusa vale. */
    public void deny(String reason) {
        if (denial == null) denial = reason == null || reason.isBlank() ? "This execution is not available." : reason;
    }

    public Optional<String> getDenial() { return Optional.ofNullable(denial); }
}
