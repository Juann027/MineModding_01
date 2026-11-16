package com.tupinicraft.client.character;

import com.tupinicraft.character.CharacterAppearance;
import com.tupinicraft.network.CharacterAppearanceClientCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Menu inicial da tecla K depois que a criacao de personagem foi confirmada pelo servidor. */
public final class CharacterHubScreen extends Screen {
    private int left, top, panelWidth;

    public CharacterHubScreen() {
        super(Component.translatable("gui.tupinicraft.hub.title"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 16, 290);
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - 178) / 2);
        int buttonWidth = Math.min(220, panelWidth - 24);
        int buttonX = left + (panelWidth - buttonWidth) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.tupinicraft.hub.edit"),
                button -> Minecraft.getInstance().setScreen(new CharacterCreationScreen()))
                .bounds(buttonX, top + 106, buttonWidth, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.tupinicraft.hub.close"),
                button -> onClose())
                .bounds(buttonX, top + 136, buttonWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Fundo nitido, sem chamar renderBackground(), que acionaria o blur do jogo.
        graphics.fill(0, 0, width, height, 0x66000000);
        graphics.fill(left, top, left + panelWidth, top + 174, 0xFF88949C);
        graphics.fill(left + 2, top + 2, left + panelWidth - 2, top + 172, 0xFF262F38);
        graphics.fill(left + 3, top + 3, left + panelWidth - 3, top + 29, 0xFF131B24);
        graphics.drawString(font, title, left + 11, top + 12, 0xFFE7F0F7, false);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CharacterAppearance a = CharacterAppearanceClientCache.get(mc.player.getUUID());
            if (a.created()) {
                graphics.drawString(font, Component.translatable("gui.tupinicraft.hub.created"),
                        left + 12, top + 40, 0xFFB3E1BB, false);
                graphics.drawString(font, Component.translatable("gui.tupinicraft.creator.body").append(": ")
                                .append(Component.translatable("gui.tupinicraft.creator.body." + CharacterAppearance.BODY_IDS[a.bodyType()])),
                        left + 12, top + 59, 0xFFE7F0F7, false);
                graphics.drawString(font, Component.translatable("gui.tupinicraft.creator.hair").append(": ")
                                .append(Component.translatable("gui.tupinicraft.creator.hair." + CharacterAppearance.HAIR_IDS[a.hairStyle()])),
                        left + 12, top + 72, 0xFFE7F0F7, false);
            }
        }
        graphics.drawString(font, Component.translatable("gui.tupinicraft.hub.future"),
                left + 12, top + 90, 0xFFBCC8D0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Sem fundo vanilla: o Screen#render da 1.21.1 chamaria o blur por cima do painel ja desenhado. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public boolean isPauseScreen() { return false; }
}
