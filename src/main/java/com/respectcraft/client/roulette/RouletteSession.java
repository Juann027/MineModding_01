package com.respectcraft.client.roulette;

import com.respectcraft.RespectCraft;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.GameplayNetworking;
import com.respectcraft.network.ProfileClientCache;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.Speciality;
import com.respectcraft.profile.SpecialityRarity;
import com.respectcraft.registry.ModSounds;
import com.respectcraft.ui.RouletteMachine;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Roleta de nascimento no cliente, sem desenho (Etapa 4.8.4-C): liga a {@link RouletteMachine} a ficha e a resposta
 * do servidor ({@link ProfileClientCache}), envia os pedidos ({@code BirthRollRequest} com {@code requestId},
 * {@code BirthConfirmRequest}), toca os sons e grava as linhas de log da Etapa 4.8.2. Uma unica implementacao para a
 * tela antiga (reserva) e a placa holografica; cada casca so desenha e soma os proprios efeitos pelo
 * {@link RouletteMachine.Listener}.
 */
public final class RouletteSession {
    private static final String KEY = "gui.respectcraft.roulette.";

    private final RouletteMachine machine = new RouletteMachine(ProfileClientCache.profile());
    private long requestRevision;
    /** Ticks desde a revelacao de um resultado BONER (efeito especial so visual; -1 = nenhum). */
    private int bonerTick = -1;

    public RouletteMachine machine() { return machine; }
    public CharacterProfile profile() { return ProfileClientCache.profile(); }
    public int bonerTick() { return bonerTick; }

    public boolean canSpin() { return machine.canSpin(profile()); }
    public boolean canContinue() { return machine.canContinue(profile()); }
    /** Continuar ainda descarta um novo giro (a casca avisa e pede confirmacao). */
    public boolean continueDiscardsReroll() { return canContinue() && profile().canReroll(); }

    /** Rotulo do botao de giro: "Spin" ou "Spin again (N)". */
    public Component spinLabel() {
        CharacterProfile p = profile();
        return p.born() ? Component.translatable(KEY + "reroll", p.rerolls()) : Component.translatable(KEY + "spin");
    }

    /** Pede o giro ao servidor; falso se agora nao for possivel. */
    public boolean spin() {
        CharacterProfile p = profile();
        if (!machine.canSpin(p)) return false;
        RouletteMachine.Request r = machine.spin(p, ProfileClientCache.nextRollRequestId());
        if (r == null) return false;
        requestRevision = ProfileClientCache.rollReplyRevision();
        PacketDistributor.sendToServer(new GameplayNetworking.BirthRollRequest(r.reroll(), r.expectedRollCount(), r.requestId()));
        return true;
    }

    /**
     * Aceita o resultado: a ficha ja esta salva no servidor; o servidor confirma o nascimento (o novo giro restante e
     * descartado). Falso se ainda nao ha resultado pronto.
     */
    public boolean confirm() {
        CharacterProfile p = profile();
        if (!machine.canContinue(p)) return false;
        if (!p.birthConfirmed()) PacketDistributor.sendToServer(new GameplayNetworking.BirthConfirmRequest(p.rollCount()));
        return true;
    }

    /** Um tick do cliente; {@code effects} recebe os mesmos momentos para os efeitos visuais da casca (pode ser null). */
    public void tick(RouletteMachine.Listener effects) {
        if (bonerTick >= 0 && ++bonerTick > 40) bonerTick = -1;
        GameplayNetworking.BirthRollReply reply = ProfileClientCache.rollReplyRevision() != requestRevision
                ? ProfileClientCache.lastRollReply() : null;
        machine.tick(profile(), reply, new RouletteMachine.Listener() {
            @Override public void segment(int index) {
                Ui.playUi(ModSounds.ROULETTE_TICK.get(), 1.0F + (index % 3) * 0.08F, 0.35F);
                if (effects != null) effects.segment(index);
            }
            @Override public void revealed(CharacterProfile profile) {
                Ui.playUi(ModSounds.ROULETTE_WIN.get(), 1F, 0.8F);
                Speciality won = profile.specialityOrNull();
                bonerTick = won != null && won.rarity() == SpecialityRarity.BONER ? 0 : -1;
                if (bonerTick == 0) Ui.playUi(ModSounds.ROULETTE_WIN.get(), 1.5F, 1F);
                if (effects != null) effects.revealed(profile);
            }
            @Override public void revealStep(int step) {
                Ui.playUi(ModSounds.ROULETTE_TICK.get(), 0.8F + step * 0.1F, 0.6F);
                if (effects != null) effects.revealStep(step);
            }
            @Override public void failed(RouletteMachine.Status status, int requestId) {
                if (status == RouletteMachine.Status.ERROR) {
                    RespectCraft.LOGGER.warn("Birth roulette request #{}: the server reported an internal error;"
                            + " look for the same request number in the server log", requestId);
                } else {
                    RespectCraft.LOGGER.warn("Birth roulette request #{}: no reply from the server after {} ticks",
                            requestId, RouletteMachine.TIMEOUT_TICKS);
                }
                if (effects != null) effects.failed(status, requestId);
            }
        });
    }

    /** Aviso atual (erro, timeout, sem giros, ficha mudou) ou null. Escondido enquanto espera a resposta. */
    public Component status() {
        RouletteMachine.Status s = machine.status();
        if (s == RouletteMachine.Status.NONE || machine.phase() == RouletteMachine.Phase.WAITING) return null;
        return switch (s) {
            case ERROR -> Component.translatable(KEY + "status.error", machine.statusRequestId());
            case TIMEOUT -> Component.translatable(KEY + "status.timeout", machine.statusRequestId());
            case NO_REROLLS -> Component.translatable(KEY + "status.no_rerolls");
            default -> Component.translatable(KEY + "status.stale");
        };
    }
}
