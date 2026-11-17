package com.tupinicraft.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base das telas do mod: painel com textura propria, sem o desfoque vanilla (que borrava o painel na 1.21.1),
 * com camada de VFX ({@link UiParticles}) por cima dos widgets. Nao pausa o jogo (multiplayer).
 */
public abstract class TupiScreen extends Screen {
    protected final UiParticles vfx = new UiParticles();
    protected int left, top, panelWidth, panelHeight;
    protected int ticks;

    protected TupiScreen(Component title) { super(title); }

    /** Define o painel centralizado, limitado ao tamanho da tela. */
    protected void layoutPanel(int preferredWidth, int preferredHeight) {
        panelWidth = Math.min(width - 8, preferredWidth);
        panelHeight = Math.min(height - 8, preferredHeight);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        vfx.tick();
    }

    /** Fundo escurecido sem desfoque. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        Ui.titledPanel(g, font, title, left, top, panelWidth, panelHeight);
        renderContent(g, mouseX, mouseY, partialTick);
        for (var renderable : renderables) renderable.render(g, mouseX, mouseY, partialTick);
        renderOverlay(g, mouseX, mouseY, partialTick);
        vfx.render(g, partialTick);
    }

    /** Conteudo entre o painel e os widgets. */
    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /** Conteudo por cima dos widgets (dicas, selos). */
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
