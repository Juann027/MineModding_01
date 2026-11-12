package com.tupinicraft.client;

import com.tupinicraft.election.ElectionManager;
import com.tupinicraft.election.ElectionPhase;
import com.tupinicraft.network.CastVotePayload;
import com.tupinicraft.network.ElectionSyncPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

/** Paginated ballot: selection is local; only explicit confirmation sends a vote. */
public final class UrnaScreen extends Screen {
    private int raceIndex, page, builtVersion, x, y, w, h, rows;
    private float uiScale = 1F;
    private int canvasW, canvasH;
    private UUID selected;
    private boolean pending;
    private long sentAt;
    private Component notice = Component.literal("Selecione um candidato para revisar seu voto.");
    public UrnaScreen() { super(Component.literal("Urna Eletrônica")); }
    private ElectionSyncPayload.RaceView race() {
        var races = ClientElectionCache.get().races();
        return races.isEmpty() ? null : races.get(Math.min(raceIndex, races.size() - 1));
    }
    private boolean eligible() {
        var state = ClientElectionCache.get();
        var race = race();
        UUID self = minecraft.player == null ? null : minecraft.player.getUUID();
        return race != null && state.electionPhase() == ElectionPhase.VOTING && !state.hasVotedIn(raceIndex)
                && race.candidates().stream().noneMatch(c -> c.id().equals(self));
    }
    @Override protected void init() {
        builtVersion = ClientElectionCache.version();
        uiScale = Math.min(1F, Math.min(width / 320F, height / 240F));
        canvasW = Math.round(width / uiScale); canvasH = Math.round(height / uiScale);
        w = Math.min(440, canvasW - 16); h = Math.min(336, canvasH - 16);
        x = (canvasW - w) / 2; y = (canvasH - h) / 2;
        rows = BallotLayout.rows(h);
        var state = ClientElectionCache.get();
        raceIndex = Math.max(0, Math.min(raceIndex, state.races().size() - 1));
        var race = race();
        int count = race == null ? 0 : race.candidates().size();
        page = BallotLayout.clampPage(page, count, rows);
        if (selected != null && (race == null || race.candidates().stream().noneMatch(c -> c.id().equals(selected)))) selected = null;
        button(x + 12, y + 47, 26, 20, "<", false, state.races().size() > 1 && !pending, () -> changeRace(-1));
        button(x + w - 38, y + 47, 26, 20, ">", false, state.races().size() > 1 && !pending, () -> changeRace(1));
        if (race != null) for (int i = page * rows; i < Math.min(count, (page + 1) * rows); i++) {
            var c = race.candidates().get(i);
            button(x + 12, y + 76 + (i % rows) * 27, w - 24, 23,
                    (c.id().equals(selected) ? "> " : "") + c.name() + (c.npc() ? " · Aldeão" : ""),
                    c.id().equals(selected), eligible() && !pending, () -> { selected = c.id(); notice = Component.literal("Revise o candidato e confirme. O voto é definitivo."); rebuildWidgets(); });
        }
        button(x + 12, y + h - 99, 28, 19, "<", false, page > 0 && !pending, () -> { page--; rebuildWidgets(); });
        button(x + w - 40, y + h - 99, 28, 19, ">", false, (page + 1) * rows < count && !pending, () -> { page++; rebuildWidgets(); });
        button(x + 12, y + h - 34, (w - 30) / 2, 23, "Fechar", false, true, this::onClose);
        button(x + 18 + (w - 30) / 2, y + h - 34, (w - 30) / 2, 23,
                pending ? "Enviando…" : "Confirmar voto", true, eligible() && selected != null && !pending, this::submit);
    }
    private void button(int bx, int by, int bw, int bh, String label, boolean primary, boolean enabled, Runnable action) {
        var b = new CivicUi.Action(bx, by, bw, bh, label, primary, ignored -> action.run());
        b.active = enabled; addRenderableWidget(b);
    }
    private void changeRace(int direction) {
        raceIndex = Math.floorMod(raceIndex + direction, ClientElectionCache.get().races().size());
        page = 0; selected = null; notice = Component.literal("Selecione um candidato para revisar seu voto."); rebuildWidgets();
    }
    private void submit() {
        if (!eligible() || selected == null || pending) return;
        pending = true; sentAt = Util.getMillis();
        PacketDistributor.sendToServer(new CastVotePayload(selected)); rebuildWidgets();
    }
    public void receiveResult(Component message) {
        pending = false; selected = null; notice = message; rebuildWidgets();
    }
    @Override public void tick() {
        super.tick();
        if (pending && Util.getMillis() - sentAt > 10000) {
            pending = false; notice = Component.literal("Sem resposta. Confira o status antes de tentar novamente."); rebuildWidgets();
        }
        if (builtVersion != ClientElectionCache.version()) rebuildWidgets();
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1);
        renderBackground(g, mx, my, partial);
        super.render(g, (int)(mx / uiScale), (int)(my / uiScale), partial);
        g.pose().popPose();
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, canvasW, canvasH, 0xBB050E0C);
        CivicUi.panel(g, x, y, w, h);
        int sweep = (int)(Util.getMillis() / 24 % (w - 4));
        g.fill(x + 2, y + 1, x + 2 + sweep, y + 2, CivicUi.GOLD);
        CivicUi.seal(g, x + 13, y + 13, CivicUi.GOLD);
        g.drawString(font, "TUPINICRAFT / URNA CÍVICA", x + 36, y + 12, CivicUi.TEXT, false);
        g.drawString(font, ClientElectionCache.phase().displayName() + " · " + ElectionManager.formatTime(ClientElectionCache.displayTicksRemaining()), x + 36, y + 26, CivicUi.JADE, false);
        var race = race();
        String label = race == null ? "Nenhuma disputa disponível" : race.label();
        g.drawCenteredString(font, CivicUi.fit(label, w - 90), x + w / 2, y + 53, CivicUi.GOLD);
        int count = race == null ? 0 : race.candidates().size();
        if (count == 0) g.drawCenteredString(font, "Nenhum candidato nesta disputa.", x + w / 2, y + 84, CivicUi.MUTED);
        String status = ClientElectionCache.get().hasVotedIn(raceIndex) ? "VOTO REGISTRADO" : !eligible() ? "Votação indisponível para você nesta disputa" : "Página " + (page + 1) + "/" + BallotLayout.pages(count, rows);
        g.drawCenteredString(font, CivicUi.fit(status, w - 92), x + w / 2, y + h - 93, CivicUi.JADE);
        String review = selected == null || race == null ? "Seu voto é validado pelo servidor." : "Escolha: " + race.candidates().stream().filter(c -> c.id().equals(selected)).map(ElectionSyncPayload.CandidateView::name).findFirst().orElse("");
        g.drawString(font, CivicUi.fit(review, w - 24), x + 12, y + h - 73, CivicUi.GOLD, false);
        var lines = font.split(notice, w - 24);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), x + 12, y + h - 60 + i * 10, CivicUi.MUTED, false);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(mx / uiScale, my / uiScale, button);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        return super.mouseReleased(mx / uiScale, my / uiScale, button);
    }
    @Override public boolean isPauseScreen() { return false; }
}
