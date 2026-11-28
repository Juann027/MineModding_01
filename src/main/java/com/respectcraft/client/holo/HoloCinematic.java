package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.NoticeClientCache;
import com.respectcraft.notice.HoloNotice;
import com.respectcraft.notice.NoticeRules;
import com.respectcraft.notice.NoticeStyle;
import com.respectcraft.registry.ModSounds;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Janela cinematografica do sistema (Etapa 4.14.1, Notification HUD V2): um retangulo de vidro escuro com moldura
 * holografica e cantoneiras luminosas que se abre diante da visao (linha que se estica e depois se abre na altura),
 * com estilo (cor/glifo), titulo, subtitulo, texto, indicador de duracao e som de entrada.
 *
 * <p>E uma camada da GUI ({@link HoloClient}): acompanha a tela em primeira e terceira pessoa, andando, pulando ou
 * girando a camera, sem {@code Screen}, sem capturar o mouse, sem pausar e sem bloquear controle nenhum. Le so a
 * fila de {@link NoticeClientCache}; posicao, escala, opacidade, duracao padrao, volume, intensidade da animacao e
 * dos efeitos vem de {@link HoloConfig} (movimento reduzido = so esmaecer; alto contraste = vidro opaco sem
 * varredura). Desligada na config, a fila vira cartoes comuns ({@link HoloToasts}). Modificadores visuais futuros
 * passam por {@link HoloPhenomena}.
 */
public final class HoloCinematic {
    private HoloCinematic() {}

    static final int WIDTH = 260;

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!HoloConfig.cinematic()) {
            NoticeClientCache.demoteCinematic();
            return;
        }
        long now = Util.getMillis();
        NoticeClientCache.Entry e = NoticeClientCache.cinematic(now);
        if (e == null) return;
        if (e.shownMillis < 0) {
            e.shownMillis = now;
            e.cinematicMillis = NoticeRules.cinematicMillis(e.notice.durationMillis(), HoloConfig.cinematicSeconds());
            sound(e.notice.style(), now);
        }
        if (e.cinematicMillis <= 0) e.cinematicMillis = NoticeRules.cinematicMillis(e.notice.durationMillis(), HoloConfig.cinematicSeconds());
        if (mc.options.hideGui || mc.player == null) return;
        float seconds = e.cinematicMillis / 1000F;
        float age = (now - e.shownMillis) / 1000F;
        float anim = HoloConfig.cinematicAnim();
        float vis = NoticeRules.cinematicVisibility(age, seconds, anim);
        if (e.exitMillis >= 0) vis *= Math.max(0F, 1F - (now - e.exitMillis) / (NoticeRules.INTERRUPT_EXIT * 1000F));
        if (vis <= 0.001F) return;
        draw(g, mc.font, e.notice, age, seconds, vis, anim, HoloConfig.cinematicFx());
    }

    /** Desenha a janela; tambem usado pelo exemplo do painel Display. */
    static void draw(GuiGraphics g, Font font, HoloNotice n, float age, float seconds, float vis, float anim, float fx) {
        NoticeStyle style = n.style();
        HoloPhenomena.Look look = HoloPhenomena.apply(new HoloPhenomena.Look(style.color()), n, age);
        int col = look.accent;
        boolean hc = HoloConfig.highContrast();
        float scale = HoloConfig.cinematicScale();
        int sw = g.guiWidth(), sh = g.guiHeight();
        float w = Math.min(WIDTH, (sw - 16) / scale);
        float inner = w - 28F;

        String label = look.text.apply(Component.translatable(style.translationKey()).getString().toUpperCase(Locale.ROOT));
        String title = look.text.apply(n.title().getString());
        String subtitle = n.subtitle().map(c -> look.text.apply(c.getString())).orElse(null);
        float titleScale = Math.min(1.45F, inner / Math.max(1F, font.width(title)));
        if (titleScale < 0.85F) {
            titleScale = 0.85F;
            title = Ui.fit(font, title, (int) (inner / titleScale));
        }
        List<String> body = Ui.wrap(font, look.text.apply(n.body().getString()), (int) inner, 4);
        boolean bar = seconds >= 1.2F;
        float h = 14F + 10F + 6F + 13F * titleScale + (subtitle == null ? 0F : 11F) + 6F + body.size() * 10F + 8F + (bar ? 4F : 0F);

        // posicao da config, mantida inteira dentro da tela
        float pw = w * scale, ph = h * scale;
        float cx = Math.max(8 + pw / 2F, Math.min(sw - 8 - pw / 2F, HoloConfig.cinematicX() * sw));
        float cy = Math.max(8 + ph / 2F, Math.min(sh - 8 - ph / 2F, HoloConfig.cinematicY() * sh));

        // abertura: linha que se estica e depois abre na altura (so esmaece sem animacao)
        float openW, openH, content;
        if (anim <= 0.01F) {
            openW = openH = 1F;
            content = vis;
        } else {
            openW = HoloAnim.smooth(Math.min(1F, vis / 0.45F));
            openH = HoloAnim.smooth(HoloAnim.clamp01((vis - 0.3F) / 0.7F));
            content = HoloAnim.clamp01((vis - 0.62F) / 0.38F);
            openW = 1F - (1F - openW) * anim;
            openH = Math.max(0.02F, 1F - (1F - openH) * anim);
        }
        float alpha = vis * look.alpha;
        if (style == NoticeStyle.MYSTERIOUS && fx > 0F && !HoloConfig.reducedMotion()) {
            float flicker = (float) Math.sin(age * 23F) * (float) Math.sin(age * 7.3F);
            alpha *= 1F - 0.25F * fx * Math.max(0F, flicker);
        }
        float glass = alpha * (hc ? 1F : HoloConfig.cinematicOpacity());
        int textA = Math.max(4, Math.round(255 * content * look.alpha)) << 24;

        g.pose().pushPose();
        g.pose().translate(cx + look.offsetX, cy + look.offsetY, 450F);
        g.pose().scale(scale, scale, 1F);
        float hw = w / 2F * openW, hh = h / 2F * openH;
        int x0 = Math.round(-hw), x1 = Math.round(hw), y0 = Math.round(-hh), y1 = Math.round(hh);

        // brilho de fundo
        if (fx > 0F) Ui.glow(g, 0, 0, w * 0.62F, Ui.alpha(col, 0.16F * fx * alpha));
        // vidro
        g.fillGradient(x0, y0, x1, y1, Ui.alpha(hc ? HoloStyle.GLASS_HC : HoloStyle.GLASS_TOP, 0.86F * glass),
                Ui.alpha(hc ? HoloStyle.GLASS_HC : HoloStyle.GLASS_BOTTOM, 0.92F * glass));
        g.fill(x0, y0, x1, y1, Ui.alpha(col, 0.06F * alpha));
        // moldura fina + cantoneiras luminosas
        int frame = Ui.alpha(col, (hc ? 0.9F : 0.5F) * alpha);
        g.fill(x0, y0, x1, y0 + 1, frame);
        g.fill(x0, y1 - 1, x1, y1, frame);
        g.fill(x0, y0, x0 + 1, y1, frame);
        g.fill(x1 - 1, y0, x1, y1, frame);
        int br = Ui.alpha(col, alpha);
        int arm = Math.max(3, Math.min(12, Math.round(Math.min(hw, hh))));
        corner(g, x0 - 2, y0 - 2, arm, 1, 1, br);
        corner(g, x1 + 2, y0 - 2, arm, -1, 1, br);
        corner(g, x0 - 2, y1 + 2, arm, 1, -1, br);
        corner(g, x1 + 2, y1 + 2, arm, -1, -1, br);
        if (fx > 0F) {
            float gl = 0.35F * fx * alpha;
            Ui.glow(g, x0, y0, 7, Ui.alpha(col, gl));
            Ui.glow(g, x1, y0, 7, Ui.alpha(col, gl));
            Ui.glow(g, x0, y1, 7, Ui.alpha(col, gl));
            Ui.glow(g, x1, y1, 7, Ui.alpha(col, gl));
        }
        // perigo: moldura pulsa (nunca pisca com movimento reduzido)
        if (style.priority() >= 4 && fx > 0F && !HoloConfig.reducedMotion()) {
            float p = 0.5F + 0.5F * (float) Math.sin(age * 9F);
            g.fill(x0, y0, x1, y0 + 2, Ui.alpha(col, 0.5F * p * fx * alpha));
            g.fill(x0, y1 - 2, x1, y1, Ui.alpha(col, 0.5F * p * fx * alpha));
        }
        // varredura
        if (!hc && fx > 0F && openH > 0.9F) {
            float t = (age * 0.55F) % 1F;
            int sy = Math.round(y0 + t * (y1 - y0));
            g.fill(x0 + 1, sy, x1 - 1, sy + 1, Ui.alpha(col, 0.14F * fx * alpha));
        }
        // interferencia (fenomenos futuros): faixas deslocadas
        if (look.interference > 0F) {
            int bands = 1 + Math.round(look.interference * 3);
            for (int i = 0; i < bands; i++) {
                int by = Math.round(y0 + ((age * 3.1F + i * 0.37F) % 1F) * (y1 - y0));
                g.fill(x0, by, x1, by + 2, Ui.alpha(col, 0.25F * look.interference * alpha));
            }
        }

        if (content > 0.01F) {
            float y = -h / 2F + 9F;
            // cabecalho: glifo + estilo, centralizado
            String head = style.glyph() + "  " + label;
            int hwid = font.width(head);
            g.drawString(font, head, -hwid / 2, Math.round(y), (col & 0xFFFFFF) | textA, false);
            y += 12F;
            int dw = Math.round(inner / 2F * content);
            g.fill(-dw, Math.round(y), dw, Math.round(y) + 1, Ui.alpha(col, 0.45F * content * alpha));
            y += 6F;
            g.pose().pushPose();
            g.pose().translate(0, y, 0);
            g.pose().scale(titleScale, titleScale, 1F);
            g.drawString(font, title, -font.width(title) / 2, 0, (HoloStyle.text(hc) & 0xFFFFFF) | textA, true);
            g.pose().popPose();
            y += 13F * titleScale;
            if (subtitle != null) {
                String s = Ui.fit(font, subtitle, (int) inner);
                g.drawString(font, s, -font.width(s) / 2, Math.round(y), (Ui.lerpColor(col, HoloStyle.text(hc), 0.45F) & 0xFFFFFF) | textA, false);
                y += 11F;
            }
            y += 4F;
            for (String line : body) {
                g.drawString(font, line, -font.width(line) / 2, Math.round(y), (HoloStyle.muted(hc) & 0xFFFFFF) | textA, false);
                y += 10F;
            }
            if (bar) {
                float left = Math.max(0F, 1F - age / seconds);
                int bw = Math.round((w - 16F) * left / 2F);
                int by = y1 - 4;
                g.fill(-bw, by, bw, by + 1, Ui.alpha(col, 0.65F * content * alpha));
            }
        }
        g.pose().popPose();
    }

    /** Cantoneira em L no canto (x, y), bracos para dentro (dx, dy = +1/-1). */
    private static void corner(GuiGraphics g, int x, int y, int arm, int dx, int dy, int color) {
        int ax = dx > 0 ? x : x - arm, ay = dy > 0 ? y : y - 1;
        g.fill(ax, ay, ax + arm, ay + 1, color);
        int bx = dx > 0 ? x : x - 1, by = dy > 0 ? y : y - arm;
        g.fill(bx, by, bx + 1, by + arm, color);
    }

    static void sound(NoticeStyle style, long now) {
        float vol = HoloConfig.noticeVolume();
        if (vol <= 0F || style.sound() == NoticeStyle.Sound.NONE || !NoticeClientCache.claimSound(now)) return;
        switch (style.sound()) {
            case TICK -> Ui.playUi(ModSounds.UI_HOVER.get(), 1.6F, 0.35F * vol);
            case SOFT -> Ui.playUi(ModSounds.UI_HOVER.get(), 1.15F, 0.55F * vol);
            case REVEAL -> Ui.playUi(ModSounds.SKILL_UNLOCK.get(), 1.3F, 0.6F * vol);
            case TRIUMPH -> Ui.playUi(ModSounds.ROULETTE_WIN.get(), 1.05F, 0.5F * vol);
            case ALARM -> Ui.playUi(ModSounds.FINISHER_STING.get(), 1.3F, 0.55F * vol);
            default -> {}
        }
    }
}
