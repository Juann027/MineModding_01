package com.respectcraft.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.respectcraft.RespectCraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Framework de UI do RespectCraft: texturas proprias (nine-slice), fundos repetidos, brilhos e linhas.
 * Tudo em {@code GuiGraphics}; nenhuma tela usa o desfoque vanilla.
 */
public final class Ui {
    public static final ResourceLocation PANEL = RespectCraft.id("textures/gui/ui/panel.png");
    public static final ResourceLocation BUTTON = RespectCraft.id("textures/gui/ui/button.png");
    public static final ResourceLocation TAB = RespectCraft.id("textures/gui/ui/tab.png");
    public static final ResourceLocation GLOW = RespectCraft.id("textures/gui/ui/glow.png");

    // Paleta
    public static final int TEXT = 0xFFE9EEF3;
    public static final int MUTED = 0xFFAAB6C2;
    public static final int GOLD = 0xFFFFE27A;
    public static final int GREEN = 0xFF7CCF8F;
    public static final int RED = 0xFFFF7A7A;
    public static final int DARK = 0xFF0B0F14;

    private Ui() {}

    /** Desenha uma regiao de textura com bordas fixas e miolo esticado. */
    public static void nineSlice(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
                                 int u, int v, int rw, int rh, int border, int texW, int texH) {
        if (w <= 0 || h <= 0) return;
        int b = Math.min(border, Math.min(w / 2, h / 2));
        int mw = w - 2 * b, mh = h - 2 * b;
        int rmw = rw - 2 * border, rmh = rh - 2 * border;
        // cantos
        g.blit(tex, x, y, u, v, b, b, texW, texH);
        g.blit(tex, x + w - b, y, u + rw - b, v, b, b, texW, texH);
        g.blit(tex, x, y + h - b, u, v + rh - b, b, b, texW, texH);
        g.blit(tex, x + w - b, y + h - b, u + rw - b, v + rh - b, b, b, texW, texH);
        // bordas e miolo esticados
        if (mw > 0) {
            g.blit(tex, x + b, y, mw, b, u + border, v, rmw, b, texW, texH);
            g.blit(tex, x + b, y + h - b, mw, b, u + border, v + rh - b, rmw, b, texW, texH);
        }
        if (mh > 0) {
            g.blit(tex, x, y + b, b, mh, u, v + border, b, rmh, texW, texH);
            g.blit(tex, x + w - b, y + b, b, mh, u + rw - b, v + border, b, rmh, texW, texH);
        }
        if (mw > 0 && mh > 0) g.blit(tex, x + b, y + b, mw, mh, u + border, v + border, rmw, rmh, texW, texH);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, PANEL, x, y, w, h, 0, 0, 32, 32, 6, 32, 32);
    }

    /** Painel com faixa de titulo. */
    public static void titledPanel(GuiGraphics g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component title,
                                   int x, int y, int w, int h) {
        panel(g, x, y, w, h);
        g.fill(x + 5, y + 5, x + w - 5, y + 21, 0xC00B0F14);
        g.fill(x + 5, y + 21, x + w - 5, y + 22, 0xFF_D6B84A);
        g.drawString(font, title, x + 10, y + 9, GOLD, true);
    }

    /** Textura repetida (fundos das abas), com deslocamento para o efeito de arrastar. */
    public static void tile(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int size, int offX, int offY) {
        g.enableScissor(x, y, x + w, y + h);
        int startX = x - Math.floorMod(offX, size);
        int startY = y - Math.floorMod(offY, size);
        for (int ty = startY; ty < y + h; ty += size) {
            for (int tx = startX; tx < x + w; tx += size) {
                g.blit(tex, tx, ty, 0, 0, size, size, size, size);
            }
        }
        g.disableScissor();
    }

    /** Brilho radial colorido (aditivo), centrado em (cx, cy). */
    public static void glow(GuiGraphics g, float cx, float cy, float radius, int argb) {
        float a = ((argb >>> 24) & 255) / 255F;
        if (a <= 0.01F || radius <= 0.5F) return;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        g.setColor(((argb >> 16) & 255) / 255F, ((argb >> 8) & 255) / 255F, (argb & 255) / 255F, a);
        g.pose().pushPose();
        g.pose().translate(cx - radius, cy - radius, 0);
        g.pose().scale(radius / 16F, radius / 16F, 1F);
        g.blit(GLOW, 0, 0, 0, 0, 32, 32, 32, 32);
        g.pose().popPose();
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.defaultBlendFunc();
    }

    /** Linha grossa entre dois pontos (retangulo girado). */
    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) return;
        g.pose().pushPose();
        g.pose().translate(x1, y1, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        int t = Math.max(1, Math.round(thickness));
        g.fill(0, -t / 2, Math.round(len), -t / 2 + t, argb);
        g.pose().popPose();
    }

    /** Textura 128x128 (icones das habilidades/origens) desenhada no tamanho pedido. */
    public static void icon(GuiGraphics g, ResourceLocation tex, int x, int y, int size) {
        RenderSystem.enableBlend();
        g.blit(tex, x, y, size, size, 0, 0, 128, 128, 128, 128);
    }

    public static int alpha(int argb, float alpha) {
        int a = Mth.clamp(Math.round(((argb >>> 24) & 255) * alpha), 0, 255);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0F, 1F);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, aa = (a >>> 24);
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255, ba = (b >>> 24);
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Barra de progresso com moldura (0..1). */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float value, int color) {
        g.fill(x, y, x + w, y + h, 0xFF06080A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2A323C);
        int filled = Math.round((w - 2) * Mth.clamp(value, 0F, 1F));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
            g.fill(x + 1, y + 1, x + 1 + filled, y + 2, lerpColor(color, 0xFFFFFFFF, 0.35F));
        }
    }

    public static void playClick(float pitch) {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        com.respectcraft.registry.ModSounds.UI_CLICK.get(), pitch));
    }

    public static void playUi(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
