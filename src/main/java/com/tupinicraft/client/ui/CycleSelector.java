package com.tupinicraft.client.ui;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * Seletor "< valor >": clique nas setas (ou na roda do mouse) troca a opcao. Mostra amostras de cor opcionais.
 */
public class CycleSelector extends AbstractWidget {
    private final Component label;
    private final int count;
    private final IntFunction<Component> names;
    private final IntConsumer onChange;
    private IntFunction<Integer> swatch;
    private int index;

    public CycleSelector(int x, int y, int w, int h, Component label, int count, int index,
                         IntFunction<Component> names, IntConsumer onChange) {
        super(x, y, w, h, label);
        this.label = label;
        this.count = count;
        this.index = index;
        this.names = names;
        this.onChange = onChange;
    }

    /** Cor de amostra por opcao (cabelo, pele). */
    public CycleSelector swatches(IntFunction<Integer> colors) { this.swatch = colors; return this; }

    public int index() { return index; }

    public void set(int value) {
        index = Math.floorMod(value, count);
        onChange.accept(index);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        set(mouseX < getX() + getWidth() / 3.0 ? index - 1 : index + 1);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollY == 0) return false;
        set(index + (scrollY > 0 ? -1 : 1));
        Ui.playClick(1.3F);
        return true;
    }

    @Override
    public void playDownSound(SoundManager manager) { Ui.playClick(1.1F); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = isHoveredOrFocused();
        Ui.nineSlice(g, Ui.BUTTON, x, y, w, h, 0, (hot ? 1 : 0) * 20, 64, 20, 4, 64, 80);
        g.drawString(font, label, x + 6, y + (h - 8) / 2, Ui.MUTED, false);
        Component value = names.apply(index);
        int arrowsW = 10;
        int right = x + w - 6;
        boolean leftHot = hot && mouseX < x + w / 3;
        g.drawString(font, "<", right - arrowsW * 2 - font.width(value) - 8, y + (h - 8) / 2, leftHot ? Ui.GOLD : Ui.TEXT, false);
        g.drawString(font, ">", right - font.width(">"), y + (h - 8) / 2, hot && !leftHot ? Ui.GOLD : Ui.TEXT, false);
        int valueX = right - arrowsW - font.width(value) - 4;
        g.drawString(font, value, valueX, y + (h - 8) / 2, Ui.TEXT, true);
        if (swatch != null) {
            int sx = right - arrowsW * 2 - font.width(value) - 22;
            g.fill(sx, y + 4, sx + 10, y + h - 4, 0xFF06080A);
            g.fill(sx + 1, y + 5, sx + 9, y + h - 5, swatch.apply(index));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.empty().append(label).append(": ").append(names.apply(index)));
    }
}
