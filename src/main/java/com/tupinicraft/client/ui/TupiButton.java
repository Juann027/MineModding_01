package com.tupinicraft.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Botao com textura propria (normal, hover, selecionado, desativado), animacao de hover, icone opcional
 * (textura 128x128 ou item) e selo numerico (ex.: pontos disponiveis).
 */
public class TupiButton extends AbstractButton {
    private final Consumer<TupiButton> onPress;
    private ResourceLocation icon;
    private ItemStack itemIcon = ItemStack.EMPTY;
    private boolean selected;
    private int accent = Ui.GOLD;
    private String badge = "";
    private float hover; // 0..1 suavizado
    private long lastFrame = System.nanoTime();

    public TupiButton(int x, int y, int w, int h, Component message, Consumer<TupiButton> onPress) {
        super(x, y, w, h, message);
        this.onPress = onPress;
    }

    public TupiButton icon(ResourceLocation texture) { this.icon = texture; return this; }
    public TupiButton item(ItemStack stack) { this.itemIcon = stack; return this; }
    public TupiButton accent(int color) { this.accent = color; return this; }
    public TupiButton selected(boolean value) { this.selected = value; return this; }
    public TupiButton badge(String text) { this.badge = text == null ? "" : text; return this; }
    public TupiButton tooltip(Component text) { setTooltip(net.minecraft.client.gui.components.Tooltip.create(text)); return this; }
    public boolean isSelected() { return selected; }

    @Override
    public void onPress() { onPress.accept(this); }

    @Override
    public void playDownSound(SoundManager manager) { Ui.playClick(selected ? 1.2F : 1F); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        boolean hot = active && isHoveredOrFocused();
        hover = Mth.clamp(hover + (hot ? dt * 8F : -dt * 6F), 0F, 1F);

        int state = !active ? 3 : selected ? 2 : hot ? 1 : 0;
        int lift = Math.round(hover * 1F);
        int x = getX(), y = getY() - lift, w = getWidth(), h = getHeight();
        if (hover > 0.01F && active) Ui.glow(g, x + w / 2F, y + h / 2F, Math.max(w, h) * 0.7F, Ui.alpha(accent, 0.25F * hover));
        Ui.nineSlice(g, Ui.BUTTON, x, y, w, h, 0, state * 20, 64, 20, 4, 64, 80);

        var font = Minecraft.getInstance().font;
        int textColor = !active ? 0xFF6E7883 : selected ? 0xFF2B1E05 : Ui.lerpColor(Ui.TEXT, 0xFFFFFFFF, hover);
        int contentX = x + 6;
        if (icon != null || !itemIcon.isEmpty()) {
            int size = Math.min(16, h - 4);
            int iy = y + (h - size) / 2;
            if (icon != null) Ui.icon(g, icon, contentX, iy, size);
            else g.renderItem(itemIcon, contentX, iy);
            contentX += size + 4;
        }
        Component text = getMessage();
        int textWidth = font.width(text);
        int textX = icon == null && itemIcon.isEmpty() ? x + (w - textWidth) / 2 : contentX;
        g.drawString(font, text, textX, y + (h - 8) / 2, textColor, !selected && active);
        if (!badge.isEmpty()) {
            int bw = font.width(badge) + 6;
            int bx = x + w - bw - 3, by = y + (h - 11) / 2;
            g.fill(bx, by, bx + bw, by + 11, 0xFF06080A);
            g.fill(bx + 1, by + 1, bx + bw - 1, by + 10, accent);
            g.drawString(font, badge, bx + 3, by + 2, 0xFF1A1205, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
