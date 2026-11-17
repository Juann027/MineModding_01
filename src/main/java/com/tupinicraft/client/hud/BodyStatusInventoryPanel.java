package com.tupinicraft.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/**
 * Painel de Body & Traits ao lado do inventário normal (somente leitura).
 *
 * <p>Mostra as necessidades reais (sincronizadas pelo servidor), sem substituir o inventário/slots.
 * Usa o lado livre do inventário; em GUIs pequenas, reduz sua largura e
 * abrevia os nomes, sem cobrir os slots do jogador.
 */
public final class BodyStatusInventoryPanel {
    private static final int PANEL_WIDTH = 128;
    private static final int PANEL_HEIGHT = 162;
    private static final int SPACING = 7;

    private static final int BACKGROUND = 0xF0_30353B;
    private static final int INNER = 0xFF_171B20;
    private static final int BORDER = 0xFF_A2ABB1;
    private static final int TEXT = 0xFF_E9EDF2;
    private static final int MUTED = 0xFF_C3C9D0;

    private BodyStatusInventoryPanel() {}

    public static void onInventoryForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!(event.getContainerScreen() instanceof InventoryScreen inventory)) return;
        if (Minecraft.getInstance().player == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = graphics.guiWidth();
        int leftSpace = inventory.getGuiLeft();
        int rightSpace = screenWidth - (inventory.getGuiLeft() + inventory.getXSize());
        // O livro de receitas ocupa a lateral esquerda quando está aberto.
        // Não desenhar o painel por cima do livro: preferir a direita ou ocultar.
        boolean recipeBookOpen = inventory.getRecipeBookComponent().isVisible();
        boolean right = recipeBookOpen || rightSpace >= leftSpace;
        int available = (right ? rightSpace : leftSpace) - SPACING - 3;
        if (available < 36) return; // escala de GUI muito pequena: não invade os slots.

        int width = Math.min(PANEL_WIDTH, available);
        int x = right ? inventory.getGuiLeft() + inventory.getXSize() + SPACING
                : inventory.getGuiLeft() - SPACING - width;
        int y = Math.min(inventory.getGuiTop() + 2, graphics.guiHeight() - PANEL_HEIGHT - 2);
        y = Math.max(2, y);

        // O evento Foreground chega com a pose ja deslocada para (guiLeft, guiTop); as contas acima estao em
        // coordenadas de tela. Sem desfazer o deslocamento o painel era desenhado fora da tela.
        graphics.pose().pushPose();
        graphics.pose().translate(-inventory.getGuiLeft(), -inventory.getGuiTop(), 0F);
        drawPanel(graphics, x, y, width, HudStatusClient.current());
        graphics.pose().popPose();
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, HudMeters status) {
        Font font = Minecraft.getInstance().font;
        boolean compact = width < 99;
        int padding = compact ? 5 : 8;

        rect(graphics, x, y, width, PANEL_HEIGHT, BORDER);
        rect(graphics, x + 2, y + 2, width - 4, PANEL_HEIGHT - 4, BACKGROUND);
        rect(graphics, x + 3, y + 3, width - 6, 17, INNER);
        String title = compact ? (width < 70 ? "ST" : "STATUS") : "BODY & TRAITS";
        graphics.drawString(font, title, x + padding, y + 7, TEXT, false);

        int step = 20;
        meter(graphics, font, x + padding, y + 24, width - 2 * padding,
                Component.translatable("need.tupinicraft.hidratacao").getString(), "H", status.hydration(), 0xFF_4FB3E8, compact);
        meter(graphics, font, x + padding, y + 24 + step, width - 2 * padding,
                Component.translatable("need.tupinicraft.energia").getString(), "E", status.energy(), 0xFF_F2CF4A, compact);
        meter(graphics, font, x + padding, y + 24 + step * 2, width - 2 * padding,
                Component.translatable("need.tupinicraft.bexiga").getString(), "B", status.bladder(), 0xFF_E8C440, compact);
        meter(graphics, font, x + padding, y + 24 + step * 3, width - 2 * padding,
                Component.translatable("need.tupinicraft.intestino").getString(), "I", status.bowel(), 0xFF_A37D51, compact);
        meter(graphics, font, x + padding, y + 24 + step * 4, width - 2 * padding,
                Component.translatable("need.tupinicraft.humor").getString(), "M", status.emotion(), 0xFF_79C8D9, compact);

        rect(graphics, x + padding, y + 125, width - padding * 2, 1, BORDER);
        var origin = com.tupinicraft.network.ProfileClientCache.profile().originOrNull();
        String originName = origin == null ? "--" : Component.translatable("origin.tupinicraft." + origin.id()).getString();
        String traits = compact ? "O: " + (origin == null ? "--" : originName.substring(0, Math.min(3, originName.length())))
                : Component.translatable("gui.tupinicraft.traits").getString() + ": " + originName;
        graphics.drawString(font, traits, x + padding, y + 131, TEXT, false);
        String footer = compact ? "K" : Component.translatable("gui.tupinicraft.hud_hint").getString();
        graphics.drawString(font, footer, x + padding, y + 146, MUTED, false);
    }

    private static void meter(GuiGraphics graphics, Font font, int x, int y,
                              int width, String title, String abbreviation,
                              int value, int fillColor, boolean compact) {
        if (width < 8) return;
        String name = compact ? abbreviation : title;
        String number = value + "%";
        int numberX = x + width - font.width(number);
        graphics.drawString(font, name, x, y, TEXT, false);
        // A barra não cobre os textos, independentemente da escala de GUI.
        if (numberX >= x + font.width(name) + 2) {
            graphics.drawString(font, number, numberX, y, MUTED, false);
        }
        rect(graphics, x, y + 10, width, 7, 0xFF_0E1319);
        rect(graphics, x + 1, y + 11, width - 2, 5, 0xFF_4B535D);
        int contentWidth = Math.max(0, width - 4);
        int filledWidth = (contentWidth * value) / 100;
        if (filledWidth > 0) rect(graphics, x + 2, y + 12, filledWidth, 3, fillColor);
    }

    private static void rect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width > 0 && height > 0) graphics.fill(x, y, x + width, y + height, color);
    }
}
