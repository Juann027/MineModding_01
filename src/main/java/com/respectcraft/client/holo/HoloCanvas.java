package com.respectcraft.client.holo;

import com.respectcraft.RespectCraft;
import com.respectcraft.client.ui.Ui;
import cyou.rimuru.rimulib.mc1211.client.api.UiApi;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Pincel de um painel holografico: primitivas de desenho (vidro, molduras, barras, pilulas, botoes), tipografia
 * (fontes DISPLAY/LABEL/MONO do Rimulib {@code UiApi}, com o fonte vanilla como reserva) e o registro das regioes
 * interativas do quadro. Tudo em coordenadas locais do painel; a opacidade de revelacao ({@link #alpha}) multiplica
 * cada cor, entao o conteudo entra em cascata sem mexer no vidro.
 */
final class HoloCanvas {
    /** Tipos do Rimulib; {@code cap} = altura das maiusculas na escala 1 (medida no cliente real). */
    enum Type {
        DISPLAY(UiApi.Font.DISPLAY), LABEL(UiApi.Font.LABEL), MONO(UiApi.Font.MONO);

        final UiApi.Font font;
        Type(UiApi.Font font) { this.font = font; }
    }

    private static boolean rimulibText = true;

    final Minecraft mc = Minecraft.getInstance();
    final Font font = mc.font;
    GuiGraphics g;
    HoloSurface surface;
    HoloPanel panel;
    HoloCharacterScreen screen;
    /** Opacidade do elemento atual (revelacao em cascata). */
    float alpha = 1F;
    boolean hc;
    float opacity = 1F;
    float time, dt;
    private final Map<String, float[]> hover = new HashMap<>();

    void bind(GuiGraphics g, HoloPanel panel, HoloCharacterScreen screen, float time, float dt) {
        this.g = g;
        this.panel = panel;
        this.surface = panel.surface;
        this.screen = screen;
        this.time = time;
        this.dt = dt;
        this.alpha = 1F;
        this.hc = HoloConfig.highContrast();
        this.opacity = HoloConfig.opacity();
        resetOrigin();
    }

    /** Opacidade do elemento {@code index} da cascata de revelacao (multiplicada pela do conteudo do painel). */
    float stage(float since, int index) {
        alpha = panel.contentAlpha * reveal(since, index, 0.035F);
        return alpha;
    }

    // ---------------------------------------------------------------- cores

    int c(int argb) { return Ui.alpha(argb, alpha); }
    int text() { return HoloStyle.text(hc); }
    int muted() { return HoloStyle.muted(hc); }
    int dim() { return HoloStyle.dim(hc); }
    boolean visible() { return alpha > 0.025F; }

    /** Revelacao em cascata: elemento {@code index} entra {@code step} s depois do anterior. */
    float reveal(float since, int index, float step) {
        if (HoloConfig.reducedMotion()) return HoloAnim.window(since, 0F, 0.12F);
        return HoloAnim.outCubic(HoloAnim.window(since, index * step, 0.22F));
    }

    // ---------------------------------------------------------------- primitivas

    void fill(float x, float y, float w, float h, int argb) {
        int col = c(argb);
        if ((col >>> 24) == 0 || w <= 0 || h <= 0) return;
        g.fill(Mth.floor(x), Mth.floor(y), Mth.floor(x + w), Mth.floor(y + h), col);
    }

    void gradient(float x, float y, float w, float h, int top, int bottom) {
        if (w <= 0 || h <= 0 || !visible()) return;
        g.fillGradient(Mth.floor(x), Mth.floor(y), Mth.floor(x + w), Mth.floor(y + h), c(top), c(bottom));
    }

    void frame(float x, float y, float w, float h, int argb) {
        g.drawManaged(() -> {
            fill(x, y, w, 1, argb);
            fill(x, y + h - 1, w, 1, argb);
            fill(x, y + 1, 1, h - 2, argb);
            fill(x + w - 1, y + 1, 1, h - 2, argb);
        });
    }

    /** Cantoneiras em L nos quatro cantos (assinatura visual do menu). */
    void brackets(float x, float y, float w, float h, float len, int argb) {
        g.drawManaged(() -> {
            fill(x, y, len, 1, argb);
            fill(x, y, 1, len, argb);
            fill(x + w - len, y, len, 1, argb);
            fill(x + w - 1, y, 1, len, argb);
            fill(x, y + h - 1, len, 1, argb);
            fill(x, y + h - len, 1, len, argb);
            fill(x + w - len, y + h - 1, len, 1, argb);
            fill(x + w - 1, y + h - len, 1, len, argb);
        });
    }

    /** Brilho radial aditivo (soma luz sem furar o alfa do vidro). */
    void glow(float cx, float cy, float radius, int argb) {
        float a = ((argb >>> 24) / 255F) * alpha;
        if (a <= 0.01F || radius <= 0.5F) return;
        g.flush();
        HoloSurface.additive();
        g.setColor(((argb >> 16) & 255) / 255F, ((argb >> 8) & 255) / 255F, (argb & 255) / 255F, a);
        g.pose().pushPose();
        g.pose().translate(cx - radius, cy - radius, 0F);
        g.pose().scale(radius / 16F, radius / 16F, 1F);
        g.blit(Ui.GLOW, 0, 0, 0, 0, 32, 32, 32, 32);
        g.pose().popPose();
        g.setColor(1F, 1F, 1F, 1F);
        HoloSurface.accumulate();
    }

    /** Icone 128x128 do mod (habilidades/abas). */
    void icon(ResourceLocation tex, float x, float y, float size, float tint) {
        if (!visible()) return;
        HoloSurface.accumulate();
        g.setColor(tint, tint, tint, alpha);
        g.pose().pushPose();
        g.pose().translate(x, y, 0F);
        g.pose().scale(size / 16F, size / 16F, 1F);
        g.blit(tex, 0, 0, 16, 16, 0, 0, 128, 128, 128, 128);
        g.pose().popPose();
        g.setColor(1F, 1F, 1F, 1F);
    }

    /** Fundo de vidro do painel inteiro: gradiente, luz no topo, linhas de varredura e cantoneiras. */
    void glass(float w, float h, int accent) {
        float o = hc ? 1F : opacity;
        int top = hc ? HoloStyle.GLASS_HC : HoloStyle.GLASS_TOP;
        int bottom = hc ? HoloStyle.GLASS_HC : HoloStyle.GLASS_BOTTOM;
        float saved = alpha;
        alpha = 1F;
        gradient(0, 0, w, h, Ui.alpha(top, (hc ? 0.97F : 0.84F) * o), Ui.alpha(bottom, (hc ? 0.97F : 0.9F) * o));
        if (!hc) {
            gradient(0, 0, w, Math.min(26, h), Ui.alpha(accent, 0.16F * o), Ui.alpha(accent, 0F));
            g.drawManaged(() -> {
                for (float y = 3; y < h - 1; y += 3) fill(1, y, w - 2, 1, 0x07CFF4FF);
            });
        }
        frame(0, 0, w, h, Ui.alpha(accent, hc ? 0.8F : 0.38F));
        brackets(0, 0, w, h, Math.min(10, Math.min(w, h) / 4), Ui.alpha(accent, 0.95F));
        alpha = saved;
    }

    /** Faixa de varredura que desce pelo painel (desenhada sob o texto, nunca por cima). */
    void scan(float w, float h, float t, int accent) {
        if (hc || t < 0F || t > 1F) return;
        float y = -14 + (h + 28) * t;
        float saved = alpha;
        alpha = 1F;
        gradient(1, y - 10, w - 2, 10, Ui.alpha(accent, 0F), Ui.alpha(accent, 0.10F));
        fill(1, y, w - 2, 1, Ui.alpha(accent, 0.32F));
        gradient(1, y + 1, w - 2, 6, Ui.alpha(accent, 0.08F), Ui.alpha(accent, 0F));
        alpha = saved;
    }

    void hline(float x, float y, float w, int argb) { fill(x, y, w, 1, argb); }

    /** Divisoria com ponto luminoso: linha fina que some nas pontas. */
    void divider(float x, float y, float w, int accent) {
        fill(x, y, w * 0.1F, 1, Ui.alpha(accent, 0.12F));
        fill(x + w * 0.9F, y, w * 0.1F, 1, Ui.alpha(accent, 0.12F));
        fill(x + w * 0.1F, y, w * 0.8F, 1, Ui.alpha(accent, 0.38F));
    }

    // ---------------------------------------------------------------- texto

    void text(String s, float x, float y, int argb, boolean shadow) {
        if (s == null || s.isEmpty() || alpha < 0.03F) return;
        g.drawString(font, s, x, y, c(argb), shadow);
    }

    void text(Component s, float x, float y, int argb, boolean shadow) {
        text(s.getString(), x, y, argb, shadow);
    }

    void text(FormattedCharSequence s, float x, float y, int argb, boolean shadow) {
        if (alpha < 0.03F) return;
        g.drawString(font, s, x, y, c(argb), shadow);
    }

    void textRight(String s, float right, float y, int argb, boolean shadow) {
        text(s, right - font.width(s), y, argb, shadow);
    }

    void textCentered(String s, float cx, float y, int argb, boolean shadow) {
        text(s, cx - font.width(s) / 2F, y, argb, shadow);
    }

    /** Texto quebrado em {@code w}; desenha ate {@code maxLines} e devolve a altura usada. */
    int wrap(Component s, float x, float y, float w, int maxLines, int argb) {
        List<FormattedCharSequence> lines = font.split(s, Math.max(10, (int) w));
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) {
            if (i == n - 1 && n < lines.size()) {
                // ultima linha visivel com reticencias (a dica mostra o texto inteiro)
                StringBuilder rest = new StringBuilder();
                for (int j = i; j < lines.size(); j++) rest.append(toPlain(lines.get(j))).append(' ');
                text(Ui.fit(font, rest.toString().strip(), (int) w), x, y + i * 10, argb, false);
            } else {
                text(lines.get(i), x, y + i * 10, argb, false);
            }
        }
        return n * 10;
    }

    int lineCount(Component s, float w) { return font.split(s, Math.max(10, (int) w)).size(); }

    private static String toPlain(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    /**
     * Altura das maiusculas em unidades de GUI, medida no cliente real (GUI 2): o {@code UiApi.text} desenha com o
     * y na linha de base; LABEL/MONO ~6 e DISPLAY ~11 na escala 1.
     */
    static float capHeight(Type type, float size) { return (type == Type.DISPLAY ? 11F : 6F) * size; }

    /** Texto nas fontes do Rimulib com {@code y} no topo das maiusculas; {@code size} = escala. Devolve a largura. */
    float type(Type type, String s, float x, float y, float size, int argb) {
        if (s == null || s.isEmpty()) return 0F;
        if (rimulibText) {
            try {
                if (alpha < 0.03F) return UiApi.measure(g, type.font, s, size);
                return UiApi.text(g, type.font, s, x, y + capHeight(type, size), 0F, size, c(argb));
            } catch (RuntimeException | LinkageError e) {
                rimulibText = false;
                RespectCraft.LOGGER.warn("Rimulib UiApi text failed; holographic menu falls back to the vanilla font", e);
            }
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0F);
        float k = capHeight(type, size) / 7F;
        g.pose().scale(k, k, 1F);
        if (alpha >= 0.03F) g.drawString(font, s, 0, 0, c(argb), type == Type.DISPLAY);
        g.pose().popPose();
        return font.width(s) * k;
    }

    float measure(Type type, String s, float size) {
        if (s == null || s.isEmpty()) return 0F;
        if (rimulibText) {
            try {
                return UiApi.measure(g, type.font, s, size);
            } catch (RuntimeException | LinkageError e) {
                rimulibText = false;
            }
        }
        return font.width(s) * capHeight(type, size) / 7F;
    }

    /** Tamanho que faz {@code s} caber em {@code maxW} (no maximo {@code size}, no minimo {@code min}). */
    float fitSize(Type type, String s, float size, float min, float maxW) {
        float w = measure(type, s, size);
        if (w <= maxW || w <= 0) return size;
        return Math.max(min, size * maxW / w);
    }

    /** Etiqueta pequena em maiusculas (titulos de bloco). */
    float caption(String s, float x, float y, int argb) {
        return type(Type.LABEL, s.toUpperCase(java.util.Locale.ROOT), x, y, 1F, argb);
    }

    /** Pilula colorida com texto escuro/claro conforme o fundo. Devolve a largura. */
    float pill(String s, float x, float y, int bg) {
        float w = font.width(s) + 8;
        fill(x, y, w, 11, Ui.alpha(bg, 0.92F));
        fill(x, y + 11, w, 1, Ui.alpha(0xFF000000, 0.35F));
        text(s, x + 4, y + 2, Ui.readableOn(bg), false);
        return w;
    }

    float pillRight(String s, float right, float y, int bg) {
        float w = font.width(s) + 8;
        return pill(s, right - w, y, bg);
    }

    /** Barra segmentada (0..1) com brilho na ponta; {@code critical} pisca em vermelho. */
    void bar(float x, float y, float w, float h, float value, int color, int segments, boolean critical) {
        value = HoloAnim.clamp01(value);
        fill(x, y, w, h, 0xC0040A12);
        frame(x - 1, y - 1, w + 2, h + 2, Ui.alpha(color, 0.30F));
        float fillW = w * value;
        int col = critical ? Ui.lerpColor(color, HoloStyle.RED, 0.5F + 0.5F * Mth.sin(time * 9F)) : color;
        if (fillW > 0) {
            fill(x, y, fillW, h, Ui.alpha(col, 0.85F));
            fill(x, y, fillW, 1, Ui.lerpColor(col, 0xFFFFFFFF, 0.45F));
        }
        if (segments > 1) {
            float step = w / segments;
            g.drawManaged(() -> {
                for (int i = 1; i < segments; i++) fill(x + i * step, y, 1, h, 0x90040A12);
            });
        }
        if (fillW > 1) glow(x + fillW, y + h / 2F, h + 3, Ui.alpha(col, 0.55F));
    }

    // ---------------------------------------------------------------- interacao

    /** Origem do conteudo rolavel dentro do painel e faixa vertical visivel (para hover e regioes). */
    private float ox, oy, clipTop = -1e9F, clipBottom = 1e9F;

    void origin(float ox, float oy, float clipTop, float clipBottom) {
        this.ox = ox;
        this.oy = oy;
        this.clipTop = clipTop;
        this.clipBottom = clipBottom;
    }

    void resetOrigin() { origin(0F, 0F, -1e9F, 1e9F); }

    /** Converte uma coordenada do conteudo atual para o espaco local do painel. */
    float panelX(float x) { return x + ox; }
    float panelY(float y) { return y + oy; }

    boolean over(float x, float y, float w, float h) {
        if (!panel.hot) return false;
        float px = panel.cursorX - ox, py = panel.cursorY - oy;
        return panel.cursorY >= clipTop && panel.cursorY < clipBottom && px >= x && py >= y && px < x + w && py < y + h;
    }

    HoloZone zone(float x, float y, float w, float h) {
        float top = Math.max(y + oy, clipTop), bottom = Math.min(y + oy + h, clipBottom);
        return panel.addZone(x + ox, top, w, Math.max(0F, bottom - top));
    }

    /** 0..1 suavizado por chave (hover animado sem estado nos widgets). */
    float hoverAnim(String key, boolean on) {
        float[] v = hover.computeIfAbsent(key, k -> new float[1]);
        if (HoloConfig.reducedMotion()) v[0] = on ? 1F : 0F;
        else v[0] = HoloAnim.approach(v[0], on ? 1F : 0F, on ? 18F : 10F, dt);
        return v[0];
    }

    /** Pede a dica para este quadro (so vale se este painel estiver sob o cursor). */
    void tooltip(List<Component> lines) {
        if (panel.hot) screen.setHoloTooltip(lines);
    }

    /**
     * Botao holografico: fundo de vidro, barra de acento, deslize no hover, icone e selo. Registra o clique no
     * quadro. Devolve true se o cursor estiver em cima.
     */
    boolean button(String key, String label, float x, float y, float w, float h, ResourceLocation icon, int accent,
                   boolean enabled, boolean selected, String badge, Runnable action) {
        HoloZone z = zone(x, y, w, h);
        z.enabled = enabled;
        z.click = enabled ? action : null;
        boolean hot = enabled && over(x, y, w, h);
        float k = hoverAnim(key, hot);
        float s = hoverAnim(key + "#sel", selected);
        int base = selected ? Ui.alpha(accent, 0.20F + 0.08F * k) : Ui.alpha(accent, 0.05F + 0.13F * k);
        fill(x, y, w, h, enabled ? base : 0x30101820);
        frame(x, y, w, h, Ui.alpha(enabled ? accent : 0xFF55657A, 0.18F + 0.45F * Math.max(k, s)));
        if (s > 0.01F) {
            fill(x, y + 1, 2, h - 2, Ui.alpha(accent, s));
            glow(x + 1, y + h / 2F, h * 0.9F, Ui.alpha(accent, 0.45F * s));
        }
        float slide = 2F * k;
        float cx = x + 6 + slide;
        if (icon != null) {
            float size = Math.min(12, h - 4);
            icon(icon, cx, y + (h - size) / 2F, size, enabled ? 1F : 0.4F);
            cx += size + 4;
        }
        int textColor = !enabled ? dim() : selected ? 0xFFFFFFFF : Ui.lerpColor(muted(), text(), Math.max(k, 0.25F));
        float badgeW = badge == null || badge.isEmpty() ? 0 : font.width(badge) + 8;
        String shown = Ui.fit(font, label, (int) (x + w - cx - 4 - badgeW));
        if (icon == null && badgeW == 0) text(shown, x + (w - font.width(shown)) / 2F + slide, y + (h - 8) / 2F, textColor, false);
        else text(shown, cx, y + (h - 8) / 2F, textColor, false);
        if (badgeW > 0) pillRight(badge, x + w - 3, y + (h - 11) / 2F, accent);
        return hot;
    }
}
