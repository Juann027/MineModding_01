package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.NoticeClientCache;
import com.respectcraft.notice.HoloNotice;
import com.respectcraft.notice.NoticeRules;
import com.respectcraft.notice.NoticeSeverity;
import com.respectcraft.registry.ModSounds;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Cartoes holograficos dos avisos automaticos na tela de jogo (Etapa 4.11): canto superior esquerdo (o direito e dos efeitos de status e das notificacoes vanilla), ate
 * {@link NoticeRules#VISIBLE_TOASTS} de uma vez, com o mesmo vidro, cantoneiras e varredura do menu K. Entram
 * deslizando, ficam o tempo da gravidade e saem; o som toca quando o cartao aparece. Com {@code noticeToasts}
 * desligado na config so o historico (secao Alerts) recebe os avisos. Camada da GUI registrada em {@link HoloClient}.
 */
public final class HoloToasts {
    private HoloToasts() {}

    static final int WIDTH = 176;

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        List<NoticeClientCache.Entry> toasts = NoticeClientCache.toasts();
        if (toasts.isEmpty()) return;
        if (!HoloConfig.noticeToasts()) {
            for (NoticeClientCache.Entry e : toasts) NoticeClientCache.dropToast(e);
            return;
        }
        if (mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;
        long now = Util.getMillis();
        float time = (now % 100_000L) / 1000F;
        int sw = g.guiWidth();
        // F3 ocupa o mesmo canto: os cartoes esperam (continuam na fila e no historico)
        if (mc.getDebugOverlay().showDebugScreen()) return;
        int w = Math.min(WIDTH, sw - 16);
        float y = 6F;
        int shown = 0;
        for (NoticeClientCache.Entry e : toasts) {
            if (shown >= NoticeRules.VISIBLE_TOASTS) break;
            HoloNotice n = e.notice;
            if (e.shownMillis < 0) {
                e.shownMillis = now;
                sound(n.severity());
            }
            float age = (now - e.shownMillis) / 1000F;
            float seconds = n.severity().seconds();
            if (age > seconds) {
                NoticeClientCache.dropToast(e);
                continue;
            }
            float vis = NoticeRules.toastVisibility(age, seconds);
            if (HoloConfig.reducedMotion()) vis = vis > 0.05F ? 1F : 0F;
            List<String> body = Ui.wrap(font, n.body().getString(), w - 26, 2);
            int h = 30 + body.size() * 10;
            float slide = (1F - HoloAnim.smooth(vis)) * (w + 12);
            float x = 6 - slide;
            draw(g, font, n, body, x, y, w, h, vis, age, seconds, time);
            y += (h + 4) * Math.min(1F, vis * 1.5F);
            shown++;
        }
    }

    private static void draw(GuiGraphics g, Font font, HoloNotice n, List<String> body, float x, float y, int w, int h,
                             float vis, float age, float seconds, float time) {
        int col = n.kind().color();
        boolean hc = HoloConfig.highContrast();
        float a = vis * (hc ? 1F : HoloConfig.opacity());
        int ix = Math.round(x), iy = Math.round(y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fillGradient(ix, iy, ix + w, iy + h, Ui.alpha(hc ? HoloStyle.GLASS_HC : HoloStyle.GLASS_TOP, 0.9F * a),
                Ui.alpha(hc ? HoloStyle.GLASS_HC : HoloStyle.GLASS_BOTTOM, 0.92F * a));
        g.fill(ix, iy, ix + w, iy + h, Ui.alpha(col, 0.07F * vis));
        // moldura, barra lateral e cantoneiras
        int frame = Ui.alpha(col, 0.55F * vis);
        g.fill(ix, iy, ix + w, iy + 1, frame);
        g.fill(ix, iy + h - 1, ix + w, iy + h, frame);
        g.fill(ix + w - 1, iy, ix + w, iy + h, frame);
        g.fill(ix, iy, ix + 2, iy + h, Ui.alpha(col, vis));
        int br = Ui.alpha(col, vis);
        g.fill(ix - 2, iy - 2, ix + 6, iy - 1, br);
        g.fill(ix - 2, iy - 2, ix - 1, iy + 6, br);
        g.fill(ix + w - 6, iy + h + 1, ix + w + 2, iy + h + 2, br);
        g.fill(ix + w + 1, iy + h - 6, ix + w + 2, iy + h + 2, br);
        // varredura que desce pelo vidro
        if (!hc && HoloConfig.motion() > 0F) {
            int sy = iy + (int) (((time * 0.6F) % 1F) * h);
            g.fill(ix + 2, sy, ix + w - 1, sy + 1, Ui.alpha(col, 0.12F * vis * HoloConfig.motion()));
        }
        // critico pisca a moldura nos primeiros segundos
        if (n.severity() == NoticeSeverity.CRITICAL && age < 2.5F && HoloConfig.motion() > 0F) {
            float p = 0.5F + 0.5F * (float) Math.sin(age * 12F);
            Ui.glow(g, ix + w / 2F, iy + h / 2F, w * 0.6F, Ui.alpha(col, 0.18F * p * vis));
        }
        Ui.glow(g, ix + 11, iy + 11, 11, Ui.alpha(col, 0.45F * vis));
        int textA = Math.max(4, Math.round(255 * vis)) << 24;
        String glyph = n.kind().glyph();
        g.drawString(font, glyph, ix + 11 - font.width(glyph) / 2, iy + 7, (col & 0xFFFFFF) | textA, true);
        String title = Ui.fit(font, n.title().getString(), w - 26);
        g.drawString(font, title, ix + 21, iy + 4, (HoloStyle.text(hc) & 0xFFFFFF) | textA, true);
        String kind = Ui.fit(font, caption(n), w - 26);
        g.drawString(font, kind, ix + 21, iy + 14, (Ui.lerpColor(col, HoloStyle.muted(hc), 0.35F) & 0xFFFFFF) | textA, false);
        g.fill(ix + 21, iy + 24, ix + w - 6, iy + 25, Ui.alpha(col, 0.25F * vis));
        int ty = iy + 28;
        for (String line : body) {
            g.drawString(font, line, ix + 21, ty, (HoloStyle.muted(hc) & 0xFFFFFF) | textA, false);
            ty += 10;
        }
        // tempo restante: linha fina no pe do cartao
        float left = Math.max(0F, 1F - age / seconds);
        g.fill(ix + 2, iy + h - 2, ix + 2 + Math.round((w - 3) * left), iy + h - 1, Ui.alpha(col, 0.6F * vis));
        g.pose().popPose();
    }

    /** Categoria e local curto ("BOSS · 120 m NE"). */
    private static String caption(HoloNotice n) {
        String kind = net.minecraft.network.chat.Component.translatable(n.kind().translationKey()).getString()
                .toUpperCase(java.util.Locale.ROOT);
        Minecraft mc = Minecraft.getInstance();
        if (n.location().isEmpty() || mc.player == null) return kind;
        HoloNotice.Location l = n.location().get();
        if (!mc.player.level().dimension().location().equals(l.dimension())) return kind;
        double dx = l.pos().getX() + 0.5 - mc.player.getX(), dz = l.pos().getZ() + 0.5 - mc.player.getZ();
        int dir = NoticeRules.direction(dx, dz);
        return kind + " · " + NoticesView.distance(dx, dz);
    }

    private static void sound(NoticeSeverity s) {
        if (!HoloConfig.noticeSounds()) return;
        switch (s) {
            case CRITICAL -> Ui.playUi(ModSounds.FINISHER_STING.get(), 1.25F, 0.55F);
            case ALERT -> Ui.playUi(ModSounds.UI_HOVER.get(), 0.8F, 0.8F);
            case INFO -> Ui.playUi(ModSounds.UI_HOVER.get(), 1.3F, 0.45F);
        }
    }
}
