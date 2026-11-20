package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Painel modal "Display": tamanho, opacidade do vidro, intensidade das animacoes, movimento reduzido, alto
 * contraste e fundo do Rimulib. Captura toda a entrada enquanto aberto; grava o arquivo de config ao fechar.
 */
final class HoloSettings {
    private static final String KEY = "gui.respectcraft.holo.settings.";
    private final HoloMenu menu;

    HoloSettings(HoloMenu menu) { this.menu = menu; }

    void draw(HoloCanvas c, float w, float h) {
        c.glass(w, h, HoloStyle.VIOLET);
        c.alpha = c.panel.contentAlpha;
        c.type(HoloCanvas.Type.LABEL, HoloView.tr(KEY + "title").toUpperCase(Locale.ROOT), 10F, 9F, 1.25F, c.text());
        c.divider(8F, 22F, w - 16F, HoloStyle.VIOLET);
        float y = 28F;
        float x = 12F, sw = w - 24F;
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
        y += 17F;
        c.alpha = c.panel.contentAlpha;
        float doneY = h - 20F;
        if (y + 10 <= doneY - 2) c.wrap(menu.world ? Component.translatable(KEY + "hint_world",
                com.respectcraft.client.character.CharacterCreatorKeybinds.recallKeyName()) : Component.translatable(KEY + "hint"), x, y, sw, Math.max(1, (int) ((doneY - y - 2) / 10)), c.dim());
        c.button("settings.done", HoloView.tr(KEY + "done"), w / 2F - 40F, doneY, 80F, 14F, null, HoloStyle.VIOLET, true, false, "",
                menu::closeSettings);
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
