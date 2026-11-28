package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.NoticeClientCache;
import com.respectcraft.notice.HoloNotice;
import com.respectcraft.notice.NoticeKind;
import com.respectcraft.notice.NoticePresentation;
import com.respectcraft.notice.NoticeStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Painel modal "Display": tamanho, opacidade do vidro, intensidade das animacoes, movimento reduzido, alto
 * contraste e fundo do Rimulib. Captura toda a entrada enquanto aberto; grava o arquivo de config ao fechar.
 *
 * <p>Etapa 4.14.1: paginas. "Menu" (as opcoes de antes), "Notices" (janela cinematografica e cartoes: ligar,
 * escala, posicao, opacidade, volume, duracao padrao, animacao, efeitos, movimento reduzido, alto contraste e um
 * exemplo local com o botao Preview) e, na Etapa 4.14.3, "Tracker" (rastreador de missoes).
 */
final class HoloSettings {
    private static final String KEY = "gui.respectcraft.holo.settings.";
    private final HoloMenu menu;

    enum Page { MENU, NOTICES, TRACKER }

    private Page page = Page.MENU;
    private int previewIndex;

    HoloSettings(HoloMenu menu) { this.menu = menu; }

    void draw(HoloCanvas c, float w, float h) {
        c.glass(w, h, HoloStyle.VIOLET);
        c.alpha = c.panel.contentAlpha;
        c.type(HoloCanvas.Type.LABEL, HoloView.tr(KEY + "title").toUpperCase(Locale.ROOT), 10F, 9F, 1.25F, c.text());
        // abas das paginas, a direita do titulo
        float tx = w - 8F;
        Page[] pages = Page.values();
        for (int i = pages.length - 1; i >= 0; i--) {
            Page p = pages[i];
            String label = HoloView.tr(KEY + "page." + p.name().toLowerCase(Locale.ROOT));
            float bw = c.font.width(label) + 8F;
            tx -= bw;
            c.button("settings.page." + p.name(), label, tx, 6F, bw, 12F, null, HoloStyle.VIOLET, true, page == p, "", () -> {
                page = p;
                Ui.playClick(1.2F);
            });
            tx -= 3F;
        }
        c.divider(8F, 22F, w - 16F, HoloStyle.VIOLET);
        float x = 12F, sw = w - 24F;
        switch (page) {
            case NOTICES -> notices(c, x, 27F, sw, w, h);
            case TRACKER -> tracker(c, x, 27F, sw, w, h);
            default -> menuPage(c, x, 28F, sw, w, h);
        }
    }

    private void menuPage(HoloCanvas c, float x, float y, float sw, float w, float h) {
        slider(c, "scale", x, y, sw, (HoloConfig.scale() - HoloConfig.MIN_SCALE) / (HoloConfig.MAX_SCALE - HoloConfig.MIN_SCALE),
                Math.round(HoloConfig.scale() * 100) + "%",
                v -> HoloConfig.setScale(HoloConfig.MIN_SCALE + v * (HoloConfig.MAX_SCALE - HoloConfig.MIN_SCALE)), true);
        y += 25F;
        slider(c, "opacity", x, y, sw, (HoloConfig.opacity() - HoloConfig.MIN_OPACITY) / (1F - HoloConfig.MIN_OPACITY),
                Math.round(HoloConfig.opacity() * 100) + "%",
                v -> HoloConfig.setOpacity(HoloConfig.MIN_OPACITY + v * (1F - HoloConfig.MIN_OPACITY)), false);
        y += 25F;
        slider(c, "motion", x, y, sw, HoloConfig.rawMotion(), Math.round(HoloConfig.rawMotion() * 100) + "%",
                HoloConfig::setMotion, false);
        y += 27F;
        toggle(c, "reduced", x, y, sw, HoloConfig.reducedMotion(), HoloConfig::toggleReducedMotion);
        y += 15F;
        toggle(c, "contrast", x, y, sw, HoloConfig.highContrast(), HoloConfig::toggleHighContrast);
        y += 15F;
        toggle(c, "backdrop", x, y, sw, HoloConfig.worldBackdrop(), HoloConfig::toggleBackdrop);
        y += 15F;
        // Etapa 4.11: cartoes dos avisos automaticos na tela (o historico continua na secao Alerts).
        toggle(c, "toasts", x, y, sw, HoloConfig.noticeToasts(), HoloConfig::toggleNoticeToasts);
        y += 17F;
        c.alpha = c.panel.contentAlpha;
        float doneY = h - 20F;
        if (y + 10 <= doneY - 2) c.wrap(menu.world ? Component.translatable(KEY + "hint_world",
                com.respectcraft.client.character.CharacterCreatorKeybinds.recallKeyName()) : Component.translatable(KEY + "hint"), x, y, sw, Math.max(1, (int) ((doneY - y - 2) / 10)), c.dim());
        done(c, w, h);
    }

    private void done(HoloCanvas c, float w, float h) {
        c.alpha = c.panel.contentAlpha;
        c.button("settings.done", HoloView.tr(KEY + "done"), w / 2F - 40F, h - 20F, 80F, 14F, null, HoloStyle.VIOLET, true, false, "",
                menu::closeSettings);
    }

    /** Etapa 4.14.1: janela cinematografica e cartoes. */
    private void notices(HoloCanvas c, float x, float y, float sw, float w, float h) {
        float row = 13F;
        toggle(c, "cinematic", x, y, sw, HoloConfig.cinematic(), () -> HoloConfig.toggle(HoloConfig.CINEMATIC));
        y += row;
        toggle(c, "toasts", x, y, sw, HoloConfig.noticeToasts(), HoloConfig::toggleNoticeToasts);
        y += row + 1F;
        compact(c, "cin_scale", x, y, sw, norm(HoloConfig.cinematicScale(), HoloConfig.MIN_CINEMATIC_SCALE, HoloConfig.MAX_CINEMATIC_SCALE),
                pct(HoloConfig.cinematicScale()), v -> HoloConfig.setDouble(HoloConfig.CINEMATIC_SCALE,
                        HoloConfig.MIN_CINEMATIC_SCALE + v * (HoloConfig.MAX_CINEMATIC_SCALE - HoloConfig.MIN_CINEMATIC_SCALE)));
        y += row;
        compact(c, "cin_x", x, y, sw, HoloConfig.cinematicX(), pct(HoloConfig.cinematicX()), v -> HoloConfig.setDouble(HoloConfig.CINEMATIC_X, v));
        y += row;
        compact(c, "cin_y", x, y, sw, HoloConfig.cinematicY(), pct(HoloConfig.cinematicY()), v -> HoloConfig.setDouble(HoloConfig.CINEMATIC_Y, v));
        y += row;
        compact(c, "cin_opacity", x, y, sw, norm(HoloConfig.cinematicOpacity(), HoloConfig.MIN_OPACITY, 1F), pct(HoloConfig.cinematicOpacity()),
                v -> HoloConfig.setDouble(HoloConfig.CINEMATIC_OPACITY, HoloConfig.MIN_OPACITY + v * (1F - HoloConfig.MIN_OPACITY)));
        y += row;
        compact(c, "volume", x, y, sw, HoloConfig.rawNoticeVolume(), pct(HoloConfig.rawNoticeVolume()),
                v -> HoloConfig.setDouble(HoloConfig.NOTICE_VOLUME, v));
        y += row;
        compact(c, "cin_seconds", x, y, sw, norm(HoloConfig.cinematicSeconds(), 1F, 10F),
                String.format(Locale.ROOT, "%.1f s", HoloConfig.cinematicSeconds()),
                v -> HoloConfig.setDouble(HoloConfig.CINEMATIC_SECONDS, 1F + v * 9F));
        y += row;
        compact(c, "cin_anim", x, y, sw, HoloConfig.rawCinematicAnim(), pct(HoloConfig.rawCinematicAnim()),
                v -> HoloConfig.setDouble(HoloConfig.CINEMATIC_ANIM, v));
        y += row;
        compact(c, "cin_fx", x, y, sw, HoloConfig.rawCinematicFx(), pct(HoloConfig.rawCinematicFx()),
                v -> HoloConfig.setDouble(HoloConfig.CINEMATIC_FX, v));
        y += row + 1F;
        toggle(c, "reduced", x, y, sw, HoloConfig.reducedMotion(), HoloConfig::toggleReducedMotion);
        y += row;
        toggle(c, "contrast", x, y, sw, HoloConfig.highContrast(), HoloConfig::toggleHighContrast);
        c.alpha = c.panel.contentAlpha;
        float by = h - 20F;
        c.button("settings.preview", HoloView.tr(KEY + "preview"), 12F, by, 70F, 14F, null, HoloStyle.CYAN, true, false, "",
                this::preview);
        c.button("settings.done", HoloView.tr(KEY + "done"), w - 12F - 70F, by, 70F, 14F, null, HoloStyle.VIOLET, true, false, "",
                menu::closeSettings);
    }

    /** Etapa 4.14.3: rastreador de missoes na HUD. */
    private void tracker(HoloCanvas c, float x, float y, float sw, float w, float h) {
        float row = 14F;
        toggle(c, "tracker", x, y, sw, HoloConfig.tracker(), () -> HoloConfig.toggle(HoloConfig.TRACKER));
        y += row + 2F;
        compact(c, "trk_scale", x, y, sw, norm(HoloConfig.trackerScale(), HoloConfig.MIN_CINEMATIC_SCALE, HoloConfig.MAX_CINEMATIC_SCALE),
                pct(HoloConfig.trackerScale()), v -> HoloConfig.setDouble(HoloConfig.TRACKER_SCALE,
                        HoloConfig.MIN_CINEMATIC_SCALE + v * (HoloConfig.MAX_CINEMATIC_SCALE - HoloConfig.MIN_CINEMATIC_SCALE)));
        y += row;
        compact(c, "trk_x", x, y, sw, HoloConfig.trackerX(), pct(HoloConfig.trackerX()), v -> HoloConfig.setDouble(HoloConfig.TRACKER_X, v));
        y += row;
        compact(c, "trk_y", x, y, sw, HoloConfig.trackerY(), pct(HoloConfig.trackerY()), v -> HoloConfig.setDouble(HoloConfig.TRACKER_Y, v));
        y += row;
        compact(c, "trk_opacity", x, y, sw, norm(HoloConfig.trackerOpacity(), 0.3F, 1F), pct(HoloConfig.trackerOpacity()),
                v -> HoloConfig.setDouble(HoloConfig.TRACKER_OPACITY, 0.3F + v * 0.7F));
        y += row + 4F;
        c.alpha = c.panel.contentAlpha;
        float doneY = h - 20F;
        if (y + 10 <= doneY - 2) c.wrap(Component.translatable(KEY + "tracker_hint"), x, y, sw, Math.max(1, (int) ((doneY - y - 2) / 10)), c.dim());
        done(c, w, h);
    }

    private static final NoticeStyle[] PREVIEW = {NoticeStyle.SECRET_DISCOVERED, NoticeStyle.QUEST_DISCOVERED,
            NoticeStyle.OBJECTIVE_UPDATED, NoticeStyle.QUEST_COMPLETED, NoticeStyle.REWARD_AVAILABLE, NoticeStyle.WARNING,
            NoticeStyle.DANGER, NoticeStyle.PORTAL_AVAILABLE, NoticeStyle.MYSTERIOUS, NoticeStyle.QUEST_FAILED, NoticeStyle.SYSTEM_INFO};

    /** Exemplo so no cliente (nada vai ao servidor nem ao historico); cada clique mostra outro estilo. */
    private void preview() {
        NoticeStyle style = PREVIEW[previewIndex++ % PREVIEW.length];
        String k = "notice.respectcraft.preview.";
        NoticeClientCache.preview(HoloNotice.cinematic(NoticeKind.QUEST, style, Component.translatable(k + "title"),
                        Component.translatable(k + "body"))
                .subtitle(Component.translatable(style.translationKey()))
                .present(HoloConfig.cinematic() ? NoticePresentation.CINEMATIC : NoticePresentation.TOAST));
        Ui.playClick(1.0F);
    }

    private static float norm(float v, float min, float max) { return (v - min) / (max - min); }

    private static String pct(float v) { return Math.round(v * 100) + "%"; }

    /** Linha unica: nome a esquerda, trilho a direita, valor no fim. */
    private void compact(HoloCanvas c, String id, float x, float y, float w, float value, String shown, Consumer<Float> set) {
        value = HoloAnim.clamp01(value);
        float labelW = w * 0.42F, valW = 30F;
        float tx = x + labelW, tw = w - labelW - valW - 4F;
        boolean hot = c.over(x - 2, y - 2, w + 4, 12);
        float k = c.hoverAnim("slider." + id, hot);
        c.text(Ui.fit(c.font, HoloView.tr(KEY + id), (int) labelW - 4), x, y + 1, Ui.lerpColor(c.muted(), c.text(), k), false);
        float ty = y + 4F;
        c.fill(tx, ty, tw, 2F, 0x60FFFFFF);
        c.fill(tx, ty, tw * value, 2F, HoloStyle.VIOLET);
        float kx = tx + tw * value;
        c.glow(kx, ty + 1F, 4F + 2F * k, Ui.alpha(HoloStyle.VIOLET, 0.6F + 0.3F * k));
        c.fill(kx - 1.5F, ty - 2.5F, 3F, 7F, Ui.lerpColor(0xFFE6DAFF, 0xFFFFFFFF, k));
        c.type(HoloCanvas.Type.MONO, shown, x + w - c.measure(HoloCanvas.Type.MONO, shown, 1F), y + 1F, 1F, HoloStyle.CYAN_SOFT);
        HoloZone z = c.zone(tx - 3F, y - 2F, tw + 6F, 12F);
        float zx = c.panelX(tx);
        z.drag = new HoloZone.Drag() {
            @Override public void press(float px, float py) { apply(px); }
            @Override public void move(float px, float py) { apply(px); }
            @Override public void release(float px, float py, boolean click) {
                apply(px);
                Ui.playClick(1.1F);
            }
            private void apply(float px) {
                set.accept(HoloAnim.clamp01((px - zx) / tw));
                menu.settingsChanged();
            }
        };
        if (hot) c.tooltip(List.of(Component.translatable(KEY + id).withStyle(ChatFormatting.BOLD),
                Component.translatable(KEY + id + ".desc").withStyle(ChatFormatting.GRAY)));
    }

    private void slider(HoloCanvas c, String id, float x, float y, float w, float value, String shown, Consumer<Float> set,
                        boolean relayoutOnRelease) {
        value = HoloAnim.clamp01(value);
        c.text(HoloView.tr(KEY + id), x, y, c.muted(), false);
        c.type(HoloCanvas.Type.MONO, shown, x + w - c.measure(HoloCanvas.Type.MONO, shown, 1.15F), y, 1.15F, HoloStyle.CYAN_SOFT);
        float ty = y + 12F;
        boolean hot = c.over(x - 2, ty - 4, w + 4, 10);
        float k = c.hoverAnim("slider." + id, hot);
        c.fill(x, ty, w, 2F, 0x60FFFFFF);
        c.fill(x, ty, w * value, 2F, HoloStyle.VIOLET);
        float kx = x + w * value;
        c.glow(kx, ty + 1F, 6F + 3F * k, Ui.alpha(HoloStyle.VIOLET, 0.6F + 0.3F * k));
        c.fill(kx - 2F, ty - 3F, 4F, 8F, Ui.lerpColor(0xFFE6DAFF, 0xFFFFFFFF, k));
        for (int i = 0; i <= 4; i++) c.fill(x + w * i / 4F, ty + 4F, 1F, 2F, 0x50FFFFFF);
        HoloZone z = c.zone(x - 3F, ty - 5F, w + 6F, 12F);
        float zx = c.panelX(x);
        z.drag = new HoloZone.Drag() {
            @Override public void press(float px, float py) { apply(px); }
            @Override public void move(float px, float py) { apply(px); }
            @Override public void release(float px, float py, boolean click) {
                apply(px);
                Ui.playClick(1.1F);
                if (relayoutOnRelease) menu.relayout();
            }
            private void apply(float px) {
                set.accept(HoloAnim.clamp01((px - zx) / w));
                menu.settingsChanged();
            }
        };
        if (hot) c.tooltip(List.of(Component.translatable(KEY + id).withStyle(ChatFormatting.BOLD),
                Component.translatable(KEY + id + ".desc").withStyle(ChatFormatting.GRAY)));
    }

    private void toggle(HoloCanvas c, String id, float x, float y, float w, boolean on, Runnable flip) {
        boolean hot = c.over(x - 2, y - 2, w + 4, 13);
        float k = c.hoverAnim("toggle." + id, hot);
        if (k > 0.01F) c.fill(x - 2, y - 2, w + 4, 13, Ui.alpha(HoloStyle.VIOLET, 0.12F * k));
        c.text(HoloView.tr(KEY + id), x, y + 1, Ui.lerpColor(c.muted(), c.text(), k), false);
        String state = HoloView.tr(KEY + (on ? "on" : "off"));
        c.pillRight(state, x + w, y - 1F, on ? HoloStyle.GREEN : 0xFF3A4654);
        HoloZone z = c.zone(x - 2, y - 2, w + 4, 13);
        z.click = () -> {
            flip.run();
            menu.settingsChanged();
        };
        if (hot) c.tooltip(List.of(Component.translatable(KEY + id).withStyle(ChatFormatting.BOLD),
                Component.translatable(KEY + id + ".desc").withStyle(ChatFormatting.GRAY)));
    }
}
