package com.tupinicraft.client;

import com.tupinicraft.network.AnnouncementPayload;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded global-event queue. All visual effects remain inside the measured card. */
public final class AnnouncementOverlay {
    private static final Deque<AnnouncementPayload> QUEUE = new ArrayDeque<>();
    private static AnnouncementPayload current;
    private static long shownAt;
    private static int duration;
    private static long lastFrame;
    private AnnouncementOverlay() {}
    static void push(AnnouncementPayload event) {
        if (QUEUE.size() == 10) QUEUE.removeFirst();
        QUEUE.addLast(event);
    }
    static void clear() { QUEUE.clear(); current = null; lastFrame = 0; }
    static void render(GuiGraphics g, DeltaTracker delta) {
        int bossBottom = BossBarArea.consumeBottom();
        var mc = Minecraft.getInstance();
        if (mc.player == null) { clear(); return; }
        long now = Util.getMillis();
        long elapsed = lastFrame == 0 ? 0 : Math.max(0, now - lastFrame);
        lastFrame = now;
        if (mc.options.hideGui || mc.screen != null) {
            if (current != null) shownAt += elapsed;
            return;
        }
        if (current != null && now - shownAt >= duration) current = null;
        if (current == null && !QUEUE.isEmpty()) {
            current = QUEUE.removeFirst(); shownAt = now;
            // Fixed for this card: new arrivals cannot abruptly shorten its animation.
            duration = QUEUE.isEmpty() ? current.durationMillis() : Math.min(4000, current.durationMillis());
        }
        if (current == null || mc.options.hideGui || mc.screen != null) return;
        var font = mc.font;
        final var event = current;
        var placement = AnnouncementLayout.place(g.guiWidth(), g.guiHeight(), bossBottom,
            PoliticalHud.panelBounds(mc, g.guiWidth()), 286, 12,
            maxWidth -> new int[]{maxWidth + 24, 38 + Math.min(5, font.split(event.message(), maxWidth).size()) * 10});
        var box = placement.box();
        int x = box.left(), y = box.top(), w = box.width(), h = box.height();
        if (y + h > g.guiHeight() - 42) { shownAt += elapsed; return; } // Never cover the hotbar on overcrowded HUDs.
        var lines = font.split(event.message(), placement.maxTextWidth());
        int pages = Math.max(1, (lines.size() + 4) / 5);
        duration = Math.max(duration, pages * 3500);
        int page = Math.min(pages - 1, (int)((now - shownAt) / 3500));
        float enter = Math.min(1F, (now - shownAt) / 360F);
        float leave = Math.min(1F, (duration - (now - shownAt)) / 280F);
        float reveal = Math.min(1F - (float)Math.pow(1F - enter, 3), leave);
        int visible = Math.max(1, Math.round(w * reveal));
        g.enableScissor(x + (w - visible) / 2, y, x + (w + visible) / 2, y + h);
        CivicUi.panel(g, x, y, w, h);
        g.fill(x, y, x + w, y + 2, event.accent());
        CivicUi.seal(g, x + 12, y + 9, event.accent());
        g.drawString(font, CivicUi.fit(event.title().getString(), w - 48), x + 34, y + 12, event.accent(), false);
        for (int i = page * 5; i < Math.min((page + 1) * 5, lines.size()); i++)
            g.drawString(font, lines.get(i), x + 12, y + 30 + (i % 5) * 10, CivicUi.TEXT, false);
        // Gentle moving scan line and deterministic sparks: no allocations or world particles.
        int scan = (int)((now - shownAt) / 14 % w);
        g.fill(x + scan, y + 3, x + scan + 1, y + h - 4, 0x126FE0B7);
        for (int i = 0; i < 9; i++) {
            int px = x + 4 + (int)((i * 37L + (now - shownAt) / 45) % Math.max(1, w - 8));
            int py = y + h - 6 - i % 3;
            g.fill(px, py, px + 1, py + 1, event.accent());
        }
        int remaining = Math.round((w - 4) * Math.max(0F, 1F - (now - shownAt) / (float)duration));
        g.fill(x + 2, y + h - 2, x + 2 + remaining, y + h, event.accent());
        g.disableScissor();
    }
}
