package com.tupinicraft.client;

import com.tupinicraft.election.ElectionManager;
import com.tupinicraft.election.ElectionPhase;
import com.tupinicraft.network.CastVotePayload;
import com.tupinicraft.network.ElectionSyncPayload;
import com.tupinicraft.politics.PoliticalOffice;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tela simples da urna eletrônica: um botão por candidato. O clique apenas envia o pedido
 * de voto; o servidor valida tudo e responde com uma mensagem e uma nova sincronização.
 */
public final class UrnaScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_SPACING = 24;

    private final List<Label> labels = new ArrayList<>();
    private int builtForVersion = -1;

    public UrnaScreen() {
        super(Component.literal("Urna Eletrônica"));
    }

    @Override
    protected void init() {
        labels.clear();
        builtForVersion = ClientElectionCache.version();
        ElectionSyncPayload state = ClientElectionCache.get();
        UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        boolean voting = state.electionPhase() == ElectionPhase.VOTING;

        int left = width / 2 - BUTTON_WIDTH / 2;
        int y = 44;
        if (!voting) {
            labels.add(new Label(Component.literal("Não há votação em andamento.").withStyle(ChatFormatting.RED), y));
            y += ROW_SPACING;
        }
        for (ElectionSyncPayload.RaceView race : state.races()) {
            PoliticalOffice office = race.politicalOffice();
            boolean alreadyVoted = state.hasVotedFor(office);
            boolean isCandidateHere = race.candidates().stream().anyMatch(c -> c.id().equals(self));
            Component header = Component.literal(office.displayName()).withStyle(ChatFormatting.GOLD);
            if (alreadyVoted) {
                header = header.copy().append(Component.literal(" — voto registrado ✔").withStyle(ChatFormatting.GREEN));
            } else if (isCandidateHere) {
                header = header.copy().append(Component.literal(" — você é candidato").withStyle(ChatFormatting.GRAY));
            }
            labels.add(new Label(header, y));
            y += 14;
            for (ElectionSyncPayload.CandidateView candidate : race.candidates()) {
                boolean isSelf = candidate.id().equals(self);
                Button button = Button.builder(
                                Component.literal("Votar em " + candidate.name() + (isSelf ? " (você)" : "")),
                                pressed -> vote(candidate.id()))
                        .bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build();
                button.active = voting && !alreadyVoted && !isCandidateHere && !isSelf;
                addRenderableWidget(button);
                y += ROW_SPACING;
            }
            y += 6;
        }
        addRenderableWidget(Button.builder(Component.literal("Fechar"), pressed -> onClose())
                .bounds(left, Math.min(y + 4, height - BUTTON_HEIGHT - 6), BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void vote(UUID candidate) {
        PacketDistributor.sendToServer(new CastVotePayload(candidate));
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (builtForVersion != ClientElectionCache.version()) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, 14, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.literal("Tempo restante: "
                        + ElectionManager.formatTime(ClientElectionCache.displayTicksRemaining())),
                width / 2, 28, 0xFFAAAAAA);
        for (Label label : labels) {
            graphics.drawCenteredString(font, label.text(), width / 2, label.y() + 2, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Label(Component text, int y) {
    }
}
