package com.tupinicraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Shared, texture-free visual language for civic screens and global events. */
public final class CivicUi {
    public static final int INK = 0xF510211F, JADE = 0xFF70E0B7, GOLD = 0xFFECC879;
    public static final int TEXT = 0xFFF1F5E9, MUTED = 0xFF9BB9AF;
    private CivicUi() {}
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x44000000);
        g.fill(x, y, x + w, y + h, INK);
        g.fill(x, y, x + w, y + 1, 0xFF42685B);
        g.fill(x, y, x + 2, y + h, JADE);
        g.fill(x + w - 12, y + h - 2, x + w, y + h, GOLD);
    }
    public static void seal(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 7; i++) {
            int span = 7 - Math.abs(3 - i) * 2;
            g.fill(x + 7 - span, y + i * 2, x + 7 + span, y + i * 2 + 2, color);
        }
        g.fill(x + 5, y + 4, x + 9, y + 10, INK);
    }
    public static String fit(String s, int width) {
        var font = Minecraft.getInstance().font;
        return font.width(s) <= width ? s : font.plainSubstrByWidth(s, Math.max(0, width - font.width("…"))) + "…";
    }
    public static final class Action extends Button {
        private final boolean primary;
        private float hover;
        private long lastFrame;
        public Action(int x, int y, int w, int h, String label, boolean primary, OnPress press) {
            super(x, y, w, h, Component.literal(label), press, DEFAULT_NARRATION);
            this.primary = primary;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            boolean focus = isHoveredOrFocused();
            long now = net.minecraft.Util.getMillis();
            float step = Math.min(1F, (now - lastFrame) / 110F); lastFrame = now;
            hover += ((focus && active ? 1F : 0F) - hover) * step;
            int color = !active ? 0xFF243A34 : focus ? 0xFF366651 : primary ? 0xFF24543F : 0xFF1C3830;
            g.fill(getX(), getY(), getX() + width, getY() + height, color);
            g.fill(getX(), getY() + height - 2, getX() + width, getY() + height,
                    !active ? 0xFF486056 : primary ? GOLD : JADE);
            int glow = Math.round(hover * 36);
            if (glow > 0) g.fill(getX(), getY(), getX() + width, getY() + height, (glow << 24) | 0x70E0B7);
            if (focus && active) g.renderOutline(getX(), getY(), width, height, JADE);
            var font = Minecraft.getInstance().font;
            g.drawCenteredString(font, fit(getMessage().getString(), width - 12), getX() + width / 2,
                    getY() + (height - 8) / 2, active ? TEXT : MUTED);
        }
    }
}
