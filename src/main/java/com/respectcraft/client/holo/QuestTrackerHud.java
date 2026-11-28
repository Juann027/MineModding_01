package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.QuestClientCache;
import com.respectcraft.network.QuestNetworking;
import com.respectcraft.network.QuestNetworking.QuestView;
import com.respectcraft.quest.QuestState;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Rastreador de missoes na HUD (Etapa 4.14.3): painel compacto de vidro com as missoes que o jogador escolheu
 * rastrear (Track na secao Quests, ate 3), nome e objetivos com progresso. Camada da GUI ({@link HoloClient}),
 * por padrao na borda direita abaixo dos efeitos de status; ligar, escala, posicao e opacidade na pagina "Tracker"
 * do painel Display. Some com F1, F3 e enquanto nada e rastreado. Passa por {@link HoloPhenomena} (sem aviso).
 */
public final class QuestTrackerHud {
    private QuestTrackerHud() {}

    static final int WIDTH = 150;
    private static final int MAX_LINES = 4;

    /** Missoes concluidas esperando resgate manual (ponto aceso do chip Quests). */
    static int claimable() {
        int n = 0;
        for (QuestClientCache.Entry e : QuestClientCache.all()) {
            if (e.view().state() == QuestState.COMPLETED && e.view().has(QuestView.MANUAL_CLAIM)) n++;
        }
        return n;
    }

    public static void render(GuiGraphics g) {
        if (!HoloConfig.tracker()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.getDebugOverlay().showDebugScreen()) return;
        List<QuestClientCache.Entry> tracked = QuestClientCache.tracked();
        if (tracked.isEmpty()) return;
        Font font = mc.font;
        boolean hc = HoloConfig.highContrast();
        float scale = HoloConfig.trackerScale();
        int sw = g.guiWidth(), sh = g.guiHeight();
        int w = Math.min(WIDTH, (int) ((sw - 12) / scale));
        // altura
        int h = 14;
        for (QuestClientCache.Entry e : tracked) h += 12 + Math.min(MAX_LINES, e.view().objectives().size()) * 10 + 3;
        float pw = w * scale, ph = h * scale;
        float x = Math.max(4, Math.min(sw - 4 - pw, HoloConfig.trackerX() * (sw - pw)));
        float y = Math.max(4, Math.min(sh - 4 - ph, HoloConfig.trackerY() * (sh - ph)));
        HoloPhenomena.Look look = HoloPhenomena.apply(new HoloPhenomena.Look(0xFF4FB4FF), null, 0F);
        float a = HoloConfig.trackerOpacity() * look.alpha;
        int col = look.accent;
        g.pose().pushPose();
        g.pose().translate(x + look.offsetX, y + look.offsetY, 300F);
        g.pose().scale(scale, scale, 1F);
        g.fillGradient(0, 0, w, h, Ui.alpha(hc ? HoloStyle.GLASS_HC : HoloStyle.GLASS_TOP, (hc ? 0.95F : 0.82F) * a),
                Ui.alpha(hc ? HoloStyle.GLASS_HC : HoloStyle.GLASS_BOTTOM, (hc ? 0.95F : 0.86F) * a));
        int frame = Ui.alpha(col, 0.45F * Math.max(0.5F, a));
        g.fill(0, 0, w, 1, frame);
        g.fill(0, h - 1, w, h, frame);
        g.fill(0, 0, 1, h, Ui.alpha(col, 0.9F));
        int br = Ui.alpha(col, 0.9F);
        g.fill(w - 6, -1, w + 1, 0, br);
        g.fill(w, -1, w + 1, 6, br);
        g.fill(-1, h, 6, h + 1, br);
        String head = "❖ " + Component.translatable("gui.respectcraft.holo.section.quests").getString().toUpperCase(Locale.ROOT);
        g.drawString(font, head, 5, 3, col, false);
        int ty = 14;
        for (QuestClientCache.Entry e : tracked) {
            QuestView v = e.view();
            int qc = v.state() == QuestState.COMPLETED ? HoloStyle.GOLD : v.has(QuestView.SECRET) ? HoloStyle.VIOLET : HoloStyle.CYAN;
            String title = look.text.apply(Ui.fit(font, v.title().getString(), w - 10));
            g.drawString(font, title, 5, ty, HoloStyle.text(hc), true);
            ty += 11;
            int n = 0;
            for (QuestNetworking.ObjectiveView o : v.objectives()) {
                if (n++ >= MAX_LINES) break;
                boolean ok = o.value() >= o.count();
                String count = o.value() + "/" + o.count();
                int cw = font.width(count);
                String label = look.text.apply(Ui.fit(font, o.label().getString(), w - 18 - cw));
                g.drawString(font, ok ? "✔" : "•", 5, ty, ok ? HoloStyle.GREEN : qc, false);
                g.drawString(font, label, 13, ty, ok ? HoloStyle.dim(hc) : HoloStyle.muted(hc), false);
                g.drawString(font, count, w - 4 - cw, ty, ok ? HoloStyle.GREEN : HoloStyle.muted(hc), false);
                ty += 10;
            }
            if (v.state() == QuestState.COMPLETED) {
                g.fill(5, ty - 1, w - 4, ty, Ui.alpha(HoloStyle.GOLD, 0.5F));
            }
            ty += 3;
        }
        g.pose().popPose();
    }
}
