package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Conteudo de uma secao do painel principal: cabecalho fixo e corpo com rolagem (recortado na superficie). As
 * secoes so leem as caches do cliente e pedem acoes ao servidor; nenhuma guarda dado de jogo proprio.
 */
abstract class HoloView {
    static final float HEADER = 26F;

    final HoloCharacterScreen screen;
    private final HoloCharacterScreen.Section section;
    private float scroll, target, contentHeight, viewportHeight;

    HoloView(HoloCharacterScreen screen, HoloCharacterScreen.Section section) {
        this.screen = screen;
        this.section = section;
    }

    HoloCharacterScreen.Section section() { return section; }

    /** Cor do painel (borda, cantoneiras, varredura). */
    int accent() { return section.accent; }

    void onEnter() {
        scroll = target = 0F;
    }

    /** Secoes estreitas encolhem o painel ate o conteudo; Skills e Character usam o retangulo inteiro. */
    boolean fitsContent() { return true; }

    /** Altura de painel que mostraria todo o conteudo (0 = ainda nao medido). */
    float desiredHeight() { return contentHeight <= 0F ? 0F : HEADER + 3F + contentHeight + 7F; }

    void tick() {}

    /** Setas esquerda/direita (ex.: trocar o ramo da arvore). */
    void arrow(int dir) {}

    /** Roda do mouse sobre o painel; devolve true se rolou. */
    boolean scrollBy(double amount) {
        float max = Math.max(0F, contentHeight - viewportHeight);
        if (max <= 0F) return false;
        target = Math.max(0F, Math.min(max, target - (float) amount * 16F));
        return true;
    }

    void draw(HoloCanvas c, float w, float h, float since) {
        c.glass(w, h, accent());
        if (HoloConfig.motion() > 0F) c.scan(w, h, ((c.time * 0.16F) % 1.4F) / 1.2F, accent());
        c.alpha = c.panel.contentAlpha;
        header(c, w, since);
        float top = HEADER + 3F, bottom = h - 5F;
        viewportHeight = bottom - top;
        scroll = HoloAnim.approach(scroll, target, 16F, c.dt);
        c.surface.scissor(1F, top, w - 2F, viewportHeight);
        c.g.pose().pushPose();
        c.g.pose().translate(0F, top - scroll, 0F);
        c.origin(0F, top - scroll, top, bottom);
        try {
            contentHeight = body(c, w, viewportHeight, since);
        } finally {
            c.resetOrigin();
            c.g.pose().popPose();
            c.surface.unscissor();
        }
        float max = Math.max(0F, contentHeight - viewportHeight);
        if (target > max) target = max;
        if (max > 0F) {
            // barra de rolagem fina na borda direita
            float barH = Math.max(14F, viewportHeight * viewportHeight / contentHeight);
            float barY = top + (viewportHeight - barH) * (scroll / max);
            c.alpha = c.panel.contentAlpha;
            c.fill(w - 4F, top, 1F, viewportHeight, Ui.alpha(accent(), 0.15F));
            c.fill(w - 5F, barY, 3F, barH, Ui.alpha(accent(), 0.7F));
        }
    }

    /** Numero da secao (fonte MONO), titulo (LABEL) e uma informacao opcional a direita. */
    void header(HoloCanvas c, float w, float since) {
        float k = c.reveal(since, 0, 0F);
        c.alpha = c.panel.contentAlpha * k;
        String index = String.format(Locale.ROOT, "%02d", section.ordinal() + 1);
        float x = 10F;
        x += c.type(HoloCanvas.Type.MONO, index, x, 10F, 1.15F, accent()) + 7F;
        c.fill(x - 4F, 8F, 1F, 11F, Ui.alpha(accent(), 0.5F));
        String title = Component.translatable(section.key()).getString().toUpperCase(Locale.ROOT);
        float right = headerRight(c, w);
        float size = c.fitSize(HoloCanvas.Type.LABEL, title, 1.3F, 0.8F, right - x - 8F);
        c.type(HoloCanvas.Type.LABEL, title, x, 10F, size, c.text());
        c.alpha = c.panel.contentAlpha;
        c.divider(8F, HEADER - 2F, w - 16F, accent());
    }

    /** Desenha algo no canto direito do cabecalho e devolve a borda esquerda ocupada. */
    float headerRight(HoloCanvas c, float w) { return w - 8F; }

    /** Corpo rolavel (y = 0 no topo da area); devolve a altura usada. */
    abstract float body(HoloCanvas c, float w, float viewportHeight, float since);

    static String tr(String key, Object... args) { return Component.translatable(key, args).getString(); }
}
