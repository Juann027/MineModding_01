package com.tupinicraft.client;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Faixa de avisos eleitorais no topo da tela (substitui o chat para os anúncios da eleição).
 * Os avisos entram numa fila e são exibidos um de cada vez. A posição vem de {@link AnnouncementLayout}:
 * nunca cobre os painéis do canto superior direito ({@link PoliticalHud}) nem as barras de chefe.
 */
public final class AnnouncementOverlay {
    private static final long DISPLAY_MILLIS = 5000L;
    private static final long FAST_DISPLAY_MILLIS = 2500L;
    private static final int MAX_QUEUE = 10;
    private static final int MAX_TEXT_WIDTH = 280;
    private static final int PADDING = 5;

    private static final Deque<Component> QUEUE = new ArrayDeque<>();
    private static Component current;
    private static long shownAtMillis;

    private AnnouncementOverlay() {
    }

    static void push(Component message) {
        if (QUEUE.size() >= MAX_QUEUE) {
            QUEUE.pollFirst();
        }
        QUEUE.addLast(message);
    }

    static void clear() {
        QUEUE.clear();
        current = null;
    }

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        // Lido em todo frame (mesmo sem aviso) para não reaproveitar a medida de um frame antigo.
        int bossBarBottom = BossBarArea.consumeBottom();
        long now = Util.getMillis();
        long duration = QUEUE.isEmpty() ? DISPLAY_MILLIS : FAST_DISPLAY_MILLIS;
        if (current != null && now - shownAtMillis > duration) {
            current = null;
        }
        if (current == null) {
            current = QUEUE.pollFirst();
            shownAtMillis = now;
        }
        if (current == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Component message = current;
        AnnouncementLayout.Placement placement = AnnouncementLayout.place(
                graphics.guiWidth(), graphics.guiHeight(), bossBarBottom,
                PoliticalHud.panelBounds(minecraft, graphics.guiWidth()),
                MAX_TEXT_WIDTH, PADDING,
                maxWidth -> boxSize(font, font.split(message, maxWidth)));
        List<FormattedCharSequence> lines = font.split(message, placement.maxTextWidth());
        AnnouncementLayout.Rect box = placement.box();
        int x = box.left();
        int y = box.top();
        int boxWidth = box.width();
        int boxHeight = box.height();

        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xB0000000);
        graphics.fill(x, y, x + boxWidth, y + 1, 0xFFFFDF00);
        graphics.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF009C3B);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int lineX = x + (boxWidth - font.width(line)) / 2;
            graphics.drawString(font, line, lineX, y + PADDING + i * font.lineHeight, 0xFFFFFFFF, true);
        }
    }

    private static int[] boxSize(Font font, List<FormattedCharSequence> lines) {
        int textWidth = 0;
        for (FormattedCharSequence line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        return new int[]{textWidth + PADDING * 2, lines.size() * font.lineHeight + PADDING * 2};
    }
}
